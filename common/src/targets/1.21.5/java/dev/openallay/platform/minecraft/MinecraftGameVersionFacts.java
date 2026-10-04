package dev.openallay.platform.minecraft;

import net.minecraft.SharedConstants;

/** Minecraft 1.21.5 names the game version accessor getName(). */
public final class MinecraftGameVersionFacts {
    private MinecraftGameVersionFacts() {}
    public static String name() { return SharedConstants.getCurrentVersion().getName(); }
}
