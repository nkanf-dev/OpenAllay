package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.util.SharedConstants;

/** Actual game bridge facts, not internal format versions. */
final class NativeWorldVersionFacts {
    private NativeWorldVersionFacts() {}
    static String name() { return SharedConstants.getCurrentVersion().getName(); }
    static int dataVersion() { return SharedConstants.getCurrentVersion().getWorldVersion(); }
}
