package dev.openallay.context;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@dev.openallay.value.ValueType(InventorySnapshot.ValueSchemaProvider.class)
public final class InventorySnapshot {
    private final List<InventorySlotSnapshot> slots;
    private final int totalSlots;
    private final int selectedHotbarSlot;
    private final int mainHandSlot;
    private final ItemStackSnapshot offHand;
    private final boolean complete;
    private final EvidenceMetadata evidence;
    public InventorySnapshot(List<InventorySlotSnapshot> slots, int totalSlots, int selectedHotbarSlot, int mainHandSlot, ItemStackSnapshot offHand, boolean complete, EvidenceMetadata evidence) {

        slots = dev.openallay.util.Java8Collections.listCopyOf(slots);
        if (totalSlots < 0 || slots.size() > totalSlots) {
            throw new IllegalArgumentException("inventory slot count is invalid");
        }
        if (complete && slots.size() != totalSlots) {
            throw new IllegalArgumentException("complete inventory must contain every slot");
        }
        HashSet<Integer> seen = new HashSet<>();
        for (InventorySlotSnapshot slot : slots) {
            if (slot.slot() >= totalSlots || !seen.add(slot.slot())) {
                throw new IllegalArgumentException("inventory slots must be unique and in range");
            }
        }
        if (selectedHotbarSlot < -1 || selectedHotbarSlot > 8) {
            throw new IllegalArgumentException("selectedHotbarSlot must be -1 or a hotbar index");
        }
        if (mainHandSlot < -1 || mainHandSlot >= totalSlots) {
            throw new IllegalArgumentException("mainHandSlot must be -1 or an inventory slot");
        }
        Objects.requireNonNull(offHand, "offHand");
        Objects.requireNonNull(evidence, "evidence");
            this.slots = slots;
        this.totalSlots = totalSlots;
        this.selectedHotbarSlot = selectedHotbarSlot;
        this.mainHandSlot = mainHandSlot;
        this.offHand = offHand;
        this.complete = complete;
        this.evidence = evidence;
    }
    public List<InventorySlotSnapshot> slots() { return slots; }
    public int totalSlots() { return totalSlots; }
    public int selectedHotbarSlot() { return selectedHotbarSlot; }
    public int mainHandSlot() { return mainHandSlot; }
    public ItemStackSnapshot offHand() { return offHand; }
    public boolean complete() { return complete; }
    public EvidenceMetadata evidence() { return evidence; }



    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof InventorySnapshot)) return false;
        InventorySnapshot that = (InventorySnapshot) other;
        return java.util.Objects.equals(slots, that.slots) && totalSlots == that.totalSlots && selectedHotbarSlot == that.selectedHotbarSlot && mainHandSlot == that.mainHandSlot && java.util.Objects.equals(offHand, that.offHand) && complete == that.complete && java.util.Objects.equals(evidence, that.evidence);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(slots);
        hash = 31 * hash + Integer.hashCode(totalSlots);
        hash = 31 * hash + Integer.hashCode(selectedHotbarSlot);
        hash = 31 * hash + Integer.hashCode(mainHandSlot);
        hash = 31 * hash + java.util.Objects.hashCode(offHand);
        hash = 31 * hash + Boolean.hashCode(complete);
        hash = 31 * hash + java.util.Objects.hashCode(evidence);
        return hash;
    }
    @Override public String toString() { return "InventorySnapshot[slots=" + slots + ", totalSlots=" + totalSlots + ", selectedHotbarSlot=" + selectedHotbarSlot + ", mainHandSlot=" + mainHandSlot + ", offHand=" + offHand + ", complete=" + complete + ", evidence=" + evidence + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<InventorySnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(InventorySnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<InventorySnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(InventorySnapshot.class, "slots", InventorySnapshot::slots),
                    new dev.openallay.value.ValueSchema.Component<>(InventorySnapshot.class, "totalSlots", InventorySnapshot::totalSlots),
                    new dev.openallay.value.ValueSchema.Component<>(InventorySnapshot.class, "selectedHotbarSlot", InventorySnapshot::selectedHotbarSlot),
                    new dev.openallay.value.ValueSchema.Component<>(InventorySnapshot.class, "mainHandSlot", InventorySnapshot::mainHandSlot),
                    new dev.openallay.value.ValueSchema.Component<>(InventorySnapshot.class, "offHand", InventorySnapshot::offHand),
                    new dev.openallay.value.ValueSchema.Component<>(InventorySnapshot.class, "complete", InventorySnapshot::complete),
                    new dev.openallay.value.ValueSchema.Component<>(InventorySnapshot.class, "evidence", InventorySnapshot::evidence)), arguments -> new InventorySnapshot((List) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (ItemStackSnapshot) arguments[4], (Boolean) arguments[5], (EvidenceMetadata) arguments[6]));
        }
    }
}
