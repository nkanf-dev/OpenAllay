package dev.openallay.platform.minecraft;

import net.minecraft.client.Options;

/** Native option values; context capture and probe policy stay shared. */
public final class MinecraftOptions {
    private MinecraftOptions() {}
    public static String language(Options options) { return options.languageCode; }
    public static int guiScale(Options options) { return options.guiScale().get(); }
    public static void guiScale(Options options, int value) { options.guiScale().set(value); }
    public static java.util.OptionalInt simulationDistance(Options options) { return java.util.OptionalInt.of(options.simulationDistance().get()); }
}
