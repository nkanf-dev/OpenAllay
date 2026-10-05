package dev.openallay.fabric.network;

import com.google.gson.Gson;
import dev.openallay.OpenAllayRuntime;
import dev.openallay.bridge.server.ServerBridgeSession;
import dev.openallay.server.MinecraftServerGuideContextProvider;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Native player/lifecycle binding; server request behavior has one engine owner. */
public final class FabricServerBridge {
    private final OpenAllayRuntime runtime;
    private final Map<UUID, ServerPlayer> players = new java.util.concurrent.ConcurrentHashMap<>();
    private final ServerBridgeSession session;

    private FabricServerBridge(OpenAllayRuntime runtime) {
        this.runtime = runtime;
        this.session = new ServerBridgeSession(runtime, (actor, kind, json) -> {
            ServerPlayer player = players.get(actor);
            return player != null && FabricNativePayloadRegistration.send(
                    player, new FabricBridgePayloads.Packet(kind, json));
        });
    }

    public static void register(OpenAllayRuntime runtime) {
        FabricServerBridge bridge = new FabricServerBridge(runtime);
        ServerLifecycleEvents.SERVER_STARTED.register(bridge::started);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            bridge.players.put(player.getUUID(), player);
            bridge.started(server);
            bridge.session.connected(player.getUUID(), (actor, kind, json) -> {
                FabricNativePayloadRegistration.sendInitial(sender, new FabricBridgePayloads.Packet(kind, json));
                return true;
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (bridge.players.remove(player.getUUID(), player)) bridge.session.disconnected(player.getUUID());
        });
        FabricNativePayloadRegistration.register((packet, player) -> {
            UUID actor = player.getUUID();
            if (bridge.players.get(actor) == player) bridge.session.receive(actor, packet.kind(), packet.json());
        });
    }

    private void started(MinecraftServer server) {
        session.started(new MinecraftServerGuideContextProvider(runtime, server, dev.openallay.json.EngineJson.withInstant(new Gson())),
                FabricLoader.getInstance().getConfigDir().resolve("openallay/server-model.json"),
                System.getenv(), server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("openallay/images"));
    }
}
