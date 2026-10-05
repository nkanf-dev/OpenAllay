package dev.openallay.neoforge;


import dev.openallay.OpenAllayRuntime;
import dev.openallay.client.ClientModelRuntimeRegistry;
import dev.openallay.settings.ClientSettingsRuntime;
import com.google.gson.Gson;
import dev.openallay.client.MinecraftGuideContextProvider;
import dev.openallay.client.MinecraftGuideHistoryScope;
import dev.openallay.guide.GuideCommandFacade;
import dev.openallay.guide.GuideLocalEndpoint;
import dev.openallay.guide.GuideServiceManager;
import dev.openallay.guide.PayloadGuideRemoteEndpoint;
import dev.openallay.guide.history.GuideHistoryCodec;
import dev.openallay.guide.history.GuideHistoryRepository;
import dev.openallay.guide.history.SqliteGuideHistoryStore;
import dev.openallay.guide.e2e.GuideClientE2EConfig;
import dev.openallay.guide.e2e.GuideClientE2EController;
import dev.openallay.client.gui.OpenAllayKeyMappings;
import dev.openallay.client.gui.GuideClientUiCoordinator;
import dev.openallay.guide.ui.GuideDisplayRuntime;
import dev.openallay.settings.ClientSettingsHistoryBinding;
import dev.openallay.tool.ToolResult;
import dev.openallay.recipe.config.RecipeClientRuntime;
import net.minecraft.client.Minecraft;
import dev.openallay.neoforge.network.NeoForgeClientBridge;

public final class OpenAllayNeoForgeClient {
    private static final java.util.concurrent.atomic.AtomicBoolean REGISTERED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean STARTED =
            new java.util.concurrent.atomic.AtomicBoolean();

    // Installed before native startup; the stable layer resolves the later runtime.
    private static volatile GuideClientUiCoordinator ui;

    private OpenAllayNeoForgeClient() {}

    public static void initialize(OpenAllayRuntime runtime) {
        if (!REGISTERED.compareAndSet(false, true)) return;
        NeoForgeNativeClientEvents.onSystemChat(runtime.commands()::acceptFeedback);
        NeoForgeClientBridge bridge = new NeoForgeClientBridge();
        bridge.register();
        NeoForgeNativeClientEvents.registerKeys();
        var resourceReloadRegistration = NeoForgeNativeResourceReloadRegistration.install();
        NeoForgeNativeHudRegistration.register(graphics -> {
            GuideClientUiCoordinator current = ui;
            if (current != null) current.extractRenderState(graphics);
        });
        NeoForgeNativeClientLifecycle.onStarted(client -> start(runtime, bridge, client, resourceReloadRegistration));
    }

    private static void start(
            OpenAllayRuntime runtime,
            NeoForgeClientBridge bridge,
            Minecraft client,
            java.util.function.Function<Runnable, Runnable> resourceReloadRegistration) {
        if (!STARTED.compareAndSet(false, true)) return;
        Gson gson = new Gson();
        java.time.Clock clock = java.time.Clock.systemUTC();
        var dispatcher = (dev.openallay.client.ClientEventDispatcher)
                client::execute;
        java.nio.file.Path configDirectory = NeoForgeNativeLoaderFacts.configDir().resolve("openallay");
        GuideDisplayRuntime display = new GuideDisplayRuntime(
                configDirectory.resolve("display.json"));
        ClientSettingsHistoryBinding historySettings = new ClientSettingsHistoryBinding();
        RecipeClientRuntime recipeClient = new RecipeClientRuntime(
                configDirectory.resolve("recipes.json"));
        ToolResult<ClientSettingsRuntime> settingsResult = ClientSettingsRuntime.create(
                runtime,
                configDirectory.resolve("models.json"),
                configDirectory.resolve("model-metadata.json"),
                configDirectory.resolve("capabilities.json"),
                configDirectory.resolve("recipes.json"),
                recipeClient,
                System.getenv(),
                dispatcher,
                bridge.remoteTools(),
                clock,
                display,
                historySettings);
        ClientSettingsRuntime settings =
                settingsResult instanceof ToolResult.Success<ClientSettingsRuntime> success
                        ? success.value()
                        : null;
        ClientModelRuntimeRegistry modelRegistry =
                settings == null ? null : settings.models();
        GuideLocalEndpoint local = modelRegistry;
        MinecraftGuideContextProvider contexts = new MinecraftGuideContextProvider(
                runtime,
                client,
                gson,
                OpenAllayNeoForgeClient.class.getClassLoader(),
                recipeClient);
        if (settings != null) contexts.setUnrestrictedJavascriptRuntime(settings.unrestrictedJavascript());
        bridge.configureClientTools(
                () -> modelRegistry == null
                        ? dev.openallay.agent.tool.ToolRuntimeCatalog.empty()
                        : modelRegistry.capabilities().localTools(),
                (required, correlation, cancellation) -> {
                    java.util.function.BooleanSupplier admitted = bridge.clientToolAdmission(correlation);
                    java.util.concurrent.CompletableFuture<
                            dev.openallay.context.ToolInvocationContext> captured =
                            new java.util.concurrent.CompletableFuture<>();
                    client.execute(() -> {
                        if (cancellation.isCancelled() || !admitted.getAsBoolean()) {
                            captured.completeExceptionally(
                                    new dev.openallay.model.ModelClientException(
                                            new dev.openallay.model.ModelFailure(
                                                    "agent_cancelled",
                                                    "Client Tool context capture was cancelled",
                                                    null)));
                            return;
                        }
                        contexts.associateInputObservation(correlation,
                                bridge.clientToolInputObservation(correlation));
                        ToolResult<dev.openallay.context.ToolInvocationContext> result =
                                contexts.captureServerToolContext(required, correlation);
                        if (result instanceof ToolResult.Success<
                                dev.openallay.context.ToolInvocationContext> success) {
                            captured.complete(success.value());
                        } else {
                            ToolResult.Failure<dev.openallay.context.ToolInvocationContext> failure =
                                    (ToolResult.Failure<
                                            dev.openallay.context.ToolInvocationContext>) result;
                            captured.completeExceptionally(new IllegalStateException(
                                    failure.code() + ": " + failure.message()));
                        }
                    });
                    return captured;
                },
                gson);
        PayloadGuideRemoteEndpoint remote = new PayloadGuideRemoteEndpoint(
                new PayloadGuideRemoteEndpoint.Port() {
                    @Override public dev.openallay.bridge.protocol.CapabilityPayload capabilities() {
                        return bridge.capabilities();
                    }
                    @Override public boolean ask(
                            dev.openallay.bridge.protocol.ServerAgentRequestPayload request,
                            java.util.function.Consumer<dev.openallay.bridge.protocol.ServerAgentEventPayload> events) {
                        return bridge.askServer(request, events);
                    }
                    @Override public boolean steer(
                            dev.openallay.bridge.protocol.ServerAgentSteerPayload payload) {
                        return bridge.steerServer(payload);
                    }
                    @Override public boolean cancel(java.util.UUID requestId) {
                        return bridge.cancelServer(requestId);
                    }
                    @Override public void disconnect() { bridge.disconnectState(); }
                },
                gson,
                dispatcher);
        dev.openallay.model.image.ImageAttachmentStore imageStore =
                new dev.openallay.model.image.FileImageAttachmentStore(
                        NeoForgeNativeLoaderFacts.configDir().resolve("openallay/images"));
        runtime.worldObservations().configureImages(actor -> imageStore);
        bridge.configureResultImages(imageStore, contexts);
        GuideHistoryRepository history = new GuideHistoryRepository(new SqliteGuideHistoryStore(
                NeoForgeNativeLoaderFacts.configDir().resolve("openallay/history.sqlite3"),
                clock,
                new GuideHistoryCodec(), imageStore));
        GuideServiceManager services = new GuideServiceManager(
                local,
                remote,
                contexts,
                dispatcher,
                clock,
                gson,
                history,
                new MinecraftGuideHistoryScope(client),
                imageStore);
        historySettings.bind(services);
        GuideClientUiCoordinator coordinator = new GuideClientUiCoordinator(client, services,
                recipeClient, display, settings == null ? null : settings.settings(),
                configDirectory, dispatcher, clock, resourceReloadRegistration);
        var observationInput = dev.openallay.client.observation.ObservationUiBindings.bind(
                client, runtime.platform(), runtime.worldObservations(), coordinator, services);
        ui = coordinator;
        bridge.onDisconnect(() -> {
            observationInput.clearConnectionState();
            coordinator.disconnect();
            if (settings != null) settings.settings().clearServerModel();
            services.disconnect();
        });
        NeoForgeNativeClientLifecycle.onStopping(() -> {
            observationInput.close();
            coordinator.close();
            ui = null;
            services.shutdown()
                        .handle((ignored, failure) -> null)
                        .thenCompose(ignored -> dev.openallay.OpenAllayBootstrap.shutdownExtensions())
                        .thenCompose(ignored -> history.closeAsync())
                        .thenCompose(ignored -> settings == null
                                ? java.util.concurrent.CompletableFuture.completedFuture(null)
                                : settings.closeAsync());
        });
        bridge.onCapabilitiesChanged(() -> {
            if (settings != null) {
                settings.settings().replaceServerModel(bridge.capabilities());
            }
            var current = services.current();
            if (current != null) current.refreshCapabilities();
        });
        if (settings != null) {
            settings.settings().replaceServerModel(bridge.capabilities());
        }
        java.util.function.Consumer<dev.openallay.guide.GuideService> showGuide = coordinator::openGuide;
        dev.openallay.guide.GuideScreenOpener screens = service -> {
            showGuide.accept(service);
            return new ToolResult.Success<>(true);
        };
        NeoForgeGuideCommands.register(new GuideCommandFacade(
                runtime,
                services,
                contexts,
                screens));
        NeoForgeNativeClientEvents.onEndTick(() -> {
            while (OpenAllayKeyMappings.OPEN_GUIDE.consumeClick()) {
                if (client.player != null && client.level != null
                        && dev.openallay.client.gui.MinecraftClientWindow.screen(client) == null && dev.openallay.client.gui.MinecraftClientWindow.overlay(client) == null) {
                    screens.open(services.forActor(client.player.getUUID()));
                }
            }
            coordinator.tick();
        });
        GuideClientE2EConfig.from(System.getProperties()).ifPresent(config -> {
            String modVersion = NeoForgeNativeLoaderFacts.modVersion();
            GuideClientE2EController controller = new GuideClientE2EController(
                    config,
                    "neoforge",
                    runtime.platform().gameVersion(),
                    modVersion,
                    services,
                    gson,
                    client::stop,
                    contexts::recipeProviderReadiness,
                    settings == null ? null : settings.settings(),
                    modelRegistry == null ? null : modelRegistry::encodedTrace);
            controller.attachGraphicalProbe(ui::openGuide, ui::e2eHudReceipt, ui::e2eVoiceSettings);
            if (Boolean.getBoolean(GuideClientE2EConfig.ENABLED)) {
                controller.attachGraphicalToastReceipt(ui::e2eNotificationReceipt);
                controller.attachNativeCommandProbe(runtime, contexts);
            }
            NeoForgeNativeClientEvents.onEndTick(() -> {
                controller.tick(client.player == null ? null : client.player.getUUID());
            });
        });
    }
}
