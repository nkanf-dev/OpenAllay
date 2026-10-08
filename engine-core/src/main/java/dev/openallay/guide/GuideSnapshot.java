package dev.openallay.guide;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@dev.openallay.value.ValueType(GuideSnapshot.ValueSchemaProvider.class)
public final class GuideSnapshot {
    private final UUID actorId;
    private final String selectedSession;
    private final GuideModelMode modelMode;
    private final boolean clientModelAvailable;
    private final boolean serverModelAvailable;
    private final GuidePersistenceSnapshot persistence;
    private final List<GuideSessionSnapshot> sessions;
    private final Instant updatedAt;
    private final GuideModelSelection modelSelection;
    private final List<GuideClientModelProfile> clientProfiles;
    private final Optional<GuideContextSpec> serverModel;
    private final dev.openallay.model.image.ImageInputCapability serverImageInputCapability;
    private final String serverImageInputSource;
    public GuideSnapshot(UUID actorId, String selectedSession, GuideModelMode modelMode, boolean clientModelAvailable, boolean serverModelAvailable, GuidePersistenceSnapshot persistence, List<GuideSessionSnapshot> sessions, Instant updatedAt, GuideModelSelection modelSelection, List<GuideClientModelProfile> clientProfiles, Optional<GuideContextSpec> serverModel, dev.openallay.model.image.ImageInputCapability serverImageInputCapability, String serverImageInputSource) {

        java.util.Objects.requireNonNull(serverImageInputCapability, "serverImageInputCapability");
        java.util.Objects.requireNonNull(actorId, "actorId");
        if (selectedSession == null || dev.openallay.util.Java8Strings.isBlank(selectedSession)) {
            throw new IllegalArgumentException("selectedSession must not be blank");
        }
        java.util.Objects.requireNonNull(modelMode, "modelMode");
        java.util.Objects.requireNonNull(persistence, "persistence");
        sessions = dev.openallay.util.Java8Collections.toList(sessions.stream()
                .sorted(Comparator.comparing(GuideSessionSnapshot::sessionId)));
        java.util.Objects.requireNonNull(updatedAt, "updatedAt");
        java.util.Objects.requireNonNull(modelSelection, "modelSelection");
        clientProfiles = dev.openallay.util.Java8Collections.listCopyOf(clientProfiles);
        serverModel = java.util.Objects.requireNonNull(serverModel, "serverModel");
        if (modelMode != modelSelection.modelMode()) {
            throw new IllegalArgumentException("modelMode must match the selected session model");
        }

        this.actorId = actorId;
        this.selectedSession = selectedSession;
        this.modelMode = modelMode;
        this.clientModelAvailable = clientModelAvailable;
        this.serverModelAvailable = serverModelAvailable;
        this.persistence = persistence;
        this.sessions = sessions;
        this.updatedAt = updatedAt;
        this.modelSelection = modelSelection;
        this.clientProfiles = clientProfiles;
        this.serverModel = serverModel;
        this.serverImageInputCapability = serverImageInputCapability;
        this.serverImageInputSource = serverImageInputSource;
    }
    public UUID actorId() { return actorId; }
    public String selectedSession() { return selectedSession; }
    public GuideModelMode modelMode() { return modelMode; }
    public boolean clientModelAvailable() { return clientModelAvailable; }
    public boolean serverModelAvailable() { return serverModelAvailable; }
    public GuidePersistenceSnapshot persistence() { return persistence; }
    public List<GuideSessionSnapshot> sessions() { return sessions; }
    public Instant updatedAt() { return updatedAt; }
    public GuideModelSelection modelSelection() { return modelSelection; }
    public List<GuideClientModelProfile> clientProfiles() { return clientProfiles; }
    public Optional<GuideContextSpec> serverModel() { return serverModel; }
    public dev.openallay.model.image.ImageInputCapability serverImageInputCapability() { return serverImageInputCapability; }
    public String serverImageInputSource() { return serverImageInputSource; }
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
                dev.openallay.util.Java8Collections.listOf(),
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
                dev.openallay.util.Java8Collections.listOf());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideSnapshot)) return false;
        GuideSnapshot that = (GuideSnapshot) other;
        return java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(selectedSession, that.selectedSession) && java.util.Objects.equals(modelMode, that.modelMode) && clientModelAvailable == that.clientModelAvailable && serverModelAvailable == that.serverModelAvailable && java.util.Objects.equals(persistence, that.persistence) && java.util.Objects.equals(sessions, that.sessions) && java.util.Objects.equals(updatedAt, that.updatedAt) && java.util.Objects.equals(modelSelection, that.modelSelection) && java.util.Objects.equals(clientProfiles, that.clientProfiles) && java.util.Objects.equals(serverModel, that.serverModel) && java.util.Objects.equals(serverImageInputCapability, that.serverImageInputCapability) && java.util.Objects.equals(serverImageInputSource, that.serverImageInputSource);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(selectedSession);
        hash = 31 * hash + java.util.Objects.hashCode(modelMode);
        hash = 31 * hash + Boolean.hashCode(clientModelAvailable);
        hash = 31 * hash + Boolean.hashCode(serverModelAvailable);
        hash = 31 * hash + java.util.Objects.hashCode(persistence);
        hash = 31 * hash + java.util.Objects.hashCode(sessions);
        hash = 31 * hash + java.util.Objects.hashCode(updatedAt);
        hash = 31 * hash + java.util.Objects.hashCode(modelSelection);
        hash = 31 * hash + java.util.Objects.hashCode(clientProfiles);
        hash = 31 * hash + java.util.Objects.hashCode(serverModel);
        hash = 31 * hash + java.util.Objects.hashCode(serverImageInputCapability);
        hash = 31 * hash + java.util.Objects.hashCode(serverImageInputSource);
        return hash;
    }
    @Override public String toString() { return "GuideSnapshot[actorId=" + actorId + ", selectedSession=" + selectedSession + ", modelMode=" + modelMode + ", clientModelAvailable=" + clientModelAvailable + ", serverModelAvailable=" + serverModelAvailable + ", persistence=" + persistence + ", sessions=" + sessions + ", updatedAt=" + updatedAt + ", modelSelection=" + modelSelection + ", clientProfiles=" + clientProfiles + ", serverModel=" + serverModel + ", serverImageInputCapability=" + serverImageInputCapability + ", serverImageInputSource=" + serverImageInputSource + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideSnapshot.class, "actorId", GuideSnapshot::actorId), new dev.openallay.value.ValueSchema.Component<>(GuideSnapshot.class, "selectedSession", GuideSnapshot::selectedSession), new dev.openallay.value.ValueSchema.Component<>(GuideSnapshot.class, "modelMode", GuideSnapshot::modelMode), new dev.openallay.value.ValueSchema.Component<>(GuideSnapshot.class, "clientModelAvailable", GuideSnapshot::clientModelAvailable), new dev.openallay.value.ValueSchema.Component<>(GuideSnapshot.class, "serverModelAvailable", GuideSnapshot::serverModelAvailable), new dev.openallay.value.ValueSchema.Component<>(GuideSnapshot.class, "persistence", GuideSnapshot::persistence), new dev.openallay.value.ValueSchema.Component<>(GuideSnapshot.class, "sessions", GuideSnapshot::sessions), new dev.openallay.value.ValueSchema.Component<>(GuideSnapshot.class, "updatedAt", GuideSnapshot::updatedAt), new dev.openallay.value.ValueSchema.Component<>(GuideSnapshot.class, "modelSelection", GuideSnapshot::modelSelection), new dev.openallay.value.ValueSchema.Component<>(GuideSnapshot.class, "clientProfiles", GuideSnapshot::clientProfiles), new dev.openallay.value.ValueSchema.Component<>(GuideSnapshot.class, "serverModel", GuideSnapshot::serverModel), new dev.openallay.value.ValueSchema.Component<>(GuideSnapshot.class, "serverImageInputCapability", GuideSnapshot::serverImageInputCapability), new dev.openallay.value.ValueSchema.Component<>(GuideSnapshot.class, "serverImageInputSource", GuideSnapshot::serverImageInputSource)), arguments -> new GuideSnapshot((UUID) arguments[0], (String) arguments[1], (GuideModelMode) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4], (GuidePersistenceSnapshot) arguments[5], (List) arguments[6], (Instant) arguments[7], (GuideModelSelection) arguments[8], (List) arguments[9], (Optional) arguments[10], (dev.openallay.model.image.ImageInputCapability) arguments[11], (String) arguments[12]));
        }
    }
}
