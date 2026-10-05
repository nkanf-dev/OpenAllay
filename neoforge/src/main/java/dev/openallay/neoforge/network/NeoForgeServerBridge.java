package dev.openallay.neoforge.network;

import com.google.gson.Gson;
import dev.openallay.OpenAllayRuntime;
import dev.openallay.bridge.server.ServerBridgeSession;
import dev.openallay.neoforge.NeoForgeNativeLoaderFacts;
import dev.openallay.server.MinecraftServerGuideContextProvider;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Native player/lifecycle binding; no family duplicates request behavior. */
public final class NeoForgeServerBridge {
    private final OpenAllayRuntime runtime;
    private final Map<UUID, ServerPlayer> players = new java.util.concurrent.ConcurrentHashMap<>();
    private final ServerBridgeSession session;

    NeoForgeServerBridge(OpenAllayRuntime runtime) {
        this.runtime = runtime;
        session = new ServerBridgeSession(runtime, (actor, kind, json) -> {
            ServerPlayer player = players.get(actor);
            if (player == null) return false;
            NeoForgeNativePayloadRegistration.send(player, new NeoForgeBridgePayloads.Packet(kind, json));
            return true;
        });
    }

    void registerLifecycle() {
        NeoForgeNativeServerLifecycle.register(this::started, player -> {
            players.put(player.getUUID(), player);
            started(dev.openallay.context.minecraft.MinecraftServerPlayerLevel.get(player).getServer());
            session.connected(player.getUUID());
        }, player -> {
            if (players.remove(player.getUUID(), player)) session.disconnected(player.getUUID());
        });
    }

    void receive(NeoForgeBridgePayloads.Packet packet, ServerPlayer player) {
        UUID actor = player.getUUID();
        if (players.get(actor) == player) session.receive(actor, packet.kind(), packet.json());
    }

    private void started(MinecraftServer server) {
        session.started(new MinecraftServerGuideContextProvider(runtime, server, new Gson()),
                NeoForgeNativeLoaderFacts.configDir().resolve("openallay/server-model.json"),
                System.getenv(), server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("openallay/images"));
    }
}
