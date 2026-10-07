package dev.openallay.bridge.protocol;

import java.util.List;

@dev.openallay.value.ValueType(CapabilityPayload.ValueSchemaProvider.class)
public final class CapabilityPayload {
    private final List<RemoteToolCapability> remoteTools;
    private final boolean serverModel;
    private final int serverContextWindowTokens;
    private final int serverMaxOutputTokens;
    private final int serverPromptAndToolTokens;
    private final String serverCanonicalModelId;
    private final dev.openallay.model.image.ImageInputCapability serverImageInputCapability;
    private final String serverImageInputCapabilitySource;
    public CapabilityPayload(List<RemoteToolCapability> remoteTools, boolean serverModel, int serverContextWindowTokens, int serverMaxOutputTokens, int serverPromptAndToolTokens, String serverCanonicalModelId, dev.openallay.model.image.ImageInputCapability serverImageInputCapability, String serverImageInputCapabilitySource) {

        remoteTools = List.copyOf(remoteTools);
        java.util.Objects.requireNonNull(serverImageInputCapability, "serverImageInputCapability");
        if (serverImageInputCapabilitySource == null || serverImageInputCapabilitySource.isBlank()) {
            throw new IllegalArgumentException("Server image capability source is required");
        }
        if (!serverModel && serverImageInputCapability != dev.openallay.model.image.ImageInputCapability.UNKNOWN) {
            throw new IllegalArgumentException("Unavailable server model cannot advertise image support");
        }
        if (serverModel) {
            if (serverCanonicalModelId == null || serverCanonicalModelId.isBlank()) {
                throw new IllegalArgumentException("Server model context capability is required");
            }
            dev.openallay.agent.context.ContextBudget budget =
                    new dev.openallay.agent.context.ContextBudget(
                            serverContextWindowTokens, serverMaxOutputTokens);
            if (serverPromptAndToolTokens < 0
                    || serverPromptAndToolTokens >= budget.inputTokens()) {
                throw new IllegalArgumentException("Invalid server model prompt/tool reservation");
            }
        } else if (serverContextWindowTokens != 0 || serverMaxOutputTokens != 0
                || serverPromptAndToolTokens != 0
                || serverCanonicalModelId == null || !serverCanonicalModelId.isEmpty()) {
            throw new IllegalArgumentException("Unavailable server model cannot advertise a budget");
        }

        this.remoteTools = remoteTools;
        this.serverModel = serverModel;
        this.serverContextWindowTokens = serverContextWindowTokens;
        this.serverMaxOutputTokens = serverMaxOutputTokens;
        this.serverPromptAndToolTokens = serverPromptAndToolTokens;
        this.serverCanonicalModelId = serverCanonicalModelId;
        this.serverImageInputCapability = serverImageInputCapability;
        this.serverImageInputCapabilitySource = serverImageInputCapabilitySource;
    }
    public List<RemoteToolCapability> remoteTools() { return remoteTools; }
    public boolean serverModel() { return serverModel; }
    public int serverContextWindowTokens() { return serverContextWindowTokens; }
    public int serverMaxOutputTokens() { return serverMaxOutputTokens; }
    public int serverPromptAndToolTokens() { return serverPromptAndToolTokens; }
    public String serverCanonicalModelId() { return serverCanonicalModelId; }
    public dev.openallay.model.image.ImageInputCapability serverImageInputCapability() { return serverImageInputCapability; }
    public String serverImageInputCapabilitySource() { return serverImageInputCapabilitySource; }
public CapabilityPayload(
            List<RemoteToolCapability> remoteTools, boolean serverModel,
            int serverContextWindowTokens, int serverMaxOutputTokens,
            int serverPromptAndToolTokens, String serverCanonicalModelId) {
        this(remoteTools, serverModel, serverContextWindowTokens, serverMaxOutputTokens,
                serverPromptAndToolTokens, serverCanonicalModelId,
                dev.openallay.model.image.ImageInputCapability.UNKNOWN, "unknown");
    }
@dev.openallay.value.ValueType(RemoteToolCapability.ValueSchemaProvider.class)
public static final class RemoteToolCapability {
    private final String id;
    private final String description;
    private final String inputSchemaJson;
    public RemoteToolCapability(String id, String description, String inputSchemaJson) {

            if (id == null || id.isBlank() || description == null || description.isBlank()) {
                throw new IllegalArgumentException("Remote tool identity and description are required");
            }
            if (inputSchemaJson == null || inputSchemaJson.isBlank()) {
                throw new IllegalArgumentException("Remote tool schema is required");
            }

        this.id = id;
        this.description = description;
        this.inputSchemaJson = inputSchemaJson;
    }
    public String id() { return id; }
    public String description() { return description; }
    public String inputSchemaJson() { return inputSchemaJson; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RemoteToolCapability)) return false;
        RemoteToolCapability that = (RemoteToolCapability) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(inputSchemaJson, that.inputSchemaJson);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(inputSchemaJson);
        return hash;
    }
    @Override public String toString() { return "RemoteToolCapability[id=" + id + ", description=" + description + ", inputSchemaJson=" + inputSchemaJson + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RemoteToolCapability> schema() {
            return new dev.openallay.value.ValueSchema<>(RemoteToolCapability.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RemoteToolCapability>>asList(new dev.openallay.value.ValueSchema.Component<>(RemoteToolCapability.class, "id", RemoteToolCapability::id), new dev.openallay.value.ValueSchema.Component<>(RemoteToolCapability.class, "description", RemoteToolCapability::description), new dev.openallay.value.ValueSchema.Component<>(RemoteToolCapability.class, "inputSchemaJson", RemoteToolCapability::inputSchemaJson)), arguments -> new RemoteToolCapability((String) arguments[0], (String) arguments[1], (String) arguments[2]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CapabilityPayload)) return false;
        CapabilityPayload that = (CapabilityPayload) other;
        return java.util.Objects.equals(remoteTools, that.remoteTools) && serverModel == that.serverModel && serverContextWindowTokens == that.serverContextWindowTokens && serverMaxOutputTokens == that.serverMaxOutputTokens && serverPromptAndToolTokens == that.serverPromptAndToolTokens && java.util.Objects.equals(serverCanonicalModelId, that.serverCanonicalModelId) && java.util.Objects.equals(serverImageInputCapability, that.serverImageInputCapability) && java.util.Objects.equals(serverImageInputCapabilitySource, that.serverImageInputCapabilitySource);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(remoteTools);
        hash = 31 * hash + Boolean.hashCode(serverModel);
        hash = 31 * hash + Integer.hashCode(serverContextWindowTokens);
        hash = 31 * hash + Integer.hashCode(serverMaxOutputTokens);
        hash = 31 * hash + Integer.hashCode(serverPromptAndToolTokens);
        hash = 31 * hash + java.util.Objects.hashCode(serverCanonicalModelId);
        hash = 31 * hash + java.util.Objects.hashCode(serverImageInputCapability);
        hash = 31 * hash + java.util.Objects.hashCode(serverImageInputCapabilitySource);
        return hash;
    }
    @Override public String toString() { return "CapabilityPayload[remoteTools=" + remoteTools + ", serverModel=" + serverModel + ", serverContextWindowTokens=" + serverContextWindowTokens + ", serverMaxOutputTokens=" + serverMaxOutputTokens + ", serverPromptAndToolTokens=" + serverPromptAndToolTokens + ", serverCanonicalModelId=" + serverCanonicalModelId + ", serverImageInputCapability=" + serverImageInputCapability + ", serverImageInputCapabilitySource=" + serverImageInputCapabilitySource + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CapabilityPayload> schema() {
            return new dev.openallay.value.ValueSchema<>(CapabilityPayload.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CapabilityPayload>>asList(new dev.openallay.value.ValueSchema.Component<>(CapabilityPayload.class, "remoteTools", CapabilityPayload::remoteTools), new dev.openallay.value.ValueSchema.Component<>(CapabilityPayload.class, "serverModel", CapabilityPayload::serverModel), new dev.openallay.value.ValueSchema.Component<>(CapabilityPayload.class, "serverContextWindowTokens", CapabilityPayload::serverContextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(CapabilityPayload.class, "serverMaxOutputTokens", CapabilityPayload::serverMaxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(CapabilityPayload.class, "serverPromptAndToolTokens", CapabilityPayload::serverPromptAndToolTokens), new dev.openallay.value.ValueSchema.Component<>(CapabilityPayload.class, "serverCanonicalModelId", CapabilityPayload::serverCanonicalModelId), new dev.openallay.value.ValueSchema.Component<>(CapabilityPayload.class, "serverImageInputCapability", CapabilityPayload::serverImageInputCapability), new dev.openallay.value.ValueSchema.Component<>(CapabilityPayload.class, "serverImageInputCapabilitySource", CapabilityPayload::serverImageInputCapabilitySource)), arguments -> new CapabilityPayload((List) arguments[0], (Boolean) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (Integer) arguments[4], (String) arguments[5], (dev.openallay.model.image.ImageInputCapability) arguments[6], (String) arguments[7]));
        }
    }
}
