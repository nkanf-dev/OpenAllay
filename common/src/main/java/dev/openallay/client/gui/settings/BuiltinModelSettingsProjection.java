package dev.openallay.client.gui.settings;

import dev.openallay.model.metadata.BuiltinModelCatalog;
import dev.openallay.model.metadata.BuiltinModelMatcher;
import dev.openallay.model.metadata.ModelContextResolution;
import dev.openallay.model.metadata.ModelOutputResolution;
import dev.openallay.model.metadata.ModelImageCapabilityResolution;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Credential-free localized published estimates for one typed model name. */
public record BuiltinModelSettingsProjection(List<Line> lines) {
    private static final String PREFIX = "screen.openallay.settings.models.builtin.";
    public BuiltinModelSettingsProjection { lines = List.copyOf(lines); }
    /** Updated only by editor/metadata events. A render read performs no resolution or matching. */
    public static final class EventCache {
        private ModelProfileDraft draft;
        private ModelContextResolution resolution;
        private ModelOutputResolution outputResolution;
        private ModelImageCapabilityResolution imageResolution;
        private BuiltinModelCatalog.Load loaded;
        private BuiltinModelSettingsProjection projection = new BuiltinModelSettingsProjection(List.of());

        public ModelProfileDraft refresh(ModelProfileDraft candidate, BuiltinModelCatalog.Load catalog,
                java.util.function.Supplier<ModelContextResolution> resolve) {
            return refresh(candidate, catalog, resolve, () -> outputResolution(candidate, catalog));
        }

        public ModelProfileDraft refresh(ModelProfileDraft candidate, BuiltinModelCatalog.Load catalog,
                java.util.function.Supplier<ModelContextResolution> resolve,
                java.util.function.Supplier<ModelOutputResolution> resolveOutput) {
            return refresh(candidate, catalog, resolve, resolveOutput,
                    () -> imageResolution(candidate, catalog));
        }

        public ModelProfileDraft refresh(ModelProfileDraft candidate, BuiltinModelCatalog.Load catalog,
                java.util.function.Supplier<ModelContextResolution> resolve,
                java.util.function.Supplier<ModelOutputResolution> resolveOutput,
                java.util.function.Supplier<ModelImageCapabilityResolution> resolveImage) {
            ModelContextResolution effective = resolve.get();
            ModelOutputResolution effectiveOutput = resolveOutput.get();
            ModelImageCapabilityResolution effectiveImage = resolveImage.get();
            ModelProfileDraft updated = candidate.withAutomaticContext(effective.contextWindowTokens())
                    .withAutomaticOutput(effectiveOutput.maxOutputTokens());
            if (!updated.equals(draft) || !effective.equals(resolution)
                    || !effectiveOutput.equals(outputResolution)
                    || !effectiveImage.equals(imageResolution) || catalog != loaded) {
                draft = updated;
                resolution = effective;
                outputResolution = effectiveOutput;
                imageResolution = effectiveImage;
                loaded = catalog;
                projection = from(updated, catalog, effective, effectiveOutput, effectiveImage);
            }
            return updated;
        }

        public BuiltinModelSettingsProjection projection() { return projection; }
    }

    public record Line(String key, List<Object> arguments) {
        public Line { arguments = List.copyOf(arguments); }
        public static Line of(String key, Object... args) {
            return new Line(PREFIX + key, List.of(args));
        }
    }

    public static BuiltinModelSettingsProjection from(ModelProfileDraft draft) {
        return from(draft, BuiltinModelCatalog.bundled());
    }

    public static BuiltinModelSettingsProjection from(
            ModelProfileDraft draft, BuiltinModelCatalog.Load loaded) {
        return from(draft, loaded, ModelContextResolution.resolve(null, draft.model(),
                draft.automaticContextWindowTokens() == null && draft.contextWindowTokens() != null
                        && !draft.contextWindowTokens().isBlank()
                        ? parseInteger(draft.contextWindowTokens()) : null,
                java.util.Map.of(), loaded.catalog()));
    }

    private static ModelOutputResolution outputResolution(
            ModelProfileDraft draft, BuiltinModelCatalog.Load loaded) {
        Integer explicit = null;
        if (draft.automaticMaxOutputTokens() == null && draft.maxOutputTokens() != null
                && !draft.maxOutputTokens().isBlank()) {
            explicit = parseInteger(draft.maxOutputTokens());
            if (explicit == null || explicit <= 0) {
                return new ModelOutputResolution(null, ModelOutputResolution.Origin.REQUIRED);
            }
        }
        return ModelOutputResolution.resolve(null, draft.model(), explicit,
                java.util.Map.of(), loaded.catalog());
    }

    private static ModelImageCapabilityResolution imageResolution(
            ModelProfileDraft draft, BuiltinModelCatalog.Load loaded) {
        return ModelImageCapabilityResolution.resolve(null, draft.model(),
                draft.imageInputCapabilityOverride(), java.util.Map.of(), loaded.catalog());
    }

    private static Integer parseInteger(String text) {
        try { return Integer.valueOf(text.trim()); }
        catch (NumberFormatException invalid) { return null; }
    }

    public static BuiltinModelSettingsProjection from(ModelProfileDraft draft,
            BuiltinModelCatalog.Load loaded, ModelContextResolution resolution) {
        return from(draft, loaded, resolution, outputResolution(draft, loaded));
    }

    public static BuiltinModelSettingsProjection from(ModelProfileDraft draft,
            BuiltinModelCatalog.Load loaded, ModelContextResolution resolution,
            ModelOutputResolution outputResolution) {
        return from(draft, loaded, resolution, outputResolution, imageResolution(draft, loaded));
    }

    public static BuiltinModelSettingsProjection from(ModelProfileDraft draft,
            BuiltinModelCatalog.Load loaded, ModelContextResolution resolution,
            ModelOutputResolution outputResolution, ModelImageCapabilityResolution imageResolution) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.of("image_input." + imageResolution.capability().encoded()));
        lines.add(Line.of("image_source." + imageResolution.origin().name()
                .toLowerCase(java.util.Locale.ROOT)));
        if (imageResolution.source() != null && imageResolution.capturedAt() != null) {
            var publishedSource = loaded.catalog().sources().get(imageResolution.source());
            String sourceLabel = publishedSource == null
                    ? imageResolution.source().equals("openrouter") ? "OpenRouter" : imageResolution.source()
                    : publishedSource.label();
            lines.add(Line.of("image_effective_source", sourceLabel, imageResolution.capturedAt().toString()));
            if (publishedSource != null) lines.add(Line.of("source_url", publishedSource.url().toString()));
        }
        lines.add(Line.of("context_source." + resolution.origin().name().toLowerCase(java.util.Locale.ROOT)));
        if (resolution.origin() == ModelContextResolution.Origin.TRUSTED
                && resolution.source() != null && resolution.capturedAt() != null) {
            String source = resolution.source().equals("openrouter") ? "OpenRouter" : resolution.source();
            lines.add(Line.of("effective_source", source, resolution.capturedAt().toString()));
        }
        lines.add(Line.of("reset_auto"));
        lines.add(Line.of("output_source." + outputResolution.origin().name()
                .toLowerCase(java.util.Locale.ROOT)));
        if (outputResolution.origin() == ModelOutputResolution.Origin.TRUSTED
                && outputResolution.source() != null && outputResolution.capturedAt() != null) {
            String source = outputResolution.source().equals("openrouter")
                    ? "OpenRouter" : outputResolution.source();
            lines.add(Line.of("effective_source", source, outputResolution.capturedAt().toString()));
        }
        lines.add(Line.of("output_reset_auto"));
        if (loaded.failure() != null) {
            lines.add(Line.of("unavailable"));
            return new BuiltinModelSettingsProjection(lines);
        }
        var matched = loaded.catalog().match(draft.model());
        if (matched.isEmpty()) {
            lines.add(Line.of("unmatched"));
            return new BuiltinModelSettingsProjection(lines);
        }
        BuiltinModelMatcher.Match match = matched.get();
        var entry = match.entry();
        lines.add(Line.of("match." + match.kind().name().toLowerCase(java.util.Locale.ROOT), entry.id()));
        lines.add(Line.of(draft.automaticContextWindowTokens() == null
                ? "manual" : "automatic"));
        lines.add(Line.of("context", entry.contextWindowTokens()));
        lines.add(entry.maxOutputTokens() == null ? Line.of("output_unknown")
                : Line.of("output", entry.maxOutputTokens()));
        if (entry.pricing() == null) {
            lines.add(Line.of("price_unknown"));
        } else {
            lines.add(Line.of("price_estimate"));
            for (var tier : entry.pricing().tiers()) {
                lines.add(Line.of("tier", tier.minInputTokens()));
                price(lines, "input", tier.input());
                price(lines, "output_price", tier.output());
                price(lines, "cache_read", tier.cacheRead());
                price(lines, "cache_write", tier.cacheWrite());
            }
            if (!entry.pricing().note().isBlank()) lines.add(Line.of("note", entry.pricing().note()));
        }
        source(lines, "capability_source", loaded.catalog().sources().get(entry.capabilitySource()));
        if (entry.pricingSource() != null)
            source(lines, "pricing_source", loaded.catalog().sources().get(entry.pricingSource()));
        lines.add(Line.of("version", loaded.catalog().version()));
        return new BuiltinModelSettingsProjection(lines);
    }
    private static void price(List<Line> lines, String label, BigDecimal rate) {
        if (rate != null) lines.add(Line.of(label, rate.toPlainString()));
    }
    private static void source(List<Line> lines, String label, BuiltinModelCatalog.Source source) {
        lines.add(Line.of(label, source.label(), source.capturedAt().toString()));
        lines.add(Line.of("source_url", source.url().toString()));
    }
}
