package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.image.ImageInputCapability;
import dev.openallay.model.metadata.BuiltinModelCatalog;
import dev.openallay.model.metadata.ModelContextResolution;
import dev.openallay.model.metadata.ModelImageCapabilityResolution;
import dev.openallay.model.metadata.ModelOutputResolution;
import dev.openallay.tool.ToolResult;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class ModelImageSettingsProjectionTest {
    @Test void manualChoiceCanCycleBackToAutomaticAndUnknownIsNotUnsupported() {
        var automatic = new ModelImageSettingsProjection(null);
        assertTrue(automatic.selectedLabelKey().endsWith("auto"));
        assertEquals(ImageInputCapability.SUPPORTED, automatic.next());
        assertEquals(ImageInputCapability.UNSUPPORTED,
                new ModelImageSettingsProjection(ImageInputCapability.SUPPORTED).next());
        assertEquals(ImageInputCapability.UNKNOWN,
                new ModelImageSettingsProjection(ImageInputCapability.UNSUPPORTED).next());
        assertNull(new ModelImageSettingsProjection(ImageInputCapability.UNKNOWN).next());
    }

    @Test void profileDraftRetainsManualChoiceAcrossOtherEditsButAutomaticSaveDoesNotPinMetadata() {
        var definition = new ModelProfileDefinition("main", "Main", true, ModelProtocol.OPENAI_CHAT,
                URI.create("https://gateway.example/v1/"), "gpt-5.4", "env:KEY", null, null,
                Duration.ofSeconds(30), Duration.ofSeconds(300), null);
        var automatic = ModelProfileDraft.from(definition);
        assertNull(success(automatic).imageInputCapabilityOverride());
        assertFalse(automatic.dirtyComparedTo(definition));
        var manual = automatic.withImageInputCapabilityOverride(ImageInputCapability.SUPPORTED)
                .withContextWindow("100000").withMaxOutput("10000");
        assertEquals(ImageInputCapability.SUPPORTED, success(manual).imageInputCapabilityOverride());
        assertTrue(manual.dirtyComparedTo(definition));
        assertEquals(ImageInputCapability.SUPPORTED,
                success(manual.withModel("private-alias")).imageInputCapabilityOverride());
        assertNull(success(manual.withImageInputCapabilityOverride(null)).imageInputCapabilityOverride());
    }

    @Test void effectiveImageEvidenceIsEventCachedAndNotCoupledToContextOutputSources() {
        var cache = new BuiltinModelSettingsProjection.EventCache();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var draft = ModelProfileDraft.create("main").withModel("private-alias");
        cache.refresh(draft, BuiltinModelCatalog.bundled(),
                () -> new ModelContextResolution(100000, ModelContextResolution.Origin.EXPLICIT),
                () -> new ModelOutputResolution(10000, ModelOutputResolution.Origin.EXPLICIT),
                () -> {
                    calls.incrementAndGet();
                    return new ModelImageCapabilityResolution(ImageInputCapability.UNSUPPORTED,
                            ModelImageCapabilityResolution.Origin.TRUSTED, "openrouter", Instant.EPOCH);
                });
        var first = cache.projection();
        String text = first.toString();
        assertTrue(text.contains("image_input.unsupported"));
        assertTrue(text.contains("image_source.trusted"));
        assertTrue(text.contains("1970-01-01T00:00:00Z"));
        assertTrue(text.contains("unmatched"));
        assertTrue(text.contains("context_source.explicit"));
        for (int frame = 0; frame < 100; frame++) assertSame(first, cache.projection());
        assertEquals(1, calls.get());
    }

    @Test void allImageProjectionAndChoiceKeysExistInEnglishAndChinese() {
        for (String locale : java.util.List.of("en_us", "zh_cn")) {
            var language = com.google.gson.JsonParser.parseReader(new InputStreamReader(
                    getClass().getResourceAsStream("/assets/openallay/lang/" + locale + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            for (ImageInputCapability capability : ImageInputCapability.values()) {
                var draft = ModelProfileDraft.create("main").withModel("private-alias")
                        .withImageInputCapabilityOverride(capability);
                assertTrue(language.has(new ModelImageSettingsProjection(capability).selectedLabelKey()));
                for (var line : BuiltinModelSettingsProjection.from(draft).lines()) {
                    assertTrue(language.has(line.key()), line.key());
                }
            }
            assertTrue(language.has(new ModelImageSettingsProjection(null).selectedLabelKey()));
            assertTrue(language.has(new ModelImageSettingsProjection(null).explanationKey()));
        }
    }

    private ModelProfileDefinition success(ModelProfileDraft draft) {
        return ((ToolResult.Success<ModelProfileDefinition>) draft.validate()).value();
    }
}
