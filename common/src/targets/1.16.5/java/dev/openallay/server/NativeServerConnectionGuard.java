package dev.openallay.server;

import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;

/** Old native publication exposes the actual NetworkManager identity. */
public final class NativeServerConnectionGuard {
    private NativeServerConnectionGuard() {}
    public static BooleanSupplier capture(ServerPlayer player) {
        var listener = player.connection;
        var connection = listener.getConnection();
        return () -> player.connection == listener && listener.getConnection() == connection && connection.isConnected();
    }
}
