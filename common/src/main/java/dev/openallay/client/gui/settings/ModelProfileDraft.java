package dev.openallay.client.gui.settings;

import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import dev.openallay.model.config.ModelProtocol;
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
        String automaticContextWindowTokens) {
    public ModelProfileDraft {
        Objects.requireNonNull(protocol, "protocol");
    }

    public ModelProfileDraft(
            String id, String displayName, boolean enabled, ModelProtocol protocol,
            String baseUrl, String model, String credentialRef, String contextWindowTokens,
            String maxOutputTokens, String connectTimeoutSeconds, String requestTimeoutSeconds,
            ModelProfileDefinition.MetadataProvenance metadata) {
        this(id, displayName, enabled, protocol, baseUrl, model, credentialRef,
                contextWindowTokens, maxOutputTokens, connectTimeoutSeconds, requestTimeoutSeconds,
                metadata, null);
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
                Integer.toString(definition.maxOutputTokens()),
                Long.toString(definition.connectTimeout().toSeconds()),
                Long.toString(definition.requestTimeout().toSeconds()),
                definition.metadata()).autoFill(BuiltinModelCatalog.bundled().catalog());
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
                "4096",
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
                maxOutputTokens, connectTimeoutSeconds, requestTimeoutSeconds,
                changed ? null : metadata,
                changed ? null : automaticContextWindowTokens).autoFill(catalog);
    }

    /** An actual player edit adopts a manual value; save alone does not. */
    public ModelProfileDraft withContextWindow(String value) {
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, value, maxOutputTokens, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, null);
    }

    public ModelProfileDraft autoFill(BuiltinModelCatalog catalog) {
        if (contextWindowTokens != null && !contextWindowTokens.isBlank()
                && automaticContextWindowTokens == null) return this;
        Integer context = catalog.match(model).map(match -> match.entry().contextWindowTokens())
                .orElse(null);
        return withAutomaticContext(context);
    }

    /** Trusted effective metadata can replace an automatic display value, never a manual edit. */
    public ModelProfileDraft withAutomaticContext(Integer value) {
        if (contextWindowTokens != null && !contextWindowTokens.isBlank()
                && automaticContextWindowTokens == null) return this;
        String text = value == null ? "" : Integer.toString(value);
        return new ModelProfileDraft(id, displayName, enabled, protocol, baseUrl, model,
                credentialRef, text, maxOutputTokens, connectTimeoutSeconds,
                requestTimeoutSeconds, metadata, value == null ? null : text);
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
                    Integer.parseInt(maxOutputTokens == null ? "" : maxOutputTokens.trim()),
                    Duration.ofSeconds(Long.parseLong(
                            connectTimeoutSeconds == null ? "" : connectTimeoutSeconds.trim())),
                    Duration.ofSeconds(Long.parseLong(
                            requestTimeoutSeconds == null ? "" : requestTimeoutSeconds.trim())),
                    metadata);
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
