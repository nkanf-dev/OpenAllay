package dev.openallay.guide;

import dev.openallay.model.ModelUsage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@dev.openallay.value.ValueType(GuideRequestSnapshot.ValueSchemaProvider.class)
public final class GuideRequestSnapshot {
    private final UUID requestId;
    private final String sessionId;
    private final GuideTopology topology;
    private final String userMessage;
    private final List<GuideTimelineEntry> timeline;
    private final GuideRequestStatus status;
    private final List<GuideSource> sources;
    private final ModelUsage usage;
    private final Long retryAfterMillis;
    private final GuideFailure failure;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant terminalAt;
    private final GuideModelSelection modelSelection;
    private final GuideRequestProgress progress;
    private final GuideUsageSnapshot usageProjection;
    private final UUID usageOriginRequestId;
    public GuideRequestSnapshot(UUID requestId, String sessionId, GuideTopology topology, String userMessage, List<GuideTimelineEntry> timeline, GuideRequestStatus status, List<GuideSource> sources, ModelUsage usage, Long retryAfterMillis, GuideFailure failure, Instant createdAt, Instant updatedAt, Instant terminalAt, GuideModelSelection modelSelection, GuideRequestProgress progress, GuideUsageSnapshot usageProjection, UUID usageOriginRequestId) {

        java.util.Objects.requireNonNull(requestId, "requestId");
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid sessionId");
        }
        java.util.Objects.requireNonNull(topology, "topology");
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be blank");
        }
        timeline = List.copyOf(timeline);
        for (int index = 0; index < timeline.size(); index++) {
            if (timeline.get(index).ordinal() != index) {
                throw new IllegalArgumentException("timeline ordinals must be contiguous");
            }
        }
        java.util.Objects.requireNonNull(status, "status");
        sources = List.copyOf(sources);
        java.util.Objects.requireNonNull(usage, "usage");
        if (retryAfterMillis != null && retryAfterMillis < 0) {
            throw new IllegalArgumentException("retryAfterMillis must not be negative");
        }
        java.util.Objects.requireNonNull(createdAt, "createdAt");
        java.util.Objects.requireNonNull(updatedAt, "updatedAt");
        java.util.Objects.requireNonNull(modelSelection, "modelSelection");
        java.util.Objects.requireNonNull(progress, "progress");
        java.util.Objects.requireNonNull(usageProjection, "usageProjection");
        if (!progress.requestStartedAt().equals(createdAt)) {
            throw new IllegalArgumentException("request progress must share createdAt");
        }
        if (modelSelection.modelMode() == GuideModelMode.SERVER
                && topology != GuideTopology.SERVER) {
            throw new IllegalArgumentException("server model selection requires server topology");
        }
        if (modelSelection.modelMode() == GuideModelMode.CLIENT
                && topology == GuideTopology.SERVER) {
            throw new IllegalArgumentException("client model selection cannot use server topology");
        }

        this.requestId = requestId;
        this.sessionId = sessionId;
        this.topology = topology;
        this.userMessage = userMessage;
        this.timeline = timeline;
        this.status = status;
        this.sources = sources;
        this.usage = usage;
        this.retryAfterMillis = retryAfterMillis;
        this.failure = failure;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.terminalAt = terminalAt;
        this.modelSelection = modelSelection;
        this.progress = progress;
        this.usageProjection = usageProjection;
        this.usageOriginRequestId = usageOriginRequestId;
    }
    public UUID requestId() { return requestId; }
    public String sessionId() { return sessionId; }
    public GuideTopology topology() { return topology; }
    public String userMessage() { return userMessage; }
    public List<GuideTimelineEntry> timeline() { return timeline; }
    public GuideRequestStatus status() { return status; }
    public List<GuideSource> sources() { return sources; }
    public ModelUsage usage() { return usage; }
    public Long retryAfterMillis() { return retryAfterMillis; }
    public GuideFailure failure() { return failure; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant terminalAt() { return terminalAt; }
    public GuideModelSelection modelSelection() { return modelSelection; }
    public GuideRequestProgress progress() { return progress; }
    public GuideUsageSnapshot usageProjection() { return usageProjection; }
    public UUID usageOriginRequestId() { return usageOriginRequestId; }
public GuideRequestSnapshot(
            UUID requestId, String sessionId, GuideTopology topology, String userMessage,
            List<GuideTimelineEntry> timeline, GuideRequestStatus status, List<GuideSource> sources,
            ModelUsage usage, Long retryAfterMillis, GuideFailure failure,
            Instant createdAt, Instant updatedAt, Instant terminalAt,
            GuideModelSelection modelSelection, GuideRequestProgress progress) {
        this(requestId, sessionId, topology, userMessage, timeline, status, sources, usage,
                retryAfterMillis, failure, createdAt, updatedAt, terminalAt, modelSelection,
                progress, GuideUsageSnapshot.empty(), null);
    }
public GuideRequestSnapshot withUsageProjection(GuideUsageSnapshot projection) {
        return new GuideRequestSnapshot(requestId, sessionId, topology, userMessage, timeline,
                status, sources, usage, retryAfterMillis, failure, createdAt, updatedAt, terminalAt,
                modelSelection, progress, projection, usageOriginRequestId);
    }
public GuideRequestSnapshot(
            UUID requestId,
            String sessionId,
            GuideTopology topology,
            String userMessage,
            List<GuideTimelineEntry> timeline,
            GuideRequestStatus status,
            List<GuideSource> sources,
            ModelUsage usage,
            Long retryAfterMillis,
            GuideFailure failure,
            Instant createdAt,
            Instant updatedAt,
            Instant terminalAt,
            GuideModelSelection modelSelection) {
        this(
                requestId,
                sessionId,
                topology,
                userMessage,
                timeline,
                status,
                sources,
                usage,
                retryAfterMillis,
                failure,
                createdAt,
                updatedAt,
                terminalAt,
                modelSelection,
                legacyProgress(status, retryAfterMillis, createdAt, updatedAt));
    }
public GuideRequestSnapshot(
            UUID requestId,
            String sessionId,
            GuideTopology topology,
            String userMessage,
            List<GuideTimelineEntry> timeline,
            GuideRequestStatus status,
            List<GuideSource> sources,
            ModelUsage usage,
            Long retryAfterMillis,
            GuideFailure failure,
            Instant createdAt,
            Instant updatedAt,
            Instant terminalAt) {
        this(
                requestId,
                sessionId,
                topology,
                userMessage,
                timeline,
                status,
                sources,
                usage,
                retryAfterMillis,
                failure,
                createdAt,
                updatedAt,
                terminalAt,
                topology == GuideTopology.SERVER
                        ? GuideModelSelection.server()
                        : GuideModelSelection.client("default"),
                legacyProgress(status, retryAfterMillis, createdAt, updatedAt));
    }
public static GuideRequestSnapshot start(
            UUID requestId,
            String sessionId,
            GuideTopology topology,
            String userMessage,
            Instant now) {
        return start(
                requestId,
                sessionId,
                topology,
                userMessage,
                now,
                topology == GuideTopology.SERVER
                        ? GuideModelSelection.server()
                        : GuideModelSelection.client("default"));
    }
public static GuideRequestSnapshot start(
            UUID requestId,
            String sessionId,
            GuideTopology topology,
            String userMessage,
            Instant now,
            GuideModelSelection modelSelection) {
        return new GuideRequestSnapshot(
                requestId,
                sessionId,
                topology,
                userMessage,
                List.of(),
                GuideRequestStatus.PREPARING,
                List.of(),
                ModelUsage.empty(),
                null,
                null,
                now,
                now,
                null,
                modelSelection,
                GuideRequestProgress.start(now));
    }
public boolean terminal() {
        return terminalAt != null;
    }
public String assistantText() {
        for (int index = timeline.size() - 1; index >= 0; index--) {
            {
final java.lang.Object $oaPattern0_value = timeline.get(index);
final boolean $oaPattern0_match = $oaPattern0_value instanceof GuideTimelineEntry.Assistant;
GuideTimelineEntry.Assistant $oaPattern0_bound = $oaPattern0_match ? (GuideTimelineEntry.Assistant) $oaPattern0_value : null;
if ($oaPattern0_match) {
                return $oaPattern0_bound.text();
            }
}
        }
        return "";
    }
public List<GuideToolActivity> tools() {
        return timeline.stream()
                .filter(GuideTimelineEntry.Tool.class::isInstance)
                .map(GuideTimelineEntry.Tool.class::cast)
                .map(GuideTimelineEntry.Tool::activity)
                .toList();
    }
public static GuideRequestProgress legacyProgress(
            GuideRequestStatus status,
            Long retryAfterMillis,
            Instant createdAt,
            Instant updatedAt) {
        GuideRequestPhase phase = switch (status) {
            case PREPARING -> GuideRequestPhase.PREPARING;
            case CONTEXT_LOADING -> GuideRequestPhase.CONTEXT_LOADING;
            case COMPACTING -> GuideRequestPhase.COMPACTING;
            case RATE_LIMITED -> GuideRequestPhase.ENDPOINT_WAIT;
            case MODEL_WAIT -> GuideRequestPhase.MODEL_WAIT;
            case TOOL_WAIT -> GuideRequestPhase.TOOL_WAIT;
            case COMPLETING, COMPLETED, FAILED, CANCELLED, INTERRUPTED ->
                    GuideRequestPhase.COMPLETING;
        };
        Instant monotonicUpdated = updatedAt.isBefore(createdAt) ? createdAt : updatedAt;
        Instant retryAt = retryAfterMillis == null
                ? null
                : monotonicUpdated.plusMillis(retryAfterMillis);
        return new GuideRequestProgress(
                phase,
                createdAt,
                monotonicUpdated,
                monotonicUpdated,
                0,
                retryAt,
                null);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideRequestSnapshot)) return false;
        GuideRequestSnapshot that = (GuideRequestSnapshot) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(topology, that.topology) && java.util.Objects.equals(userMessage, that.userMessage) && java.util.Objects.equals(timeline, that.timeline) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(sources, that.sources) && java.util.Objects.equals(usage, that.usage) && java.util.Objects.equals(retryAfterMillis, that.retryAfterMillis) && java.util.Objects.equals(failure, that.failure) && java.util.Objects.equals(createdAt, that.createdAt) && java.util.Objects.equals(updatedAt, that.updatedAt) && java.util.Objects.equals(terminalAt, that.terminalAt) && java.util.Objects.equals(modelSelection, that.modelSelection) && java.util.Objects.equals(progress, that.progress) && java.util.Objects.equals(usageProjection, that.usageProjection) && java.util.Objects.equals(usageOriginRequestId, that.usageOriginRequestId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(topology);
        hash = 31 * hash + java.util.Objects.hashCode(userMessage);
        hash = 31 * hash + java.util.Objects.hashCode(timeline);
        hash = 31 * hash + java.util.Objects.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        hash = 31 * hash + java.util.Objects.hashCode(usage);
        hash = 31 * hash + java.util.Objects.hashCode(retryAfterMillis);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        hash = 31 * hash + java.util.Objects.hashCode(createdAt);
        hash = 31 * hash + java.util.Objects.hashCode(updatedAt);
        hash = 31 * hash + java.util.Objects.hashCode(terminalAt);
        hash = 31 * hash + java.util.Objects.hashCode(modelSelection);
        hash = 31 * hash + java.util.Objects.hashCode(progress);
        hash = 31 * hash + java.util.Objects.hashCode(usageProjection);
        hash = 31 * hash + java.util.Objects.hashCode(usageOriginRequestId);
        return hash;
    }
    @Override public String toString() { return "GuideRequestSnapshot[requestId=" + requestId + ", sessionId=" + sessionId + ", topology=" + topology + ", userMessage=" + userMessage + ", timeline=" + timeline + ", status=" + status + ", sources=" + sources + ", usage=" + usage + ", retryAfterMillis=" + retryAfterMillis + ", failure=" + failure + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + ", terminalAt=" + terminalAt + ", modelSelection=" + modelSelection + ", progress=" + progress + ", usageProjection=" + usageProjection + ", usageOriginRequestId=" + usageOriginRequestId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideRequestSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideRequestSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideRequestSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "requestId", GuideRequestSnapshot::requestId), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "sessionId", GuideRequestSnapshot::sessionId), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "topology", GuideRequestSnapshot::topology), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "userMessage", GuideRequestSnapshot::userMessage), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "timeline", GuideRequestSnapshot::timeline), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "status", GuideRequestSnapshot::status), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "sources", GuideRequestSnapshot::sources), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "usage", GuideRequestSnapshot::usage), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "retryAfterMillis", GuideRequestSnapshot::retryAfterMillis), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "failure", GuideRequestSnapshot::failure), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "createdAt", GuideRequestSnapshot::createdAt), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "updatedAt", GuideRequestSnapshot::updatedAt), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "terminalAt", GuideRequestSnapshot::terminalAt), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "modelSelection", GuideRequestSnapshot::modelSelection), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "progress", GuideRequestSnapshot::progress), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "usageProjection", GuideRequestSnapshot::usageProjection), new dev.openallay.value.ValueSchema.Component<>(GuideRequestSnapshot.class, "usageOriginRequestId", GuideRequestSnapshot::usageOriginRequestId)), arguments -> new GuideRequestSnapshot((UUID) arguments[0], (String) arguments[1], (GuideTopology) arguments[2], (String) arguments[3], (List) arguments[4], (GuideRequestStatus) arguments[5], (List) arguments[6], (ModelUsage) arguments[7], (Long) arguments[8], (GuideFailure) arguments[9], (Instant) arguments[10], (Instant) arguments[11], (Instant) arguments[12], (GuideModelSelection) arguments[13], (GuideRequestProgress) arguments[14], (GuideUsageSnapshot) arguments[15], (UUID) arguments[16]));
        }
    }
}
