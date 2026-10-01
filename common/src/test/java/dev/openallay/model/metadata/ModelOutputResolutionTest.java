package dev.openallay.model.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ModelOutputResolutionTest {
    private final BuiltinModelCatalog catalog = BuiltinModelCatalogTest.sample();
    private final URI trusted = URI.create("https://openrouter.ai/api/v1/");

    @Test
    void explicitThenExactTrustedThenBestPublishedMaximum() {
        var metadata = new ModelMetadata("openrouter", "openai/gpt-6-luna", "canonical",
                1_050_000, 64_000, Instant.EPOCH);
        var entries = Map.of(metadata.key(), metadata);
        var explicit = ModelOutputResolution.resolve(trusted, metadata.providerModelId(),
                8_192, entries, catalog);
        assertEquals(8_192, explicit.maxOutputTokens());
        assertEquals(ModelOutputResolution.Origin.EXPLICIT, explicit.origin());
        var discovered = ModelOutputResolution.resolve(trusted, metadata.providerModelId(),
                null, entries, catalog);
        assertEquals(64_000, discovered.maxOutputTokens());
        assertEquals(ModelOutputResolution.Origin.TRUSTED, discovered.origin());
        assertEquals("openrouter", discovered.source());
        assertEquals(Instant.EPOCH, discovered.capturedAt());
        for (String name : java.util.List.of("gpt-6-luna", "gateway/gpt-6-luna", "gpt-6-lunna")) {
            var best = ModelOutputResolution.resolve(trusted, name, null, entries, catalog);
            assertEquals(128_000, best.maxOutputTokens());
            assertEquals(ModelOutputResolution.Origin.BUILTIN, best.origin());
        }
        assertEquals(128_000, ModelOutputResolution.resolve(
                URI.create("https://other.example/v1/"), metadata.providerModelId(),
                null, entries, catalog).maxOutputTokens());
    }

    @Test
    void unpublishedTrustedOutputFallsThroughAndUnknownOrMalformedCatalogRequiresManual() {
        var metadata = new ModelMetadata("openrouter", "gpt-6-luna", "canonical",
                1_050_000, null, Instant.EPOCH);
        assertEquals(128_000, ModelOutputResolution.resolve(trusted, metadata.providerModelId(),
                null, Map.of(metadata.key(), metadata), catalog).maxOutputTokens());
        var unknown = ModelOutputResolution.resolve(trusted, "unknown-model", null, Map.of(), catalog);
        assertNull(unknown.maxOutputTokens());
        assertEquals(ModelOutputResolution.Origin.REQUIRED, unknown.origin());
        var empty = BuiltinModelCatalog.parse(new java.io.StringReader("{\"schemaVersion\":77}"));
        assertEquals(ModelOutputResolution.Origin.REQUIRED, ModelOutputResolution.resolve(
                trusted, "gpt-6-luna", null, Map.of(), empty.catalog()).origin());
        assertEquals(4_096, ModelOutputResolution.resolve(trusted, "unknown-model", 4_096,
                Map.of(), empty.catalog()).maxOutputTokens());
    }

    @Test
    void keyMismatchCannotSupplyTrustedOutputAndUnpublishedBuiltinDoesNotGuess() {
        var metadata = new ModelMetadata("openrouter", "different-id", "gpt-6-luna",
                1_050_000, 1_000, Instant.EPOCH);
        assertEquals(128_000, ModelOutputResolution.resolve(trusted, "gpt-6-luna", null,
                Map.of(new ModelMetadata.Key("openrouter", "gpt-6-luna"), metadata), catalog)
                .maxOutputTokens());
        var entry = catalog.models().getFirst();
        var unpublished = new BuiltinModelCatalog.Entry(entry.id(), entry.provider(), entry.family(),
                entry.aliases(), entry.contextWindowTokens(), null, entry.pricing(),
                entry.capabilitySource(), entry.pricingSource(), entry.upstreamModelId());
        var modified = new BuiltinModelCatalog(catalog.version(), catalog.publishedAt(), catalog.sources(),
                java.util.List.of(unpublished));
        assertEquals(ModelOutputResolution.Origin.REQUIRED, ModelOutputResolution.resolve(
                trusted, "gpt-6-luna", null, Map.of(), modified).origin());
    }
}
