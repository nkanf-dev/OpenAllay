package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;

import java.util.Objects;
import java.util.UUID;

/** Exact reference identity is required: a same-UUID reconnect is a different session. */
final class SessionIdentity {
    private final Object connection;
    private final Object clientPlayer;
    private final Object clientLevel;
    private final Object server;
    private final UUID actor;
    private final String dimension;
    private Object serverPlayer;
    private Object serverLevel;

    SessionIdentity(Object connection, Object clientPlayer, Object clientLevel, Object server, UUID actor, String dimension) {
        this.connection = Objects.requireNonNull(connection);
        this.clientPlayer = Objects.requireNonNull(clientPlayer);
        this.clientLevel = Objects.requireNonNull(clientLevel);
        this.server = Objects.requireNonNull(server);
        this.actor = Objects.requireNonNull(actor);
        this.dimension = Objects.requireNonNull(dimension);
    }
    void bindServer(Object player, Object level) {
        if (serverPlayer != null) throw new IllegalStateException("Server identity is already captured");
        serverPlayer = Objects.requireNonNull(player); serverLevel = Objects.requireNonNull(level);
    }
    void requireClient(Object connection,Object player,Object level,Object server,UUID actor,String dimension) {
        if (this.connection != connection || clientPlayer != player || clientLevel != level || this.server != server
                || !this.actor.equals(actor) || !this.dimension.equals(dimension)) throw stale();
    }
    void requireServer(Object player,Object level,UUID actor,String dimension) {
        if (serverPlayer == null || serverPlayer != player || serverLevel != level
                || !this.actor.equals(actor) || !this.dimension.equals(dimension)) throw stale();
    }
    private static ExtensionException stale() { return new ExtensionException("stale_session", "The exact player, connection, server or dimension binding is no longer active"); }
}
