package dev.openallay.client;

import java.util.OptionalDouble;


/** This native client has no GPU utilization measurement API. */
public final class MinecraftGpuFacts {
    private MinecraftGpuFacts() {}

    public static OptionalDouble utilization(net.minecraft.client.Minecraft client) {
        return OptionalDouble.empty();
    }
}
