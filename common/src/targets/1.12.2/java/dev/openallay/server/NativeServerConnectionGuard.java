package dev.openallay.server;

import java.util.function.BooleanSupplier;
import net.minecraft.entity.player.EntityPlayerMP;

/** Capture original player/listener/NetworkManager; same UUID never substitutes new custody. */
public final class NativeServerConnectionGuard {
    private NativeServerConnectionGuard() {}
    public static BooleanSupplier capture(EntityPlayerMP player) {
        var original = player.connection;
        var manager = original.netManager;
        return () -> player.connection == original && original.player == player
                && original.netManager == manager && manager.isChannelOpen();
    }
}
