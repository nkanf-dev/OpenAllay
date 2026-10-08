package dev.openallay.model.metadata;

import com.google.gson.JsonObject;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelFailure;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.image.ImageInputCapability;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpTransportPolicy;
import dev.openallay.net.JdkHttpTransport;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Same source runs against complete immutable original owners and actual Java8 owners. */
public final class ModelCatalogJava8Fixture {
    private static final Instant CAPTURED = Instant.parse("2026-07-18T12:00:00Z");
    private static final URI ROUTER = URI.create("https://openrouter.ai/api/v1/");
    private static final URI PRIVATE = URI.create("https://gateway.example/v1/");
    private static final String MODEL = "anthropic/claude-sonnet";
    private static final String RESPONSE = "{\"data\":[{\"id\":\"other/model\",\"canonical_slug\":\"other/model\",\"context_length\":1},{\"id\":\"anthropic/claude-sonnet\",\"canonical_slug\":\"anthropic/claude-sonnet-4.5\",\"context_length\":256000,\"top_provider\":{\"max_completion_tokens\":64000}}]}";
    private ModelCatalogJava8Fixture() {}
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private static void vector(String label, Object value) { System.out.println(label + "=" + value); }
    private static String failure(Runnable action) {
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch (RuntimeException expected) { return expected.getClass().getName() + ":" + expected.getMessage(); }
    }
    private static void value(String label, Object left, Object equal, Object unequal) {
        check(left.equals(equal) && equal.equals(left), label + " equality");
        check(left.hashCode() == equal.hashCode(), label + " hash equality");
        check(!left.equals(unequal) && !left.equals(null) && !left.equals("x"), label + " unequal");
        vector(label + ".string", left);
        // Enum Object.hashCode is identity-based across separate JVMs: hash oracle uses exact
        // component hashes in the same process, not cross-VM numeric enum values.
    }
    private static void hash(Object owner, Object... fields) {
        int expected = 0;
        for (Object field : fields) expected = 31 * expected + java.util.Objects.hashCode(field);
        check(owner.hashCode() == expected, owner.getClass().getName() + " zero-seeded field hash");
    }
    static BuiltinModelCatalog sample() {
        InputStream input = ModelCatalogJava8Fixture.class.getResourceAsStream("/model-metadata/builtin-sample.json");
        check(input != null, "actual sample resource");
        BuiltinModelCatalog.Load load = BuiltinModelCatalog.parse(new InputStreamReader(input, StandardCharsets.UTF_8));
        check(load.failure() == null, "actual sample parse");
        return load.catalog();
    }
    private static String sampleJson() {
        return dev.openallay.json.JsonTrees.parse(new InputStreamReader(
                ModelCatalogJava8Fixture.class.getResourceAsStream("/model-metadata/builtin-sample.json"),
                StandardCharsets.UTF_8)).toString();
    }
    public static void catalog() {
        BuiltinModelCatalog catalog = sample();
        vector("catalog.version", catalog.version());
        vector("catalog.published", catalog.publishedAt());
        List<String> ids = new ArrayList<>();
        for (BuiltinModelCatalog.Entry row : catalog.models()) ids.add(row.id());
        vector("catalog.ids", ids);
        BuiltinModelCatalog.Entry row = catalog.models().get(0);
        value("catalog.entry", row, new BuiltinModelCatalog.Entry(row.id(), row.provider(), row.family(), row.aliases(),
                row.contextWindowTokens(), row.maxOutputTokens(), row.pricing(), row.capabilitySource(), row.pricingSource(),
                row.upstreamModelId(), row.imageInputCapability(), row.imageInputCapabilitySource()), entry("different", "provider", Collections.emptyList()));
        hash(row, row.id(), row.provider(), row.family(), row.aliases(), row.contextWindowTokens(), row.maxOutputTokens(),
                row.pricing(), row.capabilitySource(), row.pricingSource(), row.upstreamModelId(), row.imageInputCapability(), row.imageInputCapabilitySource());
        List<String> sourceIds = new ArrayList<>(catalog.sources().keySet());
        Collections.sort(sourceIds);
        BuiltinModelCatalog.Source source = catalog.sources().get(sourceIds.get(0));
        value("catalog.source", source, new BuiltinModelCatalog.Source(source.id(), source.label(), source.url(), source.capturedAt()),
                new BuiltinModelCatalog.Source("different", source.label(), source.url(), source.capturedAt()));
        hash(source, source.id(), source.label(), source.url(), source.capturedAt());
        BuiltinModelCatalog.Pricing pricing = row.pricing();
        value("catalog.pricing", pricing, new BuiltinModelCatalog.Pricing(pricing.currency(), pricing.unit(), pricing.tiers(), pricing.note()),
                new BuiltinModelCatalog.Pricing(pricing.currency(), pricing.unit(), pricing.tiers(), "different"));
        hash(pricing, pricing.currency(), pricing.unit(), pricing.tiers(), pricing.note());
        BuiltinModelCatalog.Tier tier = pricing.tiers().get(0);
        value("catalog.tier", tier, new BuiltinModelCatalog.Tier(tier.minInputTokens(), tier.input(), tier.output(), tier.cacheRead(), tier.cacheWrite()),
                new BuiltinModelCatalog.Tier(1, tier.input(), tier.output(), tier.cacheRead(), tier.cacheWrite()));
        hash(tier, tier.minInputTokens(), tier.input(), tier.output(), tier.cacheRead(), tier.cacheWrite());
        BuiltinModelCatalog.Load load = new BuiltinModelCatalog.Load(catalog, null);
        check(load.equals(new BuiltinModelCatalog.Load(catalog, null)), "Load reference-component equality");
        hash(load, catalog, null);
        vector("catalog.load.same-reference", true);
        BuiltinModelMatcher.Match matched = catalog.match(row.id()).get();
        value("matcher.match", matched, new BuiltinModelMatcher.Match(row, matched.kind(), matched.similarity(), matched.identityRank()),
                new BuiltinModelMatcher.Match(row, matched.kind(), matched.similarity(), 10));
        hash(matched, row, matched.kind(), matched.similarity(), matched.identityRank());
        check(!new BuiltinModelMatcher.Match(row, matched.kind(), -0.0, 0).equals(new BuiltinModelMatcher.Match(row, matched.kind(), 0.0, 0)), "record double signed zero");
        check(new BuiltinModelMatcher.Match(row, matched.kind(), Double.NaN, 0).equals(new BuiltinModelMatcher.Match(row, matched.kind(), Double.NaN, 0)), "record double NaN");
        vector("catalog.pricing", row.pricing());
        check(row.pricing().tiers().size() == 2, "thresholds");
        check(row.pricing().tiers().get(1).minInputTokens() == 272000, "second threshold");
        check(row.pricing().tiers().get(1).output().toPlainString().equals("0.75"), "decimal rate");
        String json = sampleJson();
        List<String> bad = Arrays.asList(
                json.replace("\"models\":", "\"sources\":[],\"models\":"),
                json.replace("\"catalogVersion\"", "\"unknown\""),
                json.replace("\"contextWindowTokens\":1050000", "\"contextWindowTokens\":0"),
                json.replace("\"maxOutputTokens\":128000", "\"maxOutputTokens\":1.5"),
                json.replace("\"capabilitySource\":\"models-dev\"", "\"capabilitySource\":\"missing\""),
                json.replace("\"imageInputCapability\":\"supported\"", "\"imageInputCapability\":true"),
                json.replace("\"imageInputCapability\":\"supported\"", "\"imageInputCapability\":\"vision\""),
                json.replace("\"imageInputCapabilitySource\":\"models-dev\"", "\"imageInputCapabilitySource\":null"),
                json.replace("\"imageInputCapabilitySource\":\"models-dev\"", "\"imageInputCapabilitySource\":\"missing\""),
                json.replace("https://models.dev/api.json", "https://models.dev/api.json?key=synthetic-secret"),
                json.replace("\"input\":\"0.1\"", "\"input\":\"-1\""),
                json.replace("\"currency\":\"USD\"", "\"currency\":\"EUR\""),
                json + "{}", json.replace("\"catalogVersion\":", "\"catalogVersion\":\"duplicate\",\"catalogVersion\":"));
        for (int i = 0; i < bad.size(); i++) {
            check(!bad.get(i).equals(json), "malformed vector changed " + i);
            BuiltinModelCatalog.Load decoded = BuiltinModelCatalog.parse(new StringReader(bad.get(i)));
            check(decoded.failure() != null && decoded.failure().code().equals("builtin_catalog_invalid"), "strict parser " + i);
            check(decoded.catalog().models().isEmpty(), "whole resource rejection " + i);
            check(!decoded.failure().toString().contains("synthetic-secret"), "redacted parser " + i);
            vector("catalog.invalid." + i, decoded.failure());
        }
        JsonObject missing = dev.openallay.json.JsonTrees.parse(json).getAsJsonObject();
        JsonObject first = missing.getAsJsonArray("models").get(0).getAsJsonObject();
        first.add("maxOutputTokens", com.google.gson.JsonNull.INSTANCE);
        first.add("pricing", com.google.gson.JsonNull.INSTANCE);
        first.add("pricingSource", com.google.gson.JsonNull.INSTANCE);
        BuiltinModelCatalog.Load decoded = BuiltinModelCatalog.parse(new StringReader(missing.toString()));
        check(decoded.failure() == null && decoded.catalog().models().get(0).pricing() == null
                && decoded.catalog().models().get(0).maxOutputTokens() == null, "unpublished stays null");
        vector("catalog.unpublished", decoded.catalog().models().get(0));
        vector("catalog.models.snapshot", failure(() -> catalog.models().clear()));
        vector("catalog.sources.snapshot", failure(() -> catalog.sources().clear()));
        vector("catalog.aliases.snapshot", failure(() -> row.aliases().clear()));
        vector("catalog.tiers.snapshot", failure(() -> row.pricing().tiers().clear()));
        BuiltinModelCatalog.Load bundled = BuiltinModelCatalog.bundled();
        check(bundled.failure() == null, "actual bundled resource parse");
        for (String name : Arrays.asList("gpt-6-luna", "openai/gpt-5.4", "claude-sonnet-4-5"))
            vector("catalog.bundled." + name, bundled.catalog().match(name).get().entry().id());
    }
    private static BuiltinModelCatalog.Entry entry(String id, String provider, List<String> aliases) {
        return new BuiltinModelCatalog.Entry(id, provider, "gpt", aliases, 1050000,
                null, null, "models-dev", null, id);
    }
    public static void matcher() {
        BuiltinModelCatalog catalog = sample();
        for (String request : Arrays.asList("openai/gpt-6-luna", "GPT-6 Luna", "gateway/openai/gpt-6-luna", "gpt-6-luna:free",
                "gpt-6-luna-latest", "gpt-6-luna-2026-09-22", "gpt-6-lunna", "gpt-6", "gpt-7-luna", "gpt-6-kanglives",
                "claude-6-luna", "mystery/model", "gpt-6-luna-preview", "gpt-6-luna-thinking", "gpt-6-luna-mini", " ", "")) {
            Optional<BuiltinModelMatcher.Match> match = catalog.match(request);
            vector("matcher." + request, match.isPresent() ? match.get().entry().id() + ":" + match.get().kind()
                    + ":" + match.get().similarity() + ":" + match.get().identityRank() : "missing");
            check(catalog.match(request).equals(match), "cache same output");
        }
        check(!catalog.match(null).isPresent(), "null request");
        BuiltinModelCatalog.Entry base = catalog.models().get(0);
        List<BuiltinModelCatalog.Entry> entries = new ArrayList<>(Arrays.asList(
                entry("openrouter/openai/gpt-6-luna", "openrouter", base.aliases()), base,
                entry("aaa/gpt-6-luna", "aaa", base.aliases())));
        for (int i = 0; i < 3; i++) {
            check(BuiltinModelMatcher.match(entries, "GPT-6 Luna").get().entry().id().equals("aaa/gpt-6-luna"), "stable tie " + i);
            vector("matcher.tie." + i, BuiltinModelMatcher.match(entries, "GPT-6 Luna").get().entry().id());
            Collections.rotate(entries, 1);
        }
        List<BuiltinModelCatalog.Entry> variants = Arrays.asList(entry("openai/gpt-6-mini", "openai", Arrays.asList("gpt-6-mini")),
                entry("openai/gpt-6-nano", "openai", Arrays.asList("gpt-6-nano")),
                new BuiltinModelCatalog.Entry("anthropic/claude-sonnet-4-5", "anthropic", "claude", Collections.emptyList(),
                        1000000, null, null, "models-dev", null, "claude-sonnet-4-5"));
        check(BuiltinModelMatcher.match(variants, "gpt-6-minni").get().entry().id().equals("openai/gpt-6-mini"), "similar variant");
        check(BuiltinModelMatcher.match(variants, "gateway/claude-sonnet-4.5").get().entry().id().equals("anthropic/claude-sonnet-4-5"), "dotted version");
        check(!BuiltinModelMatcher.match(variants, "gpt-6-pro").isPresent()
                && !BuiltinModelMatcher.match(variants, "gpt-6-nano-thinking").isPresent(), "variant ambiguity closed");
    }
    public static void precedence() {
        BuiltinModelCatalog catalog = sample();
        ModelMetadata metadata = new ModelMetadata("openrouter", "gpt-6-luna", "canonical", 600000,
                64000, CAPTURED, ImageInputCapability.UNSUPPORTED);
        Map<ModelMetadata.Key, ModelMetadata> trusted = Collections.singletonMap(metadata.key(), metadata);
        for (URI endpoint : Arrays.asList(ROUTER, PRIVATE)) {
            for (String model : Arrays.asList("gpt-6-luna", "gateway/gpt-6-luna", "gpt-6-lunna", "unknown-model")) {
                for (Integer manual : Arrays.asList(null, Integer.valueOf(1000000)))
                    vector("context." + endpoint.getHost() + "." + model + "." + manual,
                            ModelContextResolution.resolve(endpoint, model, manual, trusted, catalog));
                for (Integer manual : Arrays.asList(null, Integer.valueOf(8192)))
                    vector("output." + endpoint.getHost() + "." + model + "." + manual,
                            ModelOutputResolution.resolve(endpoint, model, manual, trusted, catalog));
                for (ImageInputCapability manual : Arrays.asList(null, ImageInputCapability.SUPPORTED, ImageInputCapability.UNKNOWN))
                    vector("image." + endpoint.getHost() + "." + model + "." + manual,
                            ModelImageCapabilityResolution.resolve(endpoint, model, manual, trusted, catalog));
            }
        }
        check(ModelContextResolution.resolve(ROUTER, "gpt-6-luna", 1000000, trusted, catalog).origin() == ModelContextResolution.Origin.EXPLICIT, "manual context");
        check(ModelOutputResolution.resolve(ROUTER, "gpt-6-luna", null, trusted, catalog).maxOutputTokens() == 64000, "trusted output");
        check(ModelImageCapabilityResolution.resolve(PRIVATE, "gpt-6-lunna", null, trusted, catalog).capability() == ImageInputCapability.UNKNOWN, "fuzzy not image proof");
        ModelMetadata unknown = new ModelMetadata("openrouter", "gpt-6-luna", "canonical", 600000, null, CAPTURED, ImageInputCapability.UNKNOWN);
        Map<ModelMetadata.Key, ModelMetadata> unknowns = Collections.singletonMap(unknown.key(), unknown);
        check(ModelOutputResolution.resolve(ROUTER, "gpt-6-luna", null, unknowns, catalog).origin() == ModelOutputResolution.Origin.BUILTIN, "missing output fallback");
        check(ModelImageCapabilityResolution.resolve(ROUTER, "gpt-6-luna", null, unknowns, catalog).capability() == ImageInputCapability.SUPPORTED, "unknown image fallback");
        ModelMetadata wrong = new ModelMetadata("openrouter", "different-id", "canonical", 10, 20, CAPTURED, ImageInputCapability.SUPPORTED);
        Map<ModelMetadata.Key, ModelMetadata> mismatch = Collections.singletonMap(metadata.key(), wrong);
        check(ModelOutputResolution.resolve(ROUTER, "gpt-6-luna", null, mismatch, catalog).origin() == ModelOutputResolution.Origin.BUILTIN, "output identity mismatch");
        vector("context.existing.key-mismatch-rule", ModelContextResolution.resolve(ROUTER, "gpt-6-luna", null, mismatch, catalog));
        check(ModelImageCapabilityResolution.resolve(ROUTER, "private-alias", null,
                Collections.singletonMap(new ModelMetadata.Key("openrouter", "private-alias"), wrong), catalog).capability() == ImageInputCapability.UNKNOWN, "image identity mismatch");
        ModelContextResolution context = new ModelContextResolution(100, ModelContextResolution.Origin.EXPLICIT);
        value("context.value", context, new ModelContextResolution(100, ModelContextResolution.Origin.EXPLICIT, null, null),
                new ModelContextResolution(101, ModelContextResolution.Origin.EXPLICIT));
        hash(context, 100, ModelContextResolution.Origin.EXPLICIT, null, null);
        ModelOutputResolution output = new ModelOutputResolution(100, ModelOutputResolution.Origin.EXPLICIT);
        value("output.value", output, new ModelOutputResolution(100, ModelOutputResolution.Origin.EXPLICIT, null, null),
                new ModelOutputResolution(101, ModelOutputResolution.Origin.EXPLICIT));
        hash(output, 100, ModelOutputResolution.Origin.EXPLICIT, null, null);
        ModelImageCapabilityResolution image = new ModelImageCapabilityResolution(ImageInputCapability.UNKNOWN, ModelImageCapabilityResolution.Origin.EXPLICIT);
        value("image.value", image, new ModelImageCapabilityResolution(ImageInputCapability.UNKNOWN, ModelImageCapabilityResolution.Origin.EXPLICIT, null, null),
                ModelImageCapabilityResolution.unknown());
        hash(image, ImageInputCapability.UNKNOWN, ModelImageCapabilityResolution.Origin.EXPLICIT, null, null);
        vector("output.invalid.zero", failure(() -> new ModelOutputResolution(0, ModelOutputResolution.Origin.EXPLICIT)));
        vector("output.invalid.required", failure(() -> new ModelOutputResolution(1, ModelOutputResolution.Origin.REQUIRED)));
        vector("output.invalid.missing", failure(() -> new ModelOutputResolution(null, ModelOutputResolution.Origin.EXPLICIT)));
        vector("output.invalid.origin", failure(() -> new ModelOutputResolution(1, null)));
        vector("image.invalid.capability", failure(() -> new ModelImageCapabilityResolution(null, ModelImageCapabilityResolution.Origin.UNKNOWN)));
        vector("image.invalid.origin", failure(() -> new ModelImageCapabilityResolution(ImageInputCapability.UNKNOWN, null)));
        check(ImageInputCapability.fromInputModalities(Collections.emptyList()) == ImageInputCapability.UNSUPPORTED
                && ImageInputCapability.fromInputModalities(null) == ImageInputCapability.UNKNOWN, "three-valued modalities");
    }
    private static OpenRouterMetadataResolver resolver(OpenRouterMetadataResolver.Transport transport, SecretValue key) {
        return new OpenRouterMetadataResolver(transport, Clock.fixed(CAPTURED, ZoneOffset.UTC), Duration.ofSeconds(5), key);
    }
    private static ModelMetadataResolution response(int status, String body) {
        return resolver((request, cancellation) -> CompletableFuture.completedFuture(new OpenRouterMetadataResolver.Response(status, body)), null)
                .resolve(MODEL, null, null, new CancellationSignal()).join();
    }
    public static void resolver() throws Exception {
        AtomicReference<HttpExchangeRequest> captured = new AtomicReference<>();
        OpenRouterMetadataResolver resolver = resolver((request, cancellation) -> {
            captured.set(request); return CompletableFuture.completedFuture(new OpenRouterMetadataResolver.Response(200, RESPONSE));
        }, SecretValue.of("synthetic-test-key"));
        ModelMetadataResolution resolved = resolver.resolve(MODEL, null, null, new CancellationSignal()).join();
        check(resolved.successful() && resolved.contextWindowTokens() == 256000 && resolved.maxOutputTokens() == 64000, "published exact fields");
        check(captured.get().uri().equals(OpenRouterMetadataResolver.MODELS_URI)
                && captured.get().headers().get("Authorization").get(0).equals("Bearer synthetic-test-key"), "genuine request shape");
        check(!resolved.toString().contains("synthetic-test-key"), "credential redacted");
        OpenRouterMetadataResolver.Response value = new OpenRouterMetadataResolver.Response(200, RESPONSE);
        value("resolver.response", value, new OpenRouterMetadataResolver.Response(200, RESPONSE), new OpenRouterMetadataResolver.Response(201, RESPONSE));
        hash(value, 200, RESPONSE);
        vector("resolver.response.null", failure(() -> new OpenRouterMetadataResolver.Response(200, null)));
        vector("resolver.resolved", resolved);
        vector("resolver.manual", resolver.resolve(MODEL, 512000, 8192, new CancellationSignal()).join());
        vector("resolver.exact-id", resolver.resolve("ANTHROPIC/CLAUDE-SONNET", null, null, new CancellationSignal()).join().failure());
        for (String modalities : Arrays.asList("[\"text\",\"image\"]", "[\"text\"]", "[]", "null")) {
            ModelMetadataResolution got = response(200, RESPONSE.replace("\"context_length\":256000,", "\"architecture\":{\"input_modalities\":" + modalities + "},\"context_length\":256000,"));
            check(got.successful(), "modality decode");
            vector("resolver.modalities." + modalities, got.metadata().imageInputCapability());
        }
        List<String> bad = Arrays.asList("{\"data\":[]}", "{\"data\":{}}", "[]", "{\"data\":[true]}",
                RESPONSE.replace("\"id\":\"other/model\"", "\"id\":\"anthropic/claude-sonnet\""),
                RESPONSE.replace("\"context_length\":256000", "\"context_length\":999999999999999999"),
                RESPONSE.replace("\"context_length\":256000", "\"context_length\":1.5"),
                RESPONSE.replace("\"context_length\":256000", "\"context_length\":0"),
                RESPONSE.replace("\"top_provider\":{\"max_completion_tokens\":64000}", "\"top_provider\":true"),
                RESPONSE.replace("\"context_length\":256000,", "\"architecture\":{\"input_modalities\":true},\"context_length\":256000,"));
        for (int i = 0; i < bad.size(); i++) {
            ModelMetadataResolution got = response(200, bad.get(i));
            check(!got.successful() && got.failure().code().equals(i == 0 ? "metadata_not_found" : "metadata_invalid"), "invalid remote payload " + i);
            vector("resolver.invalid." + i, got.failure());
        }
        check(response(200, RESPONSE.replace("\"top_provider\":{\"max_completion_tokens\":64000}", "\"top_provider\":null")).maxOutputTokens() == null, "unpublished output unknown");
        vector("resolver.http503", response(503, "synthetic-secret-body").failure());
        CompletableFuture<OpenRouterMetadataResolver.Response> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("synthetic-secret-body"));
        vector("resolver.transport", resolver((request, cancellation) -> failed, null).resolve(MODEL, null, null, new CancellationSignal()).join().failure());
        vector("resolver.sync-transport", resolver((request, cancellation) -> { throw new RuntimeException("synthetic-secret-body"); }, null).resolve(MODEL, null, null, new CancellationSignal()).join().failure());
        CancellationSignal cancelled = new CancellationSignal(); cancelled.cancel();
        check(resolver.resolve(MODEL, null, null, cancelled).join().failure().code().equals("metadata_cancelled"), "cancelled resolver");
        vector("resolver.cancelled", resolver.resolve(MODEL, null, null, cancelled).join().failure());
        vector("resolver.blank", resolver.resolve(" ", null, null, new CancellationSignal()).join().failure());
        check(OpenRouterMetadataResolver.supports(URI.create("https://EU.OpenRouter.ai/api/v1"))
                && !OpenRouterMetadataResolver.supports(URI.create("http://openrouter.ai/api/v1"))
                && !OpenRouterMetadataResolver.supports(URI.create("https://openrouter.ai.example/api/v1")), "trusted endpoint boundary");
        httpLoopback();
    }
    private static String read(InputStream body) throws java.io.IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int count;
        while ((count = body.read(buffer)) != -1) bytes.write(buffer, 0, count);
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
    private static void httpLoopback() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models", exchange -> {
            byte[] bytes = RESPONSE.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream body = exchange.getResponseBody()) { body.write(bytes); }
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/models");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.start();
        try {
            final JdkHttpTransport transport = new JdkHttpTransport(new HttpTransportPolicy(Duration.ofSeconds(5), "model-catalog-fixture-http"));
            final URI loopback = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/models");
            OpenRouterMetadataResolver resolver = resolver((request, cancellation) -> transport.execute(
                    HttpExchangeRequest.newBuilder(loopback).timeout(Duration.ofSeconds(5)).get().build(), cancellation,
                    (status, headers, body) -> new OpenRouterMetadataResolver.Response(status, read(body))), null);
            ModelMetadataResolution got = resolver.resolve(MODEL, null, null, new CancellationSignal()).get(10, TimeUnit.SECONDS);
            check(got.successful(), "accepted actual Jdk transport loopback");
            vector("resolver.real-jdk-loopback", got);
            int redirect = transport.execute(HttpExchangeRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect"))
                    .timeout(Duration.ofSeconds(5)).get().build(), new CancellationSignal(), (status, headers, body) -> status).get(10, TimeUnit.SECONDS);
            check(redirect == 302, "default redirect policy preserved");
            vector("transport.redirect", redirect);
        } finally { server.stop(0); }
    }
    public static void eventsAndCancellation() throws Exception {
        ModelUsage usage = new ModelUsage(10, 20, 3);
        value("usage", usage, new ModelUsage(10, 20, 3), ModelUsage.empty());
        hash(usage, 10L, 20L, 3L, 0L, 7L, true, true, true, true, true);
        vector("usage.openAi", ModelUsage.openAi(10, true, 2, true, 3, true));
        vector("usage.anthropic", ModelUsage.anthropic(7, true, 2, true, 3, true, 5, true));
        vector("usage.empty", ModelUsage.empty());
        vector("usage.invalid.negative", failure(() -> new ModelUsage(-1, 2, 0)));
        vector("usage.invalid.cache", failure(() -> new ModelUsage(1, 2, 3)));
        vector("usage.invalid.unknown", failure(() -> new ModelUsage(0, 1, 0, 0, 0, false, false, false, false, false)));
        vector("failure.invalid.blank", failure(() -> new ModelFailure(" ", "message", null)));
        JsonObject input = new JsonObject(); input.addProperty("value", "original");
        ModelEvent.ToolUseComplete tool = new ModelEvent.ToolUseComplete("tool-id", "tool-name", input);
        input.addProperty("value", "changed");
        tool.input().addProperty("value", "accessor-mutated");
        check(tool.input().get("value").getAsString().equals("original"), "constructor+accessor defensive copy");
        JsonObject equalInput = new JsonObject(); equalInput.addProperty("value", "original");
        value("event.tool", tool, new ModelEvent.ToolUseComplete("tool-id", "tool-name", equalInput), new ModelEvent.ToolUseComplete("other", "tool-name", equalInput));
        hash(tool, "tool-id", "tool-name", equalInput);
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        List<ModelEvent> events = Arrays.asList(new ModelEvent.TextDelta("text"), new ModelEvent.ReasoningDelta("reasoning"), tool,
                new ModelEvent.UsageUpdate(usage), new ModelEvent.UsageStarted(id, "model"), new ModelEvent.UsageObserved(id, "model", usage),
                new ModelEvent.AttemptStarted(1, 100L), new ModelEvent.ResponseStarted(), new ModelEvent.RateLimited(100, 1),
                new ModelEvent.MessageComplete(null), new ModelFailure("test_failure", "synthetic message", 503));
        for (ModelEvent event : events) {
            check(Modifier.isPublic(event.getClass().getModifiers()) && Modifier.isFinal(event.getClass().getModifiers()), "public final actual event " + event.getClass().getName());
            for (java.lang.reflect.Constructor<?> ctor : event.getClass().getDeclaredConstructors())
                check(Modifier.isPublic(ctor.getModifiers()), "public event constructor " + event.getClass().getName());
            vector("event." + event.getClass().getSimpleName(), event);
        }
        check(new ModelEvent.ResponseStarted().hashCode() == 0, "empty record hash");
        vector("event.invalid.text", failure(() -> new ModelEvent.TextDelta(null)));
        vector("event.invalid.tool", failure(() -> new ModelEvent.ToolUseComplete("id", "name", null)));
        vector("event.invalid.attempt", failure(() -> new ModelEvent.AttemptStarted(0, 1L)));
        vector("event.invalid.rate", failure(() -> new ModelEvent.RateLimited(-1, 1)));
        CancellationSignal signal = new CancellationSignal();
        List<String> calls = new ArrayList<>();
        signal.onCancel(() -> { calls.add("error"); throw new AssertionError("synthetic-test-key"); });
        signal.onCancel(() -> calls.add("cleanup"));
        check(signal.cancel(command -> { command.run(); throw new AssertionError("synthetic-test-key"); }), "cancel first");
        check(!signal.cancel(), "cancel idempotent");
        signal.onCancel(() -> calls.add("late"));
        check(calls.equals(Arrays.asList("error", "cleanup", "late")), "listeners exact once despite callback failure");
        vector("cancellation.calls", calls);
        CancellationSignal parent = new CancellationSignal(); CancellationSignal child = parent.linkedChild();
        List<Runnable> deferred = new ArrayList<>(); parent.cancel(deferred::add);
        check(child.isCancelled(), "immediate parent revocation");
        vector("cancellation.throw", failure(child::throwIfCancelled)); deferred.get(0).run();
        CancellationSignal localParent = new CancellationSignal(); localParent.linkedChild().cancel();
        check(!localParent.isCancelled(), "local deadline independent");
        CancellationSignal observed = new CancellationSignal(); CompletableFuture<String> raw = new CompletableFuture<>();
        CompletableFuture<String> result = observed.observe(raw); observed.cancel();
        check(result.isCompletedExceptionally() && !raw.isDone(), "observed operation revoked without settling raw");
        check(result.handle((value, error) -> error != null).join(), "observed failure");
        vector("cancellation.observed", "agent_cancelled");
    }
    private static void explicitSchemas() throws Exception {
        List<Class<?>> values = Arrays.asList(BuiltinModelCatalog.Source.class, BuiltinModelCatalog.Entry.class,
                BuiltinModelCatalog.Pricing.class, BuiltinModelCatalog.Tier.class, BuiltinModelCatalog.Load.class,
                BuiltinModelMatcher.Match.class, ModelContextResolution.class, ModelOutputResolution.class,
                ModelImageCapabilityResolution.class, OpenRouterMetadataResolver.Response.class,
                ModelFailure.class, ModelUsage.class, ModelEvent.TextDelta.class, ModelEvent.ReasoningDelta.class,
                ModelEvent.ToolUseComplete.class, ModelEvent.UsageUpdate.class, ModelEvent.UsageStarted.class,
                ModelEvent.UsageObserved.class, ModelEvent.AttemptStarted.class, ModelEvent.ResponseStarted.class,
                ModelEvent.RateLimited.class, ModelEvent.MessageComplete.class);
        for (Class<?> owner : values) {
            // Original modern owner uses public record metadata; Java8 owner uses the explicit schema.
            check(dev.openallay.value.ValueSchemas.isValue(owner), "real value metadata " + owner.getName());
            if (dev.openallay.value.ValueSchemas.supports(owner)) {
                dev.openallay.value.ValueSchema<?> schema = dev.openallay.value.ValueSchemas.of(owner);
                check(schema.owner() == owner, "schema direct owner " + owner.getName());
            }
        }
        if (dev.openallay.value.ValueSchemas.supports(BuiltinModelMatcher.Match.class)) {
            for (String name : Arrays.asList("dev.openallay.model.metadata.BuiltinModelMatcher$Candidate",
                    "dev.openallay.model.metadata.BuiltinModelMatcher$Index$Lookup")) {
                Class<?> owner = Class.forName(name);
                check(Modifier.isPrivate(owner.getModifiers()) && Modifier.isFinal(owner.getModifiers()), "private internal owner stays private");
                check(dev.openallay.value.ValueSchemas.supports(owner), "private internal typed metadata");
                // Public provider does not change visibility of the private matcher owner.
                check(dev.openallay.value.ValueSchemas.of(owner).owner() == owner, "private owner public provider lookup");
            }
        }
        com.google.gson.Gson gson = dev.openallay.json.EngineJson.create();
        OpenRouterMetadataResolver.Response response = new OpenRouterMetadataResolver.Response(200, "synthetic-body");
        String responseJson = gson.toJson(response);
        check(gson.fromJson(responseJson, OpenRouterMetadataResolver.Response.class).equals(response), "real constructor JSON response roundtrip");
        vector("json.response", responseJson);
        JsonObject object = new JsonObject(); object.addProperty("x", "synthetic");
        ModelEvent.ToolUseComplete tool = new ModelEvent.ToolUseComplete("id", "tool", object);
        String toolJson = gson.toJson(tool);
        check(gson.fromJson(toolJson, ModelEvent.ToolUseComplete.class).equals(tool), "real constructor JSON tool roundtrip");
        vector("json.tool", toolJson);
        check(gson.fromJson(gson.toJson(ModelUsage.empty()), ModelUsage.class).equals(ModelUsage.empty()), "usage unknown flags JSON");
        vector("json.usage", gson.toJson(ModelUsage.empty()));
        vector("json.response.invalid", failure(() -> gson.fromJson("{\"status\":200,\"body\":null}", OpenRouterMetadataResolver.Response.class)));
    }
    public static void main(String[] args) throws Exception {
        catalog(); matcher(); precedence(); resolver(); eventsAndCancellation();
        explicitSchemas();
        vector("complete", "actual ten-owner catalog + domain frontier");
    }
}
