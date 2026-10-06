package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.server.level.ServerPlayer;

/** Native actor field/accessor spelling. Session authority stays shared. */
final class NativeActorState {
    private NativeActorState() {}
    static boolean removed(ServerPlayer player) { return player.isRemoved(); }
    static float yaw(ServerPlayer player) { return player.getYRot(); }
}
