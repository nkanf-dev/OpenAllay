package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.LockCode;
import net.minecraft.world.server.ServerWorld;

/** Exact native string-lock type gate/default, retained for selected direct-family callers. */
final class NativeContainerLocks {
    private NativeContainerLocks() {}
    static void validateTag(ServerWorld level, CompoundNBT tag) { LockCode.fromTag(tag); }
}
