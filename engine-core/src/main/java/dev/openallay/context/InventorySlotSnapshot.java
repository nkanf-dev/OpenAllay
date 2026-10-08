package dev.openallay.context;

import java.util.Objects;

@dev.openallay.value.ValueType(InventorySlotSnapshot.ValueSchemaProvider.class)
public final class InventorySlotSnapshot {
    private final int slot;
    private final ItemStackSnapshot stack;
    public InventorySlotSnapshot(int slot, ItemStackSnapshot stack) {

        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative");
        }
        Objects.requireNonNull(stack, "stack");
            this.slot = slot;
        this.stack = stack;
    }
    public int slot() { return slot; }
    public ItemStackSnapshot stack() { return stack; }



    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof InventorySlotSnapshot)) return false;
        InventorySlotSnapshot that = (InventorySlotSnapshot) other;
        return slot == that.slot && java.util.Objects.equals(stack, that.stack);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(slot);
        hash = 31 * hash + java.util.Objects.hashCode(stack);
        return hash;
    }
    @Override public String toString() { return "InventorySlotSnapshot[slot=" + slot + ", stack=" + stack + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<InventorySlotSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(InventorySlotSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<InventorySlotSnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(InventorySlotSnapshot.class, "slot", InventorySlotSnapshot::slot),
                    new dev.openallay.value.ValueSchema.Component<>(InventorySlotSnapshot.class, "stack", InventorySlotSnapshot::stack)), arguments -> new InventorySlotSnapshot((Integer) arguments[0], (ItemStackSnapshot) arguments[1]));
        }
    }
}
