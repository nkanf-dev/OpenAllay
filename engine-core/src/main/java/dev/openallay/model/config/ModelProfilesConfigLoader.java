package dev.openallay.model.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.guide.GuideFailure;
import dev.openallay.model.metadata.ModelMetadata;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import dev.openallay.model.metadata.ModelContextResolution;
import dev.openallay.model.metadata.ModelOutputResolution;
import dev.openallay.model.metadata.ModelImageCapabilityResolution;
import dev.openallay.model.image.ImageInputCapability;
import dev.openallay.model.metadata.OpenRouterMetadataResolver;
import dev.openallay.tool.ToolResult;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Strict named-profile loader for client model settings. */
public final class ModelProfilesConfigLoader {
    private final BuiltinModelCatalog catalog;

    public ModelProfilesConfigLoader() {
        this(BuiltinModelCatalog.bundled().catalog());
    }

    public ModelProfilesConfigLoader(BuiltinModelCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    private static final Set<String> ROOT_FIELDS =
            Set.of("defaultProfileId", "profiles");
    private static final Set<String> REQUIRED_PROFILE_FIELDS = Set.of(
            "id", "displayName", "enabled", "protocol", "baseUrl", "model",
            "credentialRef", "connectTimeoutSeconds", "requestTimeoutSeconds");
    private static final Set<String> OPTIONAL_PROFILE_FIELDS =
            Set.of("contextWindowTokens", "maxOutputTokens", "metadata", "reasoningEffort", "tokenEncoding",
                    "imageInputCapabilityOverride");
    private static final Set<String> METADATA_FIELDS =
            Set.of("source", "upstreamModelId", "capturedAt");

    public record Load(
            ModelProfilesConfig config,
            List<ResolvedModelProfile> profiles) {
        public Load {
            Objects.requireNonNull(config, "config");
            profiles = List.copyOf(profiles);
            if (profiles.size() != config.profiles().size()) {
                throw new IllegalArgumentException("every profile must have a resolution");
            }
        }
    }

    public ToolResult<Load> load(Path profilesPath, Map<String, String> environment) {
        return load(profilesPath, CredentialResolver.environment(environment), Map.of());
    }

    public ToolResult<Load> load(
            Path profilesPath,
            Map<String, String> environment,
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        return load(profilesPath, CredentialResolver.environment(environment), metadata);
    }

    public ToolResult<Load> load(
            Path profilesPath,
            CredentialResolver credentials,
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        Objects.requireNonNull(profilesPath, "profilesPath");
        Objects.requireNonNull(credentials, "credentials");
        if (!Files.exists(profilesPath)) {
            return new ToolResult.Failure<>(
                    "model_not_configured",
                    "No client model profiles configuration exists; save models.json.");
        }
        try (Reader reader = Files.newBufferedReader(profilesPath)) {
            return load(reader, credentials, metadata);
        } catch (IOException failure) {
            return invalid("Unable to read model profiles configuration");
        }
    }

    public ToolResult<Load> load(Reader reader, Map<String, String> environment) {
        return load(reader, environment, Map.of());
    }

    public ToolResult<Load> load(
            Reader reader,
            Map<String, String> environment,
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        return load(reader, CredentialResolver.environment(environment), metadata);
    }

    public ToolResult<Load> load(
            Reader reader,
            CredentialResolver credentials,
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(credentials, "credentials");
        Map<ModelMetadata.Key, ModelMetadata> metadataCopy = Map.copyOf(metadata);
        try {
            JsonElement parsed = dev.openallay.json.JsonTrees.parse(reader);
            JsonObject root = object(parsed, "Model profiles configuration");
            exactFields(root, ROOT_FIELDS, Set.of(), "model profiles configuration");
            String defaultProfileId = string(root, "defaultProfileId");
            JsonArray encodedProfiles = array(root, "profiles");
            List<ModelProfileDefinition> definitions = new ArrayList<>();
            for (JsonElement encoded : encodedProfiles) {
                definitions.add(profile(object(encoded, "model profile")));
            }
            ModelProfilesConfig config = new ModelProfilesConfig(
                    defaultProfileId, definitions);
            List<ResolvedModelProfile> resolved = config.profiles().stream()
                    .map(profile -> resolve(profile, credentials, metadataCopy))
                    .toList();
            return new ToolResult.Success<>(new Load(config, resolved));
        } catch (RuntimeException failure) {
            return invalid(message(failure));
        }
    }

    private static ModelProfileDefinition profile(JsonObject object) {
        exactFields(object, REQUIRED_PROFILE_FIELDS, OPTIONAL_PROFILE_FIELDS, "model profile");
        ModelProfileDefinition.MetadataProvenance metadata = null;
        if (object.has("metadata") && !object.get("metadata").isJsonNull()) {
            JsonObject encoded = object(object.get("metadata"), "profile metadata");
            exactFields(encoded, METADATA_FIELDS, Set.of(), "profile metadata");
            metadata = new ModelProfileDefinition.MetadataProvenance(
                    string(encoded, "source"),
                    string(encoded, "upstreamModelId"),
                    Instant.parse(string(encoded, "capturedAt")));
        }
        return new ModelProfileDefinition(
                string(object, "id"),
                string(object, "displayName"),
                bool(object, "enabled"),
                ModelProtocol.valueOf(string(object, "protocol").toUpperCase(Locale.ROOT)),
                java.net.URI.create(string(object, "baseUrl")),
                string(object, "model"),
                CredentialReference.parse(string(object, "credentialRef")).encoded(),
                optionalInteger(object, "contextWindowTokens"),
                optionalInteger(object, "maxOutputTokens"),
                Duration.ofSeconds(integer(object, "connectTimeoutSeconds")),
                Duration.ofSeconds(integer(object, "requestTimeoutSeconds")),
                metadata,
                object.has("reasoningEffort")
                        ? ModelReasoningEffort.parse(string(object, "reasoningEffort"))
                        : ModelReasoningEffort.AUTO,
                object.has("tokenEncoding")
                        ? dev.openallay.model.tokenizer.ModelTokenEncoding.parse(string(object, "tokenEncoding"))
                        : dev.openallay.model.tokenizer.ModelTokenEncoding.AUTO,
                !object.has("imageInputCapabilityOverride") || object.get("imageInputCapabilityOverride").isJsonNull()
                        ? null : ImageInputCapability.parse(string(object, "imageInputCapabilityOverride")));
    }

    private ResolvedModelProfile resolve(
            ModelProfileDefinition definition,
            CredentialResolver credentials,
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        ModelImageCapabilityResolution imageCapability = ModelImageCapabilityResolution.resolve(
                definition.baseUri(), definition.model(), definition.imageInputCapabilityOverride(),
                metadata, catalog);
        if (!definition.enabled()) {
            return failed(definition, imageCapability, "model_disabled", "This model profile is disabled");
        }
        ModelMetadata discovered = trustedMetadata(definition, metadata);
        Integer contextWindow = ModelContextResolution.resolve(definition.baseUri(), definition.model(),
                definition.contextWindowTokens(), metadata, catalog).contextWindowTokens();
        if (contextWindow == null) {
            return failed(
                    definition, imageCapability,
                    "invalid_model_config",
                    "contextWindowTokens is required unless trusted or builtin model metadata resolves it");
        }
        Integer maxOutput = ModelOutputResolution.resolve(definition.baseUri(), definition.model(),
                definition.maxOutputTokens(), metadata, catalog).maxOutputTokens();
        if (maxOutput == null) {
            return failed(
                    definition, imageCapability,
                    "invalid_model_config",
                    "maxOutputTokens is required unless trusted or builtin metadata publishes its maximum");
        }
        ToolResult<SecretValue> resolvedCredential;
        try {
            resolvedCredential = credentials.resolve(
                    CredentialReference.parse(definition.credentialRef()));
        } catch (RuntimeException failure) {
            return failed(definition, imageCapability, "credential_store_unavailable", "Stored credentials are unavailable");
        }
        if (resolvedCredential instanceof ToolResult.Failure<SecretValue> failure) {
            return failed(definition, imageCapability, failure.code(), failure.message());
        }
        SecretValue secret = ((ToolResult.Success<SecretValue>) resolvedCredential).value();
        try {
            return new ResolvedModelProfile(
                    definition,
                    new ModelConfig(
                            true,
                            definition.protocol(),
                            definition.baseUri(),
                            definition.model(),
                            secret,
                            contextWindow,
                            maxOutput,
                            definition.connectTimeout(),
                            definition.requestTimeout(),
                            definition.reasoningEffort(), definition.tokenEncoding(), imageCapability),
                    null,
                    discovered == null
                            ? definition.model()
                            : discovered.canonicalModelId(),
                    imageCapability);
        } catch (RuntimeException failure) {
            return failed(definition, imageCapability, "invalid_model_config", message(failure));
        }
    }

    private static ModelMetadata trustedMetadata(
            ModelProfileDefinition definition,
            Map<ModelMetadata.Key, ModelMetadata> metadata) {
        if (!OpenRouterMetadataResolver.supports(definition.baseUri())) {
            return null;
        }
        return metadata.get(new ModelMetadata.Key(
                OpenRouterMetadataResolver.SOURCE, definition.model()));
    }

    private static ResolvedModelProfile failed(
            ModelProfileDefinition definition,
            ModelImageCapabilityResolution imageCapability,
            String code,
            String message) {
        return new ResolvedModelProfile(definition, null, new GuideFailure(code, message),
                definition.model(), imageCapability);
    }

    private static <T> ToolResult<T> invalid(String message) {
        return new ToolResult.Failure<>("invalid_model_config", message);
    }

    private static void exactFields(
            JsonObject object,
            Set<String> required,
            Set<String> optional,
            String label) {
        Set<String> allowed = new java.util.HashSet<>(required);
        allowed.addAll(optional);
        Set<String> missing = new java.util.TreeSet<>(required);
        missing.removeAll(dev.openallay.json.JsonTrees.keys(object));
        Set<String> extra = new java.util.TreeSet<>(dev.openallay.json.JsonTrees.keys(object));
        extra.removeAll(allowed);
        if (!missing.isEmpty() || !extra.isEmpty()) {
            throw new IllegalArgumentException(
                    label + " schema mismatch; missing=" + missing + ", extra=" + extra);
        }
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value.getAsJsonArray();
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IllegalArgumentException(field + " must be nonblank text");
        }
        return value.getAsString();
    }

    private static boolean bool(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(field + " must be boolean");
        }
        return value.getAsBoolean();
    }

    private static int integer(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new IllegalArgumentException(field + " must be an integer", failure);
        }
    }

    private static Integer optionalInteger(JsonObject object, String field) {
        return !object.has(field) || object.get(field).isJsonNull()
                ? null : integer(object, field);
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? "Invalid model profiles configuration"
                : failure.getMessage();
    }
}
