package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.LockCode;

/** Minecraft 1.21/1.21.1 stores a string lock key, not a predicate compound. */
final class NativeContainerLocks {
    private NativeContainerLocks() {}

    static void validateTag(ServerLevel level, CompoundTag tag) {
        // Delegate exact string-key/default gates to the real native loader.
        LockCode.fromTag(tag);
    }
}
