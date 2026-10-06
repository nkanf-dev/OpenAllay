package dev.openallay.server;

import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;

/** Current native listener identity binds its real connection lifetime. */
public final class NativeServerConnectionGuard {
    private NativeServerConnectionGuard() {}
    public static BooleanSupplier capture(ServerPlayer player) {
        var listener = player.connection;
        return () -> player.connection == listener && listener.isAcceptingMessages();
    }
}
