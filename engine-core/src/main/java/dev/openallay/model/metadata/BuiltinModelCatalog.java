package dev.openallay.model.metadata;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.openallay.guide.GuideFailure;
import dev.openallay.model.image.ImageInputCapability;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reviewed offline metadata, not a provider registry or provider metadata cache. */
public final class BuiltinModelCatalog {
    public static final String RESOURCE = "/data/openallay/models/builtin-model-catalog.json";
    private final String version;
    private final Instant publishedAt;
    private final Map<String, Source> sources;
    private final List<Entry> models;
    private final BuiltinModelMatcher.Index index;

    public BuiltinModelCatalog(String version, Instant publishedAt,
            Map<String, Source> sources, List<Entry> models) {
        this.version = version;
        this.publishedAt = publishedAt;
        this.sources = dev.openallay.util.Java8Collections.mapCopyOf(sources);
        this.models = dev.openallay.util.Java8Collections.listCopyOf(models);
        this.index = new BuiltinModelMatcher.Index(this.models);
    }
    public String version() { return version; }
    public Instant publishedAt() { return publishedAt; }
    public Map<String, Source> sources() { return sources; }
    public List<Entry> models() { return models; }

    @dev.openallay.value.ValueType(Source.ValueSchemaProvider.class)
public static final class Source {
    private final String id;
    private final String label;
    private final URI url;
    private final Instant capturedAt;
    public Source(String id, String label, URI url, Instant capturedAt) {
        this.id = id;
        this.label = label;
        this.url = url;
        this.capturedAt = capturedAt;
    }
    public String id() { return id; }
    public String label() { return label; }
    public URI url() { return url; }
    public Instant capturedAt() { return capturedAt; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Source)) return false;
        Source that = (Source) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(label, that.label) && java.util.Objects.equals(url, that.url) && java.util.Objects.equals(capturedAt, that.capturedAt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(label);
        hash = 31 * hash + java.util.Objects.hashCode(url);
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        return hash;
    }
    @Override public String toString() { return "Source[id=" + id + ", label=" + label + ", url=" + url + ", capturedAt=" + capturedAt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Source> schema() {
            return new dev.openallay.value.ValueSchema<>(Source.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Source>>asList(new dev.openallay.value.ValueSchema.Component<>(Source.class, "id", Source::id), new dev.openallay.value.ValueSchema.Component<>(Source.class, "label", Source::label), new dev.openallay.value.ValueSchema.Component<>(Source.class, "url", Source::url), new dev.openallay.value.ValueSchema.Component<>(Source.class, "capturedAt", Source::capturedAt)), arguments -> new Source((String) arguments[0], (String) arguments[1], (URI) arguments[2], (Instant) arguments[3]));
        }
    }
}
    @dev.openallay.value.ValueType(Entry.ValueSchemaProvider.class)
public static final class Entry {
    private final String id;
    private final String provider;
    private final String family;
    private final List<String> aliases;
    private final int contextWindowTokens;
    private final Integer maxOutputTokens;
    private final Pricing pricing;
    private final String capabilitySource;
    private final String pricingSource;
    private final String upstreamModelId;
    private final ImageInputCapability imageInputCapability;
    private final String imageInputCapabilitySource;
    public Entry(String id, String provider, String family, List<String> aliases, int contextWindowTokens, Integer maxOutputTokens, Pricing pricing, String capabilitySource, String pricingSource, String upstreamModelId, ImageInputCapability imageInputCapability, String imageInputCapabilitySource) {

            aliases = dev.openallay.util.Java8Collections.listCopyOf(aliases);
            java.util.Objects.requireNonNull(imageInputCapability, "imageInputCapability");

        this.id = id;
        this.provider = provider;
        this.family = family;
        this.aliases = aliases;
        this.contextWindowTokens = contextWindowTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.pricing = pricing;
        this.capabilitySource = capabilitySource;
        this.pricingSource = pricingSource;
        this.upstreamModelId = upstreamModelId;
        this.imageInputCapability = imageInputCapability;
        this.imageInputCapabilitySource = imageInputCapabilitySource;
    }
    public String id() { return id; }
    public String provider() { return provider; }
    public String family() { return family; }
    public List<String> aliases() { return aliases; }
    public int contextWindowTokens() { return contextWindowTokens; }
    public Integer maxOutputTokens() { return maxOutputTokens; }
    public Pricing pricing() { return pricing; }
    public String capabilitySource() { return capabilitySource; }
    public String pricingSource() { return pricingSource; }
    public String upstreamModelId() { return upstreamModelId; }
    public ImageInputCapability imageInputCapability() { return imageInputCapability; }
    public String imageInputCapabilitySource() { return imageInputCapabilitySource; }
public Entry(String id, String provider, String family, List<String> aliases,
                int contextWindowTokens, Integer maxOutputTokens, Pricing pricing,
                String capabilitySource, String pricingSource, String upstreamModelId) {
            this(id, provider, family, aliases, contextWindowTokens, maxOutputTokens, pricing,
                    capabilitySource, pricingSource, upstreamModelId, ImageInputCapability.UNKNOWN, null);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Entry)) return false;
        Entry that = (Entry) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(provider, that.provider) && java.util.Objects.equals(family, that.family) && java.util.Objects.equals(aliases, that.aliases) && contextWindowTokens == that.contextWindowTokens && java.util.Objects.equals(maxOutputTokens, that.maxOutputTokens) && java.util.Objects.equals(pricing, that.pricing) && java.util.Objects.equals(capabilitySource, that.capabilitySource) && java.util.Objects.equals(pricingSource, that.pricingSource) && java.util.Objects.equals(upstreamModelId, that.upstreamModelId) && java.util.Objects.equals(imageInputCapability, that.imageInputCapability) && java.util.Objects.equals(imageInputCapabilitySource, that.imageInputCapabilitySource);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(provider);
        hash = 31 * hash + java.util.Objects.hashCode(family);
        hash = 31 * hash + java.util.Objects.hashCode(aliases);
        hash = 31 * hash + Integer.hashCode(contextWindowTokens);
        hash = 31 * hash + java.util.Objects.hashCode(maxOutputTokens);
        hash = 31 * hash + java.util.Objects.hashCode(pricing);
        hash = 31 * hash + java.util.Objects.hashCode(capabilitySource);
        hash = 31 * hash + java.util.Objects.hashCode(pricingSource);
        hash = 31 * hash + java.util.Objects.hashCode(upstreamModelId);
        hash = 31 * hash + java.util.Objects.hashCode(imageInputCapability);
        hash = 31 * hash + java.util.Objects.hashCode(imageInputCapabilitySource);
        return hash;
    }
    @Override public String toString() { return "Entry[id=" + id + ", provider=" + provider + ", family=" + family + ", aliases=" + aliases + ", contextWindowTokens=" + contextWindowTokens + ", maxOutputTokens=" + maxOutputTokens + ", pricing=" + pricing + ", capabilitySource=" + capabilitySource + ", pricingSource=" + pricingSource + ", upstreamModelId=" + upstreamModelId + ", imageInputCapability=" + imageInputCapability + ", imageInputCapabilitySource=" + imageInputCapabilitySource + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Entry> schema() {
            return new dev.openallay.value.ValueSchema<>(Entry.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Entry>>asList(new dev.openallay.value.ValueSchema.Component<>(Entry.class, "id", Entry::id), new dev.openallay.value.ValueSchema.Component<>(Entry.class, "provider", Entry::provider), new dev.openallay.value.ValueSchema.Component<>(Entry.class, "family", Entry::family), new dev.openallay.value.ValueSchema.Component<>(Entry.class, "aliases", Entry::aliases), new dev.openallay.value.ValueSchema.Component<>(Entry.class, "contextWindowTokens", Entry::contextWindowTokens), new dev.openallay.value.ValueSchema.Component<>(Entry.class, "maxOutputTokens", Entry::maxOutputTokens), new dev.openallay.value.ValueSchema.Component<>(Entry.class, "pricing", Entry::pricing), new dev.openallay.value.ValueSchema.Component<>(Entry.class, "capabilitySource", Entry::capabilitySource), new dev.openallay.value.ValueSchema.Component<>(Entry.class, "pricingSource", Entry::pricingSource), new dev.openallay.value.ValueSchema.Component<>(Entry.class, "upstreamModelId", Entry::upstreamModelId), new dev.openallay.value.ValueSchema.Component<>(Entry.class, "imageInputCapability", Entry::imageInputCapability), new dev.openallay.value.ValueSchema.Component<>(Entry.class, "imageInputCapabilitySource", Entry::imageInputCapabilitySource)), arguments -> new Entry((String) arguments[0], (String) arguments[1], (String) arguments[2], (List) arguments[3], (Integer) arguments[4], (Integer) arguments[5], (Pricing) arguments[6], (String) arguments[7], (String) arguments[8], (String) arguments[9], (ImageInputCapability) arguments[10], (String) arguments[11]));
        }
    }
}
    @dev.openallay.value.ValueType(Pricing.ValueSchemaProvider.class)
public static final class Pricing {
    private final String currency;
    private final String unit;
    private final List<Tier> tiers;
    private final String note;
    public Pricing(String currency, String unit, List<Tier> tiers, String note) {
 tiers = dev.openallay.util.Java8Collections.listCopyOf(tiers);
        this.currency = currency;
        this.unit = unit;
        this.tiers = tiers;
        this.note = note;
    }
    public String currency() { return currency; }
    public String unit() { return unit; }
    public List<Tier> tiers() { return tiers; }
    public String note() { return note; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Pricing)) return false;
        Pricing that = (Pricing) other;
        return java.util.Objects.equals(currency, that.currency) && java.util.Objects.equals(unit, that.unit) && java.util.Objects.equals(tiers, that.tiers) && java.util.Objects.equals(note, that.note);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(currency);
        hash = 31 * hash + java.util.Objects.hashCode(unit);
        hash = 31 * hash + java.util.Objects.hashCode(tiers);
        hash = 31 * hash + java.util.Objects.hashCode(note);
        return hash;
    }
    @Override public String toString() { return "Pricing[currency=" + currency + ", unit=" + unit + ", tiers=" + tiers + ", note=" + note + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Pricing> schema() {
            return new dev.openallay.value.ValueSchema<>(Pricing.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Pricing>>asList(new dev.openallay.value.ValueSchema.Component<>(Pricing.class, "currency", Pricing::currency), new dev.openallay.value.ValueSchema.Component<>(Pricing.class, "unit", Pricing::unit), new dev.openallay.value.ValueSchema.Component<>(Pricing.class, "tiers", Pricing::tiers), new dev.openallay.value.ValueSchema.Component<>(Pricing.class, "note", Pricing::note)), arguments -> new Pricing((String) arguments[0], (String) arguments[1], (List) arguments[2], (String) arguments[3]));
        }
    }
}
    @dev.openallay.value.ValueType(Tier.ValueSchemaProvider.class)
public static final class Tier {
    private final int minInputTokens;
    private final BigDecimal input;
    private final BigDecimal output;
    private final BigDecimal cacheRead;
    private final BigDecimal cacheWrite;
    public Tier(int minInputTokens, BigDecimal input, BigDecimal output, BigDecimal cacheRead, BigDecimal cacheWrite) {
        this.minInputTokens = minInputTokens;
        this.input = input;
        this.output = output;
        this.cacheRead = cacheRead;
        this.cacheWrite = cacheWrite;
    }
    public int minInputTokens() { return minInputTokens; }
    public BigDecimal input() { return input; }
    public BigDecimal output() { return output; }
    public BigDecimal cacheRead() { return cacheRead; }
    public BigDecimal cacheWrite() { return cacheWrite; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Tier)) return false;
        Tier that = (Tier) other;
        return minInputTokens == that.minInputTokens && java.util.Objects.equals(input, that.input) && java.util.Objects.equals(output, that.output) && java.util.Objects.equals(cacheRead, that.cacheRead) && java.util.Objects.equals(cacheWrite, that.cacheWrite);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(minInputTokens);
        hash = 31 * hash + java.util.Objects.hashCode(input);
        hash = 31 * hash + java.util.Objects.hashCode(output);
        hash = 31 * hash + java.util.Objects.hashCode(cacheRead);
        hash = 31 * hash + java.util.Objects.hashCode(cacheWrite);
        return hash;
    }
    @Override public String toString() { return "Tier[minInputTokens=" + minInputTokens + ", input=" + input + ", output=" + output + ", cacheRead=" + cacheRead + ", cacheWrite=" + cacheWrite + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Tier> schema() {
            return new dev.openallay.value.ValueSchema<>(Tier.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Tier>>asList(new dev.openallay.value.ValueSchema.Component<>(Tier.class, "minInputTokens", Tier::minInputTokens), new dev.openallay.value.ValueSchema.Component<>(Tier.class, "input", Tier::input), new dev.openallay.value.ValueSchema.Component<>(Tier.class, "output", Tier::output), new dev.openallay.value.ValueSchema.Component<>(Tier.class, "cacheRead", Tier::cacheRead), new dev.openallay.value.ValueSchema.Component<>(Tier.class, "cacheWrite", Tier::cacheWrite)), arguments -> new Tier((Integer) arguments[0], (BigDecimal) arguments[1], (BigDecimal) arguments[2], (BigDecimal) arguments[3], (BigDecimal) arguments[4]));
        }
    }
}
    @dev.openallay.value.ValueType(Load.ValueSchemaProvider.class)
public static final class Load {
    private final BuiltinModelCatalog catalog;
    private final GuideFailure failure;
    public Load(BuiltinModelCatalog catalog, GuideFailure failure) {
        this.catalog = catalog;
        this.failure = failure;
    }
    public BuiltinModelCatalog catalog() { return catalog; }
    public GuideFailure failure() { return failure; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Load)) return false;
        Load that = (Load) other;
        return java.util.Objects.equals(catalog, that.catalog) && java.util.Objects.equals(failure, that.failure);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(catalog);
        hash = 31 * hash + java.util.Objects.hashCode(failure);
        return hash;
    }
    @Override public String toString() { return "Load[catalog=" + catalog + ", failure=" + failure + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Load> schema() {
            return new dev.openallay.value.ValueSchema<>(Load.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Load>>asList(new dev.openallay.value.ValueSchema.Component<>(Load.class, "catalog", Load::catalog), new dev.openallay.value.ValueSchema.Component<>(Load.class, "failure", Load::failure)), arguments -> new Load((BuiltinModelCatalog) arguments[0], (GuideFailure) arguments[1]));
        }
    }
}

    public Optional<BuiltinModelMatcher.Match> match(String modelId) {
        return index.match(modelId);
    }

    public static Load bundled() { return Bundled.VALUE; }

    private static final class Bundled {
        private static final Load VALUE = load();
        private static Load load() {
            java.io.InputStream input = BuiltinModelCatalog.class.getResourceAsStream(RESOURCE);
            if (input == null) {
                return failed("builtin_catalog_unavailable");
            }
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return parse(reader);
            } catch (IOException failure) {
                return failed("builtin_catalog_unavailable");
            }
        }
    }

    /** Rejects duplicate keys, unknown fields and partial data. */
    public static Load parse(Reader input) {
        try {
            JsonObject root = object(readStrict(input));
            fields(root, "catalogVersion", "publishedAt", "sources", "models");
            String version = text(root.get("catalogVersion"), false);
            Instant published = Instant.parse(text(root.get("publishedAt"), false));
            Map<String, Source> sources = new LinkedHashMap<>();
            for (JsonElement value : array(root.get("sources"))) {
                JsonObject source = object(value);
                fields(source, "id", "label", "url", "capturedAt");
                String id = text(source.get("id"), false);
                URI url = URI.create(text(source.get("url"), false));
                if (!"https".equals(url.getScheme()) || url.getHost() == null
                        || url.getUserInfo() != null || url.getQuery() != null
                        || url.getFragment() != null) throw invalid();
                Source decoded = new Source(id, text(source.get("label"), false), url,
                        Instant.parse(text(source.get("capturedAt"), false)));
                if (sources.put(id, decoded) != null) throw invalid();
            }
            if (sources.isEmpty()) throw invalid();
            List<Entry> models = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (JsonElement value : array(root.get("models"))) {
                JsonObject model = object(value);
                fields(model, "id", "provider", "family", "aliases", "contextWindowTokens",
                        "maxOutputTokens", "pricing", "capabilitySource", "pricingSource",
                        "upstreamModelId", "imageInputCapability", "imageInputCapabilitySource");
                String id = text(model.get("id"), false);
                if (!ids.add(id.toLowerCase(java.util.Locale.ROOT))) throw invalid();
                List<String> aliases = new ArrayList<>();
                Set<String> aliasIds = new HashSet<>();
                for (JsonElement alias : array(model.get("aliases"))) {
                    String name = text(alias, false);
                    if (!aliasIds.add(name.toLowerCase(java.util.Locale.ROOT))) throw invalid();
                    aliases.add(name);
                }
                String capabilitySource = text(model.get("capabilitySource"), false);
                String pricingSource = nullableText(model.get("pricingSource"));
                if (!sources.containsKey(capabilitySource)
                        || (pricingSource != null && !sources.containsKey(pricingSource))) throw invalid();
                String imageSource = nullableText(model.get("imageInputCapabilitySource"));
                ImageInputCapability imageInput = ImageInputCapability.parse(
                        text(model.get("imageInputCapability"), false));
                if ((imageSource != null && !sources.containsKey(imageSource))
                        || (imageInput != ImageInputCapability.UNKNOWN && imageSource == null)) throw invalid();
                Pricing pricing = model.get("pricing").isJsonNull()
                        ? null : pricing(object(model.get("pricing")));
                if ((pricing == null) != (pricingSource == null)) throw invalid();
                String family = text(model.get("family"), false);
                if (!family.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw invalid();
                models.add(new Entry(id, text(model.get("provider"), false), family,
                        aliases, positive(model.get("contextWindowTokens")),
                        model.get("maxOutputTokens").isJsonNull()
                                ? null : positive(model.get("maxOutputTokens")),
                        pricing, capabilitySource, pricingSource,
                        text(model.get("upstreamModelId"), false),
                        imageInput, imageSource));
            }
            if (models.isEmpty()) throw invalid();
            return new Load(new BuiltinModelCatalog(version, published, sources, models), null);
        } catch (IOException | RuntimeException failure) {
            return failed("builtin_catalog_invalid");
        }
    }

    private static Pricing pricing(JsonObject object) {
        fields(object, "currency", "unit", "tiers", "note");
        if (!"USD".equals(text(object.get("currency"), false))
                || !"million_tokens".equals(text(object.get("unit"), false))) throw invalid();
        List<Tier> tiers = new ArrayList<>();
        int previous = -1;
        for (JsonElement value : array(object.get("tiers"))) {
            JsonObject tier = object(value);
            fields(tier, "minInputTokens", "input", "output", "cacheRead", "cacheWrite");
            int threshold = integer(tier.get("minInputTokens"));
            if (threshold < 0 || threshold <= previous
                    || (tiers.isEmpty() && threshold != 0)) throw invalid();
            tiers.add(new Tier(threshold, rate(tier.get("input")), rate(tier.get("output")),
                    rate(tier.get("cacheRead")), rate(tier.get("cacheWrite"))));
            previous = threshold;
        }
        if (tiers.isEmpty()) throw invalid();
        return new Pricing("USD", "million_tokens", tiers, text(object.get("note"), true));
    }

    private static BigDecimal rate(JsonElement value) {
        if (value.isJsonNull()) return null;
        String text = text(value, false);
        if (!text.matches("[0-9]+(?:\\.[0-9]+)?")) throw invalid();
        BigDecimal rate = new BigDecimal(text);
        if (rate.signum() < 0) throw invalid();
        return rate;
    }
    static JsonElement readStrict(Reader input) throws IOException {
        JsonReader reader = dev.openallay.json.JsonReaders.strict(input);

        JsonElement value = read(reader);
        if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid();
        return value;
    }

    private static JsonElement read(JsonReader reader) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT: {
                reader.beginObject();
                JsonObject value = new JsonObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (value.has(name)) throw invalid();
                    value.add(name, read(reader));
                }
                reader.endObject();
                return value;
            }
            case BEGIN_ARRAY: {
                reader.beginArray();
                JsonArray value = new JsonArray();
                while (reader.hasNext()) value.add(read(reader));
                reader.endArray();
                return value;
            }
            case STRING: return new JsonPrimitive(reader.nextString());
            case NUMBER: return new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN: return new JsonPrimitive(reader.nextBoolean());
            case NULL: reader.nextNull(); return JsonNull.INSTANCE;
            default: throw invalid();
        }
    }
    private static void fields(JsonObject value, String... fields) {
        if (!dev.openallay.json.JsonTrees.keys(value).equals(dev.openallay.util.Java8Collections.setOf(fields))) throw invalid();
    }
    private static JsonObject object(JsonElement value) {
        if (value == null || !value.isJsonObject()) throw invalid();
        return value.getAsJsonObject();
    }
    private static JsonArray array(JsonElement value) {
        if (value == null || !value.isJsonArray()) throw invalid();
        return value.getAsJsonArray();
    }
    private static String text(JsonElement value, boolean blankAllowed) {
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) throw invalid();
        String text = value.getAsString();
        if ((!blankAllowed && dev.openallay.util.Java8Strings.isBlank(text)) || text.codePoints().anyMatch(Character::isISOControl))
            throw invalid();
        return text;
    }
    private static String nullableText(JsonElement value) {
        return value.isJsonNull() ? null : text(value, false);
    }
    private static int integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) throw invalid();
        return value.getAsBigDecimal().intValueExact();
    }
    private static int positive(JsonElement value) {
        int number = integer(value);
        if (number <= 0) throw invalid();
        return number;
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid builtin model catalog");
    }
    private static Load failed(String code) {
        return new Load(new BuiltinModelCatalog("unavailable", Instant.EPOCH, dev.openallay.util.Java8Collections.mapOf(), dev.openallay.util.Java8Collections.listOf()),
                new GuideFailure(code, "The builtin model catalog is unavailable"));
    }
}
