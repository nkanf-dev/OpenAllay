package dev.openallay.guide.semantic;

import com.google.gson.JsonObject;

/** Strict outer protocol before dispatch to a registered type decoder. */
public record RichComponentEnvelope(
        String type,
        JsonObject properties,
        String fallbackText,
        String narration) {
    public RichComponentEnvelope {
        if (type == null || !type.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("rich component type is invalid");
        }
        properties = java.util.Objects.requireNonNull(properties, "properties").deepCopy();
        if (fallbackText == null || fallbackText.isBlank()
                || narration == null || narration.isBlank()) {
            throw new IllegalArgumentException("rich component fallback and narration are required");
        }
    }

    @Override
    public JsonObject properties() {
        return properties.deepCopy();
    }
}
