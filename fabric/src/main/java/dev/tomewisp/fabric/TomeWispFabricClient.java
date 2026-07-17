package dev.tomewisp.fabric;

import dev.tomewisp.TomeWispBootstrap;
import dev.tomewisp.TomeWispRuntime;
import dev.tomewisp.client.ClientGuideRuntime;
import com.google.gson.Gson;
import dev.tomewisp.client.MinecraftGuideContextProvider;
import dev.tomewisp.guide.GuideCommandFacade;
import dev.tomewisp.guide.GuideLocalEndpoint;
import dev.tomewisp.guide.GuideServiceManager;
import dev.tomewisp.guide.PayloadGuideRemoteEndpoint;
import dev.tomewisp.tool.ToolResult;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import dev.tomewisp.fabric.network.FabricBridgePayloads;
import dev.tomewisp.fabric.network.FabricClientBridge;

public final class TomeWispFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TomeWispRuntime runtime = TomeWispBootstrap.initialize();
        FabricBridgePayloads.register();
        FabricClientBridge bridge = new FabricClientBridge();
        bridge.register();
        Gson gson = new Gson();
        var dispatcher = (dev.tomewisp.client.ClientEventDispatcher)
                runnable -> Minecraft.getInstance().execute(runnable);
        ToolResult<ClientGuideRuntime> guide = ClientGuideRuntime.create(
                runtime,
                FabricLoader.getInstance().getConfigDir().resolve("tomewisp/model.json"),
                System.getenv(),
                dispatcher,
                bridge.remoteTools());
        GuideLocalEndpoint local = guide instanceof ToolResult.Success<ClientGuideRuntime> success
                ? success.value()
                : null;
        MinecraftGuideContextProvider contexts = new MinecraftGuideContextProvider(
                runtime, Minecraft.getInstance(), gson, TomeWispFabricClient.class.getClassLoader());
        PayloadGuideRemoteEndpoint remote = new PayloadGuideRemoteEndpoint(
                new PayloadGuideRemoteEndpoint.Port() {
                    @Override public dev.tomewisp.bridge.protocol.CapabilityPayload capabilities() {
                        return bridge.capabilities();
                    }
                    @Override public boolean ask(
                            dev.tomewisp.bridge.protocol.ServerAgentRequestPayload request,
                            java.util.function.Consumer<dev.tomewisp.bridge.protocol.ServerAgentEventPayload> events) {
                        return bridge.askServer(request, events);
                    }
                    @Override public boolean cancel(java.util.UUID requestId) {
                        return bridge.cancelServer(requestId);
                    }
                    @Override public void disconnect() { bridge.disconnectState(); }
                },
                gson);
        GuideServiceManager services = new GuideServiceManager(
                local, remote, contexts, dispatcher, java.time.Clock.systemUTC(), gson);
        bridge.onDisconnect(services::disconnect);
        bridge.onCapabilitiesChanged(() -> {
            var current = services.current();
            if (current != null) current.refreshCapabilities();
        });
        FabricGuideCommands.register(new GuideCommandFacade(
                runtime,
                services,
                contexts,
                service -> new ToolResult.Failure<>(
                        "gui_unavailable", "玩家界面将在 Phase 3C 启用")));
    }
}
