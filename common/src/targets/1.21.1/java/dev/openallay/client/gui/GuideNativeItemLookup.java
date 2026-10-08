package dev.openallay.client.gui;

import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import net.minecraft.world.item.Item;

/** Native pre-1.21.2 registry value contract, inherited by all older UI families. */
public final class GuideNativeItemLookup {
    private GuideNativeItemLookup() {}
    public static String displayName(net.minecraft.world.item.ItemStack stack) { return dev.openallay.platform.minecraft.MinecraftComponents.getString(stack.getHoverName()); }
    public static Item item(String id) { return MinecraftNativeRegistries.ITEM.get(MinecraftResourceIds.parse(id)); }
    /** The native identifier grammar and guarded registry membership own this boundary. */
    public static boolean validItemId(String value) {
        net.minecraft.resources.ResourceLocation id = MinecraftResourceIds.tryParse(value);
        return id != null && MinecraftNativeRegistries.ITEM.containsKey(id);
    }
}
