package dev.openallay.context.minecraft;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.Registry;

/** Detach registry-value IDs and tag IDs at the actual native publication boundary. */
final class MinecraftRegistryTagSets {
    private MinecraftRegistryTagSets() {}
    static <T> Map<String, Set<String>> values(Registry<T> registry) {
        Map<String, Set<String>> result = new TreeMap<>();
        registry.getTags().forEach(named -> named.stream().forEach(holder -> result.computeIfAbsent(
                java.util.Objects.requireNonNull(registry.getKey(holder.value())).toString(), ignored -> new TreeSet<>())
                .add(named.key().location().toString())));
        return result;
    }
}
