package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.server.level.ServerPlayer;

/** Minecraft 1.19.2 validates the native server connection through its public listener accessor. */
final class NativePlayerConnection {
    private NativePlayerConnection() {}
    static boolean isAcceptingMessages(ServerPlayer player) {
        return player.connection.getConnection().isConnected();
    }
}
