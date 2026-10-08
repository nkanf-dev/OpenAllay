package dev.openallay.model.catalog;

import java.util.List;

/** Ephemeral validated provider model IDs; no credential or raw response is retained. */
@dev.openallay.value.ValueType(ModelCatalog.ValueSchemaProvider.class)
public final class ModelCatalog {
    private final List<String> modelIds;
    public ModelCatalog(List<String> modelIds) {

        modelIds = dev.openallay.util.Java8Collections.listCopyOf(modelIds);
        if (modelIds.stream().anyMatch(id -> id == null || dev.openallay.util.Java8Strings.isBlank(id))) {
            throw new IllegalArgumentException("model catalog IDs must be nonblank");
        }
        if (modelIds.size() != new java.util.LinkedHashSet<>(modelIds).size()) {
            throw new IllegalArgumentException("model catalog IDs must be duplicate-free");
        }

        this.modelIds = modelIds;
    }
    public List<String> modelIds() { return modelIds; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelCatalog)) return false;
        ModelCatalog that = (ModelCatalog) other;
        return java.util.Objects.equals(modelIds, that.modelIds);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(modelIds);
        return hash;
    }
    @Override public String toString() { return "ModelCatalog[modelIds=" + modelIds + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelCatalog> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelCatalog.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelCatalog>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelCatalog.class, "modelIds", ModelCatalog::modelIds)), arguments -> new ModelCatalog((List) arguments[0]));
        }
    }
}
