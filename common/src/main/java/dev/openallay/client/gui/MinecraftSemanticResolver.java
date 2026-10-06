package dev.openallay.client.gui;

import dev.openallay.platform.minecraft.MinecraftComponents;

import dev.openallay.platform.minecraft.MinecraftResourceIds;

import net.minecraft.client.Minecraft;
import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import net.minecraft.world.item.ItemStack;

/** Resolves registry IDs on the Minecraft client thread into detached render values. */
public final class MinecraftSemanticResolver {
    public record ItemPresentation(
            String itemId, String label, long count, ItemStack stack, boolean resolved) {
        public ItemPresentation {
            if (itemId == null || itemId.isBlank() || label == null || count < 0) {
                throw new IllegalArgumentException("invalid detached item presentation");
            }
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
        }

        @Override public ItemStack stack() { return stack.copy(); }
    }

    public ItemPresentation item(String itemId, String suppliedLabel, long count) {
        String fallback = suppliedLabel == null || suppliedLabel.isBlank() ? itemId : suppliedLabel;
        Minecraft minecraft = Minecraft.getInstance();
        if (!MinecraftClientWindow.ownerThread(minecraft)) {
            return new ItemPresentation(itemId, fallback, count, ItemStack.EMPTY, false);
        }
        var id = MinecraftResourceIds.tryParse(itemId);
        if (id == null || !MinecraftNativeRegistries.ITEM.containsKey(id)) {
            return new ItemPresentation(itemId, fallback, count, ItemStack.EMPTY, false);
        }
        ItemStack stack = new ItemStack(
                dev.openallay.client.gui.GuideNativeItemLookup.item(id.toString()),
                (int) Math.min(Integer.MAX_VALUE, Math.max(1, count)));
        String label = suppliedLabel == null || suppliedLabel.isBlank()
                ? GuideNativeItemLookup.displayName(stack) : suppliedLabel;
        return new ItemPresentation(itemId, label, count, stack, true);
    }
}
