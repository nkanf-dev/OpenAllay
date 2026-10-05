package dev.openallay.context.minecraft;

import java.util.stream.Stream;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;

/** 1.21.1 through 1.20.5 expose tag names and exact typed tag lookup. */
final class MinecraftRegistryTagSets {
    private MinecraftRegistryTagSets() {}
    static <T> Stream<HolderSet.Named<T>> sets(Registry<T> registry) {
        return registry.getTagNames().map(key -> registry.getTag(key).orElseThrow(
                () -> new IllegalStateException("Registry tag disappeared during owner-thread capture: " + key)));
    }
}
