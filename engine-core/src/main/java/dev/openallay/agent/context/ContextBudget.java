package dev.openallay.agent.context;

/** Total model context window with one output turn and one continuation reserved. */
@dev.openallay.value.ValueType(ContextBudget.ValueSchemaProvider.class)
public final class ContextBudget {
    private final int contextWindowTokens;
    private final int maxOutputTokens;
    public ContextBudget(int contextWindowTokens, int maxOutputTokens) {

        if (contextWindowTokens <= 0) {
            throw new IllegalArgumentException("contextWindowTokens must be positive");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        long reserved = (long) maxOutputTokens * 2L;
        if (reserved >= contextWindowTokens) {
            throw new IllegalArgumentException(
                    "contextWindowTokens must exceed two maxOutputTokens reserves");
        }

        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
    }
    public int contextWindowTokens() { return contextWindowTokens; }
    public int maxOutputTokens() { return maxOutputTokens; }
public int reservedTokens() {
        return Math.multiplyExact(maxOutputTokens, 2);
    }
public int inputTokens() {
        return contextWindowTokens - reservedTokens();
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ContextBudget)) return false;
        ContextBudget that = (ContextBudget) other;
        return contextWindowTokens == that.contextWindowTokens && maxOutputTokens == that.maxOutputTokens;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(contextWindowTokens);
        hash = 31 * hash + Integer.hashCode(maxOutputTokens);
        return hash;
    }
    @Override public String toString() { return "ContextBudget[contextWindowTokens=" + contextWindowTokens + ", maxOutputTokens=" + maxOutputTokens + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ContextBudget> schema() {
            return new dev.openallay.value.ValueSchema<>(ContextBudget.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ContextBudget>>asList(new dev.openallay.value.ValueSchema.Component<>(ContextBudget.class, "contextWindowTokens", ContextBudget::contextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(ContextBudget.class, "maxOutputTokens", ContextBudget::maxOutputTokens)), arguments -> new ContextBudget((Integer) arguments[0], (Integer) arguments[1]));
        }
    }
}
