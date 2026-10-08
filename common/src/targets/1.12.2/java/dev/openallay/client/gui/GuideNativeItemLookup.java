package dev.openallay.client.gui;

import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Actual Forge registry value and ItemStack display-name primitives. */
public final class GuideNativeItemLookup {
    private GuideNativeItemLookup() {}
    public static Item item(String id) { return MinecraftNativeRegistries.ITEM.getValue(MinecraftResourceIds.parse(id)); }
    public static String displayName(ItemStack stack) { return stack.getDisplayName(); }
    /** The native identifier grammar and guarded registry membership own this boundary. */
    public static boolean validItemId(String value) {
        net.minecraft.util.ResourceLocation id = MinecraftResourceIds.tryParse(value);
        return id != null && MinecraftNativeRegistries.ITEM.containsKey(id);
    }
}
