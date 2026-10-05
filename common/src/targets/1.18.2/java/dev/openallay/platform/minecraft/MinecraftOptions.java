package dev.openallay.platform.minecraft;

import net.minecraft.client.Options;

/** Native primitive options before the OptionInstance API. */
public final class MinecraftOptions {
    private MinecraftOptions() {}
    public static int guiScale(Options options) { return options.guiScale; }
    public static void guiScale(Options options, int value) { options.guiScale = value; }
    public static int simulationDistance(Options options) { return options.simulationDistance; }
}
