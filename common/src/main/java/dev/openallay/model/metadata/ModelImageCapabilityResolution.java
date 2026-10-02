package dev.openallay.model.metadata;

import dev.openallay.model.image.ImageInputCapability;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Image input is resolved independently from context/output limits and protocol choice. */
public record ModelImageCapabilityResolution(
        ImageInputCapability capability, Origin origin, String source, Instant capturedAt) {
    public ModelImageCapabilityResolution {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(origin, "origin");
    }

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
        if (OpenRouterMetadataResolver.supports(endpoint) && model != null && !model.isBlank()) {
            ModelMetadata.Key key = new ModelMetadata.Key(OpenRouterMetadataResolver.SOURCE, model);
            cached = trusted.get(key);
            if (cached != null && !cached.key().equals(key)) cached = null;
            if (cached != null && cached.imageInputCapability() != ImageInputCapability.UNKNOWN) {
                return trusted(cached);
            }
        }
        var matched = catalog.match(model)
                .filter(match -> match.kind() != BuiltinModelMatcher.Kind.SIMILAR);
        if (matched.isPresent()) {
            var entry = matched.get().entry();
            if (entry.imageInputCapability() != ImageInputCapability.UNKNOWN || cached == null) {
                var source = entry.imageInputCapabilitySource() == null ? null
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
}
