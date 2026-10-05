package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.server.level.ServerPlayer;

/** Native server connection liveness only; owner and identity validation stay shared. */
final class NativePlayerConnection {
    private NativePlayerConnection() {}
    static boolean isAcceptingMessages(ServerPlayer player) { return player.connection.isAcceptingMessages(); }
}
