package dev.openallay.model.config;

import dev.openallay.agent.context.ContextBudget;
import dev.openallay.model.tokenizer.ModelTokenEncoding;
import dev.openallay.model.image.ImageInputCapability;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/** Credential-free persisted definition for one named client model profile. */
public record ModelProfileDefinition(
        String id,
        String displayName,
        boolean enabled,
        ModelProtocol protocol,
        URI baseUri,
        String model,
        String credentialRef,
        Integer contextWindowTokens,
        Integer maxOutputTokens,
        Duration connectTimeout,
        Duration requestTimeout,
        MetadataProvenance metadata,
        ModelReasoningEffort reasoningEffort,
        ModelTokenEncoding tokenEncoding,
        ImageInputCapability imageInputCapabilityOverride) {
    public ModelProfileDefinition {
        if (id == null || !id.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid model profile id");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(tokenEncoding, "tokenEncoding");
        Objects.requireNonNull(reasoningEffort, "reasoningEffort").requireSupported(protocol);
        Objects.requireNonNull(baseUri, "baseUri");
        validateUri(baseUri);
        String raw = baseUri.toString();
        baseUri = URI.create(raw.endsWith("/") ? raw : raw + "/");
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (credentialRef != null
                && credentialRef.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            credentialRef = CredentialReference.environment(credentialRef).encoded();
        } else {
            credentialRef = CredentialReference.parse(credentialRef).encoded();
        }
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        if (contextWindowTokens != null && contextWindowTokens <= 0) {
            throw new IllegalArgumentException("contextWindowTokens must be positive");
        }
        if (contextWindowTokens != null && maxOutputTokens != null) {
            new ContextBudget(contextWindowTokens, maxOutputTokens);
        }
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (connectTimeout.isZero() || connectTimeout.isNegative()
                || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("model timeouts must be positive");
        }
    }

    /** Existing callers leave image input to trusted or builtin metadata. */
    public ModelProfileDefinition(
            String id, String displayName, boolean enabled, ModelProtocol protocol,
            URI baseUri, String model, String credentialRef, Integer contextWindowTokens,
            Integer maxOutputTokens, Duration connectTimeout, Duration requestTimeout,
            MetadataProvenance metadata, ModelReasoningEffort reasoningEffort,
            ModelTokenEncoding tokenEncoding) {
        this(id, displayName, enabled, protocol, baseUri, model, credentialRef,
                contextWindowTokens, maxOutputTokens, connectTimeout, requestTimeout,
                metadata, reasoningEffort, tokenEncoding, null);
    }

    public ModelProfileDefinition(
            String id, String displayName, boolean enabled, ModelProtocol protocol,
            URI baseUri, String model, String credentialRef, Integer contextWindowTokens,
            Integer maxOutputTokens, Duration connectTimeout, Duration requestTimeout,
            MetadataProvenance metadata, ModelReasoningEffort reasoningEffort) {
        this(id, displayName, enabled, protocol, baseUri, model, credentialRef,
                contextWindowTokens, maxOutputTokens, connectTimeout, requestTimeout,
                metadata, reasoningEffort, ModelTokenEncoding.AUTO);
    }

    /** Callers without an explicit choice leave effort to the provider. */
    public ModelProfileDefinition(
            String id, String displayName, boolean enabled, ModelProtocol protocol,
            URI baseUri, String model, String credentialRef, Integer contextWindowTokens,
            Integer maxOutputTokens, Duration connectTimeout, Duration requestTimeout,
            MetadataProvenance metadata) {
        this(id, displayName, enabled, protocol, baseUri, model, credentialRef,
                contextWindowTokens, maxOutputTokens, connectTimeout, requestTimeout,
                metadata, ModelReasoningEffort.AUTO);
    }

    private static void validateUri(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        boolean loopback = host.equals("localhost")
                || host.equals("127.0.0.1")
                || host.equals("::1")
                || host.equals("[::1]");
        if (!scheme.equals("https") && !(scheme.equals("http") && loopback)) {
            throw new IllegalArgumentException("Remote model endpoints require HTTPS");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Model base URL must not contain credentials, query, or fragment");
        }
    }

    public record MetadataProvenance(
            String source,
            String upstreamModelId,
            Instant capturedAt) {
        public MetadataProvenance {
            if (source == null || source.isBlank()
                    || upstreamModelId == null || upstreamModelId.isBlank()) {
                throw new IllegalArgumentException("metadata provenance identity must not be blank");
            }
            Objects.requireNonNull(capturedAt, "capturedAt");
        }
    }
}
