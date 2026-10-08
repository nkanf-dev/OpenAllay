package dev.openallay.model.metadata;

import dev.openallay.guide.GuideFailure;

/** Effective limits after explicit configuration is applied over one discovery result. */
@dev.openallay.value.ValueType(ModelMetadataResolution.ValueSchemaProvider.class)
public final class ModelMetadataResolution {
    private final Integer contextWindowTokens;
    private final Integer maxOutputTokens;
    private final ModelMetadata metadata;
    private final GuideFailure failure;
    public ModelMetadataResolution(Integer contextWindowTokens, Integer maxOutputTokens, ModelMetadata metadata, GuideFailure failure) {

        if ((metadata == null) == (failure == null)) {
            throw new IllegalArgumentException(
                    "metadata resolution must contain exactly one metadata value or failure");
        }
        if (contextWindowTokens != null && contextWindowTokens <= 0) {
            throw new IllegalArgumentException("context window must be positive");
        }
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("output limit must be positive");
        }
        if (failure != null && (contextWindowTokens != null || maxOutputTokens != null)) {
            throw new IllegalArgumentException("failed metadata resolution cannot supply limits");
        }

        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.metadata = metadata;
        this.failure = failure;
    }
    public Integer contextWindowTokens() { return contextWindowTokens; }
    public Integer maxOutputTokens() { return maxOutputTokens; }
    public ModelMetadata metadata() { return metadata; }
    public GuideFailure failure() { return failure; }
public static ModelMetadataResolution resolved(
            ModelMetadata metadata,
            Integer explicitContextWindowTokens,
            Integer explicitMaxOutputTokens) {
        java.util.Objects.requireNonNull(metadata, "metadata");
        return new ModelMetadataResolution(
                explicitContextWindowTokens == null
                        ? metadata.contextWindowTokens()
                        : explicitContextWindowTokens,
                explicitMaxOutputTokens == null
                        ? metadata.maxOutputTokens()
                        : explicitMaxOutputTokens,
                metadata,
                null);
    }
public static ModelMetadataResolution failed(String code, String message) {
        return new ModelMetadataResolution(
                null, null, null, new GuideFailure(code, message));
    }
public boolean successful() {
        return metadata != null;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelMetadataResolution)) return false;
        ModelMetadataResolution that = (ModelMetadataResolution) other;
        return java.util.Objects.equals(contextWindowTokens, that.contextWindowTokens) && java.util.Objects.equals(maxOutputTokens, that.maxOutputTokens) && java.util.Objects.equals(metadata, that.metadata) && java.util.Objects.equals(failure, that.failure);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(contextWindowTokens);
        hash = 31 * hash + java.util.Objects.hashCode(maxOutputTokens);
        hash = 31 * hash + java.util.Objects.hashCode(metadata);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        return hash;
    }
    @Override public String toString() { return "ModelMetadataResolution[contextWindowTokens=" + contextWindowTokens + ", maxOutputTokens=" + maxOutputTokens + ", metadata=" + metadata + ", failure=" + failure + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelMetadataResolution> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelMetadataResolution.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelMetadataResolution>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelMetadataResolution.class, "contextWindowTokens", ModelMetadataResolution::contextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(ModelMetadataResolution.class, "maxOutputTokens", ModelMetadataResolution::maxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(ModelMetadataResolution.class, "metadata", ModelMetadataResolution::metadata), new dev.openallay.value.ValueSchema.Component<>(ModelMetadataResolution.class, "failure", ModelMetadataResolution::failure)), arguments -> new ModelMetadataResolution((Integer) arguments[0], (Integer) arguments[1], (ModelMetadata) arguments[2], (GuideFailure) arguments[3]));
        }
    }
}
