package dev.openallay.model.config;

import dev.openallay.agent.context.ContextBudget;
import dev.openallay.model.tokenizer.ModelTokenEncoding;
import dev.openallay.model.metadata.ModelImageCapabilityResolution;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

@dev.openallay.value.ValueType(ModelConfig.ValueSchemaProvider.class)
public final class ModelConfig {
    private final boolean enabled;
    private final ModelProtocol protocol;
    private final URI baseUri;
    private final String model;
    private final SecretValue apiKey;
    private final int contextWindowTokens;
    private final int maxOutputTokens;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final ModelReasoningEffort reasoningEffort;
    private final ModelTokenEncoding tokenEncoding;
    private final ModelImageCapabilityResolution imageCapability;
    public ModelConfig(boolean enabled, ModelProtocol protocol, URI baseUri, String model, SecretValue apiKey, int contextWindowTokens, int maxOutputTokens, Duration connectTimeout, Duration requestTimeout, ModelReasoningEffort reasoningEffort, ModelTokenEncoding tokenEncoding, ModelImageCapabilityResolution imageCapability) {

        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(tokenEncoding, "tokenEncoding");
        Objects.requireNonNull(imageCapability, "imageCapability");
        Objects.requireNonNull(reasoningEffort, "reasoningEffort").requireSupported(protocol);
        Objects.requireNonNull(baseUri, "baseUri");
        if (model == null || dev.openallay.util.Java8Strings.isBlank(model)) {
            throw new IllegalArgumentException("Model ID must not be blank");
        }
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        new ContextBudget(contextWindowTokens, maxOutputTokens);
        if (connectTimeout.isZero()
                || connectTimeout.isNegative()
                || requestTimeout.isZero()
                || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("Model timeouts must be positive");
        }
        validateUri(baseUri);
        String raw = baseUri.toString();
        baseUri = URI.create(raw.endsWith("/") ? raw : raw + "/");

        this.enabled = enabled;
        this.protocol = protocol;
        this.baseUri = baseUri;
        this.model = model;
        this.apiKey = apiKey;
        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.connectTimeout = connectTimeout;
        this.requestTimeout = requestTimeout;
        this.reasoningEffort = reasoningEffort;
        this.tokenEncoding = tokenEncoding;
        this.imageCapability = imageCapability;
    }
    public boolean enabled() { return enabled; }
    public ModelProtocol protocol() { return protocol; }
    public URI baseUri() { return baseUri; }
    public String model() { return model; }
    public SecretValue apiKey() { return apiKey; }
    public int contextWindowTokens() { return contextWindowTokens; }
    public int maxOutputTokens() { return maxOutputTokens; }
    public Duration connectTimeout() { return connectTimeout; }
    public Duration requestTimeout() { return requestTimeout; }
    public ModelReasoningEffort reasoningEffort() { return reasoningEffort; }
    public ModelTokenEncoding tokenEncoding() { return tokenEncoding; }
    public ModelImageCapabilityResolution imageCapability() { return imageCapability; }
public ModelConfig(
            boolean enabled, ModelProtocol protocol, URI baseUri, String model,
            SecretValue apiKey, int contextWindowTokens, int maxOutputTokens,
            Duration connectTimeout, Duration requestTimeout, ModelReasoningEffort reasoningEffort,
            ModelTokenEncoding tokenEncoding) {
        this(enabled, protocol, baseUri, model, apiKey, contextWindowTokens,
                maxOutputTokens, connectTimeout, requestTimeout, reasoningEffort, tokenEncoding,
                ModelImageCapabilityResolution.resolve(baseUri, model, null, dev.openallay.util.Java8Collections.mapOf(),
                        BuiltinModelCatalog.bundled().catalog()));
    }
public ModelConfig(
            boolean enabled, ModelProtocol protocol, URI baseUri, String model,
            SecretValue apiKey, int contextWindowTokens, int maxOutputTokens,
            Duration connectTimeout, Duration requestTimeout, ModelReasoningEffort reasoningEffort) {
        this(enabled, protocol, baseUri, model, apiKey, contextWindowTokens,
                maxOutputTokens, connectTimeout, requestTimeout, reasoningEffort, ModelTokenEncoding.AUTO);
    }
public ModelConfig(
            boolean enabled, ModelProtocol protocol, URI baseUri, String model,
            SecretValue apiKey, int contextWindowTokens, int maxOutputTokens,
            Duration connectTimeout, Duration requestTimeout) {
        this(enabled, protocol, baseUri, model, apiKey, contextWindowTokens,
                maxOutputTokens, connectTimeout, requestTimeout, ModelReasoningEffort.AUTO);
    }
public DiagnosticView diagnosticView() {
        return new DiagnosticView(
                enabled,
                protocol,
                baseUri,
                model,
                apiKey.toString(),
                contextWindowTokens,
                maxOutputTokens,
                connectTimeout.toMillis(),
                requestTimeout.toMillis(),
                reasoningEffort, tokenEncoding);
    }
public ContextBudget contextBudget() {
        return new ContextBudget(contextWindowTokens, maxOutputTokens);
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
            throw new IllegalArgumentException("Model base URL must not contain credentials, query, or fragment");
        }
    }
@dev.openallay.value.ValueType(DiagnosticView.ValueSchemaProvider.class)
public static final class DiagnosticView {
    private final boolean enabled;
    private final ModelProtocol protocol;
    private final URI baseUri;
    private final String model;
    private final String apiKey;
    private final int contextWindowTokens;
    private final int maxOutputTokens;
    private final long connectTimeoutMillis;
    private final long requestTimeoutMillis;
    private final ModelReasoningEffort reasoningEffort;
    private final ModelTokenEncoding tokenEncoding;
    public DiagnosticView(boolean enabled, ModelProtocol protocol, URI baseUri, String model, String apiKey, int contextWindowTokens, int maxOutputTokens, long connectTimeoutMillis, long requestTimeoutMillis, ModelReasoningEffort reasoningEffort, ModelTokenEncoding tokenEncoding) {
        this.enabled = enabled;
        this.protocol = protocol;
        this.baseUri = baseUri;
        this.model = model;
        this.apiKey = apiKey;
        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.requestTimeoutMillis = requestTimeoutMillis;
        this.reasoningEffort = reasoningEffort;
        this.tokenEncoding = tokenEncoding;
    }
    public boolean enabled() { return enabled; }
    public ModelProtocol protocol() { return protocol; }
    public URI baseUri() { return baseUri; }
    public String model() { return model; }
    public String apiKey() { return apiKey; }
    public int contextWindowTokens() { return contextWindowTokens; }
    public int maxOutputTokens() { return maxOutputTokens; }
    public long connectTimeoutMillis() { return connectTimeoutMillis; }
    public long requestTimeoutMillis() { return requestTimeoutMillis; }
    public ModelReasoningEffort reasoningEffort() { return reasoningEffort; }
    public ModelTokenEncoding tokenEncoding() { return tokenEncoding; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiagnosticView)) return false;
        DiagnosticView that = (DiagnosticView) other;
        return enabled == that.enabled && java.util.Objects.equals(protocol, that.protocol) && java.util.Objects.equals(baseUri, that.baseUri) && java.util.Objects.equals(model, that.model) && java.util.Objects.equals(apiKey, that.apiKey) && contextWindowTokens == that.contextWindowTokens && maxOutputTokens == that.maxOutputTokens && connectTimeoutMillis == that.connectTimeoutMillis && requestTimeoutMillis == that.requestTimeoutMillis && java.util.Objects.equals(reasoningEffort, that.reasoningEffort) && java.util.Objects.equals(tokenEncoding, that.tokenEncoding);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + java.util.Objects.hashCode(protocol);
        hash = 31 * hash + java.util.Objects.hashCode(baseUri);
        hash = 31 * hash + java.util.Objects.hashCode(model);
        hash = 31 * hash + java.util.Objects.hashCode(apiKey);
        hash = 31 * hash + Integer.hashCode(contextWindowTokens);
        hash = 31 * hash + Integer.hashCode(maxOutputTokens);
        hash = 31 * hash + Long.hashCode(connectTimeoutMillis);
        hash = 31 * hash + Long.hashCode(requestTimeoutMillis);
        hash = 31 * hash + java.util.Objects.hashCode(reasoningEffort);
        hash = 31 * hash + java.util.Objects.hashCode(tokenEncoding);
        return hash;
    }
    @Override public String toString() { return "DiagnosticView[enabled=" + enabled + ", protocol=" + protocol + ", baseUri=" + baseUri + ", model=" + model + ", apiKey=" + apiKey + ", contextWindowTokens=" + contextWindowTokens + ", maxOutputTokens=" + maxOutputTokens + ", connectTimeoutMillis=" + connectTimeoutMillis + ", requestTimeoutMillis=" + requestTimeoutMillis + ", reasoningEffort=" + reasoningEffort + ", tokenEncoding=" + tokenEncoding + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DiagnosticView> schema() {
            return new dev.openallay.value.ValueSchema<>(DiagnosticView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DiagnosticView>>asList(new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "enabled", DiagnosticView::enabled), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "protocol", DiagnosticView::protocol), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "baseUri", DiagnosticView::baseUri), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "model", DiagnosticView::model), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "apiKey", DiagnosticView::apiKey), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "contextWindowTokens", DiagnosticView::contextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "maxOutputTokens", DiagnosticView::maxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "connectTimeoutMillis", DiagnosticView::connectTimeoutMillis), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "requestTimeoutMillis", DiagnosticView::requestTimeoutMillis), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "reasoningEffort", DiagnosticView::reasoningEffort), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "tokenEncoding", DiagnosticView::tokenEncoding)), arguments -> new DiagnosticView((Boolean) arguments[0], (ModelProtocol) arguments[1], (URI) arguments[2], (String) arguments[3], (String) arguments[4], (Integer) arguments[5], (Integer) arguments[6], (Long) arguments[7], (Long) arguments[8], (ModelReasoningEffort) arguments[9], (ModelTokenEncoding) arguments[10]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelConfig)) return false;
        ModelConfig that = (ModelConfig) other;
        return enabled == that.enabled && java.util.Objects.equals(protocol, that.protocol) && java.util.Objects.equals(baseUri, that.baseUri) && java.util.Objects.equals(model, that.model) && java.util.Objects.equals(apiKey, that.apiKey) && contextWindowTokens == that.contextWindowTokens && maxOutputTokens == that.maxOutputTokens && java.util.Objects.equals(connectTimeout, that.connectTimeout) && java.util.Objects.equals(requestTimeout, that.requestTimeout) && java.util.Objects.equals(reasoningEffort, that.reasoningEffort) && java.util.Objects.equals(tokenEncoding, that.tokenEncoding) && java.util.Objects.equals(imageCapability, that.imageCapability);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + java.util.Objects.hashCode(protocol);
        hash = 31 * hash + java.util.Objects.hashCode(baseUri);
        hash = 31 * hash + java.util.Objects.hashCode(model);
        hash = 31 * hash + java.util.Objects.hashCode(apiKey);
        hash = 31 * hash + Integer.hashCode(contextWindowTokens);
        hash = 31 * hash + Integer.hashCode(maxOutputTokens);
        hash = 31 * hash + java.util.Objects.hashCode(connectTimeout);
        hash = 31 * hash + java.util.Objects.hashCode(requestTimeout);
        hash = 31 * hash + java.util.Objects.hashCode(reasoningEffort);
        hash = 31 * hash + java.util.Objects.hashCode(tokenEncoding);
        hash = 31 * hash + java.util.Objects.hashCode(imageCapability);
        return hash;
    }
    @Override public String toString() { return "ModelConfig[enabled=" + enabled + ", protocol=" + protocol + ", baseUri=" + baseUri + ", model=" + model + ", apiKey=" + apiKey + ", contextWindowTokens=" + contextWindowTokens + ", maxOutputTokens=" + maxOutputTokens + ", connectTimeout=" + connectTimeout + ", requestTimeout=" + requestTimeout + ", reasoningEffort=" + reasoningEffort + ", tokenEncoding=" + tokenEncoding + ", imageCapability=" + imageCapability + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelConfig> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelConfig.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelConfig>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelConfig.class, "enabled", ModelConfig::enabled), new dev.openallay.value.ValueSchema.Component<>(ModelConfig.class, "protocol", ModelConfig::protocol), new dev.openallay.value.ValueSchema.Component<>(ModelConfig.class, "baseUri", ModelConfig::baseUri), new dev.openallay.value.ValueSchema.Component<>(ModelConfig.class, "model", ModelConfig::model), new dev.openallay.value.ValueSchema.Component<>(ModelConfig.class, "apiKey", ModelConfig::apiKey), new dev.openallay.value.ValueSchema.Component<>(ModelConfig.class, "contextWindowTokens", ModelConfig::contextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(ModelConfig.class, "maxOutputTokens", ModelConfig::maxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(ModelConfig.class, "connectTimeout", ModelConfig::connectTimeout), new dev.openallay.value.ValueSchema.Component<>(ModelConfig.class, "requestTimeout", ModelConfig::requestTimeout), new dev.openallay.value.ValueSchema.Component<>(ModelConfig.class, "reasoningEffort", ModelConfig::reasoningEffort), new dev.openallay.value.ValueSchema.Component<>(ModelConfig.class, "tokenEncoding", ModelConfig::tokenEncoding), new dev.openallay.value.ValueSchema.Component<>(ModelConfig.class, "imageCapability", ModelConfig::imageCapability)), arguments -> new ModelConfig((Boolean) arguments[0], (ModelProtocol) arguments[1], (URI) arguments[2], (String) arguments[3], (SecretValue) arguments[4], (Integer) arguments[5], (Integer) arguments[6], (Duration) arguments[7], (Duration) arguments[8], (ModelReasoningEffort) arguments[9], (ModelTokenEncoding) arguments[10], (ModelImageCapabilityResolution) arguments[11]));
        }
    }
}
