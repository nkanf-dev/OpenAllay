package dev.openallay.guide;

import dev.openallay.agent.context.ContextBudget;

/** Selected endpoint's actual model budget and canonical reuse identity. */
public record GuideContextSpec(
        ContextBudget budget,
        int promptAndToolTokens,
        String canonicalModelId,
        dev.openallay.agent.context.ContextTokenEstimator estimator) {
    public GuideContextSpec {
        java.util.Objects.requireNonNull(budget, "budget");
        java.util.Objects.requireNonNull(estimator, "estimator");
        if (promptAndToolTokens < 0 || promptAndToolTokens >= budget.inputTokens()) {
            throw new IllegalArgumentException("prompt/tool reservation exhausts model input");
        }
        if (canonicalModelId == null || canonicalModelId.isBlank()) {
            throw new IllegalArgumentException("canonical model ID is required");
        }
    }

    public dev.openallay.model.tokenizer.TokenizerMetadata tokenizerMetadata() {
        return estimator.metadata();
    }

    public GuideContextSpec(ContextBudget budget, int promptAndToolTokens, String canonicalModelId) {
        this(budget, promptAndToolTokens, canonicalModelId,
                dev.openallay.model.tokenizer.ModelContextTokenEstimator.conservative());
    }
}
