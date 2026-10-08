package dev.openallay.fabric.network;

import dev.openallay.OpenAllayRuntime;
import dev.openallay.bridge.server.ServerBridgeSession;
import dev.openallay.context.minecraft.MinecraftServerPlayerLevel;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import dev.openallay.server.MinecraftServerGuideContextProvider;
import dev.openallay.server.NativeServerActorHandoffs;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** One model/session runtime per native server; original actor sends retain native custody. */
public final class FabricServerBridge {
    private final OpenAllayRuntime runtime;
    private SessionOwner current;
    FabricServerBridge(OpenAllayRuntime runtime) { this.runtime = runtime; }
    public static void register(OpenAllayRuntime runtime) {
        FabricServerBridge bridge = new FabricServerBridge(runtime);
        ServerLifecycleEvents.SERVER_STARTED.register(bridge::started);
        ServerLifecycleEvents.SERVER_STOPPED.register(bridge::stopped);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> bridge.joined(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> bridge.disconnected(handler.getPlayer()));
        FabricNativePayloadRegistration.register(bridge::receive);
    }
    private static MinecraftServer server(ServerPlayer player) { return MinecraftServerPlayerLevel.get(player).getServer(); }
    private static void owner(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Server lifecycle requires native owner");
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
                FabricLoader.getInstance().getConfigDir().resolve("openallay/server-model.json"), System.getenv(),
                server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("openallay/images"));
    }
    private void joined(ServerPlayer player) {
        MinecraftServer server = server(player); owner(server);
        SessionOwner value = current;
        if (value == null || value.server != server) return;
        ServerPlayer previous = value.players.remove(player.getUUID());
        if (previous != null) NativeServerActorHandoffs.cleanup(value.handoffs.revoke(previous),
                () -> value.session.disconnected(previous.getUUID()));
        value.handoffs.admit(player);
        value.players.put(player.getUUID(), player);
        value.session.connected(player.getUUID());
    }
    private void disconnected(ServerPlayer player) {
        MinecraftServer server = server(player); owner(server);
        SessionOwner value = current;
        if (value == null || value.server != server || !value.players.remove(player.getUUID(), player)) return;
        NativeServerActorHandoffs.cleanup(value.handoffs.revoke(player), () -> value.session.disconnected(player.getUUID()));
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
    void receive(FabricBridgePayloads.Packet packet, ServerPlayer player) {
        MinecraftServer server = server(player); owner(server);
        SessionOwner value = current;
        if (value != null && value.server == server && value.players.get(player.getUUID()) == player) {
            value.bind(player.getUUID()).dispatch(player.getUUID(),
                    () -> value.session.receive(player.getUUID(), packet.kind(), packet.json()), () -> {});
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
                return FabricNativePayloadRegistration.send(player, new FabricBridgePayloads.Packet(kind, json));
            });
        }
    }
}
