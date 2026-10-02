package dev.openallay.model.config;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.model.image.ImageInputCapability;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import dev.openallay.model.metadata.ModelImageCapabilityResolution;
import dev.openallay.model.metadata.ModelMetadata;
import dev.openallay.model.tokenizer.ModelTokenEncoding;
import dev.openallay.settings.model.ModelProfileSettingsView;
import dev.openallay.tool.ToolResult;
import java.io.StringReader;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ModelProfileImageCapabilityTest {
    @Test void manualAliasOverrideRoundTripsAndCapturesTheSameRuntimeResolution() {
        var definition = definition(ImageInputCapability.SUPPORTED, "private-alias", "https://gateway.example/v1/");
        String json = encode(definition);
        assertTrue(json.contains("\"imageInputCapabilityOverride\":\"supported\""));
        var loaded = load(json, Map.of()).profiles().getFirst();
        assertTrue(loaded.available());
        assertEquals(definition, loaded.definition());
        assertEquals(ModelImageCapabilityResolution.Origin.EXPLICIT, loaded.imageCapability().origin());
        assertEquals(ImageInputCapability.SUPPORTED, loaded.imageCapability().capability());
        assertSame(loaded.imageCapability(), loaded.runtimeConfig().imageCapability());
        assertEquals(definition.baseUri(), loaded.runtimeConfig().baseUri());
        assertEquals("private-alias", loaded.runtimeConfig().model());
        assertEquals(ModelProtocol.OPENAI_CHAT, loaded.runtimeConfig().protocol());
        assertEquals(loaded.imageCapability(), ModelProfileSettingsView.Resolution.from(loaded).imageCapability());
    }

    @Test void trustedModalityEvidenceWinsAndSavingWithoutOverrideNeverPinsDiscovery() {
        var definition = definition(null, "vendor/model", "https://openrouter.ai/api/v1/");
        String json = encode(definition);
        assertFalse(json.contains("imageInputCapabilityOverride"));
        var metadata = new ModelMetadata("openrouter", "vendor/model", "canonical", 200000,
                20000, Instant.EPOCH, ImageInputCapability.SUPPORTED);
        var captured = load(json, Map.of(metadata.key(), metadata)).profiles().getFirst();
        assertEquals(ModelImageCapabilityResolution.Origin.TRUSTED, captured.imageCapability().origin());
        assertEquals("openrouter", captured.imageCapability().source());
        assertNull(captured.definition().imageInputCapabilityOverride());
        var replacement = new ModelMetadata("openrouter", "vendor/model", "canonical", 200000,
                20000, Instant.ofEpochSecond(1), ImageInputCapability.UNSUPPORTED);
        var future = load(json, Map.of(replacement.key(), replacement)).profiles().getFirst();
        assertEquals(ImageInputCapability.UNSUPPORTED, future.imageCapability().capability());
        assertEquals(ImageInputCapability.SUPPORTED, captured.imageCapability().capability());
        assertEquals(Instant.EPOCH, captured.imageCapability().capturedAt());
    }

    @Test void explicitUnknownAndUnsupportedRemainDistinctAndInvalidChoicesRejectConfiguration() {
        for (var capability : List.of(ImageInputCapability.UNKNOWN, ImageInputCapability.UNSUPPORTED)) {
            var loaded = load(encode(definition(capability, "alias", "https://gateway.example/v1/")), Map.of());
            assertEquals(capability, loaded.profiles().getFirst().imageCapability().capability());
            assertEquals(ModelImageCapabilityResolution.Origin.EXPLICIT,
                    loaded.profiles().getFirst().imageCapability().origin());
        }
        String json = encode(definition(ImageInputCapability.SUPPORTED, "alias", "https://gateway.example/v1/"));
        for (String bad : List.of("true", "\"vision\"", "[]", "{}")) {
            var result = new ModelProfilesConfigLoader().load(new StringReader(
                    json.replace("\"supported\"", bad)), Map.of("KEY", "fixture-only"));
            assertInstanceOf(ToolResult.Failure.class, result);
        }
    }

    @Test void profileWithoutCredentialsStillProjectsKnownImageEvidence() {
        var definition = definition(ImageInputCapability.SUPPORTED, "alias", "https://gateway.example/v1/");
        var result = new ModelProfilesConfigLoader().load(new StringReader(encode(definition)), Map.of());
        var loaded = ((ToolResult.Success<ModelProfilesConfigLoader.Load>) result).value().profiles().getFirst();
        assertFalse(loaded.available());
        assertEquals(ImageInputCapability.SUPPORTED, loaded.imageCapability().capability());
        assertEquals(loaded.imageCapability(), ModelProfileSettingsView.Resolution.from(loaded).imageCapability());
    }

    private ModelProfilesConfigLoader.Load load(String json, Map<ModelMetadata.Key, ModelMetadata> cache) {
        var result = new ModelProfilesConfigLoader().load(new StringReader(json),
                Map.of("KEY", "fixture-only"), cache);
        return ((ToolResult.Success<ModelProfilesConfigLoader.Load>) result).value();
    }

    private String encode(ModelProfileDefinition definition) {
        return new ModelProfilesConfigWriter().encode(new ModelProfilesConfig("main", List.of(definition)));
    }

    private ModelProfileDefinition definition(ImageInputCapability override, String model, String url) {
        return new ModelProfileDefinition("main", "Main", true, ModelProtocol.OPENAI_CHAT, URI.create(url),
                model, "env:KEY", 100000, 10000, Duration.ofSeconds(30), Duration.ofSeconds(300),
                null, ModelReasoningEffort.AUTO, ModelTokenEncoding.AUTO, override);
    }
}
