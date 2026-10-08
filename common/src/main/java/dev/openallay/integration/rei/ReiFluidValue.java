package dev.openallay.integration.rei;

/** Detached fluid facts; native and independently published API types stay in the entry helper. */
@dev.openallay.value.ValueType(ReiFluidValue.ValueSchemaProvider.class)
final class ReiFluidValue {
    private final String id;
    private final long amount;
    private final boolean customData;
    private final boolean amountRepresentable;
    ReiFluidValue(String id, long amount, boolean customData, boolean amountRepresentable) {
        this.id = id;
        this.amount = amount;
        this.customData = customData;
        this.amountRepresentable = amountRepresentable;
    }
    public String id() { return id; }
    public long amount() { return amount; }
    public boolean customData() { return customData; }
    public boolean amountRepresentable() { return amountRepresentable; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReiFluidValue)) return false;
        ReiFluidValue that = (ReiFluidValue) other;
        return java.util.Objects.equals(id, that.id) && amount == that.amount && customData == that.customData && amountRepresentable == that.amountRepresentable;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + Long.hashCode(amount);
        hash = 31 * hash + Boolean.hashCode(customData);
        hash = 31 * hash + Boolean.hashCode(amountRepresentable);
        return hash;
    }
    @Override public String toString() { return "ReiFluidValue[id=" + id + ", amount=" + amount + ", customData=" + customData + ", amountRepresentable=" + amountRepresentable + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ReiFluidValue> schema() {
            return new dev.openallay.value.ValueSchema<>(ReiFluidValue.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ReiFluidValue>>asList(new dev.openallay.value.ValueSchema.Component<>(ReiFluidValue.class, "id", ReiFluidValue::id), new dev.openallay.value.ValueSchema.Component<>(ReiFluidValue.class, "amount", ReiFluidValue::amount), new dev.openallay.value.ValueSchema.Component<>(ReiFluidValue.class, "customData", ReiFluidValue::customData), new dev.openallay.value.ValueSchema.Component<>(ReiFluidValue.class, "amountRepresentable", ReiFluidValue::amountRepresentable)), arguments -> new ReiFluidValue((String) arguments[0], (Long) arguments[1], (Boolean) arguments[2], (Boolean) arguments[3]));
        }
    }
}
