package dev.openallay.guide;

import java.util.Objects;
import java.util.UUID;

/** Runtime-only estimate of the latest submitted model input, including prompt and tools. */
@dev.openallay.value.ValueType(GuideContextEstimate.ValueSchemaProvider.class)
public final class GuideContextEstimate {
    private final UUID requestId;
    private final long estimatedTokens;
    private final dev.openallay.agent.context.ContextBudget budget;
    private final String modelIdentifier;
    private final dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting imageAccounting;
    public GuideContextEstimate(UUID requestId, long estimatedTokens, dev.openallay.agent.context.ContextBudget budget, String modelIdentifier, dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting imageAccounting) {

        Objects.requireNonNull(imageAccounting, "imageAccounting");
        Objects.requireNonNull(requestId, "requestId");
        if (estimatedTokens < 0) throw new IllegalArgumentException("Estimate must not be negative");
        if ((budget == null) != (modelIdentifier == null)
                || modelIdentifier != null && dev.openallay.util.Java8Strings.isBlank(modelIdentifier)) {
            throw new IllegalArgumentException("Captured budget and model identity must be supplied together");
        }

        this.requestId = requestId;
        this.estimatedTokens = estimatedTokens;
        this.budget = budget;
        this.modelIdentifier = modelIdentifier;
        this.imageAccounting = imageAccounting;
    }
    public UUID requestId() { return requestId; }
    public long estimatedTokens() { return estimatedTokens; }
    public dev.openallay.agent.context.ContextBudget budget() { return budget; }
    public String modelIdentifier() { return modelIdentifier; }
    public dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting imageAccounting() { return imageAccounting; }
public GuideContextEstimate(
            UUID requestId, long estimatedTokens,
            dev.openallay.agent.context.ContextBudget budget, String modelIdentifier) {
        this(requestId, estimatedTokens, budget, modelIdentifier,
                dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting.TEXT_ONLY);
    }
public GuideContextEstimate(UUID requestId, long estimatedTokens) {
        this(requestId, estimatedTokens, null, null);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideContextEstimate)) return false;
        GuideContextEstimate that = (GuideContextEstimate) other;
        return java.util.Objects.equals(requestId, that.requestId) && estimatedTokens == that.estimatedTokens && java.util.Objects.equals(budget, that.budget) && java.util.Objects.equals(modelIdentifier, that.modelIdentifier) && java.util.Objects.equals(imageAccounting, that.imageAccounting);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + Long.hashCode(estimatedTokens);
        hash = 31 * hash + java.util.Objects.hashCode(budget);
        hash = 31 * hash + java.util.Objects.hashCode(modelIdentifier);
        hash = 31 * hash + java.util.Objects.hashCode(imageAccounting);
        return hash;
    }
    @Override public String toString() { return "GuideContextEstimate[requestId=" + requestId + ", estimatedTokens=" + estimatedTokens + ", budget=" + budget + ", modelIdentifier=" + modelIdentifier + ", imageAccounting=" + imageAccounting + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideContextEstimate> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideContextEstimate.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideContextEstimate>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideContextEstimate.class, "requestId", GuideContextEstimate::requestId), new dev.openallay.value.ValueSchema.Component<>(GuideContextEstimate.class, "estimatedTokens", GuideContextEstimate::estimatedTokens), new dev.openallay.value.ValueSchema.Component<>(GuideContextEstimate.class, "budget", GuideContextEstimate::budget), new dev.openallay.value.ValueSchema.Component<>(GuideContextEstimate.class, "modelIdentifier", GuideContextEstimate::modelIdentifier), new dev.openallay.value.ValueSchema.Component<>(GuideContextEstimate.class, "imageAccounting", GuideContextEstimate::imageAccounting)), arguments -> new GuideContextEstimate((UUID) arguments[0], (Long) arguments[1], (dev.openallay.agent.context.ContextBudget) arguments[2], (String) arguments[3], (dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting) arguments[4]));
        }
    }
}
