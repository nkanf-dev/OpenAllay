package dev.openallay.guide;

import dev.openallay.agent.context.ContextBudget;

/** Selected endpoint's actual model budget and canonical reuse identity. */
@dev.openallay.value.ValueType(GuideContextSpec.ValueSchemaProvider.class)
public final class GuideContextSpec {
    private final ContextBudget budget;
    private final int promptAndToolTokens;
    private final String canonicalModelId;
    private final dev.openallay.agent.context.ContextTokenEstimator estimator;
    public GuideContextSpec(ContextBudget budget, int promptAndToolTokens, String canonicalModelId, dev.openallay.agent.context.ContextTokenEstimator estimator) {

        java.util.Objects.requireNonNull(budget, "budget");
        java.util.Objects.requireNonNull(estimator, "estimator");
        if (promptAndToolTokens < 0 || promptAndToolTokens >= budget.inputTokens()) {
            throw new IllegalArgumentException("prompt/tool reservation exhausts model input");
        }
        if (canonicalModelId == null || canonicalModelId.isBlank()) {
            throw new IllegalArgumentException("canonical model ID is required");
        }

        this.budget = budget;
        this.promptAndToolTokens = promptAndToolTokens;
        this.canonicalModelId = canonicalModelId;
        this.estimator = estimator;
    }
    public ContextBudget budget() { return budget; }
    public int promptAndToolTokens() { return promptAndToolTokens; }
    public String canonicalModelId() { return canonicalModelId; }
    public dev.openallay.agent.context.ContextTokenEstimator estimator() { return estimator; }
public dev.openallay.model.tokenizer.TokenizerMetadata tokenizerMetadata() {
        return estimator.metadata();
    }
public GuideContextSpec(ContextBudget budget, int promptAndToolTokens, String canonicalModelId) {
        this(budget, promptAndToolTokens, canonicalModelId,
                dev.openallay.model.tokenizer.ModelContextTokenEstimator.conservative());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideContextSpec)) return false;
        GuideContextSpec that = (GuideContextSpec) other;
        return java.util.Objects.equals(budget, that.budget) && promptAndToolTokens == that.promptAndToolTokens && java.util.Objects.equals(canonicalModelId, that.canonicalModelId) && java.util.Objects.equals(estimator, that.estimator);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(budget);
        hash = 31 * hash + Integer.hashCode(promptAndToolTokens);
        hash = 31 * hash + java.util.Objects.hashCode(canonicalModelId);
        hash = 31 * hash + java.util.Objects.hashCode(estimator);
        return hash;
    }
    @Override public String toString() { return "GuideContextSpec[budget=" + budget + ", promptAndToolTokens=" + promptAndToolTokens + ", canonicalModelId=" + canonicalModelId + ", estimator=" + estimator + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideContextSpec> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideContextSpec.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideContextSpec>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideContextSpec.class, "budget", GuideContextSpec::budget), new dev.openallay.value.ValueSchema.Component<>(GuideContextSpec.class, "promptAndToolTokens", GuideContextSpec::promptAndToolTokens), new dev.openallay.value.ValueSchema.Component<>(GuideContextSpec.class, "canonicalModelId", GuideContextSpec::canonicalModelId), new dev.openallay.value.ValueSchema.Component<>(GuideContextSpec.class, "estimator", GuideContextSpec::estimator)), arguments -> new GuideContextSpec((ContextBudget) arguments[0], (Integer) arguments[1], (String) arguments[2], (dev.openallay.agent.context.ContextTokenEstimator) arguments[3]));
        }
    }
}
