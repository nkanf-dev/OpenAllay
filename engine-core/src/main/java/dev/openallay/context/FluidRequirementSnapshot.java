package dev.openallay.context;

@dev.openallay.value.ValueType(FluidRequirementSnapshot.ValueSchemaProvider.class)
public final class FluidRequirementSnapshot {
    private final String fluidId;
    private final long amount;
    private final boolean consumed;
    public FluidRequirementSnapshot(String fluidId, long amount, boolean consumed) {

        fluidId = ContextValidation.identifier(fluidId, "fluidId");
        if (amount <= 0) {
            throw new IllegalArgumentException("fluid amount must be positive");
        }
            this.fluidId = fluidId;
        this.amount = amount;
        this.consumed = consumed;
    }
    public String fluidId() { return fluidId; }
    public long amount() { return amount; }
    public boolean consumed() { return consumed; }



    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FluidRequirementSnapshot)) return false;
        FluidRequirementSnapshot that = (FluidRequirementSnapshot) other;
        return java.util.Objects.equals(fluidId, that.fluidId) && amount == that.amount && consumed == that.consumed;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(fluidId);
        hash = 31 * hash + Long.hashCode(amount);
        hash = 31 * hash + Boolean.hashCode(consumed);
        return hash;
    }
    @Override public String toString() { return "FluidRequirementSnapshot[fluidId=" + fluidId + ", amount=" + amount + ", consumed=" + consumed + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<FluidRequirementSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(FluidRequirementSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<FluidRequirementSnapshot>>asList(
                    new dev.openallay.value.ValueSchema.Component<>(FluidRequirementSnapshot.class, "fluidId", FluidRequirementSnapshot::fluidId),
                    new dev.openallay.value.ValueSchema.Component<>(FluidRequirementSnapshot.class, "amount", FluidRequirementSnapshot::amount),
                    new dev.openallay.value.ValueSchema.Component<>(FluidRequirementSnapshot.class, "consumed", FluidRequirementSnapshot::consumed)), arguments -> new FluidRequirementSnapshot((String) arguments[0], (Long) arguments[1], (Boolean) arguments[2]));
        }
    }
}
