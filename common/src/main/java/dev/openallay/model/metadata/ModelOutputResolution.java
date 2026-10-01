package dev.openallay.model.metadata;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Shared output precedence. Automatic limits use published maxima, never a guessed budget. */
public record ModelOutputResolution(
        Integer maxOutputTokens, Origin origin, String source, Instant capturedAt) {
    public ModelOutputResolution {
        Objects.requireNonNull(origin, "origin");
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("output limit must be positive");
        }
        if ((maxOutputTokens == null) != (origin == Origin.REQUIRED)) {
            throw new IllegalArgumentException("only required output resolution may omit its limit");
        }
    }

    public ModelOutputResolution(Integer maxOutputTokens, Origin origin) {
        this(maxOutputTokens, origin, null, null);
    }

    public enum Origin { EXPLICIT, TRUSTED, BUILTIN, REQUIRED }

    public static ModelOutputResolution resolve(
            URI endpoint, String model, Integer explicit,
            Map<ModelMetadata.Key, ModelMetadata> trusted, BuiltinModelCatalog catalog) {
        if (explicit != null) return new ModelOutputResolution(explicit, Origin.EXPLICIT);
        if (OpenRouterMetadataResolver.supports(endpoint) && model != null && !model.isBlank()) {
            var cached = trusted.get(new ModelMetadata.Key(OpenRouterMetadataResolver.SOURCE, model));
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
}
