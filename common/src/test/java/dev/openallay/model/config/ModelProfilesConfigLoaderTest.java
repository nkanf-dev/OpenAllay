package dev.openallay.model.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import dev.openallay.tool.ToolResult;
import dev.openallay.model.metadata.ModelMetadata;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ModelProfilesConfigLoaderTest {
    private static final String PROFILES = """
            {
              "schemaVersion": 2,
              "defaultProfileId": "fast",
              "profiles": [
                {
                  "id": "fast",
                  "displayName": "Fast OpenRouter",
                  "enabled": true,
                  "protocol": "openai_chat",
                  "baseUrl": "https://openrouter.ai/api/v1",
                  "model": "vendor/model-a",
                  "credentialRef": "env:OPENROUTER_KEY",
                  "contextWindowTokens": 256000,
                  "maxOutputTokens": 8192,
                  "connectTimeoutSeconds": 30,
                  "requestTimeoutSeconds": 300
                },
                {
                  "id": "local",
                  "displayName": "Local Model",
                  "enabled": false,
                  "protocol": "openai_chat",
                  "baseUrl": "http://127.0.0.1:11434/v1",
                  "model": "local/model",
                  "credentialRef": "env:LOCAL_MODEL_KEY",
                  "maxOutputTokens": 4096,
                  "connectTimeoutSeconds": 10,
                  "requestTimeoutSeconds": 120
                }
              ]
            }
            """;

    private final ModelProfilesConfigLoader loader = new ModelProfilesConfigLoader();

    @Test
    void publicUiFixtureKeepsItsEnabledRuntimeWithDisabledManualAndAutomaticProfiles() {
        String json = """
                {"schemaVersion":2,"defaultProfileId":"e2e-fixture","profiles":[
                  {"id":"e2e-fixture","displayName":"Offline UI fixture","enabled":true,
                   "protocol":"openai_chat","baseUrl":"http://127.0.0.1:18765/v1/",
                   "model":"openallay-e2e-fixture","credentialRef":"env:OPENALLAY_E2E_FIXTURE_KEY",
                   "contextWindowTokens":256000,"maxOutputTokens":8192,
                   "connectTimeoutSeconds":10,"requestTimeoutSeconds":120},
                  {"id":"luna-manual","displayName":"Luna · manual 1M","enabled":false,
                   "protocol":"openai_chat","baseUrl":"https://api.openai.com/v1/",
                   "model":"gpt-6-luna","credentialRef":"env:OPENALLAY_UI_UNUSED_KEY",
                   "contextWindowTokens":1000000,"maxOutputTokens":8192,
                   "connectTimeoutSeconds":10,"requestTimeoutSeconds":120},
                  {"id":"reference-auto","displayName":"Reference · automatic","enabled":false,
                   "protocol":"openai_chat","baseUrl":"https://api.openai.com/v1/",
                   "model":"gpt-4.1","credentialRef":"env:OPENALLAY_UI_UNUSED_KEY",
                   "contextWindowTokens":null,"maxOutputTokens":8192,
                   "connectTimeoutSeconds":10,"requestTimeoutSeconds":120}]}
                """;
        ModelProfilesConfigLoader.Load loaded = success(loader.load(new StringReader(json),
                Map.of("OPENALLAY_E2E_FIXTURE_KEY", "local-fixture-value"))).value();
        assertTrue(loaded.profiles().getFirst().available());
        assertEquals(256000, loaded.profiles().getFirst().runtimeConfig().contextWindowTokens());
        assertEquals("model_disabled", loaded.profiles().get(1).failure().code());
        assertEquals("model_disabled", loaded.profiles().get(2).failure().code());
        assertEquals(1000000, loaded.config().profiles().get(1).contextWindowTokens());
        assertNull(loaded.config().profiles().get(2).contextWindowTokens());
    }

    @Test
    void disabledAutomaticContextRemainsUnknownInDiagnosticAndSettingsViews() {
        ModelProfilesConfigLoader.Load loaded = success(loader.load(new StringReader(PROFILES),
                Map.of("OPENROUTER_KEY", "local-fixture-value"))).value();
        ResolvedModelProfile disabled = loaded.profiles().get(1);
        assertNull(disabled.definition().contextWindowTokens());
        assertNull(disabled.diagnosticView().contextWindowTokens());
        assertNull(dev.openallay.settings.model.ModelProfileSettingsView.Resolution
                .from(disabled, false).effectiveContextWindowTokens());
    }

    @Test
    void loadsOrderedProfilesAndRetainsDisabledOrUnresolvedDefinitions() {
        ModelProfilesConfigLoader.Load loaded = success(loader.load(
                new StringReader(PROFILES), Map.of("OPENROUTER_KEY", "super-secret"))).value();

        assertEquals("fast", loaded.config().defaultProfileId());
        assertEquals(ModelProfilesConfig.SCHEMA_VERSION, loaded.config().schemaVersion());
        assertEquals(List.of("fast", "local"), loaded.config().profiles().stream()
                .map(ModelProfileDefinition::id).toList());
        ResolvedModelProfile fast = loaded.profiles().getFirst();
        assertTrue(fast.available());
        assertEquals(256_000, fast.runtimeConfig().contextWindowTokens());
        assertEquals("super-secret", fast.runtimeConfig().apiKey().reveal());
        assertFalse(fast.toString().contains("super-secret"));
        assertFalse(new Gson().toJson(fast.diagnosticView()).contains("super-secret"));

        ResolvedModelProfile local = loaded.profiles().get(1);
        assertFalse(local.available());
        assertEquals("model_disabled", local.failure().code());
        assertNull(local.runtimeConfig());
    }

    @Test
    void missingContextOrSecretStaysVisibleButCannotRun() {
        String enabledWithoutContext = PROFILES
                .replace("\"enabled\": false", "\"enabled\": true");
        ModelProfilesConfigLoader.Load loaded = success(loader.load(
                new StringReader(enabledWithoutContext),
                Map.of("OPENROUTER_KEY", "key"))).value();

        ResolvedModelProfile local = loaded.profiles().get(1);
        assertEquals("invalid_model_config", local.failure().code());
        assertTrue(local.failure().message().contains("contextWindowTokens"));

        ModelProfilesConfigLoader.Load missingSecret = success(loader.load(
                new StringReader(PROFILES), Map.of())).value();
        assertEquals("model_not_configured", missingSecret.profiles().getFirst().failure().code());
        assertFalse(missingSecret.profiles().getFirst().failure().message().contains("null"));
    }

    @Test
    void trustedCacheFillsOnlyMissingOpenRouterContext() {
        String withoutExplicitContext = PROFILES.replace(
                "\"contextWindowTokens\": 256000,", "");
        ModelMetadata metadata = new ModelMetadata(
                "openrouter",
                "vendor/model-a",
                "vendor/model-a-canonical",
                512_000,
                64_000,
                Instant.EPOCH);

        ModelProfilesConfigLoader.Load loaded = success(loader.load(
                new StringReader(withoutExplicitContext),
                Map.of("OPENROUTER_KEY", "key"),
                Map.of(metadata.key(), metadata))).value();

        assertEquals(512_000, loaded.profiles().getFirst()
                .runtimeConfig().contextWindowTokens());
        assertEquals(8_192, loaded.profiles().getFirst()
                .runtimeConfig().maxOutputTokens());
        assertEquals(512_000, loaded.profiles().getFirst()
                .diagnosticView().contextWindowTokens());

        String otherProvider = withoutExplicitContext.replace(
                "https://openrouter.ai/api/v1", "https://provider.example/v1");
        assertEquals("invalid_model_config", success(loader.load(
                new StringReader(otherProvider),
                Map.of("OPENROUTER_KEY", "key"),
                Map.of(metadata.key(), metadata))).value()
                .profiles().getFirst().failure().code());
    }

    @Test
    void rejectsInlineSecretsDuplicatesMissingDefaultFutureSchemaAndUnknownFields() {
        assertInvalid(PROFILES.replace(
                "\"credentialRef\": \"env:OPENROUTER_KEY\"",
                "\"credentialRef\": \"env:OPENROUTER_KEY\", \"apiKey\": \"forbidden\""));
        assertInvalid(PROFILES.replace(
                "\"credentialRef\": \"env:OPENROUTER_KEY\"",
                "\"apiKeyEnv\": \"OPENROUTER_KEY\""));
        assertInvalid(PROFILES.replace("\"id\": \"local\"", "\"id\": \"fast\""));
        assertInvalid(PROFILES.replace("\"defaultProfileId\": \"fast\"",
                "\"defaultProfileId\": \"missing\""));
        assertInvalid(PROFILES.replace("\"schemaVersion\": 2", "\"schemaVersion\": 3"));
        assertInvalid(PROFILES.replace("\"displayName\": \"Fast OpenRouter\"",
                "\"displayName\": \"Fast OpenRouter\", \"surprise\": true"));
    }

    @Test
    void rejectsSchemaOneProfilesAndDoesNotImportOrModifyLegacyModelFile(
            @TempDir Path directory) throws Exception {
        Path profiles = directory.resolve("models.json");
        Path legacy = directory.resolve("model.json");
        String legacyContents = """
                {"protocol":"anthropic_messages","baseUrl":"https://example.test/v1",
                 "model":"legacy-model","apiKey":"legacy-secret",
                 "contextWindowTokens":128000,"maxOutputTokens":4096}
                """;
        Files.writeString(legacy, legacyContents);

        ToolResult.Failure<ModelProfilesConfigLoader.Load> missing = failure(
                loader.load(profiles, Map.of()));
        assertEquals("model_not_configured", missing.code());
        assertTrue(Files.exists(legacy));
        assertEquals(legacyContents, Files.readString(legacy));

        Files.writeString(profiles, PROFILES.replace(
                "\"schemaVersion\": 2", "\"schemaVersion\": 1"));
        ToolResult.Failure<ModelProfilesConfigLoader.Load> oldSchema = failure(
                loader.load(profiles, Map.of("OPENROUTER_KEY", "key")));
        assertEquals("invalid_model_config", oldSchema.code());
        assertTrue(Files.readString(profiles).contains("\"schemaVersion\": 1"));
        assertTrue(Files.exists(legacy));
        assertEquals(legacyContents, Files.readString(legacy));
    }

    @Test
    void missingBothFormatsIsExplicit(@TempDir Path directory) {
        ToolResult.Failure<ModelProfilesConfigLoader.Load> failure = failure(loader.load(
                directory.resolve("models.json"),
                Map.of()));
        assertEquals("model_not_configured", failure.code());
    }

    private void assertInvalid(String json) {
        assertEquals("invalid_model_config", failure(loader.load(
                new StringReader(json), Map.of("OPENROUTER_KEY", "key"))).code());
    }

    @SuppressWarnings("unchecked")
    private static ToolResult.Success<ModelProfilesConfigLoader.Load> success(
            ToolResult<ModelProfilesConfigLoader.Load> result) {
        return (ToolResult.Success<ModelProfilesConfigLoader.Load>)
                assertInstanceOf(ToolResult.Success.class, result);
    }

    @SuppressWarnings("unchecked")
    private static ToolResult.Failure<ModelProfilesConfigLoader.Load> failure(
            ToolResult<ModelProfilesConfigLoader.Load> result) {
        return (ToolResult.Failure<ModelProfilesConfigLoader.Load>)
                assertInstanceOf(ToolResult.Failure.class, result);
    }
}
