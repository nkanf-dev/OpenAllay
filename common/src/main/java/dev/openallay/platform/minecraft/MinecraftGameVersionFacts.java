package dev.openallay.platform.minecraft;

import net.minecraft.SharedConstants;

/** Actual game version name for loader platform facts, without a loader API dependency. */
public final class MinecraftGameVersionFacts {
    private MinecraftGameVersionFacts() {}
    public static String name() { return SharedConstants.getCurrentVersion().name(); }
}
