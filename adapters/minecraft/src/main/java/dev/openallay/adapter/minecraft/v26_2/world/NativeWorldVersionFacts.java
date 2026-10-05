package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.SharedConstants;

/** Actual game version facts; detached context construction stays shared. */
final class NativeWorldVersionFacts {
    private NativeWorldVersionFacts() {}
    static String name() { return SharedConstants.getCurrentVersion().name(); }
    static int dataVersion() { return SharedConstants.getCurrentVersion().dataVersion().version(); }
}
