package dev.openallay.client;

import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;

/** This native client has no GPU utilization measurement API. */
public final class MinecraftGpuFacts {
    private MinecraftGpuFacts() {}

    public static OptionalDouble utilization(Minecraft client) {
        return OptionalDouble.empty();
    }
}
