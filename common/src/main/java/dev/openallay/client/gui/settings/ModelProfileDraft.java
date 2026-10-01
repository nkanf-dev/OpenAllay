package dev.openallay.client.gui.settings;

import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.ModelReasoningEffort;
import dev.openallay.model.tokenizer.ModelTokenEncoding;
import dev.openallay.model.catalog.ModelCatalogRequest;
import dev.openallay.tool.ToolResult;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/** Immutable editable model profile fields with boundary validation. */
public record ModelProfileDraft(
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
        String requestTimeoutSeconds,
        ModelProfileDefinition.MetadataProvenance metadata,
        String automaticContextWindowTokens,
        String automaticMaxOutputTokens,
        ModelReasoningEffort reasoningEffort,
        ModelTokenEncoding tokenEncoding) {
    public ModelProfileDraft {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(reasoningEffort, "reasoningEffort");
        Objects.requireNonNull(tokenEncoding, "tokenEncoding");
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
                Long.toString(definition.connectTimeout().toSeconds()),
                Long.toString(definition.requestTimeout().toSeconds()),
                definition.metadata(), null, null, definition.reasoningEffort(), definition.tokenEncoding())
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
                changed ? null : automaticMaxOutputTokens, reasoningEffort, tokenEncoding).autoFill(catalog);
    }

    /** An actual player edit adopts a manual value; save alone does not. */
    public ModelProfileDraft withContextWindow(String value) {
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, value, maxOutputTokens, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, null, automaticMaxOutputTokens, reasoningEffort, tokenEncoding);
    }

    /** An actual output edit adopts a manual value. Clearing returns ownership to automatic. */
    public ModelProfileDraft withMaxOutput(String value) {
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, contextWindowTokens, value, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, automaticContextWindowTokens, null, reasoningEffort, tokenEncoding);
    }

    public ModelProfileDraft autoFill(BuiltinModelCatalog catalog) {
        var matched = catalog.match(model);
        Integer context = matched.map(match -> match.entry().contextWindowTokens()).orElse(null);
        Integer output = matched.map(match -> match.entry().maxOutputTokens()).orElse(null);
        return withAutomaticContext(context).withAutomaticOutput(output);
    }

    /** Trusted effective metadata can replace an automatic display value, never a manual edit. */
    public ModelProfileDraft withAutomaticContext(Integer value) {
        if (contextWindowTokens != null && !contextWindowTokens.isBlank()
                && automaticContextWindowTokens == null) return this;
        String text = value == null ? "" : Integer.toString(value);
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, text, maxOutputTokens, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, value == null ? null : text,
                automaticMaxOutputTokens, reasoningEffort, tokenEncoding);
    }

    /** Metadata refresh can replace an automatic display value, never an explicit player edit. */
    public ModelProfileDraft withAutomaticOutput(Integer value) {
        if (maxOutputTokens != null && !maxOutputTokens.isBlank()
                && automaticMaxOutputTokens == null) return this;
        String text = value == null ? "" : Integer.toString(value);
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, contextWindowTokens, text, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, automaticContextWindowTokens,
                value == null ? null : text, reasoningEffort, tokenEncoding);
    }

    public ModelProfileDraft withReasoningEffort(ModelReasoningEffort value) {
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, contextWindowTokens, maxOutputTokens, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, automaticContextWindowTokens,
                automaticMaxOutputTokens, value, tokenEncoding);
    }

    public ModelProfileDraft withTokenEncoding(ModelTokenEncoding value) {
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, contextWindowTokens, maxOutputTokens, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, automaticContextWindowTokens,
                automaticMaxOutputTokens, reasoningEffort, value);
    }

    public boolean dirtyComparedTo(ModelProfileDefinition definition) {
        ToolResult<ModelProfileDefinition> validated = validate();
        return !(validated instanceof ToolResult.Success<ModelProfileDefinition> success)
                || !success.value().equals(definition);
    }

    public ToolResult<ModelProfileDefinition> validate() {
        try {
            Integer contextWindow = contextWindowTokens == null || contextWindowTokens.isBlank()
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
                    maxOutputTokens == null || maxOutputTokens.isBlank()
                            || Objects.equals(maxOutputTokens, automaticMaxOutputTokens)
                            ? null : Integer.valueOf(maxOutputTokens.trim()),
                    Duration.ofSeconds(Long.parseLong(
                            connectTimeoutSeconds == null ? "" : connectTimeoutSeconds.trim())),
                    Duration.ofSeconds(Long.parseLong(
                            requestTimeoutSeconds == null ? "" : requestTimeoutSeconds.trim())),
                    metadata,
                    reasoningEffort, tokenEncoding);
            return new ToolResult.Success<>(definition);
        } catch (RuntimeException failure) {
            return new ToolResult.Failure<>(
                    "invalid_model_profile", "Review the model profile fields");
        }
    }

    /** Validates only the fields needed for an authenticated non-inference model listing. */
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
}
