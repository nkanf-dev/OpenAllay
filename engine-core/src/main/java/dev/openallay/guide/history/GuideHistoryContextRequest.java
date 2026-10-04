package dev.openallay.guide.history;

import dev.openallay.agent.context.ContextBudget;

/** Actual selected-model budget plus non-history input reservations. */
public record GuideHistoryContextRequest(
        GuideHistoryScope scope,
        String sessionId,
        ContextBudget budget,
        int promptAndToolTokens,
        String modelIdentifier,
        dev.openallay.agent.context.ContextTokenEstimator estimator) {
    public GuideHistoryContextRequest {
        java.util.Objects.requireNonNull(scope, "scope");
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid session ID");
        }
        java.util.Objects.requireNonNull(budget, "budget");
        java.util.Objects.requireNonNull(estimator, "estimator");
        if (promptAndToolTokens < 0 || promptAndToolTokens >= budget.inputTokens()) {
            throw new IllegalArgumentException("prompt/tool reservation exhausts model input");
        }
        if (modelIdentifier == null || modelIdentifier.isBlank()) {
            throw new IllegalArgumentException("model identifier is required");
        }
    }

    public GuideHistoryContextRequest(
            GuideHistoryScope scope, String sessionId, ContextBudget budget,
            int promptAndToolTokens, String modelIdentifier) {
        this(scope, sessionId, budget, promptAndToolTokens, modelIdentifier,
                dev.openallay.model.tokenizer.ModelContextTokenEstimator.conservative());
    }

    public int availableHistoryTokens() {
        return budget.inputTokens() - promptAndToolTokens;
    }
}
