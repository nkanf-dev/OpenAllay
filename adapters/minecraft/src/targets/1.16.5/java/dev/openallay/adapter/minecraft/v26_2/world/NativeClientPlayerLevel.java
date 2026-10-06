package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.world.World;

/** 1.16.5's inherited public level field; no modern accessor alias. */
final class NativeClientPlayerLevel {
    private NativeClientPlayerLevel() {}
    static World get(ClientPlayerEntity player) { return player.level; }
}
