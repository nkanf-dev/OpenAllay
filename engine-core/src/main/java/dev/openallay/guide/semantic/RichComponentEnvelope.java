package dev.openallay.guide.semantic;

import com.google.gson.JsonObject;

/** Strict outer protocol before dispatch to a registered type decoder. */
@dev.openallay.value.ValueType(RichComponentEnvelope.ValueSchemaProvider.class)
public final class RichComponentEnvelope {
    private final String type;
    private final JsonObject properties;
    private final String fallbackText;
    private final String narration;
    public RichComponentEnvelope(String type, JsonObject properties, String fallbackText, String narration) {

        if (type == null || !type.matches("[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("rich component type is invalid");
        }
        properties = dev.openallay.json.JsonTrees.copy(java.util.Objects.requireNonNull(properties, "properties"));
        if (fallbackText == null || dev.openallay.util.Java8Strings.isBlank(fallbackText)
                || narration == null || dev.openallay.util.Java8Strings.isBlank(narration)) {
            throw new IllegalArgumentException("rich component fallback and narration are required");
        }

        this.type = type;
        this.properties = properties;
        this.fallbackText = fallbackText;
        this.narration = narration;
    }
    public String type() { return type; }
    public String fallbackText() { return fallbackText; }
    public String narration() { return narration; }

    public JsonObject properties() {
        return dev.openallay.json.JsonTrees.copy(properties);
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RichComponentEnvelope)) return false;
        RichComponentEnvelope that = (RichComponentEnvelope) other;
        return java.util.Objects.equals(type, that.type) && java.util.Objects.equals(properties, that.properties) && java.util.Objects.equals(fallbackText, that.fallbackText) && java.util.Objects.equals(narration, that.narration);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + java.util.Objects.hashCode(properties);
        hash = 31 * hash + java.util.Objects.hashCode(fallbackText);
        hash = 31 * hash + java.util.Objects.hashCode(narration);
        return hash;
    }
    @Override public String toString() { return "RichComponentEnvelope[type=" + type + ", properties=" + properties + ", fallbackText=" + fallbackText + ", narration=" + narration + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RichComponentEnvelope> schema() {
            return new dev.openallay.value.ValueSchema<>(RichComponentEnvelope.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RichComponentEnvelope>>asList(new dev.openallay.value.ValueSchema.Component<>(RichComponentEnvelope.class, "type", RichComponentEnvelope::type), new dev.openallay.value.ValueSchema.Component<>(RichComponentEnvelope.class, "properties", RichComponentEnvelope::properties), new dev.openallay.value.ValueSchema.Component<>(RichComponentEnvelope.class, "fallbackText", RichComponentEnvelope::fallbackText), new dev.openallay.value.ValueSchema.Component<>(RichComponentEnvelope.class, "narration", RichComponentEnvelope::narration)), arguments -> new RichComponentEnvelope((String) arguments[0], (JsonObject) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}
