package dev.openallay.tool;

import java.util.Objects;

/** Size and lifetime of a canonical value retained outside the model transcript. */
@dev.openallay.value.ValueType(ModelResultView.ValueSchemaProvider.class)
public final class ModelResultView {
    private final String handle;
    private final String type;
    private final long cardinality;
    private final long canonicalUtf8Bytes;
    private final boolean complete;
    private final String lifetime;
    private final String inputCoverage;
    public ModelResultView(String handle, String type, long cardinality, long canonicalUtf8Bytes, boolean complete, String lifetime, String inputCoverage) {

        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lifetime, "lifetime");
        inputCoverage = inputCoverage == null ? "" : inputCoverage;
        if (cardinality < 0 || canonicalUtf8Bytes < 0) throw new IllegalArgumentException("Negative result size");

        this.handle = handle;
        this.type = type;
        this.cardinality = cardinality;
        this.canonicalUtf8Bytes = canonicalUtf8Bytes;
        this.complete = complete;
        this.lifetime = lifetime;
        this.inputCoverage = inputCoverage;
    }
    public String handle() { return handle; }
    public String type() { return type; }
    public long cardinality() { return cardinality; }
    public long canonicalUtf8Bytes() { return canonicalUtf8Bytes; }
    public boolean complete() { return complete; }
    public String lifetime() { return lifetime; }
    public String inputCoverage() { return inputCoverage; }
public ModelResultView(String handle, String type, long cardinality,
            long canonicalUtf8Bytes, boolean complete, String lifetime) {
        this(handle, type, cardinality, canonicalUtf8Bytes, complete, lifetime, "");
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelResultView)) return false;
        ModelResultView that = (ModelResultView) other;
        return java.util.Objects.equals(handle, that.handle) && java.util.Objects.equals(type, that.type) && cardinality == that.cardinality && canonicalUtf8Bytes == that.canonicalUtf8Bytes && complete == that.complete && java.util.Objects.equals(lifetime, that.lifetime) && java.util.Objects.equals(inputCoverage, that.inputCoverage);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(handle);
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + Long.hashCode(cardinality);
        hash = 31 * hash + Long.hashCode(canonicalUtf8Bytes);
        hash = 31 * hash + Boolean.hashCode(complete);
        hash = 31 * hash + java.util.Objects.hashCode(lifetime);
        hash = 31 * hash + java.util.Objects.hashCode(inputCoverage);
        return hash;
    }
    @Override public String toString() { return "ModelResultView[handle=" + handle + ", type=" + type + ", cardinality=" + cardinality + ", canonicalUtf8Bytes=" + canonicalUtf8Bytes + ", complete=" + complete + ", lifetime=" + lifetime + ", inputCoverage=" + inputCoverage + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelResultView> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelResultView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelResultView>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelResultView.class, "handle", ModelResultView::handle), new dev.openallay.value.ValueSchema.Component<>(ModelResultView.class, "type", ModelResultView::type), new dev.openallay.value.ValueSchema.Component<>(ModelResultView.class, "cardinality", ModelResultView::cardinality), new dev.openallay.value.ValueSchema.Component<>(ModelResultView.class, "canonicalUtf8Bytes", ModelResultView::canonicalUtf8Bytes), new dev.openallay.value.ValueSchema.Component<>(ModelResultView.class, "complete", ModelResultView::complete), new dev.openallay.value.ValueSchema.Component<>(ModelResultView.class, "lifetime", ModelResultView::lifetime), new dev.openallay.value.ValueSchema.Component<>(ModelResultView.class, "inputCoverage", ModelResultView::inputCoverage)), arguments -> new ModelResultView((String) arguments[0], (String) arguments[1], (Long) arguments[2], (Long) arguments[3], (Boolean) arguments[4], (String) arguments[5], (String) arguments[6]));
        }
    }
}
