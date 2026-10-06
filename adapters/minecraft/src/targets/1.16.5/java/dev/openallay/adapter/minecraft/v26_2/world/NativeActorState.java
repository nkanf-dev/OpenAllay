package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.entity.player.ServerPlayerEntity;

/** Actual 1.16.5 actor fields, read on the exact bound server owner. */
final class NativeActorState {
    private NativeActorState() {}
    static boolean removed(ServerPlayerEntity player) { return player.removed; }
    static float yaw(ServerPlayerEntity player) { return player.yRot; }
}
