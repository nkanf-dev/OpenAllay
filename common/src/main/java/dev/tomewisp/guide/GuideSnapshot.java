package dev.tomewisp.guide;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record GuideSnapshot(
        UUID actorId,
        String selectedSession,
        GuideModelMode modelMode,
        boolean clientModelAvailable,
        boolean serverModelAvailable,
        GuidePersistenceSnapshot persistence,
        List<GuideSessionSnapshot> sessions,
        Instant updatedAt) {
    public GuideSnapshot {
        java.util.Objects.requireNonNull(actorId, "actorId");
        if (selectedSession == null || selectedSession.isBlank()) {
            throw new IllegalArgumentException("selectedSession must not be blank");
        }
        java.util.Objects.requireNonNull(modelMode, "modelMode");
        java.util.Objects.requireNonNull(persistence, "persistence");
        sessions = sessions.stream()
                .sorted(Comparator.comparing(GuideSessionSnapshot::sessionId))
                .toList();
        java.util.Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public GuideSnapshot(
            UUID actorId,
            String selectedSession,
            GuideModelMode modelMode,
            boolean clientModelAvailable,
            boolean serverModelAvailable,
            List<GuideSessionSnapshot> sessions,
            Instant updatedAt) {
        this(
                actorId,
                selectedSession,
                modelMode,
                clientModelAvailable,
                serverModelAvailable,
                GuidePersistenceSnapshot.disabled(),
                sessions,
                updatedAt);
    }
}
