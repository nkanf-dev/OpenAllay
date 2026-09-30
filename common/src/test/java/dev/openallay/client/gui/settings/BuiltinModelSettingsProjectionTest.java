package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import dev.openallay.model.metadata.ModelContextResolution;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class BuiltinModelSettingsProjectionTest {
    @Test void publishesChosenIdentityAllPriceTiersSourcesAndEstimateNotice() {
        var loaded = BuiltinModelCatalog.parse(new InputStreamReader(getClass().getResourceAsStream(
                "/model-metadata/builtin-sample.json"), StandardCharsets.UTF_8));
        var draft = ModelProfileDraft.create("main").withModel("gateway/gpt-6-luna", loaded.catalog());
        var projection = BuiltinModelSettingsProjection.from(draft, loaded);
        String text = projection.toString();
        assertTrue(text.contains("openai/gpt-6-luna"));
        assertTrue(text.contains("match.normalized"));
        assertTrue(text.contains("price_estimate"));
        assertTrue(text.contains("272000"));
        assertTrue(text.contains("0.75"));
        assertTrue(text.contains("https://models.dev/api.json"));
        assertTrue(text.contains("2026-09-30T16:22:41Z"));
        assertFalse(text.contains("credentialRef"));
        assertFalse(text.contains("apiKey"));
    }
    @Test void trustedEffectiveContextIsNotMislabelledAsBuiltinAndUnknownIsManualRequired() {
        var draft = ModelProfileDraft.create("main").withModel("gpt-6-luna");
        var loaded = BuiltinModelCatalog.bundled();
        var trusted = BuiltinModelSettingsProjection.from(draft, loaded,
                new ModelContextResolution(600_000, ModelContextResolution.Origin.TRUSTED,
                        "openrouter", java.time.Instant.parse("2026-09-30T12:00:00Z")));
        assertTrue(trusted.toString().contains("context_source.trusted"));
        assertTrue(trusted.toString().contains("OpenRouter"));
        assertTrue(trusted.toString().contains("2026-09-30T12:00:00Z"));
        assertTrue(trusted.toString().contains("reset_auto"));
        assertFalse(trusted.toString().contains("baseUri"));
        var unknown = BuiltinModelSettingsProjection.from(draft.withModel("unpublished-model"), loaded);
        assertTrue(unknown.toString().contains("unmatched"));
        assertTrue(unknown.toString().contains("context_source.required"));
    }
    @Test void unmatchedReferenceDoesNotInvalidateExplicitOrProviderContext() throws Exception {
        var draft = ModelProfileDraft.create("main").withModel("unpublished-model");
        for (var origin : java.util.List.of(ModelContextResolution.Origin.EXPLICIT,
                ModelContextResolution.Origin.TRUSTED)) {
            var projection = BuiltinModelSettingsProjection.from(draft, BuiltinModelCatalog.bundled(),
                    new ModelContextResolution(1_000_000, origin));
            assertTrue(projection.toString().contains("unmatched"));
            assertFalse(projection.toString().contains("context_source.required"));
        }
        var english = com.google.gson.JsonParser.parseReader(new InputStreamReader(
                getClass().getResourceAsStream("/assets/openallay/lang/en_us.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        assertFalse(english.get("screen.openallay.settings.models.builtin.unmatched")
                .getAsString().toLowerCase().contains("enter"));
    }

    @Test void renderingReadsCachedProjectionWithoutAnyResolverCalls() {
        var cache = new BuiltinModelSettingsProjection.EventCache();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var draft = ModelProfileDraft.create("main").withModel("gpt-6-luna");
        var updated = cache.refresh(draft, BuiltinModelCatalog.bundled(), () -> {
            calls.incrementAndGet();
            return new ModelContextResolution(600_000, ModelContextResolution.Origin.TRUSTED);
        });
        assertEquals("600000", updated.contextWindowTokens());
        var first = cache.projection();
        for (int frame = 0; frame < 100; frame++) assertSame(first, cache.projection());
        assertEquals(1, calls.get());
        cache.refresh(updated, BuiltinModelCatalog.bundled(), () -> {
            calls.incrementAndGet();
            return new ModelContextResolution(1_050_000, ModelContextResolution.Origin.BUILTIN);
        });
        assertEquals(2, calls.get());
        assertNotSame(first, cache.projection());
        for (int frame = 0; frame < 100; frame++) cache.projection();
        assertEquals(2, calls.get());
    }

    @Test void allProjectionKeysExistInEnglishAndChinese() throws Exception {
        var draft = ModelProfileDraft.create("main").withModel("gpt-6-luna");
        var lines = BuiltinModelSettingsProjection.from(draft).lines();
        for (String locale : java.util.List.of("en_us", "zh_cn")) {
            var language = com.google.gson.JsonParser.parseReader(new InputStreamReader(
                    getClass().getResourceAsStream("/assets/openallay/lang/" + locale + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            for (var line : lines) assertTrue(language.has(line.key()), line.key());
        }
    }
}
