package dev.openallay.platform.minecraft;

import net.minecraft.client.Options;

/** Old primitive GUI scale; no independent native simulation-distance option exists. */
public final class MinecraftOptions {
    private MinecraftOptions() {}
    public static int guiScale(Options options) { return options.guiScale; }
    public static void guiScale(Options options, int value) { options.guiScale = value; }
    public static java.util.OptionalInt simulationDistance(Options options) { return java.util.OptionalInt.empty(); }
}
