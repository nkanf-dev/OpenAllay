package dev.openallay.client.gui;

import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import net.minecraft.world.item.Item;

/** Native pre-1.21.2 registry value contract, inherited by all older UI families. */
public final class GuideNativeItemLookup {
    private GuideNativeItemLookup() {}
    public static Item item(String id) { return MinecraftNativeRegistries.ITEM.get(MinecraftResourceIds.parse(id)); }
}
