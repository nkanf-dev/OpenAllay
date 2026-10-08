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
@dev.openallay.value.ValueType(ModelProfileDefinition.ValueSchemaProvider.class)
public final class ModelProfileDefinition {
    private final String id;
    private final String displayName;
    private final boolean enabled;
    private final ModelProtocol protocol;
    private final URI baseUri;
    private final String model;
    private final String credentialRef;
    private final Integer contextWindowTokens;
    private final Integer maxOutputTokens;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final MetadataProvenance metadata;
    private final ModelReasoningEffort reasoningEffort;
    private final ModelTokenEncoding tokenEncoding;
    private final ImageInputCapability imageInputCapabilityOverride;
    public ModelProfileDefinition(String id, String displayName, boolean enabled, ModelProtocol protocol, URI baseUri, String model, String credentialRef, Integer contextWindowTokens, Integer maxOutputTokens, Duration connectTimeout, Duration requestTimeout, MetadataProvenance metadata, ModelReasoningEffort reasoningEffort, ModelTokenEncoding tokenEncoding, ImageInputCapability imageInputCapabilityOverride) {

        if (id == null || !id.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid model profile id");
        }
        if (displayName == null || dev.openallay.util.Java8Strings.isBlank(displayName)) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(tokenEncoding, "tokenEncoding");
        Objects.requireNonNull(reasoningEffort, "reasoningEffort").requireSupported(protocol);
        Objects.requireNonNull(baseUri, "baseUri");
        validateUri(baseUri);
        String raw = baseUri.toString();
        baseUri = URI.create(raw.endsWith("/") ? raw : raw + "/");
        if (model == null || dev.openallay.util.Java8Strings.isBlank(model)) {
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

        this.id = id;
        this.displayName = displayName;
        this.enabled = enabled;
        this.protocol = protocol;
        this.baseUri = baseUri;
        this.model = model;
        this.credentialRef = credentialRef;
        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
        this.metadata = metadata;
        this.reasoningEffort = reasoningEffort;
        this.tokenEncoding = tokenEncoding;
        this.imageInputCapabilityOverride = imageInputCapabilityOverride;
    }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public boolean enabled() { return enabled; }
    public ModelProtocol protocol() { return protocol; }
    public URI baseUri() { return baseUri; }
    public String model() { return model; }
    public String credentialRef() { return credentialRef; }
    public Integer contextWindowTokens() { return contextWindowTokens; }
    public Integer maxOutputTokens() { return maxOutputTokens; }
    public Duration connectTimeout() { return connectTimeout; }
    public Duration requestTimeout() { return requestTimeout; }
    public MetadataProvenance metadata() { return metadata; }
    public ModelReasoningEffort reasoningEffort() { return reasoningEffort; }
    public ModelTokenEncoding tokenEncoding() { return tokenEncoding; }
    public ImageInputCapability imageInputCapabilityOverride() { return imageInputCapabilityOverride; }
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
@dev.openallay.value.ValueType(MetadataProvenance.ValueSchemaProvider.class)
public static final class MetadataProvenance {
    private final String source;
    private final String upstreamModelId;
    private final Instant capturedAt;
    public MetadataProvenance(String source, String upstreamModelId, Instant capturedAt) {

            if (source == null || dev.openallay.util.Java8Strings.isBlank(source)
                    || upstreamModelId == null || dev.openallay.util.Java8Strings.isBlank(upstreamModelId)) {
                throw new IllegalArgumentException("metadata provenance identity must not be blank");
            }
            Objects.requireNonNull(capturedAt, "capturedAt");

        this.source = source;
        this.upstreamModelId = upstreamModelId;
        this.capturedAt = capturedAt;
    }
    public String source() { return source; }
    public String upstreamModelId() { return upstreamModelId; }
    public Instant capturedAt() { return capturedAt; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MetadataProvenance)) return false;
        MetadataProvenance that = (MetadataProvenance) other;
        return java.util.Objects.equals(source, that.source) && java.util.Objects.equals(upstreamModelId, that.upstreamModelId) && java.util.Objects.equals(capturedAt, that.capturedAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(upstreamModelId);
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        return hash;
    }
    @Override public String toString() { return "MetadataProvenance[source=" + source + ", upstreamModelId=" + upstreamModelId + ", capturedAt=" + capturedAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<MetadataProvenance> schema() {
            return new dev.openallay.value.ValueSchema<>(MetadataProvenance.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<MetadataProvenance>>asList(new dev.openallay.value.ValueSchema.Component<>(MetadataProvenance.class, "source", MetadataProvenance::source), new dev.openallay.value.ValueSchema.Component<>(MetadataProvenance.class, "upstreamModelId", MetadataProvenance::upstreamModelId), new dev.openallay.value.ValueSchema.Component<>(MetadataProvenance.class, "capturedAt", MetadataProvenance::capturedAt)), arguments -> new MetadataProvenance((String) arguments[0], (String) arguments[1], (Instant) arguments[2]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelProfileDefinition)) return false;
        ModelProfileDefinition that = (ModelProfileDefinition) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(displayName, that.displayName) && enabled == that.enabled && java.util.Objects.equals(protocol, that.protocol) && java.util.Objects.equals(baseUri, that.baseUri) && java.util.Objects.equals(model, that.model) && java.util.Objects.equals(credentialRef, that.credentialRef) && java.util.Objects.equals(contextWindowTokens, that.contextWindowTokens) && java.util.Objects.equals(maxOutputTokens, that.maxOutputTokens) && java.util.Objects.equals(connectTimeout, that.connectTimeout) && java.util.Objects.equals(requestTimeout, that.requestTimeout) && java.util.Objects.equals(metadata, that.metadata) && java.util.Objects.equals(reasoningEffort, that.reasoningEffort) && java.util.Objects.equals(tokenEncoding, that.tokenEncoding) && java.util.Objects.equals(imageInputCapabilityOverride, that.imageInputCapabilityOverride);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + java.util.Objects.hashCode(protocol);
        hash = 31 * hash + java.util.Objects.hashCode(baseUri);
        hash = 31 * hash + java.util.Objects.hashCode(model);
        hash = 31 * hash + java.util.Objects.hashCode(credentialRef);
        hash = 31 * hash + java.util.Objects.hashCode(contextWindowTokens);
        hash = 31 * hash + java.util.Objects.hashCode(maxOutputTokens);
        hash = 31 * hash + java.util.Objects.hashCode(connectTimeout);
        hash = 31 * hash + java.util.Objects.hashCode(requestTimeout);
        hash = 31 * hash + java.util.Objects.hashCode(metadata);
        hash = 31 * hash + java.util.Objects.hashCode(reasoningEffort);
        hash = 31 * hash + java.util.Objects.hashCode(tokenEncoding);
        hash = 31 * hash + java.util.Objects.hashCode(imageInputCapabilityOverride);
        return hash;
    }
    @Override public String toString() { return "ModelProfileDefinition[id=" + id + ", displayName=" + displayName + ", enabled=" + enabled + ", protocol=" + protocol + ", baseUri=" + baseUri + ", model=" + model + ", credentialRef=" + credentialRef + ", contextWindowTokens=" + contextWindowTokens + ", maxOutputTokens=" + maxOutputTokens + ", connectTimeout=" + connectTimeout + ", requestTimeout=" + requestTimeout + ", metadata=" + metadata + ", reasoningEffort=" + reasoningEffort + ", tokenEncoding=" + tokenEncoding + ", imageInputCapabilityOverride=" + imageInputCapabilityOverride + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelProfileDefinition> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelProfileDefinition.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelProfileDefinition>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "id", ModelProfileDefinition::id), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "displayName", ModelProfileDefinition::displayName), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "enabled", ModelProfileDefinition::enabled), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "protocol", ModelProfileDefinition::protocol), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "baseUri", ModelProfileDefinition::baseUri), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "model", ModelProfileDefinition::model), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "credentialRef", ModelProfileDefinition::credentialRef), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "contextWindowTokens", ModelProfileDefinition::contextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "maxOutputTokens", ModelProfileDefinition::maxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "connectTimeout", ModelProfileDefinition::connectTimeout), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "requestTimeout", ModelProfileDefinition::requestTimeout), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "metadata", ModelProfileDefinition::metadata), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "reasoningEffort", ModelProfileDefinition::reasoningEffort), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "tokenEncoding", ModelProfileDefinition::tokenEncoding), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDefinition.class, "imageInputCapabilityOverride", ModelProfileDefinition::imageInputCapabilityOverride)), arguments -> new ModelProfileDefinition((String) arguments[0], (String) arguments[1], (Boolean) arguments[2], (ModelProtocol) arguments[3], (URI) arguments[4], (String) arguments[5], (String) arguments[6], (Integer) arguments[7], (Integer) arguments[8], (Duration) arguments[9], (Duration) arguments[10], (MetadataProvenance) arguments[11], (ModelReasoningEffort) arguments[12], (ModelTokenEncoding) arguments[13], (ImageInputCapability) arguments[14]));
        }
    }
}
