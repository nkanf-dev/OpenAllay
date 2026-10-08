package dev.openallay.model.metadata;

import dev.openallay.guide.GuideFailure;
import java.util.Map;

/** Immutable credential-free cache event; it carries no resolved profile runtime. */
@dev.openallay.value.ValueType(ModelMetadataUpdate.ValueSchemaProvider.class)
public final class ModelMetadataUpdate {
    private final Map<ModelMetadata.Key, ModelMetadata> entries;
    private final GuideFailure failure;
    public ModelMetadataUpdate(Map<ModelMetadata.Key, ModelMetadata> entries, GuideFailure failure) {

        entries = dev.openallay.util.Java8Collections.mapCopyOf(entries);

        this.entries = entries;
        this.failure = failure;
    }
    public Map<ModelMetadata.Key, ModelMetadata> entries() { return entries; }
    public GuideFailure failure() { return failure; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelMetadataUpdate)) return false;
        ModelMetadataUpdate that = (ModelMetadataUpdate) other;
        return java.util.Objects.equals(entries, that.entries) && java.util.Objects.equals(failure, that.failure);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(entries);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        return hash;
    }
    @Override public String toString() { return "ModelMetadataUpdate[entries=" + entries + ", failure=" + failure + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelMetadataUpdate> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelMetadataUpdate.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelMetadataUpdate>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelMetadataUpdate.class, "entries", ModelMetadataUpdate::entries), new dev.openallay.value.ValueSchema.Component<>(ModelMetadataUpdate.class, "failure", ModelMetadataUpdate::failure)), arguments -> new ModelMetadataUpdate((Map) arguments[0], (GuideFailure) arguments[1]));
        }
    }
}
