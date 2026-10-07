package dev.openallay.context;

@dev.openallay.value.ValueType(ItemStackSnapshot.ValueSchemaProvider.class)
public final class ItemStackSnapshot {
    private final String itemId;
    private final int count;
    private final String displayName;
    public ItemStackSnapshot(String itemId, int count, String displayName) {

        itemId = ContextValidation.identifier(itemId, "itemId");
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        displayName = ContextValidation.nonBlank(displayName, "displayName");
            this.itemId = itemId;
        this.count = count;
        this.displayName = displayName;
    }
    public String itemId() { return itemId; }
    public int count() { return count; }
    public String displayName() { return displayName; }



    public static ItemStackSnapshot empty() {
        return new ItemStackSnapshot("minecraft:air", 0, "Air");
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ItemStackSnapshot)) return false;
        ItemStackSnapshot that = (ItemStackSnapshot) other;
        return java.util.Objects.equals(itemId, that.itemId) && count == that.count && java.util.Objects.equals(displayName, that.displayName);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(itemId);
        hash = 31 * hash + Integer.hashCode(count);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        return hash;
    }
    @Override public String toString() { return "ItemStackSnapshot[itemId=" + itemId + ", count=" + count + ", displayName=" + displayName + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ItemStackSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(ItemStackSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ItemStackSnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(ItemStackSnapshot.class, "itemId", ItemStackSnapshot::itemId),
                    new dev.openallay.value.ValueSchema.Component<>(ItemStackSnapshot.class, "count", ItemStackSnapshot::count),
                    new dev.openallay.value.ValueSchema.Component<>(ItemStackSnapshot.class, "displayName", ItemStackSnapshot::displayName)), arguments -> new ItemStackSnapshot((String) arguments[0], (Integer) arguments[1], (String) arguments[2]));
        }
    }
}
