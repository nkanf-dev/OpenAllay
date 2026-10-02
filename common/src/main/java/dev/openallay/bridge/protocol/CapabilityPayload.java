package dev.openallay.bridge.protocol;

import java.util.List;

public record CapabilityPayload(
        List<RemoteToolCapability> remoteTools,
        boolean serverModel,
        int serverContextWindowTokens,
        int serverMaxOutputTokens,
        int serverPromptAndToolTokens,
        String serverCanonicalModelId,
        dev.openallay.model.image.ImageInputCapability serverImageInputCapability,
        String serverImageInputCapabilitySource) {
    public CapabilityPayload {
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
    }

    public CapabilityPayload(
            List<RemoteToolCapability> remoteTools, boolean serverModel,
            int serverContextWindowTokens, int serverMaxOutputTokens,
            int serverPromptAndToolTokens, String serverCanonicalModelId) {
        this(remoteTools, serverModel, serverContextWindowTokens, serverMaxOutputTokens,
                serverPromptAndToolTokens, serverCanonicalModelId,
                dev.openallay.model.image.ImageInputCapability.UNKNOWN, "unknown");
    }

    public record RemoteToolCapability(String id, String description, String inputSchemaJson) {
        public RemoteToolCapability {
            if (id == null || id.isBlank() || description == null || description.isBlank()) {
                throw new IllegalArgumentException("Remote tool identity and description are required");
            }
            if (inputSchemaJson == null || inputSchemaJson.isBlank()) {
                throw new IllegalArgumentException("Remote tool schema is required");
            }
        }
    }
}
