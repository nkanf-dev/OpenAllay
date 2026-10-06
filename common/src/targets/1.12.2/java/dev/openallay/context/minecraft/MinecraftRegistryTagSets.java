package dev.openallay.context.minecraft;

import java.util.Map;
import java.util.Set;
import net.minecraftforge.registries.IForgeRegistry;

/** Minecraft 1.12 has no registry tag publication. Ore dictionary entries are separate facts. */
final class MinecraftRegistryTagSets {
    private MinecraftRegistryTagSets() {}
    static <T extends net.minecraftforge.registries.IForgeRegistryEntry<T>> Map<String, Set<String>> values(
            IForgeRegistry<T> registry) {
        return Map.of();
    }
}
