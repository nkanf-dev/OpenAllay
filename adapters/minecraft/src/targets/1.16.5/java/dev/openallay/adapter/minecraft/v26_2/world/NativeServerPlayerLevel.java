package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.server.ServerWorld;

/** Exact public authoritative owning-world accessor. */
final class NativeServerPlayerLevel {
    private NativeServerPlayerLevel() {}
    static ServerWorld get(ServerPlayerEntity player) { return player.getLevel(); }
}
