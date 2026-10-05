package dev.openallay.fabric;

import dev.openallay.platform.minecraft.MinecraftResourceIds;

import dev.openallay.OpenAllayBootstrap;
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
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import dev.openallay.fabric.network.FabricClientBridge;

public final class OpenAllayFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OpenAllayRuntime runtime = OpenAllayBootstrap.initialize();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            Minecraft client = Minecraft.getInstance();
            if (!overlay && client.player != null) {
                runtime.commands().acceptFeedback(
                        client.player.getUUID(), message.getString());
            }
        });
        FabricClientBridge bridge = new FabricClientBridge();
        bridge.register();
        Gson gson = dev.openallay.json.EngineJson.withInstant(new Gson());
        java.time.Clock clock = java.time.Clock.systemUTC();
        var dispatcher = (dev.openallay.client.ClientEventDispatcher)
                runnable -> Minecraft.getInstance().execute(runnable);
        java.nio.file.Path configDirectory =
                FabricLoader.getInstance().getConfigDir().resolve("openallay");
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
                Minecraft.getInstance(),
                gson,
                OpenAllayFabricClient.class.getClassLoader(),
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
                    Minecraft.getInstance().execute(() -> {
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
                        FabricLoader.getInstance().getConfigDir().resolve("openallay/images"));
        runtime.worldObservations().configureImages(actor -> imageStore);
        bridge.configureResultImages(imageStore, contexts);
        GuideHistoryRepository history = new GuideHistoryRepository(new SqliteGuideHistoryStore(
                FabricLoader.getInstance().getConfigDir().resolve("openallay/history.sqlite3"),
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
                new MinecraftGuideHistoryScope(Minecraft.getInstance()),
                imageStore);
        historySettings.bind(services);
        GuideClientUiCoordinator ui = new GuideClientUiCoordinator(Minecraft.getInstance(), services,
                recipeClient, display, settings == null ? null : settings.settings(),
                configDirectory, dispatcher, clock);
        var observationInput = dev.openallay.client.observation.ObservationUiBindings.bind(
                Minecraft.getInstance(), runtime.platform(), runtime.worldObservations(), ui, services);
        FabricNativeHudRegistration.register(ui);
        bridge.onDisconnect(() -> {
            observationInput.clearConnectionState();
            ui.disconnect();
            if (settings != null) settings.settings().clearServerModel();
            services.disconnect();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            observationInput.close();
            ui.close();
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
        java.util.function.Consumer<dev.openallay.guide.GuideService> showGuide = ui::openGuide;
        dev.openallay.guide.GuideScreenOpener screens = service -> {
            showGuide.accept(service);
            return new ToolResult.Success<>(true);
        };
        FabricGuideCommands.register(new GuideCommandFacade(
                runtime,
                services,
                contexts,
                screens));
        OpenAllayKeyMappings.all().forEach(FabricNativeKeys::register);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OpenAllayKeyMappings.OPEN_GUIDE.consumeClick()) {
                if (client.player != null && client.level != null
                        && dev.openallay.client.gui.MinecraftClientWindow.screen(client) == null && dev.openallay.client.gui.MinecraftClientWindow.overlay(client) == null) {
                    screens.open(services.forActor(client.player.getUUID()));
                }
            }
            ui.tick();
        });
        GuideClientE2EConfig.from(System.getProperties()).ifPresent(config -> {
            String modVersion = FabricLoader.getInstance().getModContainer("openallay")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
            GuideClientE2EController controller = new GuideClientE2EController(
                    config,
                    "fabric",
                    runtime.platform().gameVersion(),
                    modVersion,
                    services,
                    gson,
                    () -> Minecraft.getInstance().stop(),
                    contexts::recipeProviderReadiness,
                    settings == null ? null : settings.settings(),
                    modelRegistry == null ? null : modelRegistry::encodedTrace);
            controller.attachGraphicalProbe(ui::openGuide, ui::e2eHudReceipt, ui::e2eVoiceSettings);
            if (Boolean.getBoolean(GuideClientE2EConfig.ENABLED))
                controller.attachGraphicalToastReceipt(ui::e2eNotificationReceipt);
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                controller.tick(client.player == null ? null : client.player.getUUID());
            });
        });
    }
}
