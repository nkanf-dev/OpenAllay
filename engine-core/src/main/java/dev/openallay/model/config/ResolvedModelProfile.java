package dev.openallay.model.config;

import dev.openallay.guide.GuideFailure;
import dev.openallay.model.metadata.ModelImageCapabilityResolution;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import java.net.URI;
import java.util.Objects;

/** One retained profile definition and its optional usable runtime config. */
public record ResolvedModelProfile(
        ModelProfileDefinition definition,
        ModelConfig runtimeConfig,
        GuideFailure failure,
        String canonicalModelId,
        ModelImageCapabilityResolution imageCapability) {
    public ResolvedModelProfile {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(imageCapability, "imageCapability");
        if ((runtimeConfig == null) == (failure == null)) {
            throw new IllegalArgumentException(
                    "resolved profile must contain exactly one runtime or failure");
        }
        if (canonicalModelId == null || canonicalModelId.isBlank()) {
            throw new IllegalArgumentException("canonical model ID is required");
        }
    }

    public ResolvedModelProfile(
            ModelProfileDefinition definition, ModelConfig runtimeConfig,
            GuideFailure failure, String canonicalModelId) {
        this(definition, runtimeConfig, failure, canonicalModelId,
                runtimeConfig == null
                        ? ModelImageCapabilityResolution.resolve(definition.baseUri(), definition.model(),
                                definition.imageInputCapabilityOverride(), java.util.Map.of(),
                                BuiltinModelCatalog.bundled().catalog())
                        : runtimeConfig.imageCapability());
    }

    public ResolvedModelProfile(
            ModelProfileDefinition definition,
            ModelConfig runtimeConfig,
            GuideFailure failure) {
        this(definition, runtimeConfig, failure, definition.model());
    }

    public boolean available() {
        return runtimeConfig != null;
    }

    public DiagnosticView diagnosticView() {
        return new DiagnosticView(
                definition.id(),
                definition.displayName(),
                definition.enabled(),
                available(),
                definition.protocol(),
                definition.baseUri(),
                definition.model(),
                definition.credentialRef(),
                runtimeConfig != null,
                runtimeConfig == null
                        ? definition.contextWindowTokens()
                        : Integer.valueOf(runtimeConfig.contextWindowTokens()),
                runtimeConfig == null
                        ? definition.maxOutputTokens()
                        : Integer.valueOf(runtimeConfig.maxOutputTokens()),
                definition.reasoningEffort(),
                definition.tokenEncoding(),
                failure,
                imageCapability);
    }

    public record DiagnosticView(
            String id,
            String displayName,
            boolean enabled,
            boolean available,
            ModelProtocol protocol,
            URI baseUri,
            String model,
            String credentialRef,
            boolean apiKeyPresent,
            Integer contextWindowTokens,
            Integer maxOutputTokens,
            ModelReasoningEffort reasoningEffort,
            dev.openallay.model.tokenizer.ModelTokenEncoding tokenEncoding,
            GuideFailure failure,
            ModelImageCapabilityResolution imageCapability) {}
}
