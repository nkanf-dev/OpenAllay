package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.SharedConstants;

/** Minecraft 1.21.5 game version facts use the pre-record getter names. */
final class NativeWorldVersionFacts {
    private NativeWorldVersionFacts() {}
    static String name() { return SharedConstants.getCurrentVersion().getName(); }
    static int dataVersion() { return SharedConstants.getCurrentVersion().getDataVersion().getVersion(); }
}
