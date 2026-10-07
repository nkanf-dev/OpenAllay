package dev.openallay.guide;

import java.util.UUID;

/** Small immutable footer projection. Contains no prompt, transcript, credentials or reasoning. */
@dev.openallay.value.ValueType(GuideTelemetrySnapshot.ValueSchemaProvider.class)
public final class GuideTelemetrySnapshot {
    private final String sessionId;
    private final GuideModelSelection selection;
    private final UUID requestId;
    private final GuideContextEstimate context;
    private final GuideUsageSnapshot requestUsage;
    private final GuideUsageSnapshot sessionUsage;
    private final GuideUsageSnapshot inheritedUsage;
    public GuideTelemetrySnapshot(String sessionId, GuideModelSelection selection, UUID requestId, GuideContextEstimate context, GuideUsageSnapshot requestUsage, GuideUsageSnapshot sessionUsage, GuideUsageSnapshot inheritedUsage) {
        this.sessionId = sessionId;
        this.selection = selection;
        this.requestId = requestId;
        this.context = context;
        this.requestUsage = requestUsage;
        this.sessionUsage = sessionUsage;
        this.inheritedUsage = inheritedUsage;
    }
    public String sessionId() { return sessionId; }
    public GuideModelSelection selection() { return selection; }
    public UUID requestId() { return requestId; }
    public GuideContextEstimate context() { return context; }
    public GuideUsageSnapshot requestUsage() { return requestUsage; }
    public GuideUsageSnapshot sessionUsage() { return sessionUsage; }
    public GuideUsageSnapshot inheritedUsage() { return inheritedUsage; }
public static GuideTelemetrySnapshot unknown(String sessionId, GuideModelSelection selection) {
        return new GuideTelemetrySnapshot(sessionId, selection, null, null,
                GuideUsageSnapshot.unknown(), GuideUsageSnapshot.unknown(), GuideUsageSnapshot.empty());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideTelemetrySnapshot)) return false;
        GuideTelemetrySnapshot that = (GuideTelemetrySnapshot) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(selection, that.selection) && java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(context, that.context) && java.util.Objects.equals(requestUsage, that.requestUsage) && java.util.Objects.equals(sessionUsage, that.sessionUsage) && java.util.Objects.equals(inheritedUsage, that.inheritedUsage);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(selection);
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(context);
        hash = 31 * hash + java.util.Objects.hashCode(requestUsage);
        hash = 31 * hash + java.util.Objects.hashCode(sessionUsage);
        hash = 31 * hash + java.util.Objects.hashCode(inheritedUsage);
        return hash;
    }
    @Override public String toString() { return "GuideTelemetrySnapshot[sessionId=" + sessionId + ", selection=" + selection + ", requestId=" + requestId + ", context=" + context + ", requestUsage=" + requestUsage + ", sessionUsage=" + sessionUsage + ", inheritedUsage=" + inheritedUsage + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideTelemetrySnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideTelemetrySnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideTelemetrySnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideTelemetrySnapshot.class, "sessionId", GuideTelemetrySnapshot::sessionId), new dev.openallay.value.ValueSchema.Component<>(GuideTelemetrySnapshot.class, "selection", GuideTelemetrySnapshot::selection), new dev.openallay.value.ValueSchema.Component<>(GuideTelemetrySnapshot.class, "requestId", GuideTelemetrySnapshot::requestId), new dev.openallay.value.ValueSchema.Component<>(GuideTelemetrySnapshot.class, "context", GuideTelemetrySnapshot::context), new dev.openallay.value.ValueSchema.Component<>(GuideTelemetrySnapshot.class, "requestUsage", GuideTelemetrySnapshot::requestUsage), new dev.openallay.value.ValueSchema.Component<>(GuideTelemetrySnapshot.class, "sessionUsage", GuideTelemetrySnapshot::sessionUsage), new dev.openallay.value.ValueSchema.Component<>(GuideTelemetrySnapshot.class, "inheritedUsage", GuideTelemetrySnapshot::inheritedUsage)), arguments -> new GuideTelemetrySnapshot((String) arguments[0], (GuideModelSelection) arguments[1], (UUID) arguments[2], (GuideContextEstimate) arguments[3], (GuideUsageSnapshot) arguments[4], (GuideUsageSnapshot) arguments[5], (GuideUsageSnapshot) arguments[6]));
        }
    }
}
