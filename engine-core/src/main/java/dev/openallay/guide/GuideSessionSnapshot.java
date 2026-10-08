package dev.openallay.guide;

import dev.openallay.agent.context.ContextCheckpoint;
import java.util.List;

@dev.openallay.value.ValueType(GuideSessionSnapshot.ValueSchemaProvider.class)
public final class GuideSessionSnapshot {
    private final String sessionId;
    private final List<GuideMessage> messages;
    private final List<GuideRequestSnapshot> requests;
    private final List<ContextCheckpoint> checkpoints;
    private final GuideModelSelection modelSelection;
    private final GuideHistoryWindowSnapshot historyWindow;
    private final List<GuidePendingMessage> pendingMessages;
    private final java.util.UUID workingRequestId;
    public GuideSessionSnapshot(String sessionId, List<GuideMessage> messages, List<GuideRequestSnapshot> requests, List<ContextCheckpoint> checkpoints, GuideModelSelection modelSelection, GuideHistoryWindowSnapshot historyWindow, List<GuidePendingMessage> pendingMessages, java.util.UUID workingRequestId) {

        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid sessionId");
        }
        messages = dev.openallay.util.Java8Collections.listCopyOf(messages);
        requests = dev.openallay.util.Java8Collections.listCopyOf(requests);
        checkpoints = dev.openallay.util.Java8Collections.listCopyOf(checkpoints);
        pendingMessages = dev.openallay.util.Java8Collections.listCopyOf(pendingMessages);
        java.util.Objects.requireNonNull(modelSelection, "modelSelection");
        java.util.Objects.requireNonNull(historyWindow, "historyWindow");

        this.sessionId = sessionId;
        this.messages = messages;
        this.requests = requests;
        this.checkpoints = checkpoints;
        this.modelSelection = modelSelection;
        this.historyWindow = historyWindow;
        this.pendingMessages = pendingMessages;
        this.workingRequestId = workingRequestId;
    }
    public String sessionId() { return sessionId; }
    public List<GuideMessage> messages() { return messages; }
    public List<GuideRequestSnapshot> requests() { return requests; }
    public List<ContextCheckpoint> checkpoints() { return checkpoints; }
    public GuideModelSelection modelSelection() { return modelSelection; }
    public GuideHistoryWindowSnapshot historyWindow() { return historyWindow; }
    public List<GuidePendingMessage> pendingMessages() { return pendingMessages; }
    public java.util.UUID workingRequestId() { return workingRequestId; }
public GuideSessionSnapshot(
            String sessionId,
            List<GuideMessage> messages,
            List<GuideRequestSnapshot> requests,
            List<ContextCheckpoint> checkpoints,
            GuideModelSelection modelSelection,
            GuideHistoryWindowSnapshot historyWindow) {
        this(sessionId, messages, requests, checkpoints, modelSelection, historyWindow, dev.openallay.util.Java8Collections.listOf(), null);
    }
public GuideSessionSnapshot(
            String sessionId,
            List<GuideMessage> messages,
            List<GuideRequestSnapshot> requests,
            List<ContextCheckpoint> checkpoints) {
        this(
                sessionId, messages, requests, checkpoints,
                GuideModelSelection.client("default"),
                GuideHistoryWindowSnapshot.disabled(requests.size()));
    }
public GuideSessionSnapshot(
            String sessionId,
            List<GuideMessage> messages,
            List<GuideRequestSnapshot> requests) {
        this(
                sessionId, messages, requests, dev.openallay.util.Java8Collections.listOf(),
                GuideModelSelection.client("default"),
                GuideHistoryWindowSnapshot.disabled(requests.size()));
    }
public GuideSessionSnapshot(
            String sessionId,
            List<GuideMessage> messages,
            List<GuideRequestSnapshot> requests,
            List<ContextCheckpoint> checkpoints,
            GuideModelSelection modelSelection) {
        this(
                sessionId, messages, requests, checkpoints, modelSelection,
                GuideHistoryWindowSnapshot.disabled(requests.size()));
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideSessionSnapshot)) return false;
        GuideSessionSnapshot that = (GuideSessionSnapshot) other;
        return java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(messages, that.messages) && java.util.Objects.equals(requests, that.requests) && java.util.Objects.equals(checkpoints, that.checkpoints) && java.util.Objects.equals(modelSelection, that.modelSelection) && java.util.Objects.equals(historyWindow, that.historyWindow) && java.util.Objects.equals(pendingMessages, that.pendingMessages) && java.util.Objects.equals(workingRequestId, that.workingRequestId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(messages);
        hash = 31 * hash + java.util.Objects.hashCode(requests);
        hash = 31 * hash + java.util.Objects.hashCode(checkpoints);
        hash = 31 * hash + java.util.Objects.hashCode(modelSelection);
        hash = 31 * hash + java.util.Objects.hashCode(historyWindow);
        hash = 31 * hash + java.util.Objects.hashCode(pendingMessages);
        hash = 31 * hash + java.util.Objects.hashCode(workingRequestId);
        return hash;
    }
    @Override public String toString() { return "GuideSessionSnapshot[sessionId=" + sessionId + ", messages=" + messages + ", requests=" + requests + ", checkpoints=" + checkpoints + ", modelSelection=" + modelSelection + ", historyWindow=" + historyWindow + ", pendingMessages=" + pendingMessages + ", workingRequestId=" + workingRequestId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideSessionSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideSessionSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideSessionSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideSessionSnapshot.class, "sessionId", GuideSessionSnapshot::sessionId), new dev.openallay.value.ValueSchema.Component<>(GuideSessionSnapshot.class, "messages", GuideSessionSnapshot::messages), new dev.openallay.value.ValueSchema.Component<>(GuideSessionSnapshot.class, "requests", GuideSessionSnapshot::requests), new dev.openallay.value.ValueSchema.Component<>(GuideSessionSnapshot.class, "checkpoints", GuideSessionSnapshot::checkpoints), new dev.openallay.value.ValueSchema.Component<>(GuideSessionSnapshot.class, "modelSelection", GuideSessionSnapshot::modelSelection), new dev.openallay.value.ValueSchema.Component<>(GuideSessionSnapshot.class, "historyWindow", GuideSessionSnapshot::historyWindow), new dev.openallay.value.ValueSchema.Component<>(GuideSessionSnapshot.class, "pendingMessages", GuideSessionSnapshot::pendingMessages), new dev.openallay.value.ValueSchema.Component<>(GuideSessionSnapshot.class, "workingRequestId", GuideSessionSnapshot::workingRequestId)), arguments -> new GuideSessionSnapshot((String) arguments[0], (List) arguments[1], (List) arguments[2], (List) arguments[3], (GuideModelSelection) arguments[4], (GuideHistoryWindowSnapshot) arguments[5], (List) arguments[6], (java.util.UUID) arguments[7]));
        }
    }
}
