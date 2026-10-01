package dev.openallay.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.openallay.OpenAllayRuntime;
import dev.openallay.capability.CapabilityPolicy;
import dev.openallay.capability.ClientCapabilitySnapshot;
import dev.openallay.client.MinecraftGuideContextProvider;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.devmode.DevelopmentToolInspector;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.integration.patchouli.PatchouliMultiblockStore;
import dev.openallay.knowledge.KnowledgeRegistry;
import dev.openallay.model.CancellationSignal;
import dev.openallay.platform.PlatformService;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.command.CommandCapabilityConfig;
import dev.openallay.script.command.CommandCapabilityRuntime;
import dev.openallay.script.command.CommandCatalogSnapshot;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.script.workspace.AgentResultWorkspaceRegistry;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.skill.LoadSkillTool;
import dev.openallay.skill.SkillCatalogSnapshot;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.tool.ToolRegistry;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.builtin.RunJavascriptTool;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises setting publication, request freeze, the real Tool catalog, and the Rhino global. */
final class CommandSettingsRequestBindingTest {
    @TempDir Path directory;
    private static final UUID ACTOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void enabledSettingPublishesCompleteBindingForSafeAndUnrestrictedLocalRequests() {
        Fixture fixture = fixture();
        ClientSettingsRuntime settings = settings(fixture);
        try (ClientSettingsService ignored = settings.settings()) {
            assertFalse(settings.settings().snapshot().experimentalCommands().enabled());
            assertFalse(fixture.commands.enabled());
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveExperimentalCommands(true).join());
            assertTrue(settings.settings().snapshot().experimentalCommands().enabled());
            assertTrue(fixture.commands.enabled());
            for (boolean unrestricted : List.of(false, true)) {
                String correlation = "enabled-" + unrestricted;
                fixture.contexts.freezeRequest(correlation, true);
                capture(fixture, correlation);
                ToolInvocationContext context = context(correlation, unrestricted);
                ClientCapabilitySnapshot request = settings.models().capabilities().forRequest(context);
                assertTrue(request.skills().find(SkillCatalogSnapshot.GAME_COMMANDS).isPresent());
                assertBinding(fixture, request, context, true);
            }
        }
    }

    @Test
    void disabledSettingHasNeitherCommandSkillNorCommandGlobalInEitherMode() {
        Fixture fixture = fixture();
        ClientSettingsRuntime settings = settings(fixture);
        try (ClientSettingsService ignored = settings.settings()) {
            for (boolean unrestricted : List.of(false, true)) {
                String correlation = "disabled-" + unrestricted;
                fixture.contexts.freezeRequest(correlation, true);
                capture(fixture, correlation);
                ToolInvocationContext context = context(correlation, unrestricted);
                assertBinding(fixture, settings.models().capabilities().forRequest(context), context, false);
            }
        }
    }

    @Test
    void settingChangesDuringContextLoadingAffectNextRequestNotSubmittedRequest() {
        Fixture fixture = fixture();
        ClientSettingsRuntime settings = settings(fixture);
        try (ClientSettingsService ignored = settings.settings()) {
            fixture.contexts.freezeRequest("submitted-disabled", true);
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveExperimentalCommands(true).join());
            capture(fixture, "submitted-disabled");
            var disabled = context("submitted-disabled", true);
            assertBinding(fixture, settings.models().capabilities().forRequest(disabled), disabled, false);

            fixture.contexts.freezeRequest("submitted-enabled", true);
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveExperimentalCommands(false).join());
            capture(fixture, "submitted-enabled");
            var enabled = context("submitted-enabled", true);
            // The latest published catalog retains eligible guidance for the frozen enabled request.
            assertBinding(fixture, settings.models().capabilities().forRequest(enabled), enabled, true);

            fixture.contexts.freezeRequest("next-disabled", true);
            capture(fixture, "next-disabled");
            var next = context("next-disabled", true);
            assertBinding(fixture, settings.models().capabilities().forRequest(next), next, false);
            fixture.contexts.closeRequest("submitted-enabled");
            assertFalse(fixture.commands.availableFor("submitted-enabled"));
            assertFalse(fixture.commands.freezeRequest("submitted-enabled"));
        }
    }

    @Test
    void saveRestartAndReloadPublishTheSameBoolWithoutChangingFrozenRequests() throws Exception {
        Fixture first = fixture();
        ClientSettingsRuntime firstSettings = settings(first);
        try (ClientSettingsService ignored = firstSettings.settings()) {
            assertInstanceOf(ToolResult.Success.class,
                    firstSettings.settings().saveExperimentalCommands(true).join());
        }
        Fixture restarted = fixture();
        ClientSettingsRuntime settings = settings(restarted);
        try (ClientSettingsService ignored = settings.settings()) {
            assertTrue(settings.settings().snapshot().experimentalCommands().enabled());
            assertTrue(restarted.commands.enabled());
            restarted.contexts.freezeRequest("before-reload", true);
            Files.writeString(directory.resolve("experimental-commands.json"), "{\"enabled\":false}");
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().reloadExperimentalCommands().join());
            assertFalse(settings.settings().snapshot().experimentalCommands().enabled());
            assertFalse(restarted.commands.enabled());
            capture(restarted, "before-reload");
            var before = context("before-reload", false);
            assertBinding(restarted, settings.models().capabilities().forRequest(before), before, true);
            restarted.contexts.freezeRequest("after-reload", true);
            capture(restarted, "after-reload");
            var after = context("after-reload", false);
            assertBinding(restarted, settings.models().capabilities().forRequest(after), after, false);
        }
    }

    @Test
    void serverModelSubmissionFreezesCommandsWithoutGrantingUnrestrictedJavascript() {
        Fixture fixture = fixture();
        ClientSettingsRuntime settings = settings(fixture);
        try (ClientSettingsService ignored = settings.settings()) {
            fixture.contexts.setUnrestrictedJavascriptRuntime(settings.unrestrictedJavascript());
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveExperimentalCommands(true).join());
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveUnrestrictedJavascript(true).join());
            fixture.contexts.freezeRequest("server-request", false);
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveExperimentalCommands(false).join());
            assertTrue(fixture.commands.enabledFor("server-request"));
            assertFalse(settings.unrestrictedJavascript().enabledFor("server-request"));
            capture(fixture, "server-request");
            var context = context("server-request", false);
            assertBinding(fixture, settings.models().capabilities().forRequest(context), context, true);
        }
    }

    @Test
    void enabledToggleWithoutCapturedRouteDoesNotAdvertiseACommandGlobal() {
        Fixture fixture = fixture();
        ClientSettingsRuntime settings = settings(fixture);
        try (ClientSettingsService ignored = settings.settings()) {
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveExperimentalCommands(true).join());
            fixture.contexts.freezeRequest("no-route", true);
            var context = context("no-route", true);
            assertTrue(fixture.commands.enabledFor(context.correlationId()));
            assertBinding(fixture, settings.models().capabilities().forRequest(context), context, false);
        }
    }

    @Test
    void explicitSkillDenyIsPreservedEvenWhenTheCommandBindingIsEnabled() {
        Fixture fixture = fixture();
        ClientSettingsRuntime settings = settings(fixture);
        try (ClientSettingsService ignored = settings.settings()) {
            assertInstanceOf(ToolResult.Success.class, settings.settings().saveCapabilities(
                    new CapabilityPolicy(Set.of(), Set.of(SkillCatalogSnapshot.GAME_COMMANDS))).join());
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveExperimentalCommands(true).join());
            fixture.contexts.freezeRequest("denied-guide", true);
            capture(fixture, "denied-guide");
            var context = context("denied-guide", false);
            var request = settings.models().capabilities().forRequest(context);
            assertFalse(request.skills().find(SkillCatalogSnapshot.GAME_COMMANDS).isPresent());
            assertTrue(request.commandCapabilityAvailable(context.correlationId()));
            assertEquals("object", invoke(fixture, context).get("commands").getAsString());
        }
    }

    private void assertBinding(Fixture fixture, ClientCapabilitySnapshot request,
            ToolInvocationContext context, boolean enabled) {
        assertEquals(enabled, request.commandCapabilityAvailable(context.correlationId()));
        assertEquals(enabled, request.skills().find(SkillCatalogSnapshot.GAME_COMMANDS).isPresent());
        LoadSkillTool load = (LoadSkillTool) request.localTools().find("openallay:load_skill").orElseThrow();
        ToolResult<LoadSkillTool.Output> guide = load.invoke(context, new LoadSkillTool.Input(
                SkillCatalogSnapshot.GAME_COMMANDS));
        if (enabled) assertInstanceOf(ToolResult.Success.class, guide);
        else assertEquals("skill_not_found", assertInstanceOf(ToolResult.Failure.class, guide).code());
        JsonObject actual = invoke(fixture, context);
        assertEquals(enabled ? "object" : "undefined", actual.get("commands").getAsString());
        assertEquals(context.unrestrictedJavascript() ? "object" : "undefined",
                actual.get("java").getAsString());
        assertEquals("undefined", actual.get("mcCommands").getAsString());
        assertEquals(enabled ? List.of("function", "function", "function") : List.of(),
                actual.getAsJsonArray("methods").asList().stream().map(value -> value.getAsString()).toList());
        assertEquals(enabled ? 1 : 0, actual.get("nodes").getAsInt());
        assertFalse(actual.get("schemaHasCommands").getAsBoolean());
    }

    private JsonObject invoke(Fixture fixture, ToolInvocationContext context) {
        ToolResult.Success<RunJavascriptTool.Output> result = success(fixture.javascript.invokeAsync(
                context, new RunJavascriptTool.Input("""
                    return {
                      commands: typeof commands,
                      java: typeof Java,
                      mcCommands: typeof mc.commands,
                      methods: typeof commands === "object"
                        ? [typeof commands.list, typeof commands.describe, typeof commands.run] : [],
                      nodes: typeof commands === "object" ? commands.list().nodes.length : 0,
                      schemaHasCommands: schema.list().some(root => root.name === "commands")
                    };
                    """, List.of()), new CancellationSignal()).join());
        return fixture.workspaces.open(context.correlationId()).open(result.value().handle()).getAsJsonObject();
    }

    private static void capture(Fixture fixture, String correlation) {
        fixture.commands.capture(correlation, ACTOR,
                new CommandCatalogSnapshot(Instant.EPOCH, List.of(
                        new CommandCatalogSnapshot.CommandNodeSnapshot("say", "say", "literal", "",
                                true, "", List.of(), List.of()))),
                (actor, command, cancellation) -> CompletableFuture.completedFuture(null));
    }

    private static ToolInvocationContext context(String correlation, boolean unrestricted) {
        var base = ToolInvocationContext.developmentConsole(correlation);
        return new ToolInvocationContext(correlation, base.capturedAt(), base.caller(), base.player(),
                base.registries(), base.recipes(), base.observableGameState(), base.metrics(), unrestricted);
    }

    private ClientSettingsRuntime settings(Fixture fixture) {
        return CommandSettingsRequestBindingTest.<ClientSettingsRuntime>success(
                ClientSettingsRuntime.create(fixture.product, directory.resolve("models.json"),
                        directory.resolve("model-metadata.json"), Map.of(), Runnable::run, null,
                        Clock.systemUTC(), GuideDisplayConfig.defaults())).value();
    }

    private Fixture fixture() {
        ToolRegistry tools = new ToolRegistry();
        SkillRepository skills = new SkillRepository(new SkillParser(), List.of(RunJavascriptTool.ID));
        CommandCapabilityRuntime commands = new CommandCapabilityRuntime();
        AgentResultWorkspaceRegistry workspaces = new AgentResultWorkspaceRegistry();
        RunJavascriptTool javascript = new RunJavascriptTool(new RhinoJavascriptRuntime(),
                MinecraftAgentHostGraph::new, workspaces, new JavascriptResultPresenter(), commands);
        tools.register("test:javascript", List.of(javascript));
        tools.register("test:skills", List.of(new LoadSkillTool(skills)));
        OpenAllayRuntime product = new OpenAllayRuntime(new FakePlatform(), tools,
                new KnowledgeRegistry(), new PatchouliMultiblockStore(), new JavascriptDataModuleRegistry(),
                commands, skills, new DevelopmentToolInspector(tools), null,
                new dev.openallay.capability.CapabilitySettingsCatalog());
        // freeze/close need no live Minecraft instance. Capture is supplied deterministically above.
        var contexts = new MinecraftGuideContextProvider(product, null, new com.google.gson.Gson(),
                getClass().getClassLoader());
        return new Fixture(product, commands, javascript, workspaces, contexts);
    }

    @SuppressWarnings("unchecked")
    private static <T> ToolResult.Success<T> success(ToolResult<T> result) {
        return (ToolResult.Success<T>) assertInstanceOf(ToolResult.Success.class, result);
    }

    private record Fixture(OpenAllayRuntime product, CommandCapabilityRuntime commands,
            RunJavascriptTool javascript, AgentResultWorkspaceRegistry workspaces,
            MinecraftGuideContextProvider contexts) {}

    private static final class FakePlatform implements PlatformService {
        public String platformName() { return "test"; }
        public String gameVersion() { return "26.2-test"; }
        public boolean isModLoaded(String id) { return false; }
        public boolean isDevelopmentEnvironment() { return true; }
    }
}
