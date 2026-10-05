package dev.openallay.client.gui;

import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import net.minecraft.world.item.Item;

/** Native registry value lookup for UI stack construction. */
public final class GuideNativeItemLookup {
    private GuideNativeItemLookup() {}
    public static Item item(String id) { return MinecraftNativeRegistries.ITEM.getValue(MinecraftResourceIds.parse(id)); }
}
