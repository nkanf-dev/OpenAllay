package dev.openallay.context.minecraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Real pre-component Item and ItemStack NBT contracts, inherited through 1.20.1. */
public final class MinecraftItemDataFacts {
    public record Defaults(Set<String> componentIds, Map<String, JsonElement> properties) {
        public Defaults { componentIds = Set.copyOf(componentIds); properties = Map.copyOf(properties); }
    }
    private MinecraftItemDataFacts() {}
    public static Defaults defaults(Item item) {
        JsonObject facts = new JsonObject();
        facts.addProperty("max_stack_size", item.getMaxStackSize());
        facts.addProperty("max_damage", item.getMaxDamage());
        facts.addProperty("edible", item.isEdible());
        facts.addProperty("fire_resistant", item.isFireResistant());
        Map<String, JsonElement> properties = new TreeMap<>();
        properties.put("minecraft:item", facts);
        var tag = item.getDefaultInstance().getTag();
        if (tag != null) properties.put("minecraft:nbt", NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag));
        // Data components do not exist in this native family; expose native item/NBT facts instead.
        return new Defaults(Set.of(), properties);
    }
    public static boolean hasCustomData(ItemStack stack) { return stack.hasTag(); }
    public static DataResult<JsonElement> persistentData(ItemStack stack, RegistryAccess registries) {
        JsonObject data = new JsonObject();
        var tag = stack.getTag();
        if (tag != null) data.add("minecraft:nbt", NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag));
        return DataResult.success(data);
    }
    public static String persistentScope() { return "native_item_nbt;data_components_not_present"; }
}
