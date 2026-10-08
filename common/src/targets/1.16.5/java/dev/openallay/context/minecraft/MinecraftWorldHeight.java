package dev.openallay.context.minecraft;



/** Actual 1.16 world vertical interval is [0,256), retained by shared capture algorithms. */
public final class MinecraftWorldHeight {
    private MinecraftWorldHeight() {}
    public static int min(net.minecraft.world.level.Level level) { java.util.Objects.requireNonNull(level, "level"); return 0; }
    public static int max(net.minecraft.world.level.Level level) { java.util.Objects.requireNonNull(level, "level"); return 256; }
}
