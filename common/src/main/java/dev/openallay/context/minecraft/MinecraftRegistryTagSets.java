package dev.openallay.context.minecraft;

import java.util.stream.Stream;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;

/** Exact typed registry tag sets. Catalog traversal and detachment remain shared. */
final class MinecraftRegistryTagSets {
    private MinecraftRegistryTagSets() {}
    static <T> Stream<HolderSet.Named<T>> sets(Registry<T> registry) {
        return registry.getTags();
    }
}
