package dev.openallay.model.metadata;

import java.net.URI;
import java.util.Map;

/** One shared context precedence rule for loader and editable settings projections. */
@dev.openallay.value.ValueType(ModelContextResolution.ValueSchemaProvider.class)
public final class ModelContextResolution {
    private final Integer contextWindowTokens;
    private final Origin origin;
    private final String source;
    private final java.time.Instant capturedAt;
    public ModelContextResolution(Integer contextWindowTokens, Origin origin, String source, java.time.Instant capturedAt) {
        this.contextWindowTokens = contextWindowTokens;
        this.origin = origin;
        this.source = source;
        this.capturedAt = capturedAt;
    }
    public Integer contextWindowTokens() { return contextWindowTokens; }
    public Origin origin() { return origin; }
    public String source() { return source; }
    public java.time.Instant capturedAt() { return capturedAt; }
public ModelContextResolution(Integer contextWindowTokens, Origin origin) {
        this(contextWindowTokens, origin, null, null);
    }
public enum Origin { EXPLICIT, TRUSTED, BUILTIN, REQUIRED }
public static ModelContextResolution resolve(
            URI endpoint, String model, Integer explicit,
            Map<ModelMetadata.Key, ModelMetadata> trusted, BuiltinModelCatalog catalog) {
        if (explicit != null) return new ModelContextResolution(explicit, Origin.EXPLICIT);
        if (OpenRouterMetadataResolver.supports(endpoint) && model != null && !dev.openallay.util.Java8Strings.isBlank(model)) {
            ModelMetadata cached = trusted.get(new ModelMetadata.Key(OpenRouterMetadataResolver.SOURCE, model));
            if (cached != null) return new ModelContextResolution(cached.contextWindowTokens(), Origin.TRUSTED,
                    cached.source(), cached.capturedAt());
        }
        return catalog.match(model)
                .map(match -> new ModelContextResolution(match.entry().contextWindowTokens(), Origin.BUILTIN))
                .orElseGet(() -> new ModelContextResolution(null, Origin.REQUIRED));
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelContextResolution)) return false;
        ModelContextResolution that = (ModelContextResolution) other;
        return java.util.Objects.equals(contextWindowTokens, that.contextWindowTokens) && java.util.Objects.equals(origin, that.origin) && java.util.Objects.equals(source, that.source) && java.util.Objects.equals(capturedAt, that.capturedAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(contextWindowTokens);
        hash = 31 * hash + java.util.Objects.hashCode(origin);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        return hash;
    }
    @Override public String toString() { return "ModelContextResolution[contextWindowTokens=" + contextWindowTokens + ", origin=" + origin + ", source=" + source + ", capturedAt=" + capturedAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelContextResolution> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelContextResolution.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelContextResolution>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelContextResolution.class, "contextWindowTokens", ModelContextResolution::contextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(ModelContextResolution.class, "origin", ModelContextResolution::origin), new dev.openallay.value.ValueSchema.Component<>(ModelContextResolution.class, "source", ModelContextResolution::source), new dev.openallay.value.ValueSchema.Component<>(ModelContextResolution.class, "capturedAt", ModelContextResolution::capturedAt)), arguments -> new ModelContextResolution((Integer) arguments[0], (Origin) arguments[1], (String) arguments[2], (java.time.Instant) arguments[3]));
        }
    }
}
