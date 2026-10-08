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
    @dev.openallay.value.ValueType(Defaults.ValueSchemaProvider.class)
public static final class Defaults {
    private final Set<String> componentIds;
    private final Map<String, JsonElement> properties;
    public Defaults(Set<String> componentIds, Map<String, JsonElement> properties) {
 componentIds = dev.openallay.util.Java8Collections.setCopyOf(componentIds); properties = dev.openallay.util.Java8Collections.mapCopyOf(properties);
        this.componentIds = componentIds;
        this.properties = properties;
    }
    public Set<String> componentIds() { return componentIds; }
    public Map<String, JsonElement> properties() { return properties; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Defaults)) return false;
        Defaults that = (Defaults) other;
        return java.util.Objects.equals(componentIds, that.componentIds) && java.util.Objects.equals(properties, that.properties);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(componentIds);
        hash = 31 * hash + java.util.Objects.hashCode(properties);
        return hash;
    }
    @Override public String toString() { return "Defaults[componentIds=" + componentIds + ", properties=" + properties + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Defaults> schema() {
            return new dev.openallay.value.ValueSchema<>(Defaults.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Defaults>>asList(new dev.openallay.value.ValueSchema.Component<>(Defaults.class, "componentIds", Defaults::componentIds), new dev.openallay.value.ValueSchema.Component<>(Defaults.class, "properties", Defaults::properties)), arguments -> new Defaults((Set) arguments[0], (Map) arguments[1]));
        }
    }
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
        return new Defaults(dev.openallay.util.Java8Collections.setOf(), dev.openallay.util.Java8Collections.mapOf("minecraft:item", facts));
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
