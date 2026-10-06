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
        this.sources = Map.copyOf(sources);
        this.models = List.copyOf(models);
        this.index = new BuiltinModelMatcher.Index(this.models);
    }
    public String version() { return version; }
    public Instant publishedAt() { return publishedAt; }
    public Map<String, Source> sources() { return sources; }
    public List<Entry> models() { return models; }

    public record Source(String id, String label, URI url, Instant capturedAt) {}
    public record Entry(
            String id, String provider, String family, List<String> aliases,
            int contextWindowTokens, Integer maxOutputTokens, Pricing pricing,
            String capabilitySource, String pricingSource, String upstreamModelId,
            ImageInputCapability imageInputCapability, String imageInputCapabilitySource) {
        public Entry {
            aliases = List.copyOf(aliases);
            java.util.Objects.requireNonNull(imageInputCapability, "imageInputCapability");
        }
        public Entry(String id, String provider, String family, List<String> aliases,
                int contextWindowTokens, Integer maxOutputTokens, Pricing pricing,
                String capabilitySource, String pricingSource, String upstreamModelId) {
            this(id, provider, family, aliases, contextWindowTokens, maxOutputTokens, pricing,
                    capabilitySource, pricingSource, upstreamModelId, ImageInputCapability.UNKNOWN, null);
        }
    }
    public record Pricing(String currency, String unit, List<Tier> tiers, String note) {
        public Pricing { tiers = List.copyOf(tiers); }
    }
    public record Tier(
            int minInputTokens, BigDecimal input, BigDecimal output,
            BigDecimal cacheRead, BigDecimal cacheWrite) {}
    public record Load(BuiltinModelCatalog catalog, GuideFailure failure) {}

    public Optional<BuiltinModelMatcher.Match> match(String modelId) {
        return index.match(modelId);
    }

    public static Load bundled() { return Bundled.VALUE; }

    private static final class Bundled {
        private static final Load VALUE = load();
        private static Load load() {
            var input = BuiltinModelCatalog.class.getResourceAsStream(RESOURCE);
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
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                JsonObject value = new JsonObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (value.has(name)) throw invalid();
                    value.add(name, read(reader));
                }
                reader.endObject();
                yield value;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                JsonArray value = new JsonArray();
                while (reader.hasNext()) value.add(read(reader));
                reader.endArray();
                yield value;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw invalid();
        };
    }
    private static void fields(JsonObject value, String... fields) {
        if (!dev.openallay.json.JsonTrees.keys(value).equals(Set.of(fields))) throw invalid();
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
        if ((!blankAllowed && text.isBlank()) || text.codePoints().anyMatch(Character::isISOControl))
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
        return new Load(new BuiltinModelCatalog("unavailable", Instant.EPOCH, Map.of(), List.of()),
                new GuideFailure(code, "The builtin model catalog is unavailable"));
    }
}
