package dev.openallay.agent.context;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@dev.openallay.value.ValueType(ContextCheckpoint.ValueSchemaProvider.class)
public final class ContextCheckpoint {
    private final UUID checkpointId;
    private final int sourceFromIndex;
    private final int sourceToIndexExclusive;
    private final String sourceHash;
    private final String modelIdentifier;
    private final Instant createdAt;
    private final Status status;
    private final String summary;
    private final String failureCode;
    private final String failureMessage;
    private final int estimatedProjectionTokens;
    public ContextCheckpoint(UUID checkpointId, int sourceFromIndex, int sourceToIndexExclusive, String sourceHash, String modelIdentifier, Instant createdAt, Status status, String summary, String failureCode, String failureMessage, int estimatedProjectionTokens) {

        Objects.requireNonNull(checkpointId, "checkpointId");
        if (sourceFromIndex < 0 || sourceToIndexExclusive <= sourceFromIndex) {
            throw new IllegalArgumentException("checkpoint source range is invalid");
        }
        if (sourceHash == null || !sourceHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("checkpoint sourceHash must be lowercase SHA-256");
        }
        if (modelIdentifier == null || dev.openallay.util.Java8Strings.isBlank(modelIdentifier)) {
            throw new IllegalArgumentException("checkpoint modelIdentifier is required");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(status, "status");
        if (estimatedProjectionTokens < 0) {
            throw new IllegalArgumentException("checkpoint estimate must be non-negative");
        }
        if (status == Status.SUCCEEDED) {
            if (summary == null || dev.openallay.util.Java8Strings.isBlank(summary) || failureCode != null || failureMessage != null) {
                throw new IllegalArgumentException("successful checkpoint requires only a summary");
            }
        } else if (summary != null
                || failureCode == null || dev.openallay.util.Java8Strings.isBlank(failureCode)
                || failureMessage == null || dev.openallay.util.Java8Strings.isBlank(failureMessage)) {
            throw new IllegalArgumentException("failed checkpoint requires only failure details");
        }

        this.checkpointId = checkpointId;
        this.sourceFromIndex = sourceFromIndex;
        this.sourceToIndexExclusive = sourceToIndexExclusive;
        this.sourceHash = sourceHash;
        this.modelIdentifier = modelIdentifier;
        this.createdAt = createdAt;
        this.status = status;
        this.summary = summary;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.estimatedProjectionTokens = estimatedProjectionTokens;
    }
    public UUID checkpointId() { return checkpointId; }
    public int sourceFromIndex() { return sourceFromIndex; }
    public int sourceToIndexExclusive() { return sourceToIndexExclusive; }
    public String sourceHash() { return sourceHash; }
    public String modelIdentifier() { return modelIdentifier; }
    public Instant createdAt() { return createdAt; }
    public Status status() { return status; }
    public String summary() { return summary; }
    public String failureCode() { return failureCode; }
    public String failureMessage() { return failureMessage; }
    public int estimatedProjectionTokens() { return estimatedProjectionTokens; }
public enum Status {
        SUCCEEDED,
        FAILED
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ContextCheckpoint)) return false;
        ContextCheckpoint that = (ContextCheckpoint) other;
        return java.util.Objects.equals(checkpointId, that.checkpointId) && sourceFromIndex == that.sourceFromIndex && sourceToIndexExclusive == that.sourceToIndexExclusive && java.util.Objects.equals(sourceHash, that.sourceHash) && java.util.Objects.equals(modelIdentifier, that.modelIdentifier) && java.util.Objects.equals(createdAt, that.createdAt) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(summary, that.summary) && java.util.Objects.equals(failureCode, that.failureCode) && java.util.Objects.equals(failureMessage, that.failureMessage) && estimatedProjectionTokens == that.estimatedProjectionTokens;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(checkpointId);
        hash = 31 * hash + Integer.hashCode(sourceFromIndex);
        hash = 31 * hash + Integer.hashCode(sourceToIndexExclusive);
        hash = 31 * hash + java.util.Objects.hashCode(sourceHash);
        hash = 31 * hash + java.util.Objects.hashCode(modelIdentifier);
        hash = 31 * hash + java.util.Objects.hashCode(createdAt);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(summary);
        hash = 31 * hash + java.util.Objects.hashCode(failureCode);
        hash = 31 * hash + java.util.Objects.hashCode(failureMessage);
        hash = 31 * hash + Integer.hashCode(estimatedProjectionTokens);
        return hash;
    }
    @Override public String toString() { return "ContextCheckpoint[checkpointId=" + checkpointId + ", sourceFromIndex=" + sourceFromIndex + ", sourceToIndexExclusive=" + sourceToIndexExclusive + ", sourceHash=" + sourceHash + ", modelIdentifier=" + modelIdentifier + ", createdAt=" + createdAt + ", status=" + status + ", summary=" + summary + ", failureCode=" + failureCode + ", failureMessage=" + failureMessage + ", estimatedProjectionTokens=" + estimatedProjectionTokens + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ContextCheckpoint> schema() {
            return new dev.openallay.value.ValueSchema<>(ContextCheckpoint.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ContextCheckpoint>>asList(new dev.openallay.value.ValueSchema.Component<>(ContextCheckpoint.class, "checkpointId", ContextCheckpoint::checkpointId), new dev.openallay.value.ValueSchema.Component<>(ContextCheckpoint.class, "sourceFromIndex", ContextCheckpoint::sourceFromIndex), new dev.openallay.value.ValueSchema.Component<>(ContextCheckpoint.class, "sourceToIndexExclusive", ContextCheckpoint::sourceToIndexExclusive), new dev.openallay.value.ValueSchema.Component<>(ContextCheckpoint.class, "sourceHash", ContextCheckpoint::sourceHash), new dev.openallay.value.ValueSchema.Component<>(ContextCheckpoint.class, "modelIdentifier", ContextCheckpoint::modelIdentifier), new dev.openallay.value.ValueSchema.Component<>(ContextCheckpoint.class, "createdAt", ContextCheckpoint::createdAt), new dev.openallay.value.ValueSchema.Component<>(ContextCheckpoint.class, "status", ContextCheckpoint::status), new dev.openallay.value.ValueSchema.Component<>(ContextCheckpoint.class, "summary", ContextCheckpoint::summary), new dev.openallay.value.ValueSchema.Component<>(ContextCheckpoint.class, "failureCode", ContextCheckpoint::failureCode), new dev.openallay.value.ValueSchema.Component<>(ContextCheckpoint.class, "failureMessage", ContextCheckpoint::failureMessage), new dev.openallay.value.ValueSchema.Component<>(ContextCheckpoint.class, "estimatedProjectionTokens", ContextCheckpoint::estimatedProjectionTokens)), arguments -> new ContextCheckpoint((UUID) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (String) arguments[3], (String) arguments[4], (Instant) arguments[5], (Status) arguments[6], (String) arguments[7], (String) arguments[8], (String) arguments[9], (Integer) arguments[10]));
        }
    }
}
