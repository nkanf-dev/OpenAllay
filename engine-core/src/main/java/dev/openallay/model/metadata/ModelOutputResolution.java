package dev.openallay.model.metadata;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Shared output precedence. Automatic limits use published maxima, never a guessed budget. */
@dev.openallay.value.ValueType(ModelOutputResolution.ValueSchemaProvider.class)
public final class ModelOutputResolution {
    private final Integer maxOutputTokens;
    private final Origin origin;
    private final String source;
    private final Instant capturedAt;
    public ModelOutputResolution(Integer maxOutputTokens, Origin origin, String source, Instant capturedAt) {

        Objects.requireNonNull(origin, "origin");
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("output limit must be positive");
        }
        if ((maxOutputTokens == null) != (origin == Origin.REQUIRED)) {
            throw new IllegalArgumentException("only required output resolution may omit its limit");
        }

        this.maxOutputTokens = maxOutputTokens;
        this.origin = origin;
        this.source = source;
        this.capturedAt = capturedAt;
    }
    public Integer maxOutputTokens() { return maxOutputTokens; }
    public Origin origin() { return origin; }
    public String source() { return source; }
    public Instant capturedAt() { return capturedAt; }
public ModelOutputResolution(Integer maxOutputTokens, Origin origin) {
        this(maxOutputTokens, origin, null, null);
    }
public enum Origin { EXPLICIT, TRUSTED, BUILTIN, REQUIRED }
public static ModelOutputResolution resolve(
            URI endpoint, String model, Integer explicit,
            Map<ModelMetadata.Key, ModelMetadata> trusted, BuiltinModelCatalog catalog) {
        if (explicit != null) return new ModelOutputResolution(explicit, Origin.EXPLICIT);
        if (OpenRouterMetadataResolver.supports(endpoint) && model != null && !dev.openallay.util.Java8Strings.isBlank(model)) {
            ModelMetadata cached = trusted.get(new ModelMetadata.Key(OpenRouterMetadataResolver.SOURCE, model));
            if (cached != null && cached.key().equals(new ModelMetadata.Key(
                    OpenRouterMetadataResolver.SOURCE, model)) && cached.maxOutputTokens() != null) {
                return new ModelOutputResolution(cached.maxOutputTokens(), Origin.TRUSTED,
                        cached.source(), cached.capturedAt());
            }
        }
        return catalog.match(model)
                .filter(match -> match.entry().maxOutputTokens() != null)
                .map(match -> new ModelOutputResolution(match.entry().maxOutputTokens(), Origin.BUILTIN))
                .orElseGet(() -> new ModelOutputResolution(null, Origin.REQUIRED));
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelOutputResolution)) return false;
        ModelOutputResolution that = (ModelOutputResolution) other;
        return java.util.Objects.equals(maxOutputTokens, that.maxOutputTokens) && java.util.Objects.equals(origin, that.origin) && java.util.Objects.equals(source, that.source) && java.util.Objects.equals(capturedAt, that.capturedAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(maxOutputTokens);
        hash = 31 * hash + java.util.Objects.hashCode(origin);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        return hash;
    }
    @Override public String toString() { return "ModelOutputResolution[maxOutputTokens=" + maxOutputTokens + ", origin=" + origin + ", source=" + source + ", capturedAt=" + capturedAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelOutputResolution> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelOutputResolution.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelOutputResolution>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelOutputResolution.class, "maxOutputTokens", ModelOutputResolution::maxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(ModelOutputResolution.class, "origin", ModelOutputResolution::origin), new dev.openallay.value.ValueSchema.Component<>(ModelOutputResolution.class, "source", ModelOutputResolution::source), new dev.openallay.value.ValueSchema.Component<>(ModelOutputResolution.class, "capturedAt", ModelOutputResolution::capturedAt)), arguments -> new ModelOutputResolution((Integer) arguments[0], (Origin) arguments[1], (String) arguments[2], (Instant) arguments[3]));
        }
    }
}
