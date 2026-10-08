package dev.openallay.guide.history;

import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.guide.GuideMessage;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideSource;
import dev.openallay.guide.GuideTimelineEntry;
import dev.openallay.guide.GuideUsageSnapshot;
import dev.openallay.model.ModelMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Closed set of minimum durable changes accepted by the history store. */
public interface GuideHistoryMutation {
    /** Runtime admission for the exact canonical closed variant family. */
    static GuideHistoryMutation requireKnown(GuideHistoryMutation value) {
        java.util.Objects.requireNonNull(value, "value");
        Class<?> type = value.getClass();
        if (type == dev.openallay.guide.history.GuideHistoryMutation.UpsertPartition.class || type == dev.openallay.guide.history.GuideHistoryMutation.UpsertSession.class || type == dev.openallay.guide.history.GuideHistoryMutation.UpsertSessionUsage.class || type == dev.openallay.guide.history.GuideHistoryMutation.UpsertRequest.class || type == dev.openallay.guide.history.GuideHistoryMutation.UpsertMessage.class || type == dev.openallay.guide.history.GuideHistoryMutation.UpsertTimelineEntry.class || type == dev.openallay.guide.history.GuideHistoryMutation.ReplaceRequestSources.class || type == dev.openallay.guide.history.GuideHistoryMutation.ReplaceContext.class || type == dev.openallay.guide.history.GuideHistoryMutation.ReplaceRequestContext.class || type == dev.openallay.guide.history.GuideHistoryMutation.UpsertCheckpoint.class || type == dev.openallay.guide.history.GuideHistoryMutation.AppendCheckpoint.class || type == dev.openallay.guide.history.GuideHistoryMutation.CaptureRequestBoundary.class || type == dev.openallay.guide.history.GuideHistoryMutation.ForkSession.class || type == dev.openallay.guide.history.GuideHistoryMutation.DeleteSession.class || type == dev.openallay.guide.history.GuideHistoryMutation.ClearSession.class) return value;
        throw new IncompatibleClassChangeError("Unknown GuideHistoryMutation subtype");
    }


    @dev.openallay.value.ValueType(UpsertPartition.ValueSchemaProvider.class)
public static final class UpsertPartition implements GuideHistoryMutation {
    private final String selectedSession;
    private final Instant updatedAt;
    public UpsertPartition(String selectedSession, Instant updatedAt) {

            if (selectedSession == null || dev.openallay.util.Java8Strings.isBlank(selectedSession)) {
                throw new IllegalArgumentException("selected session is required");
            }
            java.util.Objects.requireNonNull(updatedAt, "updatedAt");

        this.selectedSession = selectedSession;
        this.updatedAt = updatedAt;
    }
    public String selectedSession() { return selectedSession; }
    public Instant updatedAt() { return updatedAt; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UpsertPartition)) return false;
        UpsertPartition that = (UpsertPartition) other;
        return java.util.Objects.equals(selectedSession, that.selectedSession) && java.util.Objects.equals(updatedAt, that.updatedAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(selectedSession);
        hash = 31 * hash + java.util.Objects.hashCode(updatedAt);
        return hash;
    }
    @Override public String toString() { return "UpsertPartition[selectedSession=" + selectedSession + ", updatedAt=" + updatedAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<UpsertPartition> schema() {
            return new dev.openallay.value.ValueSchema<>(UpsertPartition.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<UpsertPartition>>asList(new dev.openallay.value.ValueSchema.Component<>(UpsertPartition.class, "selectedSession", UpsertPartition::selectedSession), new dev.openallay.value.ValueSchema.Component<>(UpsertPartition.class, "updatedAt", UpsertPartition::updatedAt)), arguments -> new UpsertPartition((String) arguments[0], (Instant) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(UpsertSession.ValueSchemaProvider.class)
public static final class UpsertSession implements GuideHistoryMutation {
    private final String sessionId;
    private final int ordinal;
    private final GuideModelSelection modelSelection;
    public UpsertSession(String sessionId, int ordinal, GuideModelSelection modelSelection) {

            GuideHistoryMutationDefaults.requireSession(sessionId);
            if (ordinal < 0) throw new IllegalArgumentException("session ordinal is invalid");
            java.util.Objects.requireNonNull(modelSelection, "modelSelection");

        this.sessionId = sessionId;
        this.ordinal = ordinal;
        this.modelSelection = modelSelection;
    }
    public String sessionId() { return sessionId; }
    public int ordinal() { return ordinal; }
    public GuideModelSelection modelSelection() { return modelSelection; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UpsertSession)) return false;
        UpsertSession that = (UpsertSession) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && ordinal == that.ordinal && java.util.Objects.equals(modelSelection, that.modelSelection);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + Integer.hashCode(ordinal);
        hash = 31 * hash + java.util.Objects.hashCode(modelSelection);
        return hash;
    }
    @Override public String toString() { return "UpsertSession[sessionId=" + sessionId + ", ordinal=" + ordinal + ", modelSelection=" + modelSelection + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<UpsertSession> schema() {
            return new dev.openallay.value.ValueSchema<>(UpsertSession.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<UpsertSession>>asList(new dev.openallay.value.ValueSchema.Component<>(UpsertSession.class, "sessionId", UpsertSession::sessionId), new dev.openallay.value.ValueSchema.Component<>(UpsertSession.class, "ordinal", UpsertSession::ordinal), new dev.openallay.value.ValueSchema.Component<>(UpsertSession.class, "modelSelection", UpsertSession::modelSelection)), arguments -> new UpsertSession((String) arguments[0], (Integer) arguments[1], (GuideModelSelection) arguments[2]));
        }
    }
}

    /** Standalone session controls, separate from request usage to avoid double counting. */
    @dev.openallay.value.ValueType(UpsertSessionUsage.ValueSchemaProvider.class)
public static final class UpsertSessionUsage implements GuideHistoryMutation {
    private final String sessionId;
    private final GuideUsageSnapshot controlUsage;
    public UpsertSessionUsage(String sessionId, GuideUsageSnapshot controlUsage) {

            GuideHistoryMutationDefaults.requireSession(sessionId);
            java.util.Objects.requireNonNull(controlUsage, "controlUsage");

        this.sessionId = sessionId;
        this.controlUsage = controlUsage;
    }
    public String sessionId() { return sessionId; }
    public GuideUsageSnapshot controlUsage() { return controlUsage; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UpsertSessionUsage)) return false;
        UpsertSessionUsage that = (UpsertSessionUsage) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(controlUsage, that.controlUsage);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(controlUsage);
        return hash;
    }
    @Override public String toString() { return "UpsertSessionUsage[sessionId=" + sessionId + ", controlUsage=" + controlUsage + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<UpsertSessionUsage> schema() {
            return new dev.openallay.value.ValueSchema<>(UpsertSessionUsage.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<UpsertSessionUsage>>asList(new dev.openallay.value.ValueSchema.Component<>(UpsertSessionUsage.class, "sessionId", UpsertSessionUsage::sessionId), new dev.openallay.value.ValueSchema.Component<>(UpsertSessionUsage.class, "controlUsage", UpsertSessionUsage::controlUsage)), arguments -> new UpsertSessionUsage((String) arguments[0], (GuideUsageSnapshot) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(UpsertRequest.ValueSchemaProvider.class)
public static final class UpsertRequest implements GuideHistoryMutation {
    private final long sequence;
    private final GuideRequestSnapshot request;
    public UpsertRequest(long sequence, GuideRequestSnapshot request) {

            if (sequence < 0) throw new IllegalArgumentException("request sequence is invalid");
            java.util.Objects.requireNonNull(request, "request");

        this.sequence = sequence;
        this.request = request;
    }
    public long sequence() { return sequence; }
    public GuideRequestSnapshot request() { return request; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UpsertRequest)) return false;
        UpsertRequest that = (UpsertRequest) other;
        return sequence == that.sequence && java.util.Objects.equals(request, that.request);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(sequence);
        hash = 31 * hash + java.util.Objects.hashCode(request);
        return hash;
    }
    @Override public String toString() { return "UpsertRequest[sequence=" + sequence + ", request=" + request + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<UpsertRequest> schema() {
            return new dev.openallay.value.ValueSchema<>(UpsertRequest.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<UpsertRequest>>asList(new dev.openallay.value.ValueSchema.Component<>(UpsertRequest.class, "sequence", UpsertRequest::sequence), new dev.openallay.value.ValueSchema.Component<>(UpsertRequest.class, "request", UpsertRequest::request)), arguments -> new UpsertRequest((Long) arguments[0], (GuideRequestSnapshot) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(UpsertMessage.ValueSchemaProvider.class)
public static final class UpsertMessage implements GuideHistoryMutation {
    private final String sessionId;
    private final int ordinal;
    private final GuideMessage message;
    public UpsertMessage(String sessionId, int ordinal, GuideMessage message) {

            GuideHistoryMutationDefaults.requireSession(sessionId);
            if (ordinal < 0) throw new IllegalArgumentException("message ordinal is invalid");
            java.util.Objects.requireNonNull(message, "message");

        this.sessionId = sessionId;
        this.ordinal = ordinal;
        this.message = message;
    }
    public String sessionId() { return sessionId; }
    public int ordinal() { return ordinal; }
    public GuideMessage message() { return message; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UpsertMessage)) return false;
        UpsertMessage that = (UpsertMessage) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && ordinal == that.ordinal && java.util.Objects.equals(message, that.message);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + Integer.hashCode(ordinal);
        hash = 31 * hash + java.util.Objects.hashCode(message);
        return hash;
    }
    @Override public String toString() { return "UpsertMessage[sessionId=" + sessionId + ", ordinal=" + ordinal + ", message=" + message + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<UpsertMessage> schema() {
            return new dev.openallay.value.ValueSchema<>(UpsertMessage.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<UpsertMessage>>asList(new dev.openallay.value.ValueSchema.Component<>(UpsertMessage.class, "sessionId", UpsertMessage::sessionId), new dev.openallay.value.ValueSchema.Component<>(UpsertMessage.class, "ordinal", UpsertMessage::ordinal), new dev.openallay.value.ValueSchema.Component<>(UpsertMessage.class, "message", UpsertMessage::message)), arguments -> new UpsertMessage((String) arguments[0], (Integer) arguments[1], (GuideMessage) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(UpsertTimelineEntry.ValueSchemaProvider.class)
public static final class UpsertTimelineEntry implements GuideHistoryMutation {
    private final UUID requestId;
    private final GuideTimelineEntry entry;
    public UpsertTimelineEntry(UUID requestId, GuideTimelineEntry entry) {

            java.util.Objects.requireNonNull(requestId, "requestId");
            java.util.Objects.requireNonNull(entry, "entry");
            GuideTimelineEntry.requireKnown(entry);

        this.requestId = requestId;
        this.entry = entry;
    }
    public UUID requestId() { return requestId; }
    public GuideTimelineEntry entry() { return entry; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UpsertTimelineEntry)) return false;
        UpsertTimelineEntry that = (UpsertTimelineEntry) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(entry, that.entry);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(entry);
        return hash;
    }
    @Override public String toString() { return "UpsertTimelineEntry[requestId=" + requestId + ", entry=" + entry + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<UpsertTimelineEntry> schema() {
            return new dev.openallay.value.ValueSchema<>(UpsertTimelineEntry.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<UpsertTimelineEntry>>asList(new dev.openallay.value.ValueSchema.Component<>(UpsertTimelineEntry.class, "requestId", UpsertTimelineEntry::requestId), new dev.openallay.value.ValueSchema.Component<>(UpsertTimelineEntry.class, "entry", UpsertTimelineEntry::entry)), arguments -> new UpsertTimelineEntry((UUID) arguments[0], (GuideTimelineEntry) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(ReplaceRequestSources.ValueSchemaProvider.class)
public static final class ReplaceRequestSources implements GuideHistoryMutation {
    private final UUID requestId;
    private final List<GuideSource> sources;
    public ReplaceRequestSources(UUID requestId, List<GuideSource> sources) {

            java.util.Objects.requireNonNull(requestId, "requestId");
            sources = dev.openallay.util.Java8Collections.listCopyOf(sources);

        this.requestId = requestId;
        this.sources = sources;
    }
    public UUID requestId() { return requestId; }
    public List<GuideSource> sources() { return sources; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReplaceRequestSources)) return false;
        ReplaceRequestSources that = (ReplaceRequestSources) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(sources, that.sources);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        return hash;
    }
    @Override public String toString() { return "ReplaceRequestSources[requestId=" + requestId + ", sources=" + sources + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ReplaceRequestSources> schema() {
            return new dev.openallay.value.ValueSchema<>(ReplaceRequestSources.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ReplaceRequestSources>>asList(new dev.openallay.value.ValueSchema.Component<>(ReplaceRequestSources.class, "requestId", ReplaceRequestSources::requestId), new dev.openallay.value.ValueSchema.Component<>(ReplaceRequestSources.class, "sources", ReplaceRequestSources::sources)), arguments -> new ReplaceRequestSources((UUID) arguments[0], (List) arguments[1]));
        }
    }
}

    /** Replaces the actual Agent transcript, not any player-visible history projection. */
    @dev.openallay.value.ValueType(ReplaceContext.ValueSchemaProvider.class)
public static final class ReplaceContext implements GuideHistoryMutation {
    private final String sessionId;
    private final List<ModelMessage> messages;
    public ReplaceContext(String sessionId, List<ModelMessage> messages) {

            GuideHistoryMutationDefaults.requireSession(sessionId);
            messages = ModelContextCodec.safe(messages);

        this.sessionId = sessionId;
        this.messages = messages;
    }
    public String sessionId() { return sessionId; }
    public List<ModelMessage> messages() { return messages; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReplaceContext)) return false;
        ReplaceContext that = (ReplaceContext) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(messages, that.messages);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(messages);
        return hash;
    }
    @Override public String toString() { return "ReplaceContext[sessionId=" + sessionId + ", messages=" + messages + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ReplaceContext> schema() {
            return new dev.openallay.value.ValueSchema<>(ReplaceContext.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ReplaceContext>>asList(new dev.openallay.value.ValueSchema.Component<>(ReplaceContext.class, "sessionId", ReplaceContext::sessionId), new dev.openallay.value.ValueSchema.Component<>(ReplaceContext.class, "messages", ReplaceContext::messages)), arguments -> new ReplaceContext((String) arguments[0], (List) arguments[1]));
        }
    }
}

    /** Original model-visible request messages retained independently of session compaction. */
    @dev.openallay.value.ValueType(ReplaceRequestContext.ValueSchemaProvider.class)
public static final class ReplaceRequestContext implements GuideHistoryMutation {
    private final UUID requestId;
    private final List<ModelMessage> messages;
    public ReplaceRequestContext(UUID requestId, List<ModelMessage> messages) {

            java.util.Objects.requireNonNull(requestId, "requestId");
            messages = ModelContextCodec.safe(messages);

        this.requestId = requestId;
        this.messages = messages;
    }
    public UUID requestId() { return requestId; }
    public List<ModelMessage> messages() { return messages; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ReplaceRequestContext)) return false;
        ReplaceRequestContext that = (ReplaceRequestContext) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(messages, that.messages);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(messages);
        return hash;
    }
    @Override public String toString() { return "ReplaceRequestContext[requestId=" + requestId + ", messages=" + messages + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ReplaceRequestContext> schema() {
            return new dev.openallay.value.ValueSchema<>(ReplaceRequestContext.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ReplaceRequestContext>>asList(new dev.openallay.value.ValueSchema.Component<>(ReplaceRequestContext.class, "requestId", ReplaceRequestContext::requestId), new dev.openallay.value.ValueSchema.Component<>(ReplaceRequestContext.class, "messages", ReplaceRequestContext::messages)), arguments -> new ReplaceRequestContext((UUID) arguments[0], (List) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(UpsertCheckpoint.ValueSchemaProvider.class)
public static final class UpsertCheckpoint implements GuideHistoryMutation {
    private final String sessionId;
    private final int ordinal;
    private final ContextCheckpoint checkpoint;
    public UpsertCheckpoint(String sessionId, int ordinal, ContextCheckpoint checkpoint) {

            GuideHistoryMutationDefaults.requireSession(sessionId);
            if (ordinal < 0) throw new IllegalArgumentException("checkpoint ordinal is invalid");
            java.util.Objects.requireNonNull(checkpoint, "checkpoint");

        this.sessionId = sessionId;
        this.ordinal = ordinal;
        this.checkpoint = checkpoint;
    }
    public String sessionId() { return sessionId; }
    public int ordinal() { return ordinal; }
    public ContextCheckpoint checkpoint() { return checkpoint; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UpsertCheckpoint)) return false;
        UpsertCheckpoint that = (UpsertCheckpoint) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && ordinal == that.ordinal && java.util.Objects.equals(checkpoint, that.checkpoint);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + Integer.hashCode(ordinal);
        hash = 31 * hash + java.util.Objects.hashCode(checkpoint);
        return hash;
    }
    @Override public String toString() { return "UpsertCheckpoint[sessionId=" + sessionId + ", ordinal=" + ordinal + ", checkpoint=" + checkpoint + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<UpsertCheckpoint> schema() {
            return new dev.openallay.value.ValueSchema<>(UpsertCheckpoint.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<UpsertCheckpoint>>asList(new dev.openallay.value.ValueSchema.Component<>(UpsertCheckpoint.class, "sessionId", UpsertCheckpoint::sessionId), new dev.openallay.value.ValueSchema.Component<>(UpsertCheckpoint.class, "ordinal", UpsertCheckpoint::ordinal), new dev.openallay.value.ValueSchema.Component<>(UpsertCheckpoint.class, "checkpoint", UpsertCheckpoint::checkpoint)), arguments -> new UpsertCheckpoint((String) arguments[0], (Integer) arguments[1], (ContextCheckpoint) arguments[2]));
        }
    }
}

    /** Allocates the durable append ordinal; repeated checkpoint identity does not create another row. */
    @dev.openallay.value.ValueType(AppendCheckpoint.ValueSchemaProvider.class)
public static final class AppendCheckpoint implements GuideHistoryMutation {
    private final String sessionId;
    private final ContextCheckpoint checkpoint;
    public AppendCheckpoint(String sessionId, ContextCheckpoint checkpoint) {

            GuideHistoryMutationDefaults.requireSession(sessionId);
            java.util.Objects.requireNonNull(checkpoint, "checkpoint");

        this.sessionId = sessionId;
        this.checkpoint = checkpoint;
    }
    public String sessionId() { return sessionId; }
    public ContextCheckpoint checkpoint() { return checkpoint; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AppendCheckpoint)) return false;
        AppendCheckpoint that = (AppendCheckpoint) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(checkpoint, that.checkpoint);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(checkpoint);
        return hash;
    }
    @Override public String toString() { return "AppendCheckpoint[sessionId=" + sessionId + ", checkpoint=" + checkpoint + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<AppendCheckpoint> schema() {
            return new dev.openallay.value.ValueSchema<>(AppendCheckpoint.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<AppendCheckpoint>>asList(new dev.openallay.value.ValueSchema.Component<>(AppendCheckpoint.class, "sessionId", AppendCheckpoint::sessionId), new dev.openallay.value.ValueSchema.Component<>(AppendCheckpoint.class, "checkpoint", AppendCheckpoint::checkpoint)), arguments -> new AppendCheckpoint((String) arguments[0], (ContextCheckpoint) arguments[1]));
        }
    }
}

    /** Actual safe Agent projection after a terminal request, not a display reconstruction. */
    @dev.openallay.value.ValueType(CaptureRequestBoundary.ValueSchemaProvider.class)
public static final class CaptureRequestBoundary implements GuideHistoryMutation {
    private final UUID requestId;
    private final List<ModelMessage> messages;
    private final List<ContextCheckpoint> checkpoints;
    public CaptureRequestBoundary(UUID requestId, List<ModelMessage> messages, List<ContextCheckpoint> checkpoints) {

            java.util.Objects.requireNonNull(requestId, "requestId");
            messages = ModelContextCodec.safe(messages);
            checkpoints = dev.openallay.util.Java8Collections.listCopyOf(checkpoints);

        this.requestId = requestId;
        this.messages = messages;
        this.checkpoints = checkpoints;
    }
    public UUID requestId() { return requestId; }
    public List<ModelMessage> messages() { return messages; }
    public List<ContextCheckpoint> checkpoints() { return checkpoints; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CaptureRequestBoundary)) return false;
        CaptureRequestBoundary that = (CaptureRequestBoundary) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(messages, that.messages) && java.util.Objects.equals(checkpoints, that.checkpoints);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(messages);
        hash = 31 * hash + java.util.Objects.hashCode(checkpoints);
        return hash;
    }
    @Override public String toString() { return "CaptureRequestBoundary[requestId=" + requestId + ", messages=" + messages + ", checkpoints=" + checkpoints + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CaptureRequestBoundary> schema() {
            return new dev.openallay.value.ValueSchema<>(CaptureRequestBoundary.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CaptureRequestBoundary>>asList(new dev.openallay.value.ValueSchema.Component<>(CaptureRequestBoundary.class, "requestId", CaptureRequestBoundary::requestId), new dev.openallay.value.ValueSchema.Component<>(CaptureRequestBoundary.class, "messages", CaptureRequestBoundary::messages), new dev.openallay.value.ValueSchema.Component<>(CaptureRequestBoundary.class, "checkpoints", CaptureRequestBoundary::checkpoints)), arguments -> new CaptureRequestBoundary((UUID) arguments[0], (List) arguments[1], (List) arguments[2]));
        }
    }
}

    /** Applies only after an exact terminal request; never resumes a pending tool step. */
    @dev.openallay.value.ValueType(ForkSession.ValueSchemaProvider.class)
public static final class ForkSession implements GuideHistoryMutation {
    private final String sourceSessionId;
    private final GuideHistoryCursor cutoff;
    private final String sessionId;
    private final int ordinal;
    private final GuideModelSelection modelSelection;
    public ForkSession(String sourceSessionId, GuideHistoryCursor cutoff, String sessionId, int ordinal, GuideModelSelection modelSelection) {

            GuideHistoryMutationDefaults.requireSession(sourceSessionId);
            java.util.Objects.requireNonNull(cutoff, "cutoff");
            GuideHistoryMutationDefaults.requireSession(sessionId);
            if (sourceSessionId.equals(sessionId)) {
                throw new IllegalArgumentException("fork target must be a new session");
            }
            if (ordinal < 0) throw new IllegalArgumentException("session ordinal is invalid");
            java.util.Objects.requireNonNull(modelSelection, "modelSelection");

        this.sourceSessionId = sourceSessionId;
        this.cutoff = cutoff;
        this.sessionId = sessionId;
        this.ordinal = ordinal;
        this.modelSelection = modelSelection;
    }
    public String sourceSessionId() { return sourceSessionId; }
    public GuideHistoryCursor cutoff() { return cutoff; }
    public String sessionId() { return sessionId; }
    public int ordinal() { return ordinal; }
    public GuideModelSelection modelSelection() { return modelSelection; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ForkSession)) return false;
        ForkSession that = (ForkSession) other;
        return java.util.Objects.equals(sourceSessionId, that.sourceSessionId) && java.util.Objects.equals(cutoff, that.cutoff) && java.util.Objects.equals(sessionId, that.sessionId) && ordinal == that.ordinal && java.util.Objects.equals(modelSelection, that.modelSelection);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sourceSessionId);
        hash = 31 * hash + java.util.Objects.hashCode(cutoff);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + Integer.hashCode(ordinal);
        hash = 31 * hash + java.util.Objects.hashCode(modelSelection);
        return hash;
    }
    @Override public String toString() { return "ForkSession[sourceSessionId=" + sourceSessionId + ", cutoff=" + cutoff + ", sessionId=" + sessionId + ", ordinal=" + ordinal + ", modelSelection=" + modelSelection + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ForkSession> schema() {
            return new dev.openallay.value.ValueSchema<>(ForkSession.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ForkSession>>asList(new dev.openallay.value.ValueSchema.Component<>(ForkSession.class, "sourceSessionId", ForkSession::sourceSessionId), new dev.openallay.value.ValueSchema.Component<>(ForkSession.class, "cutoff", ForkSession::cutoff), new dev.openallay.value.ValueSchema.Component<>(ForkSession.class, "sessionId", ForkSession::sessionId), new dev.openallay.value.ValueSchema.Component<>(ForkSession.class, "ordinal", ForkSession::ordinal), new dev.openallay.value.ValueSchema.Component<>(ForkSession.class, "modelSelection", ForkSession::modelSelection)), arguments -> new ForkSession((String) arguments[0], (GuideHistoryCursor) arguments[1], (String) arguments[2], (Integer) arguments[3], (GuideModelSelection) arguments[4]));
        }
    }
}

    @dev.openallay.value.ValueType(DeleteSession.ValueSchemaProvider.class)
public static final class DeleteSession implements GuideHistoryMutation {
    private final String sessionId;
    public DeleteSession(String sessionId) {
 GuideHistoryMutationDefaults.requireSession(sessionId);
        this.sessionId = sessionId;
    }
    public String sessionId() { return sessionId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DeleteSession)) return false;
        DeleteSession that = (DeleteSession) other;
        return java.util.Objects.equals(sessionId, that.sessionId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        return hash;
    }
    @Override public String toString() { return "DeleteSession[sessionId=" + sessionId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DeleteSession> schema() {
            return new dev.openallay.value.ValueSchema<>(DeleteSession.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DeleteSession>>asList(new dev.openallay.value.ValueSchema.Component<>(DeleteSession.class, "sessionId", DeleteSession::sessionId)), arguments -> new DeleteSession((String) arguments[0]));
        }
    }
}

    @dev.openallay.value.ValueType(ClearSession.ValueSchemaProvider.class)
public static final class ClearSession implements GuideHistoryMutation {
    private final String sessionId;
    public ClearSession(String sessionId) {
 GuideHistoryMutationDefaults.requireSession(sessionId);
        this.sessionId = sessionId;
    }
    public String sessionId() { return sessionId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ClearSession)) return false;
        ClearSession that = (ClearSession) other;
        return java.util.Objects.equals(sessionId, that.sessionId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        return hash;
    }
    @Override public String toString() { return "ClearSession[sessionId=" + sessionId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ClearSession> schema() {
            return new dev.openallay.value.ValueSchema<>(ClearSession.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ClearSession>>asList(new dev.openallay.value.ValueSchema.Component<>(ClearSession.class, "sessionId", ClearSession::sessionId)), arguments -> new ClearSession((String) arguments[0]));
        }
    }
}


}

/** Package-private Java8 implementation of existing interface helper behavior. */
final class GuideHistoryMutationDefaults {
    private GuideHistoryMutationDefaults() {}
static void requireSession(String sessionId) {
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid session ID");
        }
    }
}
