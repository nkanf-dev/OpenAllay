package dev.openallay.client.gui.settings;

import dev.openallay.model.metadata.BuiltinModelCatalog;
import dev.openallay.model.metadata.BuiltinModelMatcher;
import dev.openallay.model.metadata.ModelContextResolution;
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
        private BuiltinModelCatalog.Load loaded;
        private BuiltinModelSettingsProjection projection = new BuiltinModelSettingsProjection(List.of());

        public ModelProfileDraft refresh(ModelProfileDraft candidate, BuiltinModelCatalog.Load catalog,
                java.util.function.Supplier<ModelContextResolution> resolve) {
            ModelContextResolution effective = resolve.get();
            ModelProfileDraft updated = candidate.withAutomaticContext(effective.contextWindowTokens());
            if (!updated.equals(draft) || !effective.equals(resolution) || catalog != loaded) {
                draft = updated;
                resolution = effective;
                loaded = catalog;
                projection = from(updated, catalog, effective);
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

    private static Integer parseInteger(String text) {
        try { return Integer.valueOf(text.trim()); }
        catch (NumberFormatException invalid) { return null; }
    }

    public static BuiltinModelSettingsProjection from(ModelProfileDraft draft,
            BuiltinModelCatalog.Load loaded, ModelContextResolution resolution) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.of("context_source." + resolution.origin().name().toLowerCase(java.util.Locale.ROOT)));
        if (resolution.origin() == ModelContextResolution.Origin.TRUSTED
                && resolution.source() != null && resolution.capturedAt() != null) {
            String source = resolution.source().equals("openrouter") ? "OpenRouter" : resolution.source();
            lines.add(Line.of("effective_source", source, resolution.capturedAt().toString()));
        }
        lines.add(Line.of("reset_auto"));
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
