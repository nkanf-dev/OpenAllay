package dev.openallay.model.metadata;

import dev.openallay.model.image.ImageInputCapability;
import java.time.Instant;
import java.util.Objects;

/** Credential-free capability metadata published by one trusted provider. */
@dev.openallay.value.ValueType(ModelMetadata.ValueSchemaProvider.class)
public final class ModelMetadata {
    private final String source;
    private final String providerModelId;
    private final String canonicalModelId;
    private final int contextWindowTokens;
    private final Integer maxOutputTokens;
    private final Instant capturedAt;
    private final ImageInputCapability imageInputCapability;
    public ModelMetadata(String source, String providerModelId, String canonicalModelId, int contextWindowTokens, Integer maxOutputTokens, Instant capturedAt, ImageInputCapability imageInputCapability) {

        if (source == null || dev.openallay.util.Java8Strings.isBlank(source)
                || providerModelId == null || dev.openallay.util.Java8Strings.isBlank(providerModelId)
                || canonicalModelId == null || dev.openallay.util.Java8Strings.isBlank(canonicalModelId)) {
            throw new IllegalArgumentException("model metadata identity must not be blank");
        }
        if (contextWindowTokens <= 0
                || (maxOutputTokens != null && maxOutputTokens <= 0)) {
            throw new IllegalArgumentException("model metadata limits must be positive");
        }
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(imageInputCapability, "imageInputCapability");

        this.source = source;
        this.providerModelId = providerModelId;
        this.canonicalModelId = canonicalModelId;
        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.capturedAt = capturedAt;
        this.imageInputCapability = imageInputCapability;
    }
    public String source() { return source; }
    public String providerModelId() { return providerModelId; }
    public String canonicalModelId() { return canonicalModelId; }
    public int contextWindowTokens() { return contextWindowTokens; }
    public Integer maxOutputTokens() { return maxOutputTokens; }
    public Instant capturedAt() { return capturedAt; }
    public ImageInputCapability imageInputCapability() { return imageInputCapability; }
public ModelMetadata(String source, String providerModelId, String canonicalModelId,
            int contextWindowTokens, Integer maxOutputTokens, Instant capturedAt) {
        this(source, providerModelId, canonicalModelId, contextWindowTokens, maxOutputTokens,
                capturedAt, ImageInputCapability.UNKNOWN);
    }
public Key key() {
        return new Key(source, providerModelId);
    }
@dev.openallay.value.ValueType(Key.ValueSchemaProvider.class)
public static final class Key {
    private final String source;
    private final String providerModelId;
    public Key(String source, String providerModelId) {

            if (source == null || dev.openallay.util.Java8Strings.isBlank(source)
                    || providerModelId == null || dev.openallay.util.Java8Strings.isBlank(providerModelId)) {
                throw new IllegalArgumentException("metadata cache key must not be blank");
            }

        this.source = source;
        this.providerModelId = providerModelId;
    }
    public String source() { return source; }
    public String providerModelId() { return providerModelId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Key)) return false;
        Key that = (Key) other;
        return java.util.Objects.equals(source, that.source) && java.util.Objects.equals(providerModelId, that.providerModelId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(providerModelId);
        return hash;
    }
    @Override public String toString() { return "Key[source=" + source + ", providerModelId=" + providerModelId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Key> schema() {
            return new dev.openallay.value.ValueSchema<>(Key.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Key>>asList(new dev.openallay.value.ValueSchema.Component<>(Key.class, "source", Key::source), new dev.openallay.value.ValueSchema.Component<>(Key.class, "providerModelId", Key::providerModelId)), arguments -> new Key((String) arguments[0], (String) arguments[1]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelMetadata)) return false;
        ModelMetadata that = (ModelMetadata) other;
        return java.util.Objects.equals(source, that.source) && java.util.Objects.equals(providerModelId, that.providerModelId) && java.util.Objects.equals(canonicalModelId, that.canonicalModelId) && contextWindowTokens == that.contextWindowTokens && java.util.Objects.equals(maxOutputTokens, that.maxOutputTokens) && java.util.Objects.equals(capturedAt, that.capturedAt) && java.util.Objects.equals(imageInputCapability, that.imageInputCapability);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(providerModelId);
        hash = 31 * hash + java.util.Objects.hashCode(canonicalModelId);
        hash = 31 * hash + Integer.hashCode(contextWindowTokens);
        hash = 31 * hash + java.util.Objects.hashCode(maxOutputTokens);
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        hash = 31 * hash + java.util.Objects.hashCode(imageInputCapability);
        return hash;
    }
    @Override public String toString() { return "ModelMetadata[source=" + source + ", providerModelId=" + providerModelId + ", canonicalModelId=" + canonicalModelId + ", contextWindowTokens=" + contextWindowTokens + ", maxOutputTokens=" + maxOutputTokens + ", capturedAt=" + capturedAt + ", imageInputCapability=" + imageInputCapability + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelMetadata> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelMetadata.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelMetadata>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelMetadata.class, "source", ModelMetadata::source), new dev.openallay.value.ValueSchema.Component<>(ModelMetadata.class, "providerModelId", ModelMetadata::providerModelId), new dev.openallay.value.ValueSchema.Component<>(ModelMetadata.class, "canonicalModelId", ModelMetadata::canonicalModelId), new dev.openallay.value.ValueSchema.Component<>(ModelMetadata.class, "contextWindowTokens", ModelMetadata::contextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(ModelMetadata.class, "maxOutputTokens", ModelMetadata::maxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(ModelMetadata.class, "capturedAt", ModelMetadata::capturedAt), new dev.openallay.value.ValueSchema.Component<>(ModelMetadata.class, "imageInputCapability", ModelMetadata::imageInputCapability)), arguments -> new ModelMetadata((String) arguments[0], (String) arguments[1], (String) arguments[2], (Integer) arguments[3], (Integer) arguments[4], (Instant) arguments[5], (ImageInputCapability) arguments[6]));
        }
    }
}
