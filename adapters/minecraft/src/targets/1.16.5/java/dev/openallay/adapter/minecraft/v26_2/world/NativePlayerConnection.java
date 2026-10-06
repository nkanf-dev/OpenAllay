package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.entity.player.ServerPlayerEntity;

/** Public server listener and real network channel liveness. */
final class NativePlayerConnection {
    private NativePlayerConnection() {}
    static boolean isAcceptingMessages(ServerPlayerEntity player) {
        return player.connection.getConnection().isConnected();
    }
}
