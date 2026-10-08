package dev.openallay.context.minecraft;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.util.registry.Registry;
import net.minecraft.tags.ITagCollection;
import net.minecraft.tags.TagCollectionManager;

/** Actual Forge36 tag collections, including registered custom tag types. */
final class MinecraftRegistryTagSets {
    private MinecraftRegistryTagSets() {}
    static <T> Map<String, Set<String>> values(Registry<T> registry) {
        var tags = TagCollectionManager.getInstance();
        ITagCollection<?> collection;
        if (registry.key().equals(Registry.ITEM_REGISTRY)) collection = tags.getItems();
        else if (registry.key().equals(Registry.BLOCK_REGISTRY)) collection = tags.getBlocks();
        else if (registry.key().equals(Registry.FLUID_REGISTRY)) collection = tags.getFluids();
        else if (registry.key().equals(Registry.ENTITY_TYPE_REGISTRY)) collection = tags.getEntityTypes();
        else collection = tags.getCustomTagTypes().get(registry.key().location());
        Map<String, Set<String>> result = new TreeMap<>();
        if (collection == null) return result; // No native tag type exists for this registry.
        var all = collection.getAllTags();
        registry.stream().forEach(value -> all.forEach((id, tag) -> {
            // Membership uses the actual typed collection values, without casting native ingredients.
            if (tag.getValues().contains(value)) result.computeIfAbsent(
                    java.util.Objects.requireNonNull(registry.getKey(value)).toString(), ignored -> new TreeSet<>())
                    .add(id.toString());
        }));
        return result;
    }
}
