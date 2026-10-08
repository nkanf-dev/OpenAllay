package dev.openallay.client.gui.settings;

import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.ModelReasoningEffort;
import dev.openallay.model.tokenizer.ModelTokenEncoding;
import dev.openallay.model.image.ImageInputCapability;
import dev.openallay.model.catalog.ModelCatalogRequest;
import dev.openallay.tool.ToolResult;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Immutable editable model profile fields with boundary validation. */
@dev.openallay.value.ValueType(ModelProfileDraft.ValueSchemaProvider.class)
public final class ModelProfileDraft {
    private final String id;
    private final String displayName;
    private final boolean enabled;
    private final ModelProtocol protocol;
    private final String baseUrl;
    private final String model;
    private final String credentialRef;
    private final String contextWindowTokens;
    private final String maxOutputTokens;
    private final String connectTimeoutSeconds;
    private final String requestTimeoutSeconds;
    private final ModelProfileDefinition.MetadataProvenance metadata;
    private final String automaticContextWindowTokens;
    private final String automaticMaxOutputTokens;
    private final ModelReasoningEffort reasoningEffort;
    private final ModelTokenEncoding tokenEncoding;
    private final ImageInputCapability imageInputCapabilityOverride;
    public ModelProfileDraft(String id, String displayName, boolean enabled, ModelProtocol protocol, String baseUrl, String model, String credentialRef, String contextWindowTokens, String maxOutputTokens, String connectTimeoutSeconds, String requestTimeoutSeconds, ModelProfileDefinition.MetadataProvenance metadata, String automaticContextWindowTokens, String automaticMaxOutputTokens, ModelReasoningEffort reasoningEffort, ModelTokenEncoding tokenEncoding, ImageInputCapability imageInputCapabilityOverride) {

        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(reasoningEffort, "reasoningEffort");
        Objects.requireNonNull(tokenEncoding, "tokenEncoding");

        this.id = id;
        this.displayName = displayName;
        this.enabled = enabled;
        this.protocol = protocol;
        this.baseUrl = baseUrl;
        this.model = model;
        this.credentialRef = credentialRef;
        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.metadata = metadata;
        this.automaticContextWindowTokens = automaticContextWindowTokens;
        this.automaticMaxOutputTokens = automaticMaxOutputTokens;
        this.reasoningEffort = reasoningEffort;
        this.tokenEncoding = tokenEncoding;
        this.imageInputCapabilityOverride = imageInputCapabilityOverride;
    }
    public String id() { return id; }
    public String displayName() { return displayName; }
    public boolean enabled() { return enabled; }
    public ModelProtocol protocol() { return protocol; }
    public String baseUrl() { return baseUrl; }
    public String model() { return model; }
    public String credentialRef() { return credentialRef; }
    public String contextWindowTokens() { return contextWindowTokens; }
    public String maxOutputTokens() { return maxOutputTokens; }
    public String connectTimeoutSeconds() { return connectTimeoutSeconds; }
    public String requestTimeoutSeconds() { return requestTimeoutSeconds; }
    public ModelProfileDefinition.MetadataProvenance metadata() { return metadata; }
    public String automaticContextWindowTokens() { return automaticContextWindowTokens; }
    public String automaticMaxOutputTokens() { return automaticMaxOutputTokens; }
    public ModelReasoningEffort reasoningEffort() { return reasoningEffort; }
    public ModelTokenEncoding tokenEncoding() { return tokenEncoding; }
    public ImageInputCapability imageInputCapabilityOverride() { return imageInputCapabilityOverride; }
public ModelProfileDraft(
            String id, String displayName, boolean enabled, ModelProtocol protocol,
            String baseUrl, String model, String credentialRef, String contextWindowTokens,
            String maxOutputTokens, String connectTimeoutSeconds, String requestTimeoutSeconds,
            ModelProfileDefinition.MetadataProvenance metadata, String automaticContextWindowTokens,
            String automaticMaxOutputTokens, ModelReasoningEffort reasoningEffort,
            ModelTokenEncoding tokenEncoding) {
        this(id, displayName, enabled, protocol, baseUrl, model, credentialRef,
                contextWindowTokens, maxOutputTokens, connectTimeoutSeconds, requestTimeoutSeconds,
                metadata, automaticContextWindowTokens, automaticMaxOutputTokens, reasoningEffort,
                tokenEncoding, null);
    }
public ModelProfileDraft(
            String id, String displayName, boolean enabled, ModelProtocol protocol,
            String baseUrl, String model, String credentialRef, String contextWindowTokens,
            String maxOutputTokens, String connectTimeoutSeconds, String requestTimeoutSeconds,
            ModelProfileDefinition.MetadataProvenance metadata, String automaticContextWindowTokens,
            String automaticMaxOutputTokens, ModelReasoningEffort reasoningEffort) {
        this(id, displayName, enabled, protocol, baseUrl, model, credentialRef,
                contextWindowTokens, maxOutputTokens, connectTimeoutSeconds, requestTimeoutSeconds,
                metadata, automaticContextWindowTokens, automaticMaxOutputTokens, reasoningEffort,
                ModelTokenEncoding.AUTO);
    }
public ModelProfileDraft(
            String id, String displayName, boolean enabled, ModelProtocol protocol,
            String baseUrl, String model, String credentialRef, String contextWindowTokens,
            String maxOutputTokens, String connectTimeoutSeconds, String requestTimeoutSeconds,
            ModelProfileDefinition.MetadataProvenance metadata, String automaticContextWindowTokens,
            String automaticMaxOutputTokens) {
        this(id, displayName, enabled, protocol, baseUrl, model, credentialRef,
                contextWindowTokens, maxOutputTokens, connectTimeoutSeconds, requestTimeoutSeconds,
                metadata, automaticContextWindowTokens, automaticMaxOutputTokens,
                ModelReasoningEffort.AUTO);
    }
public ModelProfileDraft(
            String id, String displayName, boolean enabled, ModelProtocol protocol,
            String baseUrl, String model, String credentialRef, String contextWindowTokens,
            String maxOutputTokens, String connectTimeoutSeconds, String requestTimeoutSeconds,
            ModelProfileDefinition.MetadataProvenance metadata, String automaticContextWindowTokens) {
        this(id, displayName, enabled, protocol, baseUrl, model, credentialRef,
                contextWindowTokens, maxOutputTokens, connectTimeoutSeconds, requestTimeoutSeconds,
                metadata, automaticContextWindowTokens, null);
    }
public ModelProfileDraft(
            String id, String displayName, boolean enabled, ModelProtocol protocol,
            String baseUrl, String model, String credentialRef, String contextWindowTokens,
            String maxOutputTokens, String connectTimeoutSeconds, String requestTimeoutSeconds,
            ModelProfileDefinition.MetadataProvenance metadata) {
        this(id, displayName, enabled, protocol, baseUrl, model, credentialRef,
                contextWindowTokens, maxOutputTokens, connectTimeoutSeconds, requestTimeoutSeconds,
                metadata, null, null);
    }
public ModelProfileDraft(
            String id,
            String displayName,
            boolean enabled,
            ModelProtocol protocol,
            String baseUrl,
            String model,
            String credentialRef,
            String contextWindowTokens,
            String maxOutputTokens,
            String connectTimeoutSeconds,
            String requestTimeoutSeconds) {
        this(
                id,
                displayName,
                enabled,
                protocol,
                baseUrl,
                model,
                credentialRef,
                contextWindowTokens,
                maxOutputTokens,
                connectTimeoutSeconds,
                requestTimeoutSeconds,
                null);
    }
public static ModelProfileDraft from(ModelProfileDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        return new ModelProfileDraft(
                definition.id(),
                definition.displayName(),
                definition.enabled(),
                definition.protocol(),
                definition.baseUri().toString(),
                definition.model(),
                definition.credentialRef(),
                definition.contextWindowTokens() == null
                        ? ""
                        : Integer.toString(definition.contextWindowTokens()),
                definition.maxOutputTokens() == null
                        ? ""
                        : Integer.toString(definition.maxOutputTokens()),
                Long.toString((definition.connectTimeout()).getSeconds()),
                Long.toString((definition.requestTimeout()).getSeconds()),
                definition.metadata(), null, null, definition.reasoningEffort(), definition.tokenEncoding(),
                definition.imageInputCapabilityOverride())
                .autoFill(BuiltinModelCatalog.bundled().catalog());
    }
public static ModelProfileDraft create(String id) {
        return new ModelProfileDraft(
                id,
                id,
                true,
                ModelProtocol.OPENAI_CHAT,
                "https://",
                "",
                "env:OPENALLAY_API_KEY",
                "",
                "",
                "30",
                "300",
                null);
    }
public ModelProfileDraft withModel(String replacement) {
        return withModel(replacement, BuiltinModelCatalog.bundled().catalog());
    }
public ModelProfileDraft withModel(String replacement, BuiltinModelCatalog catalog) {
        boolean changed = !Objects.equals(model, replacement);
        return new ModelProfileDraft(
                id, displayName, enabled, protocol, baseUrl, replacement, credentialRef,
                changed && automaticContextWindowTokens != null ? "" : contextWindowTokens,
                changed && automaticMaxOutputTokens != null ? "" : maxOutputTokens,
                connectTimeoutSeconds, requestTimeoutSeconds,
                changed ? null : metadata,
                changed ? null : automaticContextWindowTokens,
                changed ? null : automaticMaxOutputTokens, reasoningEffort, tokenEncoding,
                imageInputCapabilityOverride).autoFill(catalog);
    }
public ModelProfileDraft withContextWindow(String value) {
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, value, maxOutputTokens, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, null, automaticMaxOutputTokens, reasoningEffort,
                tokenEncoding, imageInputCapabilityOverride);
    }
public ModelProfileDraft withMaxOutput(String value) {
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, contextWindowTokens, value, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, automaticContextWindowTokens, null, reasoningEffort,
                tokenEncoding, imageInputCapabilityOverride);
    }
public ModelProfileDraft autoFill(BuiltinModelCatalog catalog) {
        java.util.Optional<dev.openallay.model.metadata.BuiltinModelMatcher.Match> matched = catalog.match(model);
        Integer context = matched.map(match -> match.entry().contextWindowTokens()).orElse(null);
        Integer output = matched.map(match -> match.entry().maxOutputTokens()).orElse(null);
        return withAutomaticContext(context).withAutomaticOutput(output);
    }
public ModelProfileDraft withAutomaticContext(Integer value) {
        if (contextWindowTokens != null && !dev.openallay.util.Java8Strings.isBlank(contextWindowTokens)
                && automaticContextWindowTokens == null) return this;
        String text = value == null ? "" : Integer.toString(value);
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, text, maxOutputTokens, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, value == null ? null : text,
                automaticMaxOutputTokens, reasoningEffort, tokenEncoding, imageInputCapabilityOverride);
    }
public ModelProfileDraft withAutomaticOutput(Integer value) {
        if (maxOutputTokens != null && !dev.openallay.util.Java8Strings.isBlank(maxOutputTokens)
                && automaticMaxOutputTokens == null) return this;
        String text = value == null ? "" : Integer.toString(value);
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, contextWindowTokens, text, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, automaticContextWindowTokens,
                value == null ? null : text, reasoningEffort, tokenEncoding, imageInputCapabilityOverride);
    }
public ModelProfileDraft withReasoningEffort(ModelReasoningEffort value) {
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, contextWindowTokens, maxOutputTokens, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, automaticContextWindowTokens,
                automaticMaxOutputTokens, value, tokenEncoding, imageInputCapabilityOverride);
    }
public ModelProfileDraft withTokenEncoding(ModelTokenEncoding value) {
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, contextWindowTokens, maxOutputTokens, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, automaticContextWindowTokens,
                automaticMaxOutputTokens, reasoningEffort, value, imageInputCapabilityOverride);
    }
public ModelProfileDraft withImageInputCapabilityOverride(ImageInputCapability value) {
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, contextWindowTokens, maxOutputTokens, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, automaticContextWindowTokens,
                automaticMaxOutputTokens, reasoningEffort, tokenEncoding, value);
    }
public boolean dirtyComparedTo(ModelProfileDefinition definition) {
        ToolResult<ModelProfileDefinition> validated = validate();
        final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.model.config.ModelProfileDefinition> value; ToolResult.Success<ModelProfileDefinition> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
return !((($oaPattern0_holder.value = validated) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern0_holder.bound = (ToolResult.Success<ModelProfileDefinition>) $oaPattern0_holder.value) != null)))
                || !$oaPattern0_holder.bound.value().equals(definition);
    }
public ToolResult<ModelProfileDefinition> validate() {
        try {
            Integer contextWindow = contextWindowTokens == null || dev.openallay.util.Java8Strings.isBlank(contextWindowTokens)
                    || Objects.equals(contextWindowTokens, automaticContextWindowTokens)
                    ? null
                    : Integer.valueOf(contextWindowTokens.trim());
            ModelProfileDefinition definition = new ModelProfileDefinition(
                    id == null ? null : id.trim(),
                    displayName == null ? null : displayName.trim(),
                    enabled,
                    protocol,
                    URI.create(baseUrl == null ? "" : baseUrl.trim()),
                    model == null ? null : model.trim(),
                    credentialRef == null ? null : credentialRef.trim(),
                    contextWindow,
                    maxOutputTokens == null || dev.openallay.util.Java8Strings.isBlank(maxOutputTokens)
                            || Objects.equals(maxOutputTokens, automaticMaxOutputTokens)
                            ? null : Integer.valueOf(maxOutputTokens.trim()),
                    Duration.ofSeconds(Long.parseLong(
                            connectTimeoutSeconds == null ? "" : connectTimeoutSeconds.trim())),
                    Duration.ofSeconds(Long.parseLong(
                            requestTimeoutSeconds == null ? "" : requestTimeoutSeconds.trim())),
                    metadata,
                    reasoningEffort, tokenEncoding, imageInputCapabilityOverride);
            return new ToolResult.Success<>(definition);
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>(
                    "invalid_model_profile", "Review the model profile fields");
        }
    }
public ToolResult<ModelCatalogRequest> catalogRequest() {
        try {
            return new ToolResult.Success<>(new ModelCatalogRequest(
                    id == null ? null : id.trim(),
                    protocol,
                    URI.create(baseUrl == null ? "" : baseUrl.trim()),
                    credentialRef == null ? null : credentialRef.trim(),
                    Duration.ofSeconds(Long.parseLong(
                            connectTimeoutSeconds == null ? "" : connectTimeoutSeconds.trim())),
                    Duration.ofSeconds(Long.parseLong(
                            requestTimeoutSeconds == null ? "" : requestTimeoutSeconds.trim()))));
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>(
                    "invalid_model_catalog_request",
                    "Review the profile ID, base URL, and timeout fields");
        }
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelProfileDraft)) return false;
        ModelProfileDraft that = (ModelProfileDraft) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(displayName, that.displayName) && enabled == that.enabled && java.util.Objects.equals(protocol, that.protocol) && java.util.Objects.equals(baseUrl, that.baseUrl) && java.util.Objects.equals(model, that.model) && java.util.Objects.equals(credentialRef, that.credentialRef) && java.util.Objects.equals(contextWindowTokens, that.contextWindowTokens) && java.util.Objects.equals(maxOutputTokens, that.maxOutputTokens) && java.util.Objects.equals(connectTimeoutSeconds, that.connectTimeoutSeconds) && java.util.Objects.equals(requestTimeoutSeconds, that.requestTimeoutSeconds) && java.util.Objects.equals(metadata, that.metadata) && java.util.Objects.equals(automaticContextWindowTokens, that.automaticContextWindowTokens) && java.util.Objects.equals(automaticMaxOutputTokens, that.automaticMaxOutputTokens) && java.util.Objects.equals(reasoningEffort, that.reasoningEffort) && java.util.Objects.equals(tokenEncoding, that.tokenEncoding) && java.util.Objects.equals(imageInputCapabilityOverride, that.imageInputCapabilityOverride);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(displayName);
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + java.util.Objects.hashCode(protocol);
        hash = 31 * hash + java.util.Objects.hashCode(baseUrl);
        hash = 31 * hash + java.util.Objects.hashCode(model);
        hash = 31 * hash + java.util.Objects.hashCode(credentialRef);
        hash = 31 * hash + java.util.Objects.hashCode(contextWindowTokens);
        hash = 31 * hash + java.util.Objects.hashCode(maxOutputTokens);
        hash = 31 * hash + java.util.Objects.hashCode(connectTimeoutSeconds);
        hash = 31 * hash + java.util.Objects.hashCode(requestTimeoutSeconds);
        hash = 31 * hash + java.util.Objects.hashCode(metadata);
        hash = 31 * hash + java.util.Objects.hashCode(automaticContextWindowTokens);
        hash = 31 * hash + java.util.Objects.hashCode(automaticMaxOutputTokens);
        hash = 31 * hash + java.util.Objects.hashCode(reasoningEffort);
        hash = 31 * hash + java.util.Objects.hashCode(tokenEncoding);
        hash = 31 * hash + java.util.Objects.hashCode(imageInputCapabilityOverride);
        return hash;
    }
    @Override public String toString() { return "ModelProfileDraft[id=" + id + ", displayName=" + displayName + ", enabled=" + enabled + ", protocol=" + protocol + ", baseUrl=" + baseUrl + ", model=" + model + ", credentialRef=" + credentialRef + ", contextWindowTokens=" + contextWindowTokens + ", maxOutputTokens=" + maxOutputTokens + ", connectTimeoutSeconds=" + connectTimeoutSeconds + ", requestTimeoutSeconds=" + requestTimeoutSeconds + ", metadata=" + metadata + ", automaticContextWindowTokens=" + automaticContextWindowTokens + ", automaticMaxOutputTokens=" + automaticMaxOutputTokens + ", reasoningEffort=" + reasoningEffort + ", tokenEncoding=" + tokenEncoding + ", imageInputCapabilityOverride=" + imageInputCapabilityOverride + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelProfileDraft> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelProfileDraft.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelProfileDraft>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "id", ModelProfileDraft::id), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "displayName", ModelProfileDraft::displayName), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "enabled", ModelProfileDraft::enabled), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "protocol", ModelProfileDraft::protocol), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "baseUrl", ModelProfileDraft::baseUrl), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "model", ModelProfileDraft::model), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "credentialRef", ModelProfileDraft::credentialRef), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "contextWindowTokens", ModelProfileDraft::contextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "maxOutputTokens", ModelProfileDraft::maxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "connectTimeoutSeconds", ModelProfileDraft::connectTimeoutSeconds), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "requestTimeoutSeconds", ModelProfileDraft::requestTimeoutSeconds), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "metadata", ModelProfileDraft::metadata), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "automaticContextWindowTokens", ModelProfileDraft::automaticContextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "automaticMaxOutputTokens", ModelProfileDraft::automaticMaxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "reasoningEffort", ModelProfileDraft::reasoningEffort), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "tokenEncoding", ModelProfileDraft::tokenEncoding), new dev.openallay.value.ValueSchema.Component<>(ModelProfileDraft.class, "imageInputCapabilityOverride", ModelProfileDraft::imageInputCapabilityOverride)), arguments -> new ModelProfileDraft((String) arguments[0], (String) arguments[1], (Boolean) arguments[2], (ModelProtocol) arguments[3], (String) arguments[4], (String) arguments[5], (String) arguments[6], (String) arguments[7], (String) arguments[8], (String) arguments[9], (String) arguments[10], (ModelProfileDefinition.MetadataProvenance) arguments[11], (String) arguments[12], (String) arguments[13], (ModelReasoningEffort) arguments[14], (ModelTokenEncoding) arguments[15], (ImageInputCapability) arguments[16]));
        }
    }
}
