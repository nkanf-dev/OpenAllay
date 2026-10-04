package dev.openallay.model.metadata;

import java.net.URI;
import java.util.Map;

/** One shared context precedence rule for loader and editable settings projections. */
public record ModelContextResolution(
        Integer contextWindowTokens, Origin origin, String source, java.time.Instant capturedAt) {
    public ModelContextResolution(Integer contextWindowTokens, Origin origin) {
        this(contextWindowTokens, origin, null, null);
    }

    public enum Origin { EXPLICIT, TRUSTED, BUILTIN, REQUIRED }

    public static ModelContextResolution resolve(
            URI endpoint, String model, Integer explicit,
            Map<ModelMetadata.Key, ModelMetadata> trusted, BuiltinModelCatalog catalog) {
        if (explicit != null) return new ModelContextResolution(explicit, Origin.EXPLICIT);
        if (OpenRouterMetadataResolver.supports(endpoint) && model != null && !model.isBlank()) {
            var cached = trusted.get(new ModelMetadata.Key(OpenRouterMetadataResolver.SOURCE, model));
            if (cached != null) return new ModelContextResolution(cached.contextWindowTokens(), Origin.TRUSTED,
                    cached.source(), cached.capturedAt());
        }
        return catalog.match(model)
                .map(match -> new ModelContextResolution(match.entry().contextWindowTokens(), Origin.BUILTIN))
                .orElseGet(() -> new ModelContextResolution(null, Origin.REQUIRED));
    }
}
