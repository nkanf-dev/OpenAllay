package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.LockCode;

/** Reject complete predicate-lock codec errors with the native compound type gate. */
final class NativeContainerLocks {
    private NativeContainerLocks() {}

    static void validateTag(ServerLevel level, CompoundTag tag) {
        if (tag.contains("lock", Tag.TAG_COMPOUND)) {
            var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            LockCode.CODEC.parse(ops, tag.get("lock")).getOrThrow();
        }
    }
}
