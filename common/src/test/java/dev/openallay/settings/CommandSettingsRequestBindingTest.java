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
                assertInstanceOf(ToolResult.Success.class,
                        settings.settings().saveUnrestrictedJavascript(unrestricted).join());
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
    void fullAccessIncludesCommandsWithoutChangingTheDisabledCommandOnlySetting() {
        Fixture fixture = fixture();
        ClientSettingsRuntime settings = settings(fixture);
        try (ClientSettingsService ignored = settings.settings()) {
            for (boolean unrestricted : List.of(false, true)) {
                assertInstanceOf(ToolResult.Success.class,
                        settings.settings().saveUnrestrictedJavascript(unrestricted).join());
                assertFalse(settings.settings().snapshot().experimentalCommands().enabled());
                assertFalse(fixture.commands.enabled());
                assertEquals(unrestricted, settings.models().capabilities().skills()
                        .find(SkillCatalogSnapshot.GAME_COMMANDS).isPresent());
                String correlation = "disabled-" + unrestricted;
                fixture.contexts.freezeRequest(correlation, true);
                capture(fixture, correlation);
                ToolInvocationContext context = context(correlation, unrestricted);
                assertBinding(fixture, settings.models().capabilities().forRequest(context), context, unrestricted);
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
            var disabled = context("submitted-disabled", false);
            assertBinding(fixture, settings.models().capabilities().forRequest(disabled), disabled, false);

            fixture.contexts.freezeRequest("submitted-enabled", true);
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveExperimentalCommands(false).join());
            capture(fixture, "submitted-enabled");
            var enabled = context("submitted-enabled", false);
            // The latest published catalog retains eligible guidance for the frozen enabled request.
            assertBinding(fixture, settings.models().capabilities().forRequest(enabled), enabled, true);

            fixture.contexts.freezeRequest("next-disabled", true);
            capture(fixture, "next-disabled");
            var next = context("next-disabled", false);
            assertBinding(fixture, settings.models().capabilities().forRequest(next), next, false);
            fixture.contexts.closeRequest("submitted-enabled");
            assertFalse(fixture.commands.availableFor("submitted-enabled"));
            assertFalse(fixture.commands.freezeRequest("submitted-enabled"));
        }
    }

    @Test
    void fullAccessChangesAffectOnlyFutureRequestsAndLeaveTheCommandToggleFalse() {
        Fixture fixture = fixture();
        ClientSettingsRuntime settings = settings(fixture);
        try (ClientSettingsService ignored = settings.settings()) {
            fixture.contexts.freezeRequest("before-full", true);
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveUnrestrictedJavascript(true).join());
            capture(fixture, "before-full");
            var before = context("before-full", settings.unrestrictedJavascript().enabledFor("before-full"));
            assertBinding(fixture, settings.models().capabilities().forRequest(before), before, false);

            fixture.contexts.freezeRequest("captured-full", true);
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveUnrestrictedJavascript(false).join());
            fixture.contexts.freezeRequest("captured-full", true);
            capture(fixture, "captured-full");
            var full = context("captured-full", settings.unrestrictedJavascript().enabledFor("captured-full"));
            assertTrue(full.unrestrictedJavascript());
            assertBinding(fixture, settings.models().capabilities().forRequest(full), full, true);
            assertFalse(settings.settings().snapshot().experimentalCommands().enabled());
            assertFalse(fixture.commands.enabled());
            assertFalse(settings.models().capabilities().skills()
                    .find(SkillCatalogSnapshot.GAME_COMMANDS).isPresent());

            fixture.contexts.freezeRequest("after-full", true);
            capture(fixture, "after-full");
            var after = context("after-full", settings.unrestrictedJavascript().enabledFor("after-full"));
            assertBinding(fixture, settings.models().capabilities().forRequest(after), after, false);
            fixture.contexts.closeRequest("captured-full");
            assertFalse(fixture.commands.availableFor("captured-full"));
            assertFalse(settings.unrestrictedJavascript().enabledFor("captured-full"));
        }
    }

    @Test
    void fullAccessWithoutCapturedRouteDoesNotAdvertiseCommands() {
        Fixture fixture = fixture();
        ClientSettingsRuntime settings = settings(fixture);
        try (ClientSettingsService ignored = settings.settings()) {
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveUnrestrictedJavascript(true).join());
            fixture.contexts.freezeRequest("full-no-route", true);
            var context = context("full-no-route", true);
            assertTrue(fixture.commands.enabledFor(context.correlationId()));
            assertBinding(fixture, settings.models().capabilities().forRequest(context), context, false);
        }
    }

    @Test
    void fullAccessSurvivesRestartAndLaterTurningItOffKeepsTheStoredCommandToggleFalse() throws Exception {
        Fixture first = fixture();
        ClientSettingsRuntime initial = settings(first);
        try (ClientSettingsService ignored = initial.settings()) {
            assertInstanceOf(ToolResult.Success.class,
                    initial.settings().saveExperimentalCommands(false).join());
            assertInstanceOf(ToolResult.Success.class,
                    initial.settings().saveUnrestrictedJavascript(true).join());
        }
        assertEquals(new dev.openallay.script.command.CommandCapabilityConfigWriter()
                .encode(CommandCapabilityConfig.defaults()), Files.readString(
                directory.resolve("experimental-commands.json")));
        Fixture second = fixture();
        ClientSettingsRuntime restarted = settings(second);
        try (ClientSettingsService ignored = restarted.settings()) {
            assertTrue(restarted.unrestrictedJavascript().enabled());
            assertFalse(second.commands.enabled());
            assertTrue(restarted.models().capabilities().skills()
                    .find(SkillCatalogSnapshot.GAME_COMMANDS).isPresent());
            second.contexts.freezeRequest("restart-full", true);
            capture(second, "restart-full");
            var full = context("restart-full", true);
            assertBinding(second, restarted.models().capabilities().forRequest(full), full, true);
            assertInstanceOf(ToolResult.Success.class,
                    restarted.settings().saveUnrestrictedJavascript(false).join());
            second.contexts.freezeRequest("restart-restricted", true);
            capture(second, "restart-restricted");
            var restricted = context("restart-restricted", false);
            assertBinding(second, restarted.models().capabilities().forRequest(restricted), restricted, false);
            assertFalse(second.commands.enabled());
            assertFalse(restarted.settings().snapshot().experimentalCommands().enabled());
        }
        assertEquals(new dev.openallay.script.command.CommandCapabilityConfigWriter()
                .encode(CommandCapabilityConfig.defaults()), Files.readString(
                directory.resolve("experimental-commands.json")));
    }

    @Test
    void serverOriginNeverInheritsTheCurrentClientLocalFullAccessSetting() {
        Fixture fixture = fixture();
        ClientSettingsRuntime settings = settings(fixture);
        try (ClientSettingsService ignored = settings.settings()) {
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveUnrestrictedJavascript(true).join());
            fixture.contexts.freezeRequest("server-full-off-command", false);
            assertFalse(fixture.commands.enabledFor("server-full-off-command"));
            assertFalse(settings.unrestrictedJavascript().enabledFor("server-full-off-command"));
            capture(fixture, "server-full-off-command");
            var server = context("server-full-off-command", false);
            assertBinding(fixture, settings.models().capabilities().forRequest(server), server, false);
            fixture.contexts.freezeRequest("local-full-off-command", true);
            capture(fixture, "local-full-off-command");
            var local = context("local-full-off-command", true);
            assertBinding(fixture, settings.models().capabilities().forRequest(local), local, true);
        }
    }

    @Test
    void providerFreezesAuthorityBeforeNativeCaptureAndNativeCaptureUsesTheFrozenDecision() throws Exception {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path root = current.getFileName().toString().equals("common") ? current.getParent() : current;
        String source = Files.readString(root.resolve(
                "common/src/main/java/dev/openallay/client/MinecraftGuideContextProvider.java"));
        int freeze = source.indexOf("private boolean freezeJavascriptAndCommands(");
        String freezeBody = source.substring(freeze, source.indexOf("public void closeRequest(", freeze));
        int javascriptFreeze = freezeBody.indexOf("javascript.freeze(correlationId)");
        int commandFreeze = freezeBody.indexOf("runtime.commands().freezeRequest(correlationId, unrestricted)");
        assertTrue(javascriptFreeze >= 0 && commandFreeze > javascriptFreeze);
        assertTrue(freezeBody.contains("clientLocalModel && javascript != null"));
        assertFalse(freezeBody.contains("javascript.enabled()"));
        int capture = source.indexOf("private ToolResult<ToolInvocationContext> capture(");
        String captureBody = source.substring(capture, source.indexOf("public RecipeProviderReadiness", capture));
        assertTrue(captureBody.indexOf("freezeJavascriptAndCommands(correlationId, clientLocalModel)")
                < captureBody.indexOf("client.player == null"));
        assertFalse(source.contains("freezeJavascriptRequest("));
        String nativeCapture = Files.readString(root.resolve(
                "common/src/main/java/dev/openallay/script/command/MinecraftCommandCapture.java"));
        assertTrue(nativeCapture.contains("if (!runtime.enabledFor(correlationId))"));
        assertFalse(nativeCapture.contains("runtime.freezeRequest(correlationId)"));
        assertFalse(nativeCapture.contains("unrestrictedJavascript"));
        assertTrue(nativeCapture.contains("cancellation.throwIfCancelled()"));
        assertTrue(nativeCapture.contains("client.player.getUUID().equals(expectedActor)"));
        assertTrue(nativeCapture.contains("client.getConnection().sendCommand(command)"));
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
            var context = context("no-route", false);
            assertTrue(fixture.commands.enabledFor(context.correlationId()));
            assertBinding(fixture, settings.models().capabilities().forRequest(context), context, false);
        }
    }

    @Test
    void explicitSkillDenyIsPreservedWhenFullAccessIncludesCommands() {
        Fixture fixture = fixture();
        ClientSettingsRuntime settings = settings(fixture);
        try (ClientSettingsService ignored = settings.settings()) {
            assertInstanceOf(ToolResult.Success.class, settings.settings().saveCapabilities(
                    new CapabilityPolicy(Set.of(), Set.of(SkillCatalogSnapshot.GAME_COMMANDS))).join());
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveUnrestrictedJavascript(true).join());
            assertFalse(fixture.commands.enabled());
            fixture.contexts.freezeRequest("denied-guide", true);
            capture(fixture, "denied-guide");
            var context = context("denied-guide", true);
            var request = settings.models().capabilities().forRequest(context);
            assertFalse(request.skills().find(SkillCatalogSnapshot.GAME_COMMANDS).isPresent());
            assertTrue(request.commandCapabilityAvailable(context.correlationId()));
            assertEquals("object", invoke(fixture, context).get("commands").getAsString());
        }
    }

    @Test
    void fullAccessDoesNotReEnableAUserDisabledJavascriptTool() {
        Fixture fixture = fixture();
        ClientSettingsRuntime settings = settings(fixture);
        try (ClientSettingsService ignored = settings.settings()) {
            Set<String> dependentSkills = fixture.product.skills().metadata().stream()
                    .filter(skill -> skill.allowedTools().contains(RunJavascriptTool.ID))
                    .map(skill -> skill.name()).collect(java.util.stream.Collectors.toSet());
            assertInstanceOf(ToolResult.Success.class, settings.settings().saveCapabilities(
                    new CapabilityPolicy(Set.of(RunJavascriptTool.ID), dependentSkills)).join());
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveUnrestrictedJavascript(true).join());
            fixture.contexts.freezeRequest("disabled-tool", true);
            capture(fixture, "disabled-tool");
            var request = settings.models().capabilities().forRequest(context("disabled-tool", true));
            assertTrue(fixture.commands.availableFor("disabled-tool"));
            assertTrue(request.localTools().find(RunJavascriptTool.ID).isEmpty());
            assertFalse(request.commandCapabilityAvailable("disabled-tool"));
            assertTrue(request.skills().find(SkillCatalogSnapshot.GAME_COMMANDS).isEmpty());
        }
    }

    @Test
    void unrestrictedReloadPublishesEffectiveGuidanceWithoutChangingFrozenRequests() throws Exception {
        Fixture fixture = fixture();
        ClientSettingsRuntime settings = settings(fixture);
        try (ClientSettingsService ignored = settings.settings()) {
            fixture.contexts.freezeRequest("before-full-reload", true);
            var store = new dev.openallay.script.UnrestrictedJavascriptConfigStore(
                    directory.resolve("unrestricted-javascript.json"));
            assertInstanceOf(ToolResult.Success.class, store.save(
                    new dev.openallay.script.UnrestrictedJavascriptConfig(true)));
            assertInstanceOf(ToolResult.Success.class, publishUnrestricted(
                    fixture.product, settings, store, store.reload()));
            assertTrue(settings.unrestrictedJavascript().enabled());
            assertFalse(fixture.commands.enabled());
            assertTrue(settings.models().capabilities().skills()
                    .find(SkillCatalogSnapshot.GAME_COMMANDS).isPresent());
            capture(fixture, "before-full-reload");
            var before = context("before-full-reload", false);
            assertBinding(fixture, settings.models().capabilities().forRequest(before), before, false);
            fixture.contexts.freezeRequest("after-full-reload", true);
            capture(fixture, "after-full-reload");
            var after = context("after-full-reload", true);
            assertBinding(fixture, settings.models().capabilities().forRequest(after), after, true);
        }
    }

    @Test
    void failedFullAccessPublicationRestoresRuntimeGuidanceAndItsOwnStoreOnly() throws Exception {
        Fixture fixture = fixture();
        ClientSettingsRuntime settings = settings(fixture);
        try (ClientSettingsService ignored = settings.settings()) {
            assertInstanceOf(ToolResult.Success.class,
                    settings.settings().saveExperimentalCommands(false).join());
            String commandDocument = Files.readString(directory.resolve("experimental-commands.json"));
            dev.openallay.FeatureServices unavailableTools = new dev.openallay.FeatureServices() {
                public ToolRegistry tools() { return new ToolRegistry(); }
                public SkillRepository skills() { return fixture.product.skills(); }
                public PlatformService platform() { return fixture.product.platform(); }
                public CommandCapabilityRuntime commands() { return fixture.commands; }
                public dev.openallay.extension.OpenAllayExtensionRegistry extensions() {
                    return fixture.product.extensions();
                }
                public JavascriptDataModuleRegistry javascriptModules() { return fixture.product.javascriptModules(); }
                public KnowledgeRegistry knowledge() { return fixture.product.knowledge(); }
                public dev.openallay.capability.CapabilitySettingsCatalog capabilitySettings() {
                    return fixture.product.capabilitySettings();
                }
            };
            var store = new dev.openallay.script.UnrestrictedJavascriptConfigStore(
                    directory.resolve("unrestricted-javascript.json"));
            var result = publishUnrestricted(unavailableTools, settings, store, store.save(
                    new dev.openallay.script.UnrestrictedJavascriptConfig(true)));
            assertEquals("capability_dependency_conflict",
                    assertInstanceOf(ToolResult.Failure.class, result).code());
            assertFalse(settings.unrestrictedJavascript().enabled());
            assertFalse(fixture.commands.enabled());
            assertFalse(fixture.product.skills().snapshot(Set.of())
                    .find(SkillCatalogSnapshot.GAME_COMMANDS).isPresent());
            assertFalse(success(store.reload()).value().enabled());
            assertEquals(commandDocument, Files.readString(directory.resolve("experimental-commands.json")));
        }
    }

    @SuppressWarnings("unchecked")
    private ToolResult<dev.openallay.script.UnrestrictedJavascriptConfig> publishUnrestricted(
            dev.openallay.FeatureServices product, ClientSettingsRuntime settings,
            dev.openallay.script.UnrestrictedJavascriptConfigStore store,
            ToolResult<dev.openallay.script.UnrestrictedJavascriptConfig> loaded) throws Exception {
        var capabilities = new dev.openallay.settings.capability.CapabilitySettingsBackend(
                directory.resolve("capabilities.json"), product, settings.models());
        var publish = ClientSettingsRuntime.class.getDeclaredMethod("publishUnrestrictedConfig",
                ToolResult.class, dev.openallay.script.UnrestrictedJavascriptConfigStore.class,
                dev.openallay.script.UnrestrictedJavascriptRuntime.class, dev.openallay.FeatureServices.class,
                dev.openallay.settings.capability.CapabilitySettingsBackend.class);
        publish.setAccessible(true);
        return (ToolResult<dev.openallay.script.UnrestrictedJavascriptConfig>) publish.invoke(null,
                loaded, store, settings.unrestrictedJavascript(), product, capabilities);
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
        ClientSettingsRuntime settings = CommandSettingsRequestBindingTest.<ClientSettingsRuntime>success(
                ClientSettingsRuntime.create(fixture.product, directory.resolve("models.json"),
                        directory.resolve("model-metadata.json"), Map.of(), Runnable::run, null,
                        Clock.systemUTC(), GuideDisplayConfig.defaults())).value();
        fixture.contexts.setUnrestrictedJavascriptRuntime(settings.unrestrictedJavascript());
        return settings;
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
