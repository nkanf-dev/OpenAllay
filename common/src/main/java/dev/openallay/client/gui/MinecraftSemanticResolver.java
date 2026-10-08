package dev.openallay.client.gui;

import dev.openallay.platform.minecraft.MinecraftComponents;

import dev.openallay.platform.minecraft.MinecraftResourceIds;

import net.minecraft.client.Minecraft;
import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import net.minecraft.world.item.ItemStack;

/** Resolves registry IDs on the Minecraft client thread into detached render values. */
public final class MinecraftSemanticResolver {
    @dev.openallay.value.ValueType(ItemPresentation.ValueSchemaProvider.class)
public static final class ItemPresentation {
    private final String itemId;
    private final String label;
    private final long count;
    private final ItemStack stack;
    private final boolean resolved;
    public ItemPresentation(String itemId, String label, long count, ItemStack stack, boolean resolved) {

            if (itemId == null || itemId.isBlank() || label == null || count < 0) {
                throw new IllegalArgumentException("invalid detached item presentation");
            }
            stack = stack == null ? ItemStack.EMPTY : stack.copy();

        this.itemId = itemId;
        this.label = label;
        this.count = count;
        this.stack = stack;
        this.resolved = resolved;
    }
    public String itemId() { return itemId; }
    public String label() { return label; }
    public long count() { return count; }
    public boolean resolved() { return resolved; }
 public ItemStack stack() { return stack.copy(); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ItemPresentation)) return false;
        ItemPresentation that = (ItemPresentation) other;
        return java.util.Objects.equals(itemId, that.itemId) && java.util.Objects.equals(label, that.label) && count == that.count && java.util.Objects.equals(stack, that.stack) && resolved == that.resolved;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(itemId);
        hash = 31 * hash + java.util.Objects.hashCode(label);
        hash = 31 * hash + Long.hashCode(count);
        hash = 31 * hash + java.util.Objects.hashCode(stack);
        hash = 31 * hash + Boolean.hashCode(resolved);
        return hash;
    }
    @Override public String toString() { return "ItemPresentation[itemId=" + itemId + ", label=" + label + ", count=" + count + ", stack=" + stack + ", resolved=" + resolved + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ItemPresentation> schema() {
            return new dev.openallay.value.ValueSchema<>(ItemPresentation.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ItemPresentation>>asList(new dev.openallay.value.ValueSchema.Component<>(ItemPresentation.class, "itemId", ItemPresentation::itemId), new dev.openallay.value.ValueSchema.Component<>(ItemPresentation.class, "label", ItemPresentation::label), new dev.openallay.value.ValueSchema.Component<>(ItemPresentation.class, "count", ItemPresentation::count), new dev.openallay.value.ValueSchema.Component<>(ItemPresentation.class, "stack", ItemPresentation::stack), new dev.openallay.value.ValueSchema.Component<>(ItemPresentation.class, "resolved", ItemPresentation::resolved)), arguments -> new ItemPresentation((String) arguments[0], (String) arguments[1], (Long) arguments[2], (ItemStack) arguments[3], (Boolean) arguments[4]));
        }
    }
}

    public ItemPresentation item(String itemId, String suppliedLabel, long count) {
        String fallback = suppliedLabel == null || suppliedLabel.isBlank() ? itemId : suppliedLabel;
        Minecraft minecraft = MinecraftClientWindow.instance();
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
