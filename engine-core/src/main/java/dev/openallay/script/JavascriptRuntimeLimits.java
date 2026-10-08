package dev.openallay.script;

/**
 * Reviewable resource defaults for one model-authored Rhino execution.
 *
 * <p>Rhino is an embedded engine rather than a process boundary, so these limits reject oversized
 * programs and result graphs before they can enter the request workspace or model context.
 */
@dev.openallay.value.ValueType(JavascriptRuntimeLimits.ValueSchemaProvider.class)
public final class JavascriptRuntimeLimits {
    private final int maxSourceCharacters;
    private final int maxResultDepth;
    private final long maxResultNodes;
    private final long maxArrayLength;
    private final int maxObjectFields;
    private final int maxStringCharacters;
    public JavascriptRuntimeLimits(int maxSourceCharacters, int maxResultDepth, long maxResultNodes, long maxArrayLength, int maxObjectFields, int maxStringCharacters) {

        if (maxSourceCharacters <= 0
                || maxResultDepth <= 0
                || maxResultNodes <= 0
                || maxArrayLength <= 0
                || maxObjectFields <= 0
                || maxStringCharacters <= 0) {
            throw new IllegalArgumentException("JavaScript runtime limits must be positive");
        }

        this.maxSourceCharacters = maxSourceCharacters;
        this.maxResultDepth = maxResultDepth;
        this.maxResultNodes = maxResultNodes;
        this.maxArrayLength = maxArrayLength;
        this.maxObjectFields = maxObjectFields;
        this.maxStringCharacters = maxStringCharacters;
    }
    public int maxSourceCharacters() { return maxSourceCharacters; }
    public int maxResultDepth() { return maxResultDepth; }
    public long maxResultNodes() { return maxResultNodes; }
    public long maxArrayLength() { return maxArrayLength; }
    public int maxObjectFields() { return maxObjectFields; }
    public int maxStringCharacters() { return maxStringCharacters; }
public static final JavascriptRuntimeLimits DEFAULT = new JavascriptRuntimeLimits(
            65_536,
            64,
            250_000,
            250_000,
            16_384,
            524_288);
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JavascriptRuntimeLimits)) return false;
        JavascriptRuntimeLimits that = (JavascriptRuntimeLimits) other;
        return maxSourceCharacters == that.maxSourceCharacters && maxResultDepth == that.maxResultDepth && maxResultNodes == that.maxResultNodes && maxArrayLength == that.maxArrayLength && maxObjectFields == that.maxObjectFields && maxStringCharacters == that.maxStringCharacters;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(maxSourceCharacters);
        hash = 31 * hash + Integer.hashCode(maxResultDepth);
        hash = 31 * hash + Long.hashCode(maxResultNodes);
        hash = 31 * hash + Long.hashCode(maxArrayLength);
        hash = 31 * hash + Integer.hashCode(maxObjectFields);
        hash = 31 * hash + Integer.hashCode(maxStringCharacters);
        return hash;
    }
    @Override public String toString() { return "JavascriptRuntimeLimits[maxSourceCharacters=" + maxSourceCharacters + ", maxResultDepth=" + maxResultDepth + ", maxResultNodes=" + maxResultNodes + ", maxArrayLength=" + maxArrayLength + ", maxObjectFields=" + maxObjectFields + ", maxStringCharacters=" + maxStringCharacters + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<JavascriptRuntimeLimits> schema() {
            return new dev.openallay.value.ValueSchema<>(JavascriptRuntimeLimits.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<JavascriptRuntimeLimits>>asList(new dev.openallay.value.ValueSchema.Component<>(JavascriptRuntimeLimits.class, "maxSourceCharacters", JavascriptRuntimeLimits::maxSourceCharacters), new dev.openallay.value.ValueSchema.Component<>(JavascriptRuntimeLimits.class, "maxResultDepth", JavascriptRuntimeLimits::maxResultDepth), new dev.openallay.value.ValueSchema.Component<>(JavascriptRuntimeLimits.class, "maxResultNodes", JavascriptRuntimeLimits::maxResultNodes), new dev.openallay.value.ValueSchema.Component<>(JavascriptRuntimeLimits.class, "maxArrayLength", JavascriptRuntimeLimits::maxArrayLength), new dev.openallay.value.ValueSchema.Component<>(JavascriptRuntimeLimits.class, "maxObjectFields", JavascriptRuntimeLimits::maxObjectFields), new dev.openallay.value.ValueSchema.Component<>(JavascriptRuntimeLimits.class, "maxStringCharacters", JavascriptRuntimeLimits::maxStringCharacters)), arguments -> new JavascriptRuntimeLimits((Integer) arguments[0], (Integer) arguments[1], (Long) arguments[2], (Long) arguments[3], (Integer) arguments[4], (Integer) arguments[5]));
        }
    }
}
