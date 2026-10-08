package dev.openallay.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.openallay.OpenAllayRuntime;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.devmode.DevelopmentToolInspector;
import dev.openallay.knowledge.KnowledgeRegistry;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import dev.openallay.platform.PlatformService;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import dev.openallay.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ClientArchitectureTest {
    @Test
    void modelCallbacksArePublishedThroughClientDispatcher() throws Exception {
        java.util.concurrent.LinkedBlockingQueue<Runnable> queued = new java.util.concurrent.LinkedBlockingQueue<>();
        ToolRegistry tools = new ToolRegistry();
        OpenAllayRuntime base = new OpenAllayRuntime(
                new FakePlatform(),
                tools,
                new KnowledgeRegistry(),
                new dev.openallay.integration.patchouli.PatchouliMultiblockStore(),
                new SkillRepository(new SkillParser(), List.of()),
                new DevelopmentToolInspector(tools),
                null);
        ClientGuideRuntime runtime = new ClientGuideRuntime(
                base,
                (request, events, cancellation) -> java.util.concurrent.CompletableFuture.completedFuture(
                        new ModelTurn("test", "test", List.of(new ModelContent.Text("answer")),
                                "end_turn", ModelUsage.empty())),
                new AgentSessionStore(),
                dev.openallay.json.EngineJson.create(),
                queued::add);
        List<AgentEvent> delivered = new ArrayList<>();

        var completed = runtime.ask(UUID.randomUUID(), "question",
                dev.openallay.context.ToolInvocationContext.developmentConsole("test"), delivered::add);
        assertEquals(0, delivered.size());
        assertTrue(!completed.isDone(), "completion waits for queued event handoff");
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!completed.isDone()) {
            long remaining = deadline - System.nanoTime();
            assertTrue(remaining > 0, "completion must arrive through the client dispatcher within the deadline");
            Runnable next = queued.poll(remaining, java.util.concurrent.TimeUnit.NANOSECONDS);
            org.junit.jupiter.api.Assertions.assertNotNull(next, "completion needs its dispatcher handoff");
            next.run();
        }
        completed.get(0, java.util.concurrent.TimeUnit.NANOSECONDS);
        assertTrue(delivered.stream().anyMatch(event -> event instanceof AgentEvent.ContextUpdated));
        assertTrue(delivered.stream().anyMatch(event -> event instanceof AgentEvent.FinalText));
        assertTrue(delivered.stream().anyMatch(event -> event instanceof AgentEvent.ModelProgress));
    }

    @Test
    void emptyClientToolCatalogIsActuallyEmpty() {
        var catalog = dev.openallay.agent.tool.ToolRuntimeCatalog.empty();
        assertTrue(catalog.descriptors().isEmpty());
        assertTrue(catalog.find("openallay:run_javascript").isEmpty());
    }

    @Test
    void bothLoadersShareSettingsHistoryAndDisplayRuntimesWithoutLoaderLeaks()
            throws Exception {
        Path root = repositoryRoot();
        List<Path> entrypoints = List.of(
                root.resolve("fabric/src/main/java/dev/openallay/fabric/OpenAllayFabricClient.java"),
                root.resolve("neoforge/src/main/java/dev/openallay/neoforge/OpenAllayNeoForgeClient.java"));
        for (Path entrypoint : entrypoints) {
            String source = Files.readString(entrypoint);
            assertTrue(source.contains("GuideDisplayRuntime"), entrypoint::toString);
            assertEquals(1, occurrences(source, "new GuideDisplayRuntime("),
                    entrypoint::toString);
            assertTrue(source.contains("configDirectory.resolve(\"display.json\")"), entrypoint::toString);
            assertTrue(source.contains("ClientSettingsHistoryBinding"), entrypoint::toString);
            assertTrue(source.contains("history.sqlite3"), entrypoint::toString);
            assertEquals(1, occurrences(source, "historySettings.bind(services)"),
                    entrypoint::toString);
            assertTrue(source.contains("ClientModelRuntimeRegistry"), entrypoint::toString);
            assertTrue(source.contains("ToolRuntimeCatalog.empty()"), entrypoint::toString);
            assertTrue(source.contains("modelRegistry == null"), entrypoint::toString);
            assertTrue(!source.contains("ToolRuntimeCatalog.from(\n                        runtime.tools().registrations()"),
                    entrypoint::toString);
            assertTrue(source.contains("models.json"), entrypoint::toString);
            assertTrue(source.contains("model-metadata.json"), entrypoint::toString);
            assertTrue(source.contains("configDirectory.resolve(\"capabilities.json\")"),
                    entrypoint::toString);
            assertTrue(source.contains("configDirectory.resolve(\"recipes.json\")"),
                    entrypoint::toString);
            assertEquals(1, occurrences(source, "new RecipeClientRuntime("),
                    entrypoint::toString);
            assertTrue(source.contains("recipeClient,\n                System.getenv()"),
                    entrypoint::toString);
            assertTrue(source.contains("ClientSettingsRuntime"), entrypoint::toString);
            assertEquals(1, occurrences(source, "new GuideServiceManager("), entrypoint::toString);
            assertEquals(1, occurrences(source, "new GuideClientUiCoordinator("), entrypoint::toString);
            assertTrue(source.contains("recipeClient, display, settings == null ? null : settings.settings()"),
                    entrypoint::toString);
            assertTrue(source.indexOf("new GuideClientUiCoordinator(") < source.indexOf("new GuideCommandFacade("),
                    "Presentation binds before command/task admission: " + entrypoint);
            assertTrue(!source.contains("new OpenAllayScreen("), "Screen ownership stays in the shared coordinator");
            assertTrue(!source.contains("new GuideClientUiState("), "No loader-local draft owner");
            assertTrue(
                    source.contains("settings.settings().replaceServerModel(bridge.capabilities())"),
                    entrypoint::toString);
            assertTrue(
                    source.contains("settings.settings().clearServerModel()"),
                    entrypoint::toString);
            assertTrue(source.contains("services.shutdown()"), entrypoint::toString);
            assertTrue(source.contains("history.closeAsync()"), entrypoint::toString);
            assertTrue(source.contains("settings.closeAsync()"), entrypoint::toString);
            assertTrue(source.indexOf("services.shutdown()")
                    < source.indexOf("history.closeAsync()"), entrypoint::toString);
            assertTrue(source.indexOf("history.closeAsync()")
                    < source.indexOf("settings.closeAsync()"), entrypoint::toString);
        }

        String facade = Files.readString(root.resolve(
                "common/src/main/java/dev/openallay/client/gui/GuideClientUiCoordinator.java"));
        String coordinator = Files.readString(root.resolve(
                "engine-core/src/main/java/dev/openallay/client/presentation/GuidePresentationCoordinator.java"));
        String host = Files.readString(root.resolve(
                "common/src/main/java/dev/openallay/client/gui/NativeGuidePresentationHost.java"));
        String states = Files.readString(root.resolve(
                "common/src/main/java/dev/openallay/client/gui/GuideClientUiStates.java"));
        assertEquals(1, occurrences(facade, "new GuidePresentationCoordinator("));
        assertEquals(1, occurrences(facade, "service -> GuideClientUiStates.create(service, dispatcher)"));
        assertEquals(1, occurrences(states, "new GuideClientUiState(service, new SystemImageClipboard()"));
        assertEquals(1, occurrences(coordinator, "state = Objects.requireNonNull(states.apply(next), \"state\")"));
        assertTrue(coordinator.contains("notificationBinding = services.listenPresentation(notifications)"));
        assertTrue(coordinator.contains("binding = services.listenPresentation(new GuidePresentationListener()"));
        assertTrue(coordinator.contains("if (closed || bound == next) return;"));
        assertTrue(coordinator.contains("notifications.tick()"));
        assertTrue(coordinator.contains("if (ownerValid.getAsBoolean()) notifications.testNotification(config)"));
        assertTrue(coordinator.contains("BooleanSupplier ownerValid = () -> valid(service, owner)"));
        assertTrue(host.contains(".withNotifications(view.notifications()).withVoice(view.voice())"));
        assertTrue(host.contains("new GuideHudEditorScreen(draft, applied, returnScreen"));
        assertTrue(host.contains("new GuideChatLiteScreen(view.service(), view.state(), display, openGuide,"));
        assertTrue(!coordinator.contains("bindCurrent()"), "Live notification binding is not a tick identity diff");
        assertTrue(coordinator.contains("client.voice"));
        assertTrue(facade.contains("client.presentation"));
        assertTrue(facade.contains("drafts -> VoiceClientRuntimes.create(configDirectory, drafts, dispatcher::execute)"));
        assertTrue(coordinator.contains("voices.apply(new VoiceRuntime.DraftPort()"));
        assertTrue(host.contains("withVoiceActions(voice)"));
        assertTrue(coordinator.contains("new GuidePresentationHost.View(service, owner, notifications, voice.input()"));
        assertTrue(coordinator.contains("bound.snapshot().actorId(), state.ownerId(), state.generation()"));
        assertTrue(coordinator.contains("bound.presentationSessionOwner(state.selectedSession())"));
        assertTrue(coordinator.contains("target.uiOwnerId(), target.uiGeneration()"));
        assertTrue(coordinator.contains("target.sessionId(), target.draftRevision()"));
        assertTrue(coordinator.contains("bound.presentationGeneration().equals(target.connectionGeneration())"));
        assertTrue(coordinator.contains("bound.presentationSessionOwner(target.sessionId()).filter(target.sessionOwner()::equals).isPresent()"));
        assertTrue(coordinator.contains("state.insertTranscript(captured, text, observationForVoice(target).orElse(null))"));
        assertTrue(coordinator.contains("state.leaseObservation(state.captureObservation(state.selectedSession()))"));
        assertTrue(coordinator.contains("finally { releaseVoiceObservation(target); }"));
        assertTrue(coordinator.contains("voiceObservations.remove(target)"));
        assertTrue(coordinator.contains("if (lease != null) lease.close()"));
        assertTrue(coordinator.contains("voice.input().setFeedbackVisible(feedback)"));
        assertTrue(host.contains("screen instanceof OpenAllayScreen ? Surface.GUIDE"));
        assertTrue(host.contains("screen instanceof GuideChatLiteScreen ? Surface.HUD_INPUT"));
        assertTrue(host.contains("GuideNativeKeyMappings.down(OpenAllayKeyMappings.VOICE_PTT)"));
        assertTrue(coordinator.contains("voice.input().pressPtt()"));
        assertTrue(coordinator.contains("voice.input().release()"));
        assertTrue(coordinator.contains("voice.input().tick(current.windowActive(), current.connected(), physicalDown, feedback)"));
        assertTrue(facade.contains("GuideVoiceIndicator.extract(graphics, minecraft, presentation.voiceInput())"));
        assertTrue(coordinator.contains("voice.close()"));
        assertTrue(coordinator.contains("GuideClientUiState owner = state;"));
        assertTrue(host.contains("new OpenAllayScreen(view.service(), recipes, display, openSettings, view.state())"));
        assertTrue(coordinator.contains("host.showSettings(() -> openGuide(service), ownerValid, voice.settings(), config ->"));
        assertTrue(host.contains("new OpenAllaySettingsScreen(settings, returnToGuide)"));
        assertTrue(coordinator.contains("host.showGuide(view(service, owner), openSettings)"));
        assertTrue(coordinator.contains("settings.saveDisplay(current.withUi(current.ui().withHud("));
        assertTrue(coordinator.contains("settings::saveDisplay"));
        assertTrue(facade.contains("renderer.extractRenderState(graphics, presentation.hud().view())"));
        for (String source : List.of(coordinator, host, facade)) {
            assertTrue(!source.contains("new GuideService("));
            assertTrue(!source.contains("new GuideDisplayRuntime("));
            assertTrue(!source.contains("new ClientSettingsRuntime("));
        }
        assertTrue(!coordinator.contains("import net.minecraft."));
        assertTrue(!coordinator.contains("import org.lwjgl."));
        assertTrue(!host.contains("new GuideClientUiState("));
        assertTrue(!facade.contains("new GuideClientUiState("));
        String closeState = coordinator.substring(coordinator.indexOf("private void closeState()"),
                coordinator.indexOf("private static void close("));
        assertTrue(closeState.indexOf("state.close()") < closeState.indexOf("state = null"));
        assertTrue(closeState.indexOf("state.close()") < closeState.indexOf("bound = null"));
        assertTrue(closeState.indexOf("state.close()") < closeState.indexOf("voice.input().cancel("));
        assertTrue(closeState.indexOf("voice.input().cancel(") < closeState.indexOf("state = null"));
        String disconnect = coordinator.substring(coordinator.indexOf("public void disconnect()"),
                coordinator.indexOf("private GuideClientUiState.Insertion voiceInsertion("));
        assertTrue(disconnect.indexOf("notifications.invalidated(bound.presentationGeneration())")
                < disconnect.indexOf("closeState()"));
        assertTrue(disconnect.indexOf("closeState()") < disconnect.indexOf("voice.input().cancel("));
        assertTrue(disconnect.contains("pttDown = false"));
        assertTrue(facade.contains("public void disconnect() { presentation.disconnect(); }"));
        String facadeClose = facade.substring(facade.indexOf("@Override public void close()"));
        assertTrue(facadeClose.contains("if (releaseResourceReload != null) releaseResourceReload.run()"));
        assertTrue(facadeClose.contains("presentation.close()"));
        assertTrue(facadeClose.indexOf("releaseResourceReload.run()") < facadeClose.indexOf("presentation.close()"),
                "Native reload binding is released before the canonical presentation closes");

        String fabricClient = Files.readString(entrypoints.getFirst());
        assertTrue(fabricClient.contains("FabricNativeHudRegistration.register(ui)"));
        String fabricHud = Files.readString(root.resolve(
                "fabric/src/main/java/dev/openallay/fabric/FabricNativeHudRegistration.java"));
        assertTrue(fabricHud.contains("HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT"));
        assertTrue(fabricClient.contains("ui::openGuide"));
        assertTrue(fabricClient.contains("ui.tick()"));
        assertTrue(fabricClient.contains("ui.disconnect()"));
        assertTrue(fabricClient.contains("ui.close()"));
        String neoForgeClient = Files.readString(entrypoints.get(1));
        String neoForgeLifecycle = Files.readString(root.resolve(
                "neoforge/src/main/java/dev/openallay/neoforge/NeoForgeNativeClientLifecycle.java"));
        assertTrue(neoForgeClient.contains("NeoForgeNativeClientLifecycle.onStarted(client -> start(runtime, bridge, client, resourceReloadRegistration))"));
        assertTrue(neoForgeLifecycle.contains("(ClientStartedEvent event) -> started.accept(event.getClient())"));
        String neoHud = Files.readString(root.resolve(
                "neoforge/src/main/java/dev/openallay/neoforge/NeoForgeNativeHudRegistration.java"));
        assertTrue(neoHud.contains("RegisterGuiLayersEvent"));
        assertTrue(neoHud.contains("VanillaGuiLayers.CHAT"));
        assertTrue(neoHud.contains("render.accept(GuideGraphics.wrap(graphics))"));
        assertTrue(neoForgeClient.indexOf("NeoForgeNativeHudRegistration.register(")
                < neoForgeClient.indexOf("private static void start("));
        assertTrue(neoForgeClient.contains("if (current != null) current.extractRenderState(graphics)"));
        assertTrue(neoForgeClient.contains("coordinator::openGuide"));
        assertTrue(neoForgeClient.contains("coordinator.tick()"));
        assertTrue(neoForgeClient.contains("coordinator.disconnect()"));
        assertTrue(neoForgeClient.contains("coordinator.close()"));
        assertTrue(neoForgeClient.contains("start(runtime, bridge, client, resourceReloadRegistration)"));
        assertTrue(neoForgeClient.contains("NeoForgeNativeResourceReloadRegistration.install()"));
        assertTrue(neoForgeClient.indexOf("NeoForgeNativeResourceReloadRegistration.install()")
                < neoForgeClient.indexOf("NeoForgeNativeClientLifecycle.onStarted("),
                "Actual native reload binding is installed before the same client runtime starts");
        assertTrue(neoForgeClient.contains("new GuideClientUiCoordinator("));
        assertTrue(neoForgeClient.contains("resourceReloadRegistration)"));
        assertTrue(neoForgeClient.indexOf("new MinecraftGuideHistoryScope(client)")
                > neoForgeClient.indexOf("private static void start("));

        List<Path> commands = List.of(
                root.resolve("fabric/src/main/java/dev/openallay/fabric/FabricGuideCommands.java"),
                root.resolve("neoforge/src/main/java/dev/openallay/neoforge/NeoForgeGuideCommands.java"));
        String fabricCommands = Files.readString(commands.get(0));
        assertTrue(fabricCommands.contains("literal(\"profile\")"));
        assertTrue(fabricCommands.contains("guide.modelProfile("));
        String neoCommands = Files.readString(commands.get(1));
        assertTrue(neoCommands.contains("GuideCommandSpec.routes()"));
        assertTrue(neoCommands.contains("GuideCommandSpec.dispatch(guide, route.action(), value, source::actor, source::publish)"));
        String sharedCommands = Files.readString(root.resolve(
                "engine-core/src/main/java/dev/openallay/command/GuideCommandSpec.java"));
        assertTrue(sharedCommands.contains("route(Action.MODEL_PROFILE, CommandArgument.ID_WORD, \"model\", \"profile\")"));
        assertTrue(sharedCommands.contains("guide.modelProfile(actor.get(), value, notices)"));

        String protocol = Files.readString(root.resolve(
                "engine-core/src/main/java/dev/openallay/bridge/protocol/BridgeProtocol.java"));
        assertTrue(!protocol.contains("VERSION"));
        assertTrue(!protocol.contains("requireVersion"));
        String serverSession = Files.readString(root.resolve(
                "engine-core/src/main/java/dev/openallay/bridge/server/ServerBridgeSession.java"));
        assertTrue(serverSession.contains("ServerModelCapabilityProjection.from("));
        assertTrue(serverSession.contains("if (remoteTools != null) return;"));
        for (Path bridge : List.of(
                root.resolve("fabric/src/main/java/dev/openallay/fabric/network/FabricServerBridge.java"),
                root.resolve("neoforge/src/main/java/dev/openallay/neoforge/network/NeoForgeServerBridge.java"))) {
            String source = Files.readString(bridge);
            assertTrue(source.contains("new ServerBridgeSession(runtime,"), bridge::toString);
            assertTrue(source.contains("session.started("), bridge::toString);
            assertFalse(source.contains("ServerModelCapabilityProjection.from("), bridge::toString);
        }
        assertTrue(Files.readString(root.resolve(
                        "fabric/src/main/java/dev/openallay/fabric/network/FabricServerBridge.java"))
                .contains("SERVER_STARTED.register(bridge::started)"));
        assertTrue(Files.readString(root.resolve(
                        "neoforge/src/main/java/dev/openallay/neoforge/network/NeoForgeServerBridge.java"))
                .contains("NeoForgeNativeServerLifecycle.register(this::started"));
        assertTrue(Files.readString(root.resolve(
                        "neoforge/src/main/java/dev/openallay/neoforge/network/NeoForgeNativeServerLifecycle.java"))
                .contains("(ServerStartedEvent event) -> started.accept(event.getServer())"));

        try (var files = Files.walk(root.resolve("common/src/main/java"))) {
            List<Path> violations = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("import net.fabricmc.")
                                    || source.contains("import net.neoforged.");
                        } catch (java.io.IOException failure) {
                            throw new java.io.UncheckedIOException(failure);
                        }
                    })
                    .toList();
            assertTrue(violations.isEmpty(), () -> "loader imports in common: " + violations);
        }
    }

    @Test
    void optionalRecipeViewerBridgesRegisterStableSourceAndNavigatorDescriptors()
            throws Exception {
        Path root = repositoryRoot();
        assertViewerBridge(
                root.resolve("common/src/main/java/dev/openallay/integration/jei/"
                        + "OpenAllayJeiBridge.java"),
                "viewer:jei");
        assertViewerBridge(
                root.resolve("common/src/main/java/dev/openallay/integration/rei/"
                        + "OpenAllayReiClientPlugin.java"),
                "viewer:rei");
    }

    private static void assertViewerBridge(Path path, String sourceId) throws Exception {
        String source = Files.readString(path);
        assertTrue(source.contains("RecipeViewerProviderRegistry.register("), path::toString);
        assertTrue(source.contains("\"" + sourceId + "\""), path::toString);
        assertTrue(source.contains("RecipeViewerNavigatorRegistry.register("), path::toString);
        assertTrue(source.contains("NativeDomainViewProviderRegistry.register("), path::toString);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("common"))
                && Files.isDirectory(current.resolve("fabric"))) {
            return current;
        }
        if (current.getFileName() != null && current.getFileName().toString().equals("common")) {
            return current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root from " + current);
    }

    private static int occurrences(String source, String needle) {
        return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static final class FakePlatform implements PlatformService {
        @Override public String platformName() { return "test"; }
        @Override public String gameVersion() { return "26.2-test"; }
        @Override public boolean isModLoaded(String modId) { return false; }
        @Override public boolean isDevelopmentEnvironment() { return true; }
    }
}
