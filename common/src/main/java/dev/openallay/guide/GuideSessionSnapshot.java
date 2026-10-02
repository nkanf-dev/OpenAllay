package dev.openallay.guide;

import dev.openallay.agent.context.ContextCheckpoint;
import java.util.List;

public record GuideSessionSnapshot(
        String sessionId,
        List<GuideMessage> messages,
        List<GuideRequestSnapshot> requests,
        List<ContextCheckpoint> checkpoints,
        GuideModelSelection modelSelection,
        GuideHistoryWindowSnapshot historyWindow,
        List<GuidePendingMessage> pendingMessages,
        java.util.UUID workingRequestId) {
    public GuideSessionSnapshot {
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid sessionId");
        }
        messages = List.copyOf(messages);
        requests = List.copyOf(requests);
        checkpoints = List.copyOf(checkpoints);
        pendingMessages = List.copyOf(pendingMessages);
        java.util.Objects.requireNonNull(modelSelection, "modelSelection");
        java.util.Objects.requireNonNull(historyWindow, "historyWindow");
    }

    public GuideSessionSnapshot(
            String sessionId,
            List<GuideMessage> messages,
            List<GuideRequestSnapshot> requests,
            List<ContextCheckpoint> checkpoints,
            GuideModelSelection modelSelection,
            GuideHistoryWindowSnapshot historyWindow) {
        this(sessionId, messages, requests, checkpoints, modelSelection, historyWindow, List.of(), null);
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
                sessionId, messages, requests, List.of(),
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
}
