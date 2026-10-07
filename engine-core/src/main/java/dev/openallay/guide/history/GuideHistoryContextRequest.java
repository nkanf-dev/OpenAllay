package dev.openallay.guide.history;

import dev.openallay.agent.context.ContextBudget;

/** Actual selected-model budget plus non-history input reservations. */
@dev.openallay.value.ValueType(GuideHistoryContextRequest.ValueSchemaProvider.class)
public final class GuideHistoryContextRequest {
    private final GuideHistoryScope scope;
    private final String sessionId;
    private final ContextBudget budget;
    private final int promptAndToolTokens;
    private final String modelIdentifier;
    private final dev.openallay.agent.context.ContextTokenEstimator estimator;
    public GuideHistoryContextRequest(GuideHistoryScope scope, String sessionId, ContextBudget budget, int promptAndToolTokens, String modelIdentifier, dev.openallay.agent.context.ContextTokenEstimator estimator) {

        java.util.Objects.requireNonNull(scope, "scope");
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid session ID");
        }
        java.util.Objects.requireNonNull(budget, "budget");
        java.util.Objects.requireNonNull(estimator, "estimator");
        if (promptAndToolTokens < 0 || promptAndToolTokens >= budget.inputTokens()) {
            throw new IllegalArgumentException("prompt/tool reservation exhausts model input");
        }
        if (modelIdentifier == null || dev.openallay.util.Java8Strings.isBlank(modelIdentifier)) {
            throw new IllegalArgumentException("model identifier is required");
        }

        this.scope = scope;
        this.sessionId = sessionId;
        this.budget = budget;
        this.promptAndToolTokens = promptAndToolTokens;
        this.modelIdentifier = modelIdentifier;
        this.estimator = estimator;
    }
    public GuideHistoryScope scope() { return scope; }
    public String sessionId() { return sessionId; }
    public ContextBudget budget() { return budget; }
    public int promptAndToolTokens() { return promptAndToolTokens; }
    public String modelIdentifier() { return modelIdentifier; }
    public dev.openallay.agent.context.ContextTokenEstimator estimator() { return estimator; }
public GuideHistoryContextRequest(
            GuideHistoryScope scope, String sessionId, ContextBudget budget,
            int promptAndToolTokens, String modelIdentifier) {
        this(scope, sessionId, budget, promptAndToolTokens, modelIdentifier,
                dev.openallay.model.tokenizer.ModelContextTokenEstimator.conservative());
    }
public int availableHistoryTokens() {
        return budget.inputTokens() - promptAndToolTokens;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideHistoryContextRequest)) return false;
        GuideHistoryContextRequest that = (GuideHistoryContextRequest) other;
        return java.util.Objects.equals(scope, that.scope) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(budget, that.budget) && promptAndToolTokens == that.promptAndToolTokens && java.util.Objects.equals(modelIdentifier, that.modelIdentifier) && java.util.Objects.equals(estimator, that.estimator);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(scope);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(budget);
        hash = 31 * hash + Integer.hashCode(promptAndToolTokens);
        hash = 31 * hash + java.util.Objects.hashCode(modelIdentifier);
        hash = 31 * hash + java.util.Objects.hashCode(estimator);
        return hash;
    }
    @Override public String toString() { return "GuideHistoryContextRequest[scope=" + scope + ", sessionId=" + sessionId + ", budget=" + budget + ", promptAndToolTokens=" + promptAndToolTokens + ", modelIdentifier=" + modelIdentifier + ", estimator=" + estimator + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideHistoryContextRequest> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideHistoryContextRequest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideHistoryContextRequest>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideHistoryContextRequest.class, "scope", GuideHistoryContextRequest::scope), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryContextRequest.class, "sessionId", GuideHistoryContextRequest::sessionId), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryContextRequest.class, "budget", GuideHistoryContextRequest::budget), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryContextRequest.class, "promptAndToolTokens", GuideHistoryContextRequest::promptAndToolTokens), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryContextRequest.class, "modelIdentifier", GuideHistoryContextRequest::modelIdentifier), new dev.openallay.value.ValueSchema.Component<>(GuideHistoryContextRequest.class, "estimator", GuideHistoryContextRequest::estimator)), arguments -> new GuideHistoryContextRequest((GuideHistoryScope) arguments[0], (String) arguments[1], (ContextBudget) arguments[2], (Integer) arguments[3], (String) arguments[4], (dev.openallay.agent.context.ContextTokenEstimator) arguments[5]));
        }
    }
}
