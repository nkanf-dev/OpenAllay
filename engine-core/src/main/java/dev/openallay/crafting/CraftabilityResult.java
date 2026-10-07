package dev.openallay.crafting;

import java.util.List;

@dev.openallay.value.ValueType(CraftabilityResult.ValueSchemaProvider.class)
public final class CraftabilityResult {
    private final boolean craftable;
    private final boolean conclusive;
    private final long requestedCrafts;
    private final long maximumCrafts;
    private final List<IngredientAllocation> allocations;
    private final List<MissingRequirement> missing;
    public CraftabilityResult(boolean craftable, boolean conclusive, long requestedCrafts, long maximumCrafts, List<IngredientAllocation> allocations, List<MissingRequirement> missing) {

        if (requestedCrafts <= 0 || maximumCrafts < 0) {
            throw new IllegalArgumentException("craft counts are invalid");
        }
        allocations = List.copyOf(allocations);
        missing = List.copyOf(missing);
        if (craftable != missing.isEmpty()) {
            throw new IllegalArgumentException("craftable must agree with missing requirements");
        }

        this.craftable = craftable;
        this.conclusive = conclusive;
        this.requestedCrafts = requestedCrafts;
        this.maximumCrafts = maximumCrafts;
        this.allocations = allocations;
        this.missing = missing;
    }
    public boolean craftable() { return craftable; }
    public boolean conclusive() { return conclusive; }
    public long requestedCrafts() { return requestedCrafts; }
    public long maximumCrafts() { return maximumCrafts; }
    public List<IngredientAllocation> allocations() { return allocations; }
    public List<MissingRequirement> missing() { return missing; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CraftabilityResult)) return false;
        CraftabilityResult that = (CraftabilityResult) other;
        return craftable == that.craftable && conclusive == that.conclusive && requestedCrafts == that.requestedCrafts && maximumCrafts == that.maximumCrafts && java.util.Objects.equals(allocations, that.allocations) && java.util.Objects.equals(missing, that.missing);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(craftable);
        hash = 31 * hash + Boolean.hashCode(conclusive);
        hash = 31 * hash + Long.hashCode(requestedCrafts);
        hash = 31 * hash + Long.hashCode(maximumCrafts);
        hash = 31 * hash + java.util.Objects.hashCode(allocations);
        hash = 31 * hash + java.util.Objects.hashCode(missing);
        return hash;
    }
    @Override public String toString() { return "CraftabilityResult[craftable=" + craftable + ", conclusive=" + conclusive + ", requestedCrafts=" + requestedCrafts + ", maximumCrafts=" + maximumCrafts + ", allocations=" + allocations + ", missing=" + missing + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CraftabilityResult> schema() {
            return new dev.openallay.value.ValueSchema<>(CraftabilityResult.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CraftabilityResult>>asList(new dev.openallay.value.ValueSchema.Component<>(CraftabilityResult.class, "craftable", CraftabilityResult::craftable), new dev.openallay.value.ValueSchema.Component<>(CraftabilityResult.class, "conclusive", CraftabilityResult::conclusive), new dev.openallay.value.ValueSchema.Component<>(CraftabilityResult.class, "requestedCrafts", CraftabilityResult::requestedCrafts), new dev.openallay.value.ValueSchema.Component<>(CraftabilityResult.class, "maximumCrafts", CraftabilityResult::maximumCrafts), new dev.openallay.value.ValueSchema.Component<>(CraftabilityResult.class, "allocations", CraftabilityResult::allocations), new dev.openallay.value.ValueSchema.Component<>(CraftabilityResult.class, "missing", CraftabilityResult::missing)), arguments -> new CraftabilityResult((Boolean) arguments[0], (Boolean) arguments[1], (Long) arguments[2], (Long) arguments[3], (List) arguments[4], (List) arguments[5]));
        }
    }
}
