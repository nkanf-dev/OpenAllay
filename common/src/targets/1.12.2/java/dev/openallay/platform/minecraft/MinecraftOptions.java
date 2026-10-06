package dev.openallay.platform.minecraft;

import java.util.OptionalInt;
import net.minecraft.client.settings.GameSettings;

/** Actual 1.12.2 options. Simulation distance is not an independent native option in this release. */
public final class MinecraftOptions {
    private MinecraftOptions() {}
    public static int guiScale(GameSettings options) { return options.guiScale; }
    public static void guiScale(GameSettings options, int value) { options.guiScale = value; }
    public static OptionalInt simulationDistance(GameSettings options) { return OptionalInt.empty(); }
}
