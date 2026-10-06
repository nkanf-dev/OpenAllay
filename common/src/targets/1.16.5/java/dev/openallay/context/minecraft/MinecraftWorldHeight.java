package dev.openallay.context.minecraft;

import net.minecraft.world.level.Level;

/** Actual 1.16 world vertical interval is [0,256), retained by shared capture algorithms. */
public final class MinecraftWorldHeight {
    private MinecraftWorldHeight() {}
    public static int min(Level level) { java.util.Objects.requireNonNull(level, "level"); return 0; }
    public static int max(Level level) { java.util.Objects.requireNonNull(level, "level"); return 256; }
}
