package dev.openallay.client;

import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;

/** Native optional GPU utilization sample; shared capture owns diagnostic meaning. */
public final class MinecraftGpuFacts {
    private MinecraftGpuFacts() {}

    public static OptionalDouble utilization(Minecraft client) {
        double value = client.getGpuUtilization();
        return Double.isFinite(value) && value >= 0 ? OptionalDouble.of(value) : OptionalDouble.empty();
    }
}
