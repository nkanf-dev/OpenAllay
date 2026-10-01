package dev.openallay.model.metadata;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import dev.openallay.model.config.CredentialResolver;
import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ModelProfilesConfigLoader;
import dev.openallay.model.config.ModelProfilesConfigWriter;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.tool.ToolResult;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class BuiltinModelCatalogTest {
    static BuiltinModelCatalog sample() {
        return BuiltinModelCatalog.parse(new InputStreamReader(
                BuiltinModelCatalogTest.class.getResourceAsStream("/model-metadata/builtin-sample.json"),
                StandardCharsets.UTF_8)).catalog();
    }
    private static String sampleJson() {
        return new Gson().toJson(JsonParser.parseReader(new InputStreamReader(
                BuiltinModelCatalogTest.class.getResourceAsStream("/model-metadata/builtin-sample.json"),
                StandardCharsets.UTF_8)));
    }
    @Test void exactAliasesWrappersAndKnownSuffixesMatchWithoutRerouting() {
        BuiltinModelCatalog catalog = sample();
        assertEquals(BuiltinModelMatcher.Kind.EXACT, catalog.match("openai/gpt-6-luna").orElseThrow().kind());
        assertEquals(BuiltinModelMatcher.Kind.EXACT, catalog.match("GPT-6 Luna").orElseThrow().kind());
        for (String name : List.of("gateway/openai/gpt-6-luna", "gpt-6-luna:free",
                "gpt-6-luna-latest", "gpt-6-luna-2026-09-22")) {
            var match = catalog.match(name).orElseThrow();
            assertEquals("openai/gpt-6-luna", match.entry().id());
            assertEquals(BuiltinModelMatcher.Kind.NORMALIZED, match.kind());
        }
    }
    @Test void genuineCloseNamesUseBestButUnknownVersionAndVariantDoNot() {
        BuiltinModelCatalog catalog = sample();
        assertEquals(BuiltinModelMatcher.Kind.SIMILAR,
                catalog.match("gpt-6-lunna").orElseThrow().kind());
        for (String name : List.of("gpt-6", "gpt-7-luna", "gpt-6-kanglives", "claude-6-luna",
                "mystery/model", "gpt-6-luna-preview", "gpt-6-luna-thinking", "gpt-6-luna-mini"))
            assertTrue(catalog.match(name).isEmpty(), name);
    }
    @Test void tieBreakDoesNotDependOnResourceOrderAndPrefersDirectProvider() {
        var base = sample().models().getFirst();
        var router = entry("openrouter/openai/gpt-6-luna", "openrouter", base.aliases());
        var direct = entry("aaa/gpt-6-luna", "aaa", base.aliases());
        var entries = new ArrayList<>(List.of(router, base, direct));
        for (int rotation = 0; rotation < 3; rotation++) {
            assertEquals(direct.id(), BuiltinModelMatcher.match(entries, "GPT-6 Luna")
                    .orElseThrow().entry().id());
            Collections.rotate(entries, 1);
        }
    }
    @Test void preservesVersionsFamiliesVariantsAndDottedVersionSpelling() {
        var entries = List.of(entry("openai/gpt-6-mini", "openai", List.of("gpt-6-mini")),
                entry("openai/gpt-6-nano", "openai", List.of("gpt-6-nano")),
                new BuiltinModelCatalog.Entry("anthropic/claude-sonnet-4-5", "anthropic", "claude",
                        List.of(), 1_000_000, null, null, "models-dev", null, "claude-sonnet-4-5"));
        assertEquals("openai/gpt-6-mini", BuiltinModelMatcher.match(entries, "gpt-6-minni")
                .orElseThrow().entry().id());
        assertEquals("anthropic/claude-sonnet-4-5", BuiltinModelMatcher.match(entries,
                "gateway/claude-sonnet-4.5").orElseThrow().entry().id());
        assertTrue(BuiltinModelMatcher.match(entries, "gpt-6-pro").isEmpty());
        assertTrue(BuiltinModelMatcher.match(entries, "gpt-6-nano-thinking").isEmpty());
    }
    @Test void strictSchemaProvenanceNumbersAndDuplicatesRejectWholeResource() {
        String json = sampleJson();
        for (String bad : List.of(
                json.replace("\"models\":", "\"sources\":[],\"models\":"),
                json.replace("\"catalogVersion\"", "\"unknown\""),
                json.replace("\"contextWindowTokens\":1050000", "\"contextWindowTokens\":0"),
                json.replace("\"maxOutputTokens\":128000", "\"maxOutputTokens\":1.5"),
                json.replace("\"capabilitySource\":\"models-dev\"", "\"capabilitySource\":\"missing\""),
                json.replace("https://models.dev/api.json", "https://models.dev/api.json?key=secret"),
                json.replace("\"input\":\"0.1\"", "\"input\":\"-1\""),
                json.replace("\"currency\":\"USD\"", "\"currency\":\"EUR\""))) {
            var loaded = BuiltinModelCatalog.parse(new StringReader(bad));
            assertEquals("builtin_catalog_invalid", loaded.failure().code());
            assertTrue(loaded.catalog().models().isEmpty());
            assertFalse(loaded.failure().toString().contains("secret"));
        }
    }
    @Test void pricesKeepAllThresholdsAndUnpublishedOutputAndPricesRemainNull() {
        var entry = sample().models().getFirst();
        assertEquals(List.of(0, 272_000), entry.pricing().tiers().stream()
                .map(BuiltinModelCatalog.Tier::minInputTokens).toList());
        assertEquals("0.75", entry.pricing().tiers().get(1).output().toPlainString());
        var json = JsonParser.parseString(sampleJson()).getAsJsonObject();
        var model = json.getAsJsonArray("models").get(0).getAsJsonObject();
        model.add("maxOutputTokens", com.google.gson.JsonNull.INSTANCE);
        model.add("pricing", com.google.gson.JsonNull.INSTANCE);
        model.add("pricingSource", com.google.gson.JsonNull.INSTANCE);
        var decoded = BuiltinModelCatalog.parse(new StringReader(json.toString()));
        assertNull(decoded.failure());
        assertNull(decoded.catalog().models().getFirst().pricing());
        assertNull(decoded.catalog().models().getFirst().maxOutputTokens());
    }
    @Test void offlineLoaderPreservesEndpointIdOutputAndExplicitMillionBudget() {
        var loader = new ModelProfilesConfigLoader(sample());
        var definition = definition("gateway/gpt-6-luna", "https://arbitrary.example/v1/", null);
        var loaded = load(loader, definition, Map.of());
        var profile = loaded.profiles().getFirst();
        assertTrue(profile.available());
        assertEquals(1_050_000, profile.runtimeConfig().contextWindowTokens());
        assertEquals(8192, profile.runtimeConfig().maxOutputTokens());
        assertEquals(definition.model(), profile.runtimeConfig().model());
        assertEquals(definition.model(), profile.canonicalModelId());
        assertEquals(definition.baseUri(), profile.runtimeConfig().baseUri());
        assertNull(loaded.config().profiles().getFirst().contextWindowTokens());
        assertEquals(1_000_000, load(loader, definition("gpt-6-luna",
                "https://arbitrary.example/v1/", 1_000_000), Map.of())
                .profiles().getFirst().runtimeConfig().contextWindowTokens());
    }
    @Test void providerCacheOutranksBuiltinAndExplicitOutranksBoth() {
        var loader = new ModelProfilesConfigLoader(sample());
        var metadata = new ModelMetadata("openrouter", "openai/gpt-6-luna", "published/canonical",
                2_000_000, 500_000, Instant.EPOCH);
        var cache = Map.of(metadata.key(), metadata);
        var profile = load(loader, definition("openai/gpt-6-luna",
                "https://openrouter.ai/api/v1/", null), cache).profiles().getFirst();
        assertEquals(2_000_000, profile.runtimeConfig().contextWindowTokens());
        assertEquals(8192, profile.runtimeConfig().maxOutputTokens());
        assertEquals("published/canonical", profile.canonicalModelId());
        assertEquals(1_000_000, load(loader, definition("openai/gpt-6-luna",
                "https://openrouter.ai/api/v1/", 1_000_000), cache)
                .profiles().getFirst().runtimeConfig().contextWindowTokens());
    }
    @Test void malformedBuiltinDoesNotBreakExplicitProfilesAndUnknownContextStaysRequired() {
        var empty = BuiltinModelCatalog.parse(new StringReader("{}"));
        var loader = new ModelProfilesConfigLoader(empty.catalog());
        assertTrue(load(loader, definition("gpt-6-luna", "https://provider.example/v1/", 1_000_000),
                Map.of()).profiles().getFirst().available());
        var missing = load(new ModelProfilesConfigLoader(sample()),
                definition("new-unrelated-model", "https://provider.example/v1/", null), Map.of())
                .profiles().getFirst();
        assertEquals("invalid_model_config", missing.failure().code());
        assertNull(missing.runtimeConfig());
    }
    @Test void currentDraftEndpointAndModelUseTheSameTrustedPrecedenceAsLoader() {
        var cache = new ModelMetadata("openrouter", "openai/gpt-6-luna", "canonical",
                600_000, 256_000, Instant.EPOCH);
        var metadata = Map.of(cache.key(), cache);
        var trusted = ModelContextResolution.resolve(URI.create("https://openrouter.ai/api/v1/"),
                "openai/gpt-6-luna", null, metadata, sample());
        assertEquals(600_000, trusted.contextWindowTokens());
        assertEquals(ModelContextResolution.Origin.TRUSTED, trusted.origin());
        assertEquals("openrouter", trusted.source());
        assertEquals(Instant.EPOCH, trusted.capturedAt());
        var changedEndpoint = ModelContextResolution.resolve(URI.create("https://other.example/v1/"),
                "openai/gpt-6-luna", null, metadata, sample());
        assertEquals(1_050_000, changedEndpoint.contextWindowTokens());
        assertEquals(ModelContextResolution.Origin.BUILTIN, changedEndpoint.origin());
        assertEquals(1_000_000, ModelContextResolution.resolve(URI.create("https://openrouter.ai/api/v1/"),
                "openai/gpt-6-luna", 1_000_000, metadata, sample()).contextWindowTokens());
        assertNull(ModelContextResolution.resolve(URI.create("https://openrouter.ai/api/v1/"),
                "unknown-new-model", null, metadata, sample()).contextWindowTokens());
    }

    @Test void bundledResourceIsValidAndActuallyResolvesCommonModels() {
        var bundled = BuiltinModelCatalog.bundled();
        assertNull(bundled.failure());
        for (String name : List.of("gpt-6-luna", "openai/gpt-6-luna")) {
            var entry = bundled.catalog().match(name).orElseThrow().entry();
            assertEquals("openai/gpt-6-luna", entry.id());
            assertEquals(1_050_000, entry.contextWindowTokens());
            assertEquals("models-dev", entry.capabilitySource());
        }
        for (String name : List.of("gpt-5.4", "openai/gpt-5.4")) {
            var entry = bundled.catalog().match(name).orElseThrow().entry();
            assertEquals("openai/gpt-5.4", entry.id());
            assertEquals(1_050_000, entry.contextWindowTokens());
        }
        assertTrue(bundled.catalog().match("claude-sonnet-4-5").isPresent());
    }
    static ModelProfileDefinition definition(String model, String url, Integer context) {
        return new ModelProfileDefinition("main", "Main", true, ModelProtocol.OPENAI_CHAT,
                URI.create(url), model, "env:KEY", context, 8192,
                Duration.ofSeconds(30), Duration.ofSeconds(300), null);
    }
    static ModelProfilesConfigLoader.Load load(ModelProfilesConfigLoader loader,
            ModelProfileDefinition definition, Map<ModelMetadata.Key, ModelMetadata> metadata) {
        var config = new ModelProfilesConfig(definition.id(), List.of(definition));
        var result = loader.load(new StringReader(new ModelProfilesConfigWriter().encode(config)),
                CredentialResolver.environment(Map.of("KEY", "private-sentinel")), metadata);
        return ((ToolResult.Success<ModelProfilesConfigLoader.Load>) result).value();
    }
    private static BuiltinModelCatalog.Entry entry(String id, String provider, List<String> aliases) {
        return new BuiltinModelCatalog.Entry(id, provider, "gpt", aliases, 1_050_000,
                null, null, "models-dev", null, id);
    }
}
