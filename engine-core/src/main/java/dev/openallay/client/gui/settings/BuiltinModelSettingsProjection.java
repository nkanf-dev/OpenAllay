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
@dev.openallay.value.ValueType(BuiltinModelSettingsProjection.ValueSchemaProvider.class)
public final class BuiltinModelSettingsProjection {
    private final List<Line> lines;
    public BuiltinModelSettingsProjection(List<Line> lines) {
 lines = dev.openallay.util.Java8Collections.listCopyOf(lines);
        this.lines = lines;
    }
    public List<Line> lines() { return lines; }
private static final String PREFIX = "screen.openallay.settings.models.builtin.";
public static final class EventCache {
        private ModelProfileDraft draft;
        private ModelContextResolution resolution;
        private ModelOutputResolution outputResolution;
        private ModelImageCapabilityResolution imageResolution;
        private BuiltinModelCatalog.Load loaded;
        private BuiltinModelSettingsProjection projection = new BuiltinModelSettingsProjection(dev.openallay.util.Java8Collections.listOf());

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
@dev.openallay.value.ValueType(Line.ValueSchemaProvider.class)
public static final class Line {
    private final String key;
    private final List<Object> arguments;
    public Line(String key, List<Object> arguments) {
 arguments = dev.openallay.util.Java8Collections.listCopyOf(arguments);
        this.key = key;
        this.arguments = arguments;
    }
    public String key() { return key; }
    public List<Object> arguments() { return arguments; }
public static Line of(String key, Object... args) {
            return new Line(PREFIX + key, dev.openallay.util.Java8Collections.listOf(args));
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Line)) return false;
        Line that = (Line) other;
        return java.util.Objects.equals(key, that.key) && java.util.Objects.equals(arguments, that.arguments);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(key);
        hash = 31 * hash + java.util.Objects.hashCode(arguments);
        return hash;
    }
    @Override public String toString() { return "Line[key=" + key + ", arguments=" + arguments + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Line> schema() {
            return new dev.openallay.value.ValueSchema<>(Line.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Line>>asList(new dev.openallay.value.ValueSchema.Component<>(Line.class, "key", Line::key), new dev.openallay.value.ValueSchema.Component<>(Line.class, "arguments", Line::arguments)), arguments -> new Line((String) arguments[0], (List) arguments[1]));
        }
    }
}
public static BuiltinModelSettingsProjection from(ModelProfileDraft draft) {
        return from(draft, BuiltinModelCatalog.bundled());
    }
public static BuiltinModelSettingsProjection from(
            ModelProfileDraft draft, BuiltinModelCatalog.Load loaded) {
        return from(draft, loaded, ModelContextResolution.resolve(null, draft.model(),
                draft.automaticContextWindowTokens() == null && draft.contextWindowTokens() != null
                        && !dev.openallay.util.Java8Strings.isBlank(draft.contextWindowTokens())
                        ? parseInteger(draft.contextWindowTokens()) : null,
                dev.openallay.util.Java8Collections.mapOf(), loaded.catalog()));
    }
private static ModelOutputResolution outputResolution(
            ModelProfileDraft draft, BuiltinModelCatalog.Load loaded) {
        Integer explicit = null;
        if (draft.automaticMaxOutputTokens() == null && draft.maxOutputTokens() != null
                && !dev.openallay.util.Java8Strings.isBlank(draft.maxOutputTokens())) {
            explicit = parseInteger(draft.maxOutputTokens());
            if (explicit == null || explicit <= 0) {
                return new ModelOutputResolution(null, ModelOutputResolution.Origin.REQUIRED);
            }
        }
        return ModelOutputResolution.resolve(null, draft.model(), explicit,
                dev.openallay.util.Java8Collections.mapOf(), loaded.catalog());
    }
private static ModelImageCapabilityResolution imageResolution(
            ModelProfileDraft draft, BuiltinModelCatalog.Load loaded) {
        return ModelImageCapabilityResolution.resolve(null, draft.model(),
                draft.imageInputCapabilityOverride(), dev.openallay.util.Java8Collections.mapOf(), loaded.catalog());
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
            dev.openallay.model.metadata.BuiltinModelCatalog.Source publishedSource = loaded.catalog().sources().get(imageResolution.source());
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
        java.util.Optional<dev.openallay.model.metadata.BuiltinModelMatcher.Match> matched = loaded.catalog().match(draft.model());
        if (dev.openallay.util.Java8ApiSupport.isEmpty(matched)) {
            lines.add(Line.of("unmatched"));
            return new BuiltinModelSettingsProjection(lines);
        }
        BuiltinModelMatcher.Match match = matched.get();
        dev.openallay.model.metadata.BuiltinModelCatalog.Entry entry = match.entry();
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
            for (dev.openallay.model.metadata.BuiltinModelCatalog.Tier tier : entry.pricing().tiers()) {
                lines.add(Line.of("tier", tier.minInputTokens()));
                price(lines, "input", tier.input());
                price(lines, "output_price", tier.output());
                price(lines, "cache_read", tier.cacheRead());
                price(lines, "cache_write", tier.cacheWrite());
            }
            if (!dev.openallay.util.Java8Strings.isBlank(entry.pricing().note())) lines.add(Line.of("note", entry.pricing().note()));
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BuiltinModelSettingsProjection)) return false;
        BuiltinModelSettingsProjection that = (BuiltinModelSettingsProjection) other;
        return java.util.Objects.equals(lines, that.lines);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(lines);
        return hash;
    }
    @Override public String toString() { return "BuiltinModelSettingsProjection[lines=" + lines + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<BuiltinModelSettingsProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(BuiltinModelSettingsProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<BuiltinModelSettingsProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(BuiltinModelSettingsProjection.class, "lines", BuiltinModelSettingsProjection::lines)), arguments -> new BuiltinModelSettingsProjection((List) arguments[0]));
        }
    }
}
