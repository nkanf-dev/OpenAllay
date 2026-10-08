package dev.openallay.context.minecraft;

import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Changed persistent item-data contracts only; catalog and focus algorithms remain shared. */
public final class MinecraftItemDataFacts {
    public record Defaults(Set<String> componentIds, Map<String, JsonElement> properties) {
        public Defaults { componentIds = Set.copyOf(componentIds); properties = Map.copyOf(properties); }
    }
    private MinecraftItemDataFacts() {}
    public static Defaults defaults(Item item) {
        Set<String> ids = new TreeSet<>();
        item.components().keySet().forEach(type -> {
            var id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (id != null) ids.add(id.toString());
        });
        Map<String, JsonElement> encoded = new TreeMap<>();
        var ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        item.components().forEach(component -> {
            var id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type());
            if (id != null) component.encodeValue(ops).result()
                    .ifPresent(value -> encoded.put(id.toString(), value));
        });
        return new Defaults(ids, encoded);
    }
    public static boolean hasCustomData(ItemStack stack) { return !stack.getComponentsPatch().isEmpty(); }
    public static DataResult<JsonElement> persistentData(ItemStack stack, RegistryAccess registries) {
        return DataComponentMap.CODEC.encodeStart(
                registries.createSerializationContext(JsonOps.INSTANCE), stack.getComponents());
    }
    public static String persistentScope() { return "effective_persistent_components;transient_components_excluded"; }
}
