package dev.openallay.neoforge.network;

import dev.openallay.OpenAllayRuntime;
import dev.openallay.bridge.server.ServerBridgeSession;
import dev.openallay.server.NativeServerOwner;
import dev.openallay.neoforge.NeoForgeNativeLoaderFacts;
import dev.openallay.server.MinecraftServerGuideContextProvider;
import dev.openallay.server.NativeServerActorHandoffs;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** One model/session runtime per native server; original actor sends retain native custody. */
public final class NeoForgeServerBridge {
    private final OpenAllayRuntime runtime;
    private SessionOwner current;
    NeoForgeServerBridge(OpenAllayRuntime runtime) { this.runtime = runtime; }
    void registerLifecycle() {
        NeoForgeNativeServerLifecycle.register(this::started, this::joined, this::disconnected, this::stopped);
    }
    private static MinecraftServer server(ServerPlayer player) { return NativeServerOwner.server(player); }
    private static void owner(MinecraftServer server) {
        if (!NativeServerOwner.isOwner(server)) throw new IllegalStateException("Server lifecycle requires native owner");
    }
    private void started(MinecraftServer server) {
        owner(server);
        if (current != null) {
            if (current.server == server) return;
            throw new IllegalStateException("Previous native server owner is still retained");
        }
        SessionOwner value = new SessionOwner(server);
        current = value;
        value.session.started(new MinecraftServerGuideContextProvider(runtime, server,
                        dev.openallay.json.EngineJson.create(), value::bind),
                NeoForgeNativeLoaderFacts.configDir().resolve("openallay/server-model.json"), System.getenv(),
                NativeServerOwner.worldDirectory(server).resolve("openallay/images"));
    }
    private void joined(ServerPlayer player) {
        MinecraftServer server = server(player); owner(server);
        SessionOwner value = current;
        if (value == null || value.server != server) return;
        ServerPlayer previous = value.players.remove(NativeServerOwner.actor(player));
        if (previous != null) NativeServerActorHandoffs.cleanup(value.handoffs.revoke(previous),
                () -> value.session.disconnected(NativeServerOwner.actor(previous)));
        value.handoffs.admit(player);
        value.players.put(NativeServerOwner.actor(player), player);
        value.session.connected(NativeServerOwner.actor(player));
    }
    private void disconnected(ServerPlayer player) {
        MinecraftServer server = server(player); owner(server);
        SessionOwner value = current;
        if (value == null || value.server != server || !value.players.remove(NativeServerOwner.actor(player), player)) return;
        NativeServerActorHandoffs.cleanup(value.handoffs.revoke(player), () -> value.session.disconnected(NativeServerOwner.actor(player)));
    }
    private void stopped(MinecraftServer server) {
        owner(server);
        SessionOwner value = current;
        if (value == null || value.server != server) return;
        Runnable retire = value.handoffs.stop();
        var actors = java.util.List.copyOf(value.players.keySet());
        value.players.clear(); current = null;
        java.util.List<Runnable> cleanup = new java.util.ArrayList<>();
        cleanup.add(retire);
        actors.forEach(actor -> cleanup.add(() -> value.session.disconnected(actor)));
        NativeServerActorHandoffs.cleanup(cleanup.toArray(Runnable[]::new));
    }
    void receive(NeoForgeBridgePayloads.Packet packet, ServerPlayer player) {
        MinecraftServer server = server(player); owner(server);
        SessionOwner value = current;
        if (value != null && value.server == server && value.players.get(NativeServerOwner.actor(player)) == player) {
            value.bind(NativeServerOwner.actor(player)).dispatch(NativeServerOwner.actor(player),
                    () -> value.session.receive(NativeServerOwner.actor(player), packet.kind(), packet.json()), () -> {});
        }
    }
    private final class SessionOwner {
        final MinecraftServer server;
        final Map<UUID, ServerPlayer> players = new HashMap<>();
        final NativeServerActorHandoffs handoffs;
        final ServerBridgeSession session;
        SessionOwner(MinecraftServer server) {
            this.server = server; handoffs = new NativeServerActorHandoffs(server);
            session = new ServerBridgeSession(runtime, new ServerBridgeSession.Transport() {
                @Override public ServerBridgeSession.Transport bind(UUID actor) { return SessionOwner.this.bind(actor); }
                @Override public boolean send(UUID actor, String kind, String json) { return bind(actor).send(actor, kind, json); }
            });
        }
        ServerBridgeSession.Transport bind(UUID actor) {
            owner(server);
            ServerPlayer player = players.get(actor);
            return handoffs.bind(actor, (ignored, kind, json) -> {
                if (player == null || players.get(actor) != player) return false;
                NeoForgeNativePayloadRegistration.send(player, new NeoForgeBridgePayloads.Packet(kind, json));
                return true;
            });
        }
    }
}
