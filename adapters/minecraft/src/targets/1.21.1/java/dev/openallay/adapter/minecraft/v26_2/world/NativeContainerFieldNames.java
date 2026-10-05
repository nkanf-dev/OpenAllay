package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.world.LockCode;

/** Minecraft 1.21/1.21.1 owns the spelling of its persisted string lock key. */
final class NativeContainerFieldNames {
    private NativeContainerFieldNames() {}
    static String lock() { return LockCode.TAG_LOCK; }
}
