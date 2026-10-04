package dev.openallay.guide;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public record GuideSnapshot(
        UUID actorId,
        String selectedSession,
        GuideModelMode modelMode,
        boolean clientModelAvailable,
        boolean serverModelAvailable,
        GuidePersistenceSnapshot persistence,
        List<GuideSessionSnapshot> sessions,
        Instant updatedAt,
        GuideModelSelection modelSelection,
        List<GuideClientModelProfile> clientProfiles,
        Optional<GuideContextSpec> serverModel,
        dev.openallay.model.image.ImageInputCapability serverImageInputCapability,
        String serverImageInputSource) {
    public GuideSnapshot(
            UUID actorId, String selectedSession, GuideModelMode modelMode,
            boolean clientModelAvailable, boolean serverModelAvailable,
            GuidePersistenceSnapshot persistence, List<GuideSessionSnapshot> sessions,
            Instant updatedAt, GuideModelSelection modelSelection,
            List<GuideClientModelProfile> clientProfiles, Optional<GuideContextSpec> serverModel) {
        this(actorId, selectedSession, modelMode, clientModelAvailable, serverModelAvailable,
                persistence, sessions, updatedAt, modelSelection, clientProfiles, serverModel,
                dev.openallay.model.image.ImageInputCapability.UNKNOWN, null);
    }

    public dev.openallay.model.image.ImageInputCapability imageInputCapability(
            GuideModelSelection selection) {
        if (selection.kind() == GuideModelSelection.Kind.SERVER) return serverImageInputCapability;
        return clientProfiles.stream().filter(profile -> profile.id().equals(selection.profileId()))
                .map(GuideClientModelProfile::imageInputCapability).findFirst()
                .orElse(dev.openallay.model.image.ImageInputCapability.UNKNOWN);
    }

    public dev.openallay.model.image.ImageInputCapability selectedImageInputCapability() {
        return imageInputCapability(modelSelection);
    }

    public GuideSnapshot {
        java.util.Objects.requireNonNull(serverImageInputCapability, "serverImageInputCapability");
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
        java.util.Objects.requireNonNull(modelSelection, "modelSelection");
        clientProfiles = List.copyOf(clientProfiles);
        serverModel = java.util.Objects.requireNonNull(serverModel, "serverModel");
        if (modelMode != modelSelection.modelMode()) {
            throw new IllegalArgumentException("modelMode must match the selected session model");
        }
    }

    public GuideSnapshot(
            UUID actorId,
            String selectedSession,
            GuideModelMode modelMode,
            boolean clientModelAvailable,
            boolean serverModelAvailable,
            GuidePersistenceSnapshot persistence,
            List<GuideSessionSnapshot> sessions,
            Instant updatedAt,
            GuideModelSelection modelSelection,
            List<GuideClientModelProfile> clientProfiles) {
        this(
                actorId,
                selectedSession,
                modelMode,
                clientModelAvailable,
                serverModelAvailable,
                persistence,
                sessions,
                updatedAt,
                modelSelection,
                clientProfiles,
                Optional.empty());
    }

    public GuideSnapshot(
            UUID actorId,
            String selectedSession,
            GuideModelMode modelMode,
            boolean clientModelAvailable,
            boolean serverModelAvailable,
            GuidePersistenceSnapshot persistence,
            List<GuideSessionSnapshot> sessions,
            Instant updatedAt) {
        this(
                actorId,
                selectedSession,
                modelMode,
                clientModelAvailable,
                serverModelAvailable,
                persistence,
                sessions,
                updatedAt,
                modelMode == GuideModelMode.SERVER
                        ? GuideModelSelection.server()
                        : GuideModelSelection.client("default"),
                List.of(),
                Optional.empty());
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
                updatedAt,
                modelMode == GuideModelMode.SERVER
                        ? GuideModelSelection.server()
                        : GuideModelSelection.client("default"),
                List.of());
    }
}
