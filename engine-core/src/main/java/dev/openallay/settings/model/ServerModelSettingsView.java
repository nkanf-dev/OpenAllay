package dev.openallay.settings.model;

import dev.openallay.bridge.protocol.CapabilityPayload;
import java.util.Objects;

/** Credential-free, connection-scoped projection of the model hosted by the current server. */
@dev.openallay.value.ValueType(ServerModelSettingsView.ValueSchemaProvider.class)
public final class ServerModelSettingsView {
    private final boolean available;
    private final String canonicalModelId;
    private final int contextWindowTokens;
    private final int maxOutputTokens;
    private final int promptAndToolTokens;
    private final dev.openallay.model.metadata.ModelImageCapabilityResolution imageCapability;
    public ServerModelSettingsView(boolean available, String canonicalModelId, int contextWindowTokens, int maxOutputTokens, int promptAndToolTokens, dev.openallay.model.metadata.ModelImageCapabilityResolution imageCapability) {

        canonicalModelId = Objects.requireNonNull(canonicalModelId, "canonicalModelId");
        Objects.requireNonNull(imageCapability, "imageCapability");
        if (!available && imageCapability.capability()
                != dev.openallay.model.image.ImageInputCapability.UNKNOWN) {
            throw new IllegalArgumentException("Unavailable server model cannot retain image capability");
        }
        if (available) {
            if (canonicalModelId.isBlank()) {
                throw new IllegalArgumentException("available server model requires an identity");
            }
            if (contextWindowTokens <= 0
                    || maxOutputTokens <= 0
                    || promptAndToolTokens < 0) {
                throw new IllegalArgumentException("available server model requires a valid budget");
            }
        } else if (!canonicalModelId.isEmpty()
                || contextWindowTokens != 0
                || maxOutputTokens != 0
                || promptAndToolTokens != 0) {
            throw new IllegalArgumentException(
                    "unavailable server model cannot retain connection state");
        }

        this.available = available;
        this.canonicalModelId = canonicalModelId;
        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.promptAndToolTokens = promptAndToolTokens;
        this.imageCapability = imageCapability;
    }
    public boolean available() { return available; }
    public String canonicalModelId() { return canonicalModelId; }
    public int contextWindowTokens() { return contextWindowTokens; }
    public int maxOutputTokens() { return maxOutputTokens; }
    public int promptAndToolTokens() { return promptAndToolTokens; }
    public dev.openallay.model.metadata.ModelImageCapabilityResolution imageCapability() { return imageCapability; }
public ServerModelSettingsView(
            boolean available, String canonicalModelId, int contextWindowTokens,
            int maxOutputTokens, int promptAndToolTokens) {
        this(available, canonicalModelId, contextWindowTokens, maxOutputTokens, promptAndToolTokens,
                dev.openallay.model.metadata.ModelImageCapabilityResolution.unknown());
    }
public static ServerModelSettingsView unavailable() {
        return new ServerModelSettingsView(false, "", 0, 0, 0);
    }
public static ServerModelSettingsView from(CapabilityPayload capability) {
        Objects.requireNonNull(capability, "capability");
        return capability.serverModel()
                ? new ServerModelSettingsView(
                        true,
                        capability.serverCanonicalModelId(),
                        capability.serverContextWindowTokens(),
                        capability.serverMaxOutputTokens(),
                        capability.serverPromptAndToolTokens(),
                        new dev.openallay.model.metadata.ModelImageCapabilityResolution(
                                capability.serverImageInputCapability(),
                                dev.openallay.model.metadata.ModelImageCapabilityResolution.Origin.TRUSTED,
                                capability.serverImageInputCapabilitySource(), null))
                : unavailable();
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ServerModelSettingsView)) return false;
        ServerModelSettingsView that = (ServerModelSettingsView) other;
        return available == that.available && java.util.Objects.equals(canonicalModelId, that.canonicalModelId) && contextWindowTokens == that.contextWindowTokens && maxOutputTokens == that.maxOutputTokens && promptAndToolTokens == that.promptAndToolTokens && java.util.Objects.equals(imageCapability, that.imageCapability);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + java.util.Objects.hashCode(canonicalModelId);
        hash = 31 * hash + Integer.hashCode(contextWindowTokens);
        hash = 31 * hash + Integer.hashCode(maxOutputTokens);
        hash = 31 * hash + Integer.hashCode(promptAndToolTokens);
        hash = 31 * hash + java.util.Objects.hashCode(imageCapability);
        return hash;
    }
    @Override public String toString() { return "ServerModelSettingsView[available=" + available + ", canonicalModelId=" + canonicalModelId + ", contextWindowTokens=" + contextWindowTokens + ", maxOutputTokens=" + maxOutputTokens + ", promptAndToolTokens=" + promptAndToolTokens + ", imageCapability=" + imageCapability + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ServerModelSettingsView> schema() {
            return new dev.openallay.value.ValueSchema<>(ServerModelSettingsView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ServerModelSettingsView>>asList(new dev.openallay.value.ValueSchema.Component<>(ServerModelSettingsView.class, "available", ServerModelSettingsView::available), new dev.openallay.value.ValueSchema.Component<>(ServerModelSettingsView.class, "canonicalModelId", ServerModelSettingsView::canonicalModelId), new dev.openallay.value.ValueSchema.Component<>(ServerModelSettingsView.class, "contextWindowTokens", ServerModelSettingsView::contextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(ServerModelSettingsView.class, "maxOutputTokens", ServerModelSettingsView::maxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(ServerModelSettingsView.class, "promptAndToolTokens", ServerModelSettingsView::promptAndToolTokens), new dev.openallay.value.ValueSchema.Component<>(ServerModelSettingsView.class, "imageCapability", ServerModelSettingsView::imageCapability)), arguments -> new ServerModelSettingsView((Boolean) arguments[0], (String) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (Integer) arguments[4], (dev.openallay.model.metadata.ModelImageCapabilityResolution) arguments[5]));
        }
    }
}
