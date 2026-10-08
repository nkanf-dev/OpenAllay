package dev.openallay.model.metadata;

import dev.openallay.model.image.ImageInputCapability;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Image input is resolved independently from context/output limits and protocol choice. */
@dev.openallay.value.ValueType(ModelImageCapabilityResolution.ValueSchemaProvider.class)
public final class ModelImageCapabilityResolution {
    private final ImageInputCapability capability;
    private final Origin origin;
    private final String source;
    private final Instant capturedAt;
    public ModelImageCapabilityResolution(ImageInputCapability capability, Origin origin, String source, Instant capturedAt) {

        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(origin, "origin");

        this.capability = capability;
        this.origin = origin;
        this.source = source;
        this.capturedAt = capturedAt;
    }
    public ImageInputCapability capability() { return capability; }
    public Origin origin() { return origin; }
    public String source() { return source; }
    public Instant capturedAt() { return capturedAt; }
public ModelImageCapabilityResolution(ImageInputCapability capability, Origin origin) {
        this(capability, origin, null, null);
    }
public enum Origin { EXPLICIT, TRUSTED, BUILTIN, UNKNOWN }
public static ModelImageCapabilityResolution unknown() {
        return new ModelImageCapabilityResolution(ImageInputCapability.UNKNOWN, Origin.UNKNOWN);
    }
public static ModelImageCapabilityResolution resolve(
            URI endpoint, String model, ImageInputCapability explicit,
            Map<ModelMetadata.Key, ModelMetadata> trusted, BuiltinModelCatalog catalog) {
        if (explicit != null) return new ModelImageCapabilityResolution(explicit, Origin.EXPLICIT);
        ModelMetadata cached = null;
        if (OpenRouterMetadataResolver.supports(endpoint) && model != null && !dev.openallay.util.Java8Strings.isBlank(model)) {
            ModelMetadata.Key key = new ModelMetadata.Key(OpenRouterMetadataResolver.SOURCE, model);
            cached = trusted.get(key);
            if (cached != null && !cached.key().equals(key)) cached = null;
            if (cached != null && cached.imageInputCapability() != ImageInputCapability.UNKNOWN) {
                return trusted(cached);
            }
        }
        java.util.Optional<BuiltinModelMatcher.Match> matched = catalog.match(model)
                .filter(match -> match.kind() != BuiltinModelMatcher.Kind.SIMILAR);
        if (matched.isPresent()) {
            BuiltinModelCatalog.Entry entry = matched.get().entry();
            if (entry.imageInputCapability() != ImageInputCapability.UNKNOWN || cached == null) {
                BuiltinModelCatalog.Source source = entry.imageInputCapabilitySource() == null ? null
                        : catalog.sources().get(entry.imageInputCapabilitySource());
                return new ModelImageCapabilityResolution(entry.imageInputCapability(), Origin.BUILTIN,
                        entry.imageInputCapabilitySource(), source == null ? null : source.capturedAt());
            }
        }
        return cached == null ? unknown() : trusted(cached);
    }
private static ModelImageCapabilityResolution trusted(ModelMetadata metadata) {
        return new ModelImageCapabilityResolution(metadata.imageInputCapability(), Origin.TRUSTED,
                metadata.source(), metadata.capturedAt());
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelImageCapabilityResolution)) return false;
        ModelImageCapabilityResolution that = (ModelImageCapabilityResolution) other;
        return java.util.Objects.equals(capability, that.capability) && java.util.Objects.equals(origin, that.origin) && java.util.Objects.equals(source, that.source) && java.util.Objects.equals(capturedAt, that.capturedAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(capability);
        hash = 31 * hash + java.util.Objects.hashCode(origin);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        return hash;
    }
    @Override public String toString() { return "ModelImageCapabilityResolution[capability=" + capability + ", origin=" + origin + ", source=" + source + ", capturedAt=" + capturedAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelImageCapabilityResolution> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelImageCapabilityResolution.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelImageCapabilityResolution>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelImageCapabilityResolution.class, "capability", ModelImageCapabilityResolution::capability), new dev.openallay.value.ValueSchema.Component<>(ModelImageCapabilityResolution.class, "origin", ModelImageCapabilityResolution::origin), new dev.openallay.value.ValueSchema.Component<>(ModelImageCapabilityResolution.class, "source", ModelImageCapabilityResolution::source), new dev.openallay.value.ValueSchema.Component<>(ModelImageCapabilityResolution.class, "capturedAt", ModelImageCapabilityResolution::capturedAt)), arguments -> new ModelImageCapabilityResolution((ImageInputCapability) arguments[0], (Origin) arguments[1], (String) arguments[2], (Instant) arguments[3]));
        }
    }
}
