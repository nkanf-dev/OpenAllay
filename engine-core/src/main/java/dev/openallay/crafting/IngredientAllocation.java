package dev.openallay.crafting;

@dev.openallay.value.ValueType(IngredientAllocation.ValueSchemaProvider.class)
public final class IngredientAllocation {
    private final String requirementKey;
    private final String itemId;
    private final long count;
    public IngredientAllocation(String requirementKey, String itemId, long count) {

        if (requirementKey == null || dev.openallay.util.Java8Strings.isBlank(requirementKey)
                || itemId == null || dev.openallay.util.Java8Strings.isBlank(itemId)
                || count <= 0) {
            throw new IllegalArgumentException("allocation fields and positive count are required");
        }

        this.requirementKey = requirementKey;
        this.itemId = itemId;
        this.count = count;
    }
    public String requirementKey() { return requirementKey; }
    public String itemId() { return itemId; }
    public long count() { return count; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IngredientAllocation)) return false;
        IngredientAllocation that = (IngredientAllocation) other;
        return java.util.Objects.equals(requirementKey, that.requirementKey) && java.util.Objects.equals(itemId, that.itemId) && count == that.count;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requirementKey);
        hash = 31 * hash + java.util.Objects.hashCode(itemId);
        hash = 31 * hash + Long.hashCode(count);
        return hash;
    }
    @Override public String toString() { return "IngredientAllocation[requirementKey=" + requirementKey + ", itemId=" + itemId + ", count=" + count + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<IngredientAllocation> schema() {
            return new dev.openallay.value.ValueSchema<>(IngredientAllocation.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<IngredientAllocation>>asList(new dev.openallay.value.ValueSchema.Component<>(IngredientAllocation.class, "requirementKey", IngredientAllocation::requirementKey), new dev.openallay.value.ValueSchema.Component<>(IngredientAllocation.class, "itemId", IngredientAllocation::itemId), new dev.openallay.value.ValueSchema.Component<>(IngredientAllocation.class, "count", IngredientAllocation::count)), arguments -> new IngredientAllocation((String) arguments[0], (String) arguments[1], (Long) arguments[2]));
        }
    }
}
