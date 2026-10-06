package dev.openallay.model.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.openallay.tool.ToolResult;
import java.io.StringReader;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ModelProfilesConfigWriterTest {
    @Test
    void encodesCanonicalCredentialFreeDocumentThatRoundTrips() {
        ModelProfilesConfig config = config();

        String encoded = new ModelProfilesConfigWriter().encode(config);
        JsonObject root = dev.openallay.json.JsonTrees.parse(encoded).getAsJsonObject();

        assertEquals(List.of("defaultProfileId", "profiles"),
                dev.openallay.json.JsonTrees.keys(root).stream().toList());
        assertEquals(List.of(
                        "id", "displayName", "enabled", "protocol", "baseUrl", "model",
                        "credentialRef", "contextWindowTokens", "maxOutputTokens",
                        "connectTimeoutSeconds", "requestTimeoutSeconds", "metadata"),
                dev.openallay.json.JsonTrees.keys(root.getAsJsonArray("profiles").get(0).getAsJsonObject()
                        ).stream().toList());
        assertTrue(encoded.endsWith(System.lineSeparator()));
        assertFalse(encoded.contains("secret-value"));
        assertFalse(encoded.contains("\"apiKey\""));

        ToolResult<ModelProfilesConfigLoader.Load> decoded = new ModelProfilesConfigLoader()
                .load(new StringReader(encoded), Map.of("MODEL_KEY", "secret-value"));
        ModelProfilesConfigLoader.Load load = success(decoded).value();
        assertEquals(config, load.config());
        assertTrue(load.profiles().getFirst().available());
    }

    @Test
    void omitsNullableOptionalFieldsInsteadOfEncodingNull() {
        ModelProfileDefinition source = config().profiles().getFirst();
        ModelProfileDefinition minimal = new ModelProfileDefinition(
                source.id(),
                source.displayName(),
                false,
                source.protocol(),
                source.baseUri(),
                source.model(),
                source.credentialRef(),
                null,
                source.maxOutputTokens(),
                source.connectTimeout(),
                source.requestTimeout(),
                null);
        String encoded = new ModelProfilesConfigWriter().encode(new ModelProfilesConfig(
                minimal.id(), List.of(minimal)));
        JsonObject profile = dev.openallay.json.JsonTrees.parse(encoded).getAsJsonObject()
                .getAsJsonArray("profiles").get(0).getAsJsonObject();

        assertFalse(profile.has("contextWindowTokens"));
        assertFalse(profile.has("metadata"));
        assertEquals(List.of(
                        "id", "displayName", "enabled", "protocol", "baseUrl", "model",
                        "credentialRef", "maxOutputTokens", "connectTimeoutSeconds",
                        "requestTimeoutSeconds"),
                dev.openallay.json.JsonTrees.keys(profile).stream().toList());
    }

    @Test
    void automaticOutputStaysOmittedAcrossCanonicalSchemaTwoRoundTrip() {
        ModelProfileDefinition profile = new ModelProfileDefinition("luna", "Luna", true,
                ModelProtocol.OPENAI_CHAT, URI.create("https://provider.example/v1/"),
                "gpt-6-luna", "env:MODEL_KEY", 1_000_000, null,
                Duration.ofSeconds(30), Duration.ofSeconds(300), null);
        var config = new ModelProfilesConfig(profile.id(), List.of(profile));
        String encoded = new ModelProfilesConfigWriter().encode(config);
        JsonObject json = dev.openallay.json.JsonTrees.parse(encoded).getAsJsonObject();
        assertFalse(json.has("schemaVersion"));
        assertFalse(json.getAsJsonArray("profiles").get(0).getAsJsonObject().has("maxOutputTokens"));
        var loaded = success(new ModelProfilesConfigLoader().load(new StringReader(encoded),
                Map.of("MODEL_KEY", "fixture-key"))).value();
        assertEquals(config, loaded.config());
        assertNull(loaded.config().profiles().getFirst().maxOutputTokens());
        assertEquals(128_000, loaded.profiles().getFirst().runtimeConfig().maxOutputTokens());
        assertEquals(encoded, new ModelProfilesConfigWriter().encode(loaded.config()));
    }

    @Test
    void explicitEffortsRoundTripCanonicallyWhileAutoIsOmitted() {
        ModelProfileDefinition source = config().profiles().getFirst();
        for (var effort : ModelReasoningEffort.values()) {
            ModelProfileDefinition profile = new ModelProfileDefinition(source.id(),
                    source.displayName(), source.enabled(), source.protocol(), source.baseUri(),
                    source.model(), source.credentialRef(), source.contextWindowTokens(),
                    source.maxOutputTokens(), source.connectTimeout(), source.requestTimeout(),
                    source.metadata(), effort);
            var configured = new ModelProfilesConfig(profile.id(), List.of(profile));
            String encoded = new ModelProfilesConfigWriter().encode(configured);
            var json = dev.openallay.json.JsonTrees.parse(encoded).getAsJsonObject()
                    .getAsJsonArray("profiles").get(0).getAsJsonObject();
            assertEquals(effort != ModelReasoningEffort.AUTO, json.has("reasoningEffort"));
            if (effort != ModelReasoningEffort.AUTO) {
                assertEquals(effort.encoded(), json.get("reasoningEffort").getAsString());
            }
            var loaded = success(new ModelProfilesConfigLoader().load(new StringReader(encoded),
                    Map.of("MODEL_KEY", "fixture-key"))).value();
            assertEquals(configured, loaded.config());
            assertEquals(effort, loaded.profiles().getFirst().runtimeConfig().reasoningEffort());
            assertEquals(encoded, new ModelProfilesConfigWriter().encode(loaded.config()));
            assertFalse(encoded.contains("schemaVersion"));
        }
    }

    private static ModelProfilesConfig config() {
        ModelProfileDefinition definition = new ModelProfileDefinition(
                "main",
                "Main Model",
                true,
                ModelProtocol.OPENAI_CHAT,
                URI.create("https://provider.example/v1"),
                "vendor/model",
                "MODEL_KEY",
                256_000,
                8_192,
                Duration.ofSeconds(30),
                Duration.ofSeconds(300),
                new ModelProfileDefinition.MetadataProvenance(
                        "openrouter", "vendor/model", Instant.parse("2026-07-18T00:00:00Z")));
        return new ModelProfilesConfig(
                definition.id(), List.of(definition));
    }

    @SuppressWarnings("unchecked")
    private static ToolResult.Success<ModelProfilesConfigLoader.Load> success(
            ToolResult<ModelProfilesConfigLoader.Load> result) {
        return (ToolResult.Success<ModelProfilesConfigLoader.Load>)
                assertInstanceOf(ToolResult.Success.class, result);
    }
}
