package dev.openallay.model.metadata;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.openallay.guide.GuideFailure;
import dev.openallay.json.EngineJson;
import dev.openallay.model.image.ImageInputCapability;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Identical vectors use complete original modern owners and complete lowered Java8 owners. */
public final class ModelMetadataValuesFixture {
    private ModelMetadataValuesFixture() {}
    private interface Checked { void run() throws Exception; }
    private static int checks;
    private static final List<String> vectors = new ArrayList<>();
    private static final Gson JSON = EngineJson.create(builder -> builder.enableComplexMapKeySerialization());
    private static final Instant CAPTURED = Instant.ofEpochSecond(-123456789, 987654321);
    private static final String SYNTHETIC_SECRET = "synthetic-api-key-do-not-echo";
    private static final String SYNTHETIC_URL = "https://synthetic.invalid/private?token=" + SYNTHETIC_SECRET;

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1 || !(arguments[0].equals("java8") || arguments[0].equals("modern")))
            throw new IllegalArgumentException("Expected java8 or modern");
        if (arguments[0].equals("java8")) {
            equal("1.8", System.getProperty("java.specification.version"));
            for (Class<?> owner : Arrays.<Class<?>>asList(ModelMetadata.class, ModelMetadata.Key.class,
                    ModelMetadataResolution.class, ModelMetadataUpdate.class, GuideFailure.class,
                    ImageInputCapability.class, EngineJson.class, ValueSchema.class, ModelMetadataValuesFixture.class)) {
                major52(owner);
            }
        } else check(!System.getProperty("java.specification.version").equals("1.8"), "modern oracle JVM");
        run();
        // Check counts differ only for JVM identity/bytecode gates, not behavior vectors.
        for (String vector : vectors) System.out.println(vector);
        System.err.println("PASS model metadata values " + arguments[0] + " checks=" + checks);
    }

    public static void run() throws Exception {
        identityAndMetadata();
        boundsAndFailures();
        capabilities();
        updates();
        typedJson();
    }

    private static ModelMetadata metadata(Integer output, ImageInputCapability capability) {
        return new ModelMetadata("synthetic-provider", " Vendor/Model:manual ", "vendor/model-canonical",
                128000, output, CAPTURED, capability);
    }
    private static void identityAndMetadata() throws Exception {
        ModelMetadata value = metadata(4096, ImageInputCapability.SUPPORTED);
        ModelMetadata copy = metadata(4096, ImageInputCapability.SUPPORTED);
        equal(value, copy);
        equal(value.hashCode(), copy.hashCode());
        equal(value, value);
        check(!value.equals(null) && !value.equals("other"), "exact value owner");
        check(!value.equals(metadata(4097, ImageInputCapability.SUPPORTED)), "output contributes to equality");
        check(!value.equals(metadata(4096, ImageInputCapability.UNKNOWN)), "capability contributes to equality");
        equal(hash(value.source(), value.providerModelId(), value.canonicalModelId(), value.contextWindowTokens(),
                value.maxOutputTokens(), value.capturedAt(), value.imageInputCapability()), value.hashCode());
        equal("ModelMetadata[source=synthetic-provider, providerModelId= Vendor/Model:manual , canonicalModelId=vendor/model-canonical, contextWindowTokens=128000, maxOutputTokens=4096, capturedAt="
                + CAPTURED + ", imageInputCapability=SUPPORTED]", value.toString());
        equal(new ModelMetadata.Key("synthetic-provider", " Vendor/Model:manual "), value.key());
        equal(" Vendor/Model:manual ", value.key().providerModelId());
        check(!value.key().equals(new ModelMetadata.Key("synthetic-provider", "vendor/model:manual")), "key is exact published request identity, not canonical slug");
        check(!value.key().equals(new ModelMetadata.Key("other-provider", value.providerModelId())), "source contributes to key");
        equal(hash(value.source(), value.providerModelId()), value.key().hashCode());
        equal("Key[source=synthetic-provider, providerModelId= Vendor/Model:manual ]", value.key().toString());
        vector("identity", value.toString());
        vector("key", value.key().toString());
        equal(ImageInputCapability.UNKNOWN,
                new ModelMetadata(value.source(), value.providerModelId(), value.canonicalModelId(),
                        value.contextWindowTokens(), null, value.capturedAt()).imageInputCapability());
        metadataFacts(ModelMetadata.class, Arrays.asList("source", "providerModelId", "canonicalModelId",
                "contextWindowTokens", "maxOutputTokens", "capturedAt", "imageInputCapability"));
        metadataFacts(ModelMetadata.Key.class, Arrays.asList("source", "providerModelId"));
        metadataFacts(ModelMetadataResolution.class, Arrays.asList("contextWindowTokens", "maxOutputTokens", "metadata", "failure"));
        metadataFacts(ModelMetadataUpdate.class, Arrays.asList("entries", "failure"));
        metadataFacts(GuideFailure.class, Arrays.asList("code", "message"));
        java.lang.reflect.Type generic = ModelMetadataUpdate.class.getMethod("entries").getGenericReturnType();
        check(generic instanceof ParameterizedType, "entries parameterized map");
        ParameterizedType map = (ParameterizedType) generic;
        equal(Map.class, map.getRawType());
        equal(Arrays.<java.lang.reflect.Type>asList(ModelMetadata.Key.class, ModelMetadata.class), Arrays.asList(map.getActualTypeArguments()));
        vector("generic", generic.getTypeName());
        equal(0, ModelMetadata.class.getDeclaredField("source").getAnnotations().length);
        equal(0, ModelMetadataUpdate.class.getDeclaredField("entries").getAnnotations().length);
        if (ValueSchemas.supports(ModelMetadata.class)) {
            ValueSchema<ModelMetadata> schema = ValueSchemas.of(ModelMetadata.class);
            Object[] arguments = {value.source(), value.providerModelId(), value.canonicalModelId(),
                    value.contextWindowTokens(), value.maxOutputTokens(), value.capturedAt(), value.imageInputCapability()};
            equal(value, schema.construct(arguments));
            arguments[0] = "later";
            equal("synthetic-provider", value.source());
            fails(IllegalArgumentException.class, () -> schema.construct(new Object[0]), false);
            fails(UnsupportedOperationException.class, () -> schema.components().clear(), false);
        }
    }

    private static void metadataFacts(Class<?> owner, List<String> expected) throws Exception {
        List<String> names = new ArrayList<>();
        List<String> types = new ArrayList<>();
        if (ValueSchemas.supports(owner)) {
            ValueSchema<?> schema = ValueSchemas.of(owner);
            for (ValueSchema.Component<?> component : schema.components()) {
                names.add(component.name()); types.add(component.genericType().getTypeName());
                equal(owner, component.fieldMetadata().getDeclaringClass());
                check(Modifier.isPrivate(component.fieldMetadata().getModifiers())
                        && Modifier.isFinal(component.fieldMetadata().getModifiers()), "private final metadata field");
                equal(owner.getMethod(component.name()).getReturnType(), component.rawType());
                equal(0, component.fieldMetadata().getAnnotations().length);
            }
            dev.openallay.value.ValueType declaration = owner.getAnnotation(dev.openallay.value.ValueType.class);
            check(Modifier.isPublic(declaration.value().getModifiers()), "public schema provider");
            declaration.value().getConstructor().newInstance();
        } else {
            Object[] records = (Object[]) Class.class.getMethod("getRecordComponents").invoke(owner);
            for (Object component : records) {
                String name = (String) component.getClass().getMethod("getName").invoke(component);
                java.lang.reflect.Type type = (java.lang.reflect.Type) component.getClass().getMethod("getGenericType").invoke(component);
                names.add(name); types.add(type.getTypeName());
                equal(owner, owner.getDeclaredField(name).getDeclaringClass());
                equal(0, owner.getDeclaredField(name).getAnnotations().length);
            }
        }
        equal(expected, names);
        check(Modifier.isPublic(owner.getModifiers()) && Modifier.isFinal(owner.getModifiers()), "public final owner");
        vector("schema:" + owner.getSimpleName(), names + "|" + types);
    }

    private static void boundsAndFailures() throws Exception {
        ModelMetadata published = metadata(64000, ImageInputCapability.UNKNOWN);
        ModelMetadataResolution discovered = ModelMetadataResolution.resolved(published, null, null);
        ModelMetadataResolution manual = ModelMetadataResolution.resolved(published, 512000, 8192);
        equal(Integer.valueOf(128000), discovered.contextWindowTokens());
        equal(Integer.valueOf(64000), discovered.maxOutputTokens());
        equal(Integer.valueOf(512000), manual.contextWindowTokens());
        equal(Integer.valueOf(8192), manual.maxOutputTokens());
        equal(published, manual.metadata());
        equal(null, manual.failure()); check(manual.successful(), "successful resolution");
        equal(null, ModelMetadataResolution.resolved(metadata(null, ImageInputCapability.UNKNOWN), null, null).maxOutputTokens());
        equal(Integer.valueOf(1), ModelMetadataResolution.resolved(metadata(null, ImageInputCapability.UNKNOWN), 1, 1).maxOutputTokens());
        ModelMetadata max = new ModelMetadata("s", "m", "c", Integer.MAX_VALUE, Integer.MAX_VALUE, CAPTURED);
        equal(Integer.MAX_VALUE, max.contextWindowTokens());
        equal(Integer.valueOf(Integer.MAX_VALUE), ModelMetadataResolution.resolved(max, null, null).maxOutputTokens());
        // This value validates positivity, not ContextBudget's separate continuation reservation policy.
        equal(Integer.valueOf(9), ModelMetadataResolution.resolved(new ModelMetadata("s", "m", "c", 1, 9, CAPTURED), null, null).maxOutputTokens());
        for (String blank : Arrays.asList("", " \t\r\n", "\u2003")) {
            fails(IllegalArgumentException.class, () -> new ModelMetadata(blank, "m", "c", 1, null, CAPTURED), true);
            fails(IllegalArgumentException.class, () -> new ModelMetadata("s", blank, "c", 1, null, CAPTURED), true);
            fails(IllegalArgumentException.class, () -> new ModelMetadata("s", "m", blank, 1, null, CAPTURED), true);
            fails(IllegalArgumentException.class, () -> new ModelMetadata.Key(blank, "m"), true);
            fails(IllegalArgumentException.class, () -> new ModelMetadata.Key("s", blank), true);
            fails(IllegalArgumentException.class, () -> new GuideFailure(blank, "message"), true);
            fails(IllegalArgumentException.class, () -> new GuideFailure("code", blank), true);
        }
        // Nonbreaking space is not Character.isWhitespace. The published exact identity stays untouched.
        equal("\u00a0", new ModelMetadata.Key("\u00a0", "m").source());
        for (int bound : new int[] {0, -1, Integer.MIN_VALUE}) {
            fails(IllegalArgumentException.class, () -> new ModelMetadata("s", "m", "c", bound, null, CAPTURED), true);
            fails(IllegalArgumentException.class, () -> new ModelMetadata("s", "m", "c", 1, bound, CAPTURED), true);
            fails(IllegalArgumentException.class, () -> ModelMetadataResolution.resolved(published, bound, null), true);
            fails(IllegalArgumentException.class, () -> ModelMetadataResolution.resolved(published, null, bound), true);
        }
        fails(NullPointerException.class, () -> new ModelMetadata("s", "m", "c", 1, null, null), true);
        fails(NullPointerException.class, () -> new ModelMetadata("s", "m", "c", 1, null, CAPTURED, null), true);
        fails(NullPointerException.class, () -> ModelMetadataResolution.resolved(null, null, null), true);
        GuideFailure failure = new GuideFailure("metadata_unavailable", "Synthetic metadata is unavailable");
        fails(IllegalArgumentException.class, () -> new ModelMetadataResolution(null, null, null, null), true);
        fails(IllegalArgumentException.class, () -> new ModelMetadataResolution(null, null, published, failure), true);
        fails(IllegalArgumentException.class, () -> new ModelMetadataResolution(1, null, null, failure), true);
        fails(IllegalArgumentException.class, () -> new ModelMetadataResolution(null, 1, null, failure), true);
        ModelMetadataResolution failed = ModelMetadataResolution.failed(failure.code(), failure.message());
        check(!failed.successful(), "failure resolution"); equal(null, failed.metadata());
        equal(null, failed.contextWindowTokens()); equal(null, failed.maxOutputTokens()); equal(failure, failed.failure());
        equal(hash(null, null, null, failure), failed.hashCode());
        equal(hash(failure.code(), failure.message()), failure.hashCode());
        vector("manual", manual.toString()); vector("failed", failed.toString());
        vector("failureHash", failure.hashCode());
        fails(IllegalArgumentException.class, () -> new ModelMetadata(SYNTHETIC_SECRET, SYNTHETIC_URL, "", 1, null, CAPTURED), true);
        fails(IllegalArgumentException.class, () -> new ModelMetadata.Key(SYNTHETIC_SECRET, ""), true);
        fails(IllegalArgumentException.class, () -> new GuideFailure("", SYNTHETIC_URL), true);
    }

    private static void capabilities() throws Exception {
        equal(ImageInputCapability.UNKNOWN, ImageInputCapability.fromInputModalities(null));
        equal(ImageInputCapability.UNSUPPORTED, ImageInputCapability.fromInputModalities(Collections.<String>emptyList()));
        equal(ImageInputCapability.SUPPORTED, ImageInputCapability.fromInputModalities(Arrays.asList("text", "image")));
        equal(ImageInputCapability.UNSUPPORTED, ImageInputCapability.fromInputModalities(Arrays.asList("text", "audio")));
        equal(ImageInputCapability.UNSUPPORTED, ImageInputCapability.fromInputModalities(Arrays.asList("IMAGE")));
        equal(ImageInputCapability.UNKNOWN, ImageInputCapability.parse("UnKnOwN"));
        equal("supported", ImageInputCapability.SUPPORTED.encoded());
        for (ImageInputCapability capability : ImageInputCapability.values()) {
            equal(capability, ImageInputCapability.parse(capability.encoded()));
            vector("capability", capability.encoded());
        }
        fails(IllegalArgumentException.class, () -> ImageInputCapability.fromInputModalities(Arrays.asList("text", null)), true);
        fails(IllegalArgumentException.class, () -> ImageInputCapability.fromInputModalities(Arrays.asList("\u2003")), true);
        fails(NullPointerException.class, () -> ImageInputCapability.parse(null), true);
        fails(IllegalArgumentException.class, () -> ImageInputCapability.parse("missing"), true);
    }

    private static void updates() throws Exception {
        ModelMetadata value = metadata(null, ImageInputCapability.UNKNOWN);
        Map<ModelMetadata.Key, ModelMetadata> mutable = new HashMap<>(); mutable.put(value.key(), value);
        ModelMetadataUpdate update = new ModelMetadataUpdate(mutable, null); mutable.clear();
        equal(1, update.entries().size()); equal(value, update.entries().get(value.key()));
        fails(UnsupportedOperationException.class, () -> update.entries().clear(), true);
        fails(NullPointerException.class, () -> new ModelMetadataUpdate(null, null), false);
        Map<ModelMetadata.Key, ModelMetadata> nullKey = new HashMap<>(); nullKey.put(null, value);
        Map<ModelMetadata.Key, ModelMetadata> nullValue = new HashMap<>(); nullValue.put(value.key(), null);
        // JVM collection implementations use different null messages. Type and rejection are the contract.
        fails(NullPointerException.class, () -> new ModelMetadataUpdate(nullKey, null), false);
        fails(NullPointerException.class, () -> new ModelMetadataUpdate(nullValue, null), false);
        GuideFailure diagnostic = new GuideFailure("metadata_cache_invalid", "Synthetic cache is invalid");
        ModelMetadataUpdate both = new ModelMetadataUpdate(update.entries(), diagnostic);
        equal(diagnostic, both.failure()); equal(update.entries(), both.entries());
        equal(hash(update.entries(), null), update.hashCode());
        equal(update, new ModelMetadataUpdate(update.entries(), null));
        vector("update", update.toString());
    }

    private static void typedJson() throws Exception {
        for (ImageInputCapability capability : ImageInputCapability.values()) {
            ModelMetadata value = metadata(null, capability);
            String encoded = JSON.toJson(value);
            equal(value, JSON.fromJson(encoded, ModelMetadata.class));
            vector("json:" + capability.encoded(), encoded);
        }
        ModelMetadata value = metadata(4096, ImageInputCapability.SUPPORTED);
        String encoded = JSON.toJson(value);
        ModelMetadataResolution resolved = ModelMetadataResolution.resolved(value, 512000, 8192);
        equal(resolved, JSON.fromJson(JSON.toJson(resolved), ModelMetadataResolution.class));
        vector("json:resolved", JSON.toJson(resolved));
        ModelMetadataResolution failed = ModelMetadataResolution.failed("metadata_invalid", "Synthetic metadata invalid");
        equal(failed, JSON.fromJson(JSON.toJson(failed), ModelMetadataResolution.class));
        vector("json:failed", JSON.toJson(failed));
        ModelMetadataUpdate update = new ModelMetadataUpdate(Collections.singletonMap(value.key(), value), null);
        equal(update, JSON.fromJson(JSON.toJson(update), ModelMetadataUpdate.class));
        vector("json:update", JSON.toJson(update));
        java.lang.reflect.Type listType = new TypeToken<List<ModelMetadata>>() {}.getType();
        equal(Arrays.asList(value), JSON.fromJson(JSON.toJson(Arrays.asList(value), listType), listType));
        // The accepted EngineJson shape ignores unknown DTO fields but never ignores duplicate fields.
        equal(value, JSON.fromJson(encoded.substring(0, encoded.length() - 1) + ",\"future\":{\"flag\":true}}", ModelMetadata.class));
        fails(JsonParseException.class, () -> JSON.fromJson(encoded.substring(0, encoded.length() - 1) + ",\"source\":\"other\"}", ModelMetadata.class), true);
        fails(JsonParseException.class, () -> JSON.fromJson(encoded.replace("128000", "0"), ModelMetadata.class), true);
        fails(JsonParseException.class, () -> JSON.fromJson(encoded.replace("128000", "-1"), ModelMetadata.class), true);
        fails(JsonParseException.class, () -> JSON.fromJson(encoded.replace("128000", "2147483648"), ModelMetadata.class), false);
        fails(JsonParseException.class, () -> JSON.fromJson(encoded.replace("128000", "1.5"), ModelMetadata.class), false);
        fails(JsonParseException.class, () -> JSON.fromJson(encoded.replace("128000", "null"), ModelMetadata.class), true);
        fails(JsonParseException.class, () -> JSON.fromJson(encoded.replace("\"contextWindowTokens\":128000,", ""), ModelMetadata.class), true);
        fails(JsonParseException.class, () -> JSON.fromJson(encoded.replace("\"imageInputCapability\":\"SUPPORTED\"", "\"imageInputCapability\":null"), ModelMetadata.class), true);
        fails(JsonParseException.class, () -> JSON.fromJson(encoded.replace(",\"imageInputCapability\":\"SUPPORTED\"", ""), ModelMetadata.class), true);
        fails(JsonParseException.class, () -> JSON.fromJson(encoded.replace("\"SUPPORTED\"", "\"not-a-capability\""), ModelMetadata.class), true);
        equal(Integer.MAX_VALUE, JSON.fromJson(encoded.replace("128000", "2147483647"), ModelMetadata.class).contextWindowTokens());
        equal(128000, JSON.fromJson(encoded.replace("128000", "1.28e5"), ModelMetadata.class).contextWindowTokens());
        equal(null, JSON.fromJson("null", ModelMetadata.class));
        fails(JsonParseException.class, () -> JSON.fromJson("{}", ModelMetadata.class), true);
        fails(JsonParseException.class, () -> JSON.fromJson("[]", ModelMetadata.class), true);
        fails(JsonParseException.class, () -> JSON.fromJson("{}", ModelMetadataResolution.class), true);
        fails(JsonParseException.class, () -> JSON.fromJson("{}", ModelMetadataUpdate.class), true);
        fails(JsonParseException.class, () -> JSON.fromJson("{}", GuideFailure.class), true);
        fails(JsonParseException.class, () -> JSON.fromJson("{\"source\":\"" + SYNTHETIC_SECRET + "\",\"providerModelId\":\"" + SYNTHETIC_URL + "\",\"canonicalModelId\":\"\",\"contextWindowTokens\":0}", ModelMetadata.class), true);
    }

    private static int hash(Object... values) { int result = 0; for (Object value : values) result = 31 * result + Objects.hashCode(value); return result; }
    private static void vector(String name, Object value) { vectors.add(name + "=" + value); }
    private static void equal(Object expected, Object actual) { check(Objects.equals(expected, actual), "Expected " + expected + ", got " + actual); }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void fails(Class<? extends Throwable> expected, Checked operation, boolean stableDiagnostic) throws Exception {
        try { operation.run(); } catch (Throwable failure) {
            check(expected.isInstance(failure), "Expected " + expected.getName() + ", got " + failure.getClass().getName());
            if (stableDiagnostic) {
                String message = String.valueOf(failure.getMessage());
                check(!message.contains(SYNTHETIC_SECRET) && !message.contains(SYNTHETIC_URL), "diagnostic redaction");
                vector("rejected", expected.getSimpleName() + ":" + message);
            }
            return;
        }
        throw new AssertionError("Expected " + expected.getName());
    }
    private static void major52(Class<?> owner) throws Exception {
        try (InputStream stream = owner.getResourceAsStream("/" + owner.getName().replace('.', '/') + ".class")) {
            byte[] header = new byte[8]; int read = 0;
            while (read < header.length) { int count = stream.read(header, read, header.length - read); check(count > 0, "class header"); read += count; }
            equal(52, ((header[6] & 255) << 8) | (header[7] & 255));
        }
    }
}
