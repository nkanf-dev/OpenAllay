package dev.openallay.model.metadata;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.model.image.ImageInputCapability;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ModelImageCapabilityResolutionTest {
    private static final URI ROUTER = URI.create("https://openrouter.ai/api/v1/");
    private static final URI PRIVATE = URI.create("https://gateway.example/v1/");

    private BuiltinModelCatalog catalog() {
        return BuiltinModelCatalog.parse(new InputStreamReader(getClass().getResourceAsStream(
                "/model-metadata/builtin-sample.json"), StandardCharsets.UTF_8)).catalog();
    }

    @Test void inputModalitiesDistinguishSupportAbsenceAndMissingEvidence() {
        assertEquals(ImageInputCapability.SUPPORTED,
                ImageInputCapability.fromInputModalities(List.of("text", "image")));
        assertEquals(ImageInputCapability.UNSUPPORTED,
                ImageInputCapability.fromInputModalities(List.of("text", "audio")));
        assertEquals(ImageInputCapability.UNSUPPORTED,
                ImageInputCapability.fromInputModalities(List.of()));
        assertEquals(ImageInputCapability.UNKNOWN, ImageInputCapability.fromInputModalities(null));
    }

    @Test void explicitChoiceOutranksTrustedAndBuiltinWithoutChangingEndpointOrModel() {
        var metadata = metadata(ImageInputCapability.UNSUPPORTED);
        var cache = Map.of(metadata.key(), metadata);
        var builtin = resolve(PRIVATE, "gpt-6-luna", null, cache);
        assertEquals(ImageInputCapability.SUPPORTED, builtin.capability());
        assertEquals(ModelImageCapabilityResolution.Origin.BUILTIN, builtin.origin());
        assertEquals("models-dev", builtin.source());
        var trusted = resolve(ROUTER, "gpt-6-luna", null, cache);
        assertEquals(ImageInputCapability.UNSUPPORTED, trusted.capability());
        assertEquals(ModelImageCapabilityResolution.Origin.TRUSTED, trusted.origin());
        assertEquals("openrouter", trusted.source());
        assertEquals(Instant.EPOCH, trusted.capturedAt());
        var manual = resolve(ROUTER, "gpt-6-luna", ImageInputCapability.SUPPORTED, cache);
        assertEquals(ImageInputCapability.SUPPORTED, manual.capability());
        assertEquals(ModelImageCapabilityResolution.Origin.EXPLICIT, manual.origin());
        assertNull(manual.source());
        var suppressed = resolve(ROUTER, "gpt-6-luna", ImageInputCapability.UNKNOWN, cache);
        assertEquals(ImageInputCapability.UNKNOWN, suppressed.capability());
        assertEquals(ModelImageCapabilityResolution.Origin.EXPLICIT, suppressed.origin());
    }

    @Test void unknownTrustedEvidenceFallsBackToKnownBuiltinButNotAnotherIdentity() {
        var unknown = metadata(ImageInputCapability.UNKNOWN);
        assertEquals(ImageInputCapability.SUPPORTED,
                resolve(ROUTER, "gpt-6-luna", null, Map.of(unknown.key(), unknown)).capability());
        var wrong = new ModelMetadata("openrouter", "other-model", "other-model", 100000,
                10000, Instant.EPOCH, ImageInputCapability.SUPPORTED);
        var mismatch = resolve(ROUTER, "private-alias", null,
                Map.of(new ModelMetadata.Key("openrouter", "private-alias"), wrong));
        assertEquals(ImageInputCapability.UNKNOWN, mismatch.capability());
    }

    @Test void fuzzyReferenceMatchNeverEstablishesImageSupportAndAliasesCanBeOverridden() {
        assertEquals(BuiltinModelMatcher.Kind.SIMILAR,
                catalog().match("gpt-6-lunna").orElseThrow().kind());
        assertEquals(ImageInputCapability.UNKNOWN, resolve(PRIVATE, "gpt-6-lunna", null, Map.of()).capability());
        assertEquals(ImageInputCapability.SUPPORTED,
                resolve(PRIVATE, "gateway/gpt-6-luna", null, Map.of()).capability());
        var alias = resolve(PRIVATE, "private-alias", ImageInputCapability.SUPPORTED, Map.of());
        assertEquals(ModelImageCapabilityResolution.Origin.EXPLICIT, alias.origin());
        assertEquals(ImageInputCapability.SUPPORTED, alias.capability());
    }

    @Test void bundledSourcesAreIndependentFromOldLimitsAndRouterMissingEvidenceStaysUnknown() {
        var bundled = BuiltinModelCatalog.bundled();
        assertNull(bundled.failure());
        var direct = bundled.catalog().match("openai/gpt-5.4").orElseThrow().entry();
        assertEquals(ImageInputCapability.SUPPORTED, direct.imageInputCapability());
        assertEquals("models-dev", direct.capabilitySource());
        assertEquals("models-dev-image-input", direct.imageInputCapabilitySource());
        assertEquals(ImageInputCapability.UNSUPPORTED,
                bundled.catalog().match("ai21/jamba-large").orElseThrow().entry().imageInputCapability());
        assertTrue(bundled.catalog().models().stream().filter(row -> row.provider().equals("openrouter"))
                .allMatch(row -> row.imageInputCapability() == ImageInputCapability.UNKNOWN
                        && row.imageInputCapabilitySource() == null));
    }

    private ModelImageCapabilityResolution resolve(URI endpoint, String model,
            ImageInputCapability explicit, Map<ModelMetadata.Key, ModelMetadata> cache) {
        return ModelImageCapabilityResolution.resolve(endpoint, model, explicit, cache, catalog());
    }

    private ModelMetadata metadata(ImageInputCapability capability) {
        return new ModelMetadata("openrouter", "gpt-6-luna", "canonical", 100000, 10000,
                Instant.EPOCH, capability);
    }
}
