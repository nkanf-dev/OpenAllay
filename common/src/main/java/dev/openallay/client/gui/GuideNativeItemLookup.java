package dev.openallay.client.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import net.minecraft.world.item.Item;

/** Native registry value lookup for UI stack construction. */
public final class GuideNativeItemLookup {
    private GuideNativeItemLookup() {}
    public static Item item(String id) { return BuiltInRegistries.ITEM.getValue(MinecraftResourceIds.parse(id)); }
}
