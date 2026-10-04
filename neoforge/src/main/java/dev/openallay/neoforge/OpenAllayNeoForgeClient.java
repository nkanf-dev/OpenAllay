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
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.minecraft.resources.Identifier;
import dev.openallay.guide.ui.GuideDisplayRuntime;
import dev.openallay.settings.ClientSettingsHistoryBinding;
import dev.openallay.tool.ToolResult;
import dev.openallay.recipe.config.RecipeClientRuntime;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.bus.api.IEventBus;
import dev.openallay.neoforge.network.NeoForgeClientBridge;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class OpenAllayNeoForgeClient {
    private static final java.util.concurrent.atomic.AtomicBoolean REGISTERED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicBoolean STARTED =
            new java.util.concurrent.atomic.AtomicBoolean();

    // Installed before ClientStartedEvent; the stable layer resolves the later runtime.
    private static volatile GuideClientUiCoordinator ui;

    private OpenAllayNeoForgeClient() {}

    public static void initialize(OpenAllayRuntime runtime, IEventBus modBus) {
        if (!REGISTERED.compareAndSet(false, true)) return;
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent.System event) -> {
            Minecraft client = Minecraft.getInstance();
            if (!event.isOverlay() && client.player != null) {
                runtime.commands().acceptFeedback(
                        client.player.getUUID(), event.getMessage().getString());
            }
        });
        NeoForgeClientBridge bridge = new NeoForgeClientBridge();
        bridge.register(modBus);
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.registerCategory(OpenAllayKeyMappings.CATEGORY);
            OpenAllayKeyMappings.all().forEach(event::register);
        });
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerBelow(
                net.neoforged.neoforge.client.gui.VanillaGuiLayers.CHAT,
                Identifier.fromNamespaceAndPath("openallay", "guide_hud"),
                (graphics, deltaTracker) -> {
                    GuideClientUiCoordinator current = ui;
                    if (current != null) current.extractRenderState(graphics);
                }));
        NeoForge.EVENT_BUS.addListener((ClientStartedEvent event) ->
                start(runtime, bridge, event.getClient()));
    }

    private static void start(
            OpenAllayRuntime runtime,
            NeoForgeClientBridge bridge,
            Minecraft client) {
        if (!STARTED.compareAndSet(false, true)) return;
        Gson gson = new Gson();
        java.time.Clock clock = java.time.Clock.systemUTC();
        var dispatcher = (dev.openallay.client.ClientEventDispatcher)
                client::execute;
        java.nio.file.Path configDirectory = FMLPaths.CONFIGDIR.get().resolve("openallay");
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
                        FMLPaths.CONFIGDIR.get().resolve("openallay/images"));
        runtime.worldObservations().configureImages(actor -> imageStore);
        bridge.configureResultImages(imageStore, contexts);
        GuideHistoryRepository history = new GuideHistoryRepository(new SqliteGuideHistoryStore(
                FMLPaths.CONFIGDIR.get().resolve("openallay/history.sqlite3"),
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
                configDirectory, dispatcher, clock);
        var observationInput = dev.openallay.client.observation.ObservationUiBindings.bind(
                client, runtime.platform(), runtime.worldObservations(), coordinator, services);
        ui = coordinator;
        bridge.onDisconnect(() -> {
            observationInput.clearConnectionState();
            coordinator.disconnect();
            if (settings != null) settings.settings().clearServerModel();
            services.disconnect();
        });
        NeoForge.EVENT_BUS.addListener((ClientStoppingEvent event) -> {
            observationInput.close();
            coordinator.close();
            ui = null;
            services.shutdown()
                        .handle((ignored, failure) -> null)
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
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            while (OpenAllayKeyMappings.OPEN_GUIDE.consumeClick()) {
                if (client.player != null && client.level != null
                        && client.gui.screen() == null && client.gui.overlay() == null) {
                    screens.open(services.forActor(client.player.getUUID()));
                }
            }
            coordinator.tick();
        });
        GuideClientE2EConfig.from(System.getProperties()).ifPresent(config -> {
            String modVersion = ModList.get().getModContainerById("openallay")
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
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
            if (Boolean.getBoolean(GuideClientE2EConfig.ENABLED))
                controller.attachGraphicalToastReceipt(ui::e2eNotificationReceipt);
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
                controller.tick(client.player == null ? null : client.player.getUUID());
            });
        });
    }
}
