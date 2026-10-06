package dev.openallay.context.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.oredict.OreDictionary;

/** Actual pre-flattening metadata, native SNBT and ore dictionary facts. No component aliases. */
public final class MinecraftItemDataFacts {
    public record Defaults(Set<String> componentIds, Map<String, JsonElement> properties) {
        public Defaults { componentIds = Set.copyOf(componentIds); properties = Map.copyOf(properties); }
    }
    private MinecraftItemDataFacts() {}
    public static Defaults defaults(Item item) {
        JsonObject facts = new JsonObject();
        facts.addProperty("max_stack_size", item.getItemStackLimit());
        facts.addProperty("max_damage", item.getMaxDamage());
        facts.addProperty("has_subtypes", item.getHasSubtypes());
        facts.addProperty("edible", item instanceof ItemFood);
        NonNullList<ItemStack> nativeVariants = NonNullList.create();
        item.getSubItems(CreativeTabs.SEARCH, nativeVariants);
        JsonArray variants = new JsonArray();
        for (ItemStack stack : nativeVariants) {
            if (stack.isEmpty() || stack.getItem() != item) continue;
            JsonObject variant = persistentData(stack);
            variant.addProperty("display_name", stack.getDisplayName());
            JsonArray ores = new JsonArray();
            for (int ore : OreDictionary.getOreIDs(stack)) ores.add(OreDictionary.getOreName(ore));
            variant.add("ore_dictionary", ores);
            variants.add(variant);
        }
        facts.add("creative_variants", variants);
        return new Defaults(Set.of(), Map.of("minecraft:item", facts));
    }
    public static boolean hasCustomData(ItemStack stack) {
        return stack.getMetadata() != 0 || stack.hasTagCompound();
    }
    public static JsonObject persistentData(ItemStack stack) {
        JsonObject data = new JsonObject();
        data.addProperty("metadata", stack.getMetadata());
        // SNBT preserves native numeric tag kinds and arrays without a modern NbtOps dependency.
        if (stack.hasTagCompound()) data.addProperty("nbt_snbt", stack.getTagCompound().toString());
        return data;
    }
    public static String persistentScope() { return "native_item_metadata_and_nbt_snbt;data_components_not_present"; }
}
