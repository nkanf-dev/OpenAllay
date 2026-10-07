package dev.openallay.model.config;

import dev.openallay.guide.GuideFailure;
import dev.openallay.model.metadata.ModelImageCapabilityResolution;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import java.net.URI;
import java.util.Objects;

/** One retained profile definition and its optional usable runtime config. */
@dev.openallay.value.ValueType(ResolvedModelProfile.ValueSchemaProvider.class)
public final class ResolvedModelProfile {
    private final ModelProfileDefinition definition;
    private final ModelConfig runtimeConfig;
    private final GuideFailure failure;
    private final String canonicalModelId;
    private final ModelImageCapabilityResolution imageCapability;
    public ResolvedModelProfile(ModelProfileDefinition definition, ModelConfig runtimeConfig, GuideFailure failure, String canonicalModelId, ModelImageCapabilityResolution imageCapability) {

        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(imageCapability, "imageCapability");
        if ((runtimeConfig == null) == (failure == null)) {
            throw new IllegalArgumentException(
                    "resolved profile must contain exactly one runtime or failure");
        }
        if (canonicalModelId == null || dev.openallay.util.Java8Strings.isBlank(canonicalModelId)) {
            throw new IllegalArgumentException("canonical model ID is required");
        }

        this.definition = definition;
        this.runtimeConfig = runtimeConfig;
        this.failure = failure;
        this.canonicalModelId = canonicalModelId;
        this.imageCapability = imageCapability;
    }
    public ModelProfileDefinition definition() { return definition; }
    public ModelConfig runtimeConfig() { return runtimeConfig; }
    public GuideFailure failure() { return failure; }
    public String canonicalModelId() { return canonicalModelId; }
    public ModelImageCapabilityResolution imageCapability() { return imageCapability; }
public ResolvedModelProfile(
            ModelProfileDefinition definition, ModelConfig runtimeConfig,
            GuideFailure failure, String canonicalModelId) {
        this(definition, runtimeConfig, failure, canonicalModelId,
                runtimeConfig == null
                        ? ModelImageCapabilityResolution.resolve(definition.baseUri(), definition.model(),
                                definition.imageInputCapabilityOverride(), dev.openallay.util.Java8Collections.mapOf(),
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
@dev.openallay.value.ValueType(DiagnosticView.ValueSchemaProvider.class)
public static final class DiagnosticView {
    private final String id;
    private final String displayName;
    private final boolean enabled;
    private final boolean available;
    private final ModelProtocol protocol;
    private final URI baseUri;
    private final String model;
    private final String credentialRef;
    private final boolean apiKeyPresent;
    private final Integer contextWindowTokens;
    private final Integer maxOutputTokens;
    private final ModelReasoningEffort reasoningEffort;
    private final dev.openallay.model.tokenizer.ModelTokenEncoding tokenEncoding;
    private final GuideFailure failure;
    private final ModelImageCapabilityResolution imageCapability;
    public DiagnosticView(String id, String displayName, boolean enabled, boolean available, ModelProtocol protocol, URI baseUri, String model, String credentialRef, boolean apiKeyPresent, Integer contextWindowTokens, Integer maxOutputTokens, ModelReasoningEffort reasoningEffort, dev.openallay.model.tokenizer.ModelTokenEncoding tokenEncoding, GuideFailure failure, ModelImageCapabilityResolution imageCapability) {
        this.id = id;
        this.displayName = displayName;
        this.enabled = enabled;
        this.available = available;
        this.protocol = protocol;
        this.baseUri = baseUri;
        this.model = model;
        this.credentialRef = credentialRef;
        this.apiKeyPresent = apiKeyPresent;
        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.reasoningEffort = reasoningEffort;
        this.tokenEncoding = tokenEncoding;
        this.failure = failure;
        this.imageCapability = imageCapability;
    }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public boolean enabled() { return enabled; }
    public boolean available() { return available; }
    public ModelProtocol protocol() { return protocol; }
    public URI baseUri() { return baseUri; }
    public String model() { return model; }
    public String credentialRef() { return credentialRef; }
    public boolean apiKeyPresent() { return apiKeyPresent; }
    public Integer contextWindowTokens() { return contextWindowTokens; }
    public Integer maxOutputTokens() { return maxOutputTokens; }
    public ModelReasoningEffort reasoningEffort() { return reasoningEffort; }
    public dev.openallay.model.tokenizer.ModelTokenEncoding tokenEncoding() { return tokenEncoding; }
    public GuideFailure failure() { return failure; }
    public ModelImageCapabilityResolution imageCapability() { return imageCapability; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiagnosticView)) return false;
        DiagnosticView that = (DiagnosticView) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(displayName, that.displayName) && enabled == that.enabled && available == that.available && java.util.Objects.equals(protocol, that.protocol) && java.util.Objects.equals(baseUri, that.baseUri) && java.util.Objects.equals(model, that.model) && java.util.Objects.equals(credentialRef, that.credentialRef) && apiKeyPresent == that.apiKeyPresent && java.util.Objects.equals(contextWindowTokens, that.contextWindowTokens) && java.util.Objects.equals(maxOutputTokens, that.maxOutputTokens) && java.util.Objects.equals(reasoningEffort, that.reasoningEffort) && java.util.Objects.equals(tokenEncoding, that.tokenEncoding) && java.util.Objects.equals(failure, that.failure) && java.util.Objects.equals(imageCapability, that.imageCapability);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + Boolean.hashCode(available);
        hash = 31 * hash + java.util.Objects.hashCode(protocol);
        hash = 31 * hash + java.util.Objects.hashCode(baseUri);
        hash = 31 * hash + java.util.Objects.hashCode(model);
        hash = 31 * hash + java.util.Objects.hashCode(credentialRef);
        hash = 31 * hash + Boolean.hashCode(apiKeyPresent);
        hash = 31 * hash + java.util.Objects.hashCode(contextWindowTokens);
        hash = 31 * hash + java.util.Objects.hashCode(maxOutputTokens);
        hash = 31 * hash + java.util.Objects.hashCode(reasoningEffort);
        hash = 31 * hash + java.util.Objects.hashCode(tokenEncoding);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        hash = 31 * hash + java.util.Objects.hashCode(imageCapability);
        return hash;
    }
    @Override public String toString() { return "DiagnosticView[id=" + id + ", displayName=" + displayName + ", enabled=" + enabled + ", available=" + available + ", protocol=" + protocol + ", baseUri=" + baseUri + ", model=" + model + ", credentialRef=" + credentialRef + ", apiKeyPresent=" + apiKeyPresent + ", contextWindowTokens=" + contextWindowTokens + ", maxOutputTokens=" + maxOutputTokens + ", reasoningEffort=" + reasoningEffort + ", tokenEncoding=" + tokenEncoding + ", failure=" + failure + ", imageCapability=" + imageCapability + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DiagnosticView> schema() {
            return new dev.openallay.value.ValueSchema<>(DiagnosticView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DiagnosticView>>asList(new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "id", DiagnosticView::id), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "displayName", DiagnosticView::displayName), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "enabled", DiagnosticView::enabled), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "available", DiagnosticView::available), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "protocol", DiagnosticView::protocol), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "baseUri", DiagnosticView::baseUri), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "model", DiagnosticView::model), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "credentialRef", DiagnosticView::credentialRef), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "apiKeyPresent", DiagnosticView::apiKeyPresent), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "contextWindowTokens", DiagnosticView::contextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "maxOutputTokens", DiagnosticView::maxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "reasoningEffort", DiagnosticView::reasoningEffort), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "tokenEncoding", DiagnosticView::tokenEncoding), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "failure", DiagnosticView::failure), new dev.openallay.value.ValueSchema.Component<>(DiagnosticView.class, "imageCapability", DiagnosticView::imageCapability)), arguments -> new DiagnosticView((String) arguments[0], (String) arguments[1], (Boolean) arguments[2], (Boolean) arguments[3], (ModelProtocol) arguments[4], (URI) arguments[5], (String) arguments[6], (String) arguments[7], (Boolean) arguments[8], (Integer) arguments[9], (Integer) arguments[10], (ModelReasoningEffort) arguments[11], (dev.openallay.model.tokenizer.ModelTokenEncoding) arguments[12], (GuideFailure) arguments[13], (ModelImageCapabilityResolution) arguments[14]));
        }
    }
}
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ResolvedModelProfile)) return false;
        ResolvedModelProfile that = (ResolvedModelProfile) other;
        return java.util.Objects.equals(definition, that.definition) && java.util.Objects.equals(runtimeConfig, that.runtimeConfig) && java.util.Objects.equals(failure, that.failure) && java.util.Objects.equals(canonicalModelId, that.canonicalModelId) && java.util.Objects.equals(imageCapability, that.imageCapability);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(definition);
        hash = 31 * hash + java.util.Objects.hashCode(runtimeConfig);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        hash = 31 * hash + java.util.Objects.hashCode(canonicalModelId);
        hash = 31 * hash + java.util.Objects.hashCode(imageCapability);
        return hash;
    }
    @Override public String toString() { return "ResolvedModelProfile[definition=" + definition + ", runtimeConfig=" + runtimeConfig + ", failure=" + failure + ", canonicalModelId=" + canonicalModelId + ", imageCapability=" + imageCapability + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ResolvedModelProfile> schema() {
            return new dev.openallay.value.ValueSchema<>(ResolvedModelProfile.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ResolvedModelProfile>>asList(new dev.openallay.value.ValueSchema.Component<>(ResolvedModelProfile.class, "definition", ResolvedModelProfile::definition), new dev.openallay.value.ValueSchema.Component<>(ResolvedModelProfile.class, "runtimeConfig", ResolvedModelProfile::runtimeConfig), new dev.openallay.value.ValueSchema.Component<>(ResolvedModelProfile.class, "failure", ResolvedModelProfile::failure), new dev.openallay.value.ValueSchema.Component<>(ResolvedModelProfile.class, "canonicalModelId", ResolvedModelProfile::canonicalModelId), new dev.openallay.value.ValueSchema.Component<>(ResolvedModelProfile.class, "imageCapability", ResolvedModelProfile::imageCapability)), arguments -> new ResolvedModelProfile((ModelProfileDefinition) arguments[0], (ModelConfig) arguments[1], (GuideFailure) arguments[2], (String) arguments[3], (ModelImageCapabilityResolution) arguments[4]));
        }
    }
}
