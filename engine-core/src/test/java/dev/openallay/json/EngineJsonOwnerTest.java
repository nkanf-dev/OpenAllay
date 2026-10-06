package dev.openallay.json;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonIOException;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializer;
import com.google.gson.LongSerializationPolicy;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.Since;
import com.google.gson.annotations.Until;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.openallay.context.ContextSnapshotMetricsJson;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class EngineJsonOwnerTest {
    private record Text(String text, String absent) {}
    private record Box<T>(T value, List<T> history) {}
    private record Other(Instant time, int count) {}
    private record Empty() {}
    private record Counts(int count, boolean enabled) {}
    private record Node(Instant time, List<Node> children) {}
    private record Direct(Direct child) {}
    private record Arrays<T>(T[] values) {}
    private record Open(Object value) {}
    private record Marked(@Expose String visible, String hidden,
                          @Since(2.0) String future, @Until(1.0) String past) {}
    private interface Tagged {}
    private record Tag(String text) implements Tagged {}
    private static final class Plain {
        String text = "<&>";
        String absent;
        long bigNumber = 9_007_199_254_740_993L;
        Date date = new Date(0);
        double special = Double.NaN;
    }

    @Test void bindingIsIdempotentAndRawOwnersFailWithMigrationInstruction() {
        Gson bound = EngineJson.create();
        assertSame(bound, EngineJson.withInstant(bound));
        Gson raw = new GsonBuilder().serializeNulls().create();
        String before = raw.toJson(new Text("x", null));
        JsonIOException error = assertThrows(JsonIOException.class, () -> EngineJson.withInstant(raw));
        assertTrue(error.getMessage().contains("EngineJson.create(configuration)"));
        assertThrows(JsonIOException.class, () -> EngineJson.derive(raw, GsonBuilder::setPrettyPrinting));
        assertThrows(JsonIOException.class, () -> new ContextSnapshotMetricsJson(raw));
        assertEquals(before, raw.toJson(new Text("x", null)));
        assertThrows(NullPointerException.class, () -> EngineJson.create(null));
        assertThrows(NullPointerException.class, () -> EngineJson.withInstant(null));
        assertThrows(NullPointerException.class, () -> EngineJson.derive(bound, null));
    }

    @Test void derivedRecipesKeepOrderedOverlaysAndOriginalOwnerUnchanged() {
        Gson owner = EngineJson.create(builder -> builder
                .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE));
        Text value = new Text("<&>", null);
        String original = owner.toJson(value);
        Gson overlay = EngineJson.derive(owner, builder -> builder
                .serializeNulls().disableHtmlEscaping().setPrettyPrinting()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES));
        Gson further = EngineJson.derive(overlay, builder -> builder
                .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE));
        Gson sibling = EngineJson.derive(owner, builder -> builder.disableHtmlEscaping());
        assertNotSame(owner, overlay);
        assertSame(overlay, EngineJson.withInstant(overlay));
        assertTrue(overlay.toJson(value).contains("\n"));
        assertEquals("<&>", tree(overlay.toJson(value)).getAsJsonObject().get("text").getAsString());
        assertTrue(tree(overlay.toJson(value)).getAsJsonObject().get("absent").isJsonNull());
        assertTrue(tree(further.toJson(value)).getAsJsonObject().has("Text"));
        assertFalse(tree(sibling.toJson(value)).getAsJsonObject().has("Absent"));
        assertEquals(original, owner.toJson(value));
        assertEquals("{\"seconds\":0,\"nanos\":0}", owner.toJson(Instant.EPOCH));
    }

    @Test void probesAndFallbacksUseDistinctFreshBuildersWithoutLeakingSentinelsOrCreators() {
        Set<GsonBuilder> builders = Collections.newSetFromMap(new IdentityHashMap<>());
        Consumer<GsonBuilder> configuration = builder -> {
            // Diagnostic recording only: every replay still applies identical configuration.
            assertTrue(builders.add(builder), "A builder was reused across recipe replays");
            builder.disableHtmlEscaping();
        };
        Gson owner = EngineJson.create(configuration);
        int afterCreate = builders.size();
        Type generic = TypeToken.getParameterized(Box.class, Instant.class).getType();
        Box<Instant> box = new Box<>(Instant.ofEpochSecond(-1, 7), List.of(Instant.EPOCH));
        assertEquals(box, owner.fromJson(owner.toJson(box, generic), generic));
        assertEquals(new Empty(), owner.fromJson("{}", Empty.class));
        Other other = new Other(Instant.EPOCH, 7);
        assertEquals(other, owner.fromJson(owner.toJson(other), Other.class));
        assertTrue(builders.size() > afterCreate);
        String beforeOverlay = owner.toJson(other);
        int afterProbes = builders.size();
        owner.toJson(box, generic);
        owner.toJson(new Empty());
        assertEquals(afterProbes, builders.size(), "Bound adapter cache should retain resolved types");
        EngineJson.derive(owner, GsonBuilder::serializeNulls).fromJson("{}", Empty.class);
        assertEquals(beforeOverlay, owner.toJson(other));
        assertEquals("{\"text\":\"<&>\"}", owner.toJson(new Text("<&>", null)));
    }

    @Test void lazyRecursiveParameterizedArraysAndRuntimeRecordsStillUseCanonicalAdapters() {
        Gson owner = EngineJson.create();
        Node value = new Node(Instant.EPOCH, List.of(new Node(Instant.ofEpochSecond(2, 3), List.of())));
        assertEquals(value, owner.fromJson(owner.toJson(value), Node.class));
        Direct direct = new Direct(new Direct(null));
        assertEquals(direct, owner.fromJson(owner.toJson(direct), Direct.class));
        Type arrayType = TypeToken.getParameterized(Arrays.class, Instant.class).getType();
        Arrays<Instant> array = new Arrays<>(new Instant[] {Instant.EPOCH, Instant.ofEpochSecond(-2, 5)});
        Arrays<Instant> decoded = owner.fromJson(owner.toJson(array, arrayType), arrayType);
        assertArrayEquals(array.values(), decoded.values());
        Type nested = TypeToken.getParameterized(Box.class,
                TypeToken.getParameterized(Map.class, String.class, Instant.class).getType()).getType();
        Box<Map<String, Instant>> box = new Box<>(Map.of("first", Instant.EPOCH), List.of(Map.of("next", Instant.EPOCH)));
        assertEquals(box, owner.fromJson(owner.toJson(box, nested), nested));
        assertEquals(tree(owner.toJson(new Other(Instant.EPOCH, 7))),
                tree(owner.toJson(new Open(new Other(Instant.EPOCH, 7)))).getAsJsonObject().get("value"));
    }

    @Test void directionSpecificAndClassExclusionsDoNotLeakAcrossRecipes() {
        Gson writeOnly = EngineJson.create(builder -> builder.addDeserializationExclusionStrategy(skipCount()));
        assertEquals("{\"count\":7,\"enabled\":true}", writeOnly.toJson(new Counts(7, true)));
        assertEquals(new Counts(0, true), writeOnly.fromJson("{\"count\":7,\"enabled\":true}", Counts.class));
        Gson readOnly = EngineJson.create(builder -> builder.addSerializationExclusionStrategy(skipCount()));
        assertEquals("{\"enabled\":true}", readOnly.toJson(new Counts(7, true)));
        assertEquals(new Counts(7, true), readOnly.fromJson("{\"count\":7,\"enabled\":true}", Counts.class));
        ExclusionStrategy classes = new ExclusionStrategy() {
            @Override public boolean shouldSkipClass(Class<?> type) { return type == Counts.class; }
            @Override public boolean shouldSkipField(FieldAttributes field) { return false; }
        };
        Gson excluded = EngineJson.create(builder -> builder.setExclusionStrategies(classes));
        assertEquals("null", excluded.toJson(new Counts(7, true)));
        assertNull(excluded.fromJson("{\"count\":7}", Counts.class));
        assertSame(excluded, EngineJson.withInstant(excluded));
        assertEquals(new Counts(7, true), EngineJson.create().fromJson("{\"count\":7,\"enabled\":true}", Counts.class));
        ExclusionStrategy everyClass = new ExclusionStrategy() {
            @Override public boolean shouldSkipClass(Class<?> type) { return true; }
            @Override public boolean shouldSkipField(FieldAttributes field) { return false; }
        };
        Gson allExcluded = EngineJson.create(builder -> builder.setExclusionStrategies(everyClass));
        assertSame(allExcluded, EngineJson.withInstant(allExcluded));
        Gson allDerived = EngineJson.derive(allExcluded, GsonBuilder::serializeNulls);
        assertSame(allDerived, EngineJson.withInstant(allDerived));
        assertEquals("null", allDerived.toJson(new Counts(7, true)));
        Gson rawExcluded = new GsonBuilder().setExclusionStrategies(everyClass).create();
        assertThrows(JsonIOException.class, () -> EngineJson.withInstant(rawExcluded));
    }

    @Test void standardVersionExposeAndModifierPoliciesRemainPublicBuilderConfiguration() {
        Marked value = new Marked("yes", "hidden", "future", "past");
        Gson versioned = EngineJson.create(builder -> builder.setVersion(1.5));
        assertEquals("{\"visible\":\"yes\",\"hidden\":\"hidden\"}", versioned.toJson(value));
        assertEquals(new Marked("yes", "hidden", null, null), versioned.fromJson(versioned.toJson(value), Marked.class));
        Gson exposed = EngineJson.create(GsonBuilder::excludeFieldsWithoutExposeAnnotation);
        assertEquals("{\"visible\":\"yes\"}", exposed.toJson(value));
        assertEquals(new Marked("yes", null, null, null), exposed.fromJson(exposed.toJson(value), Marked.class));
        Gson finals = EngineJson.create(builder -> builder.excludeFieldsWithModifiers(java.lang.reflect.Modifier.FINAL));
        assertEquals("{}", finals.toJson(new Counts(7, true)));
        assertEquals(new Counts(0, false), finals.fromJson("{\"count\":7}", Counts.class));
    }

    @Test void explicitAdaptersAndHierarchyFactoriesKeepOriginalOrderAfterDerivation() {
        TypeAdapter<Tag> first = tagAdapter("first:");
        TypeAdapter<Tag> last = tagAdapter("last:");
        Gson owner = EngineJson.create(builder -> builder
                .registerTypeAdapter(Tag.class, first).registerTypeAdapter(Tag.class, last));
        assertEquals("\"last:x\"", owner.toJson(new Tag("x")));
        assertSame(last, owner.getAdapter(Tag.class));
        assertEquals(new Tag("x"), owner.fromJson("\"last:x\"", Tag.class));
        Gson derived = EngineJson.derive(owner, builder -> builder.registerTypeAdapter(Tag.class, first));
        assertEquals("\"first:x\"", derived.toJson(new Tag("x")));
        assertSame(first, derived.getAdapter(Tag.class));
        assertEquals("\"last:x\"", owner.toJson(new Tag("x")));
        JsonSerializer<Tagged> hierarchy = (value, type, context) -> context.serialize("hierarchy:" + ((Tag) value).text());
        Gson hierarchyOwner = EngineJson.create(builder -> builder.registerTypeHierarchyAdapter(Tagged.class, hierarchy));
        assertEquals("\"hierarchy:x\"", hierarchyOwner.toJson(new Tag("x")));
        assertEquals("\"hierarchy:x\"", EngineJson.derive(hierarchyOwner, GsonBuilder::serializeNulls).toJson(new Tag("x")));
        Gson exactOverHierarchy = EngineJson.create(builder -> builder
                .registerTypeHierarchyAdapter(Tagged.class, hierarchy).registerTypeAdapter(Tag.class, first));
        assertEquals("\"first:x\"", exactOverHierarchy.toJson(new Tag("x")));
    }

    @Test void explicitRecordFactoryCanDelegateNestedInstantToItsFreshConfiguredGson() {
        TypeAdapterFactory factory = new TypeAdapterFactory() {
            @SuppressWarnings("unchecked")
            @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                if (type.getRawType() != Other.class) return null;
                TypeAdapter<Instant> instant = gson.getAdapter(Instant.class);
                return (TypeAdapter<T>) new TypeAdapter<Other>() {
                    @Override public void write(JsonWriter out, Other value) throws IOException { instant.write(out, value.time()); }
                    @Override public Other read(JsonReader in) throws IOException { return new Other(instant.read(in), 11); }
                };
            }
        };
        TypeAdapter<Instant> custom = new TypeAdapter<>() {
            @Override public void write(JsonWriter out, Instant value) throws IOException { out.value(value.getEpochSecond()); }
            @Override public Instant read(JsonReader in) throws IOException { return Instant.ofEpochSecond(in.nextLong()); }
        };
        Gson owner = EngineJson.create(builder -> builder.registerTypeAdapter(Instant.class, custom)
                .registerTypeAdapterFactory(factory));
        assertEquals("2", owner.toJson(new Other(Instant.ofEpochSecond(2), 7)));
        assertEquals(new Other(Instant.ofEpochSecond(2), 11), owner.fromJson("2", Other.class));
    }

    @Test void canonicalFallbackDoesNotReuseCallerRecordInstanceCreator() {
        InstanceCreator<Counts> caller = ignored -> new Counts(99, true);
        ExclusionStrategy allFields = new ExclusionStrategy() {
            @Override public boolean shouldSkipClass(Class<?> type) { return false; }
            @Override public boolean shouldSkipField(FieldAttributes field) { return true; }
        };
        Gson owner = EngineJson.create(builder -> builder.registerTypeAdapter(Counts.class, caller)
                .setExclusionStrategies(allFields));
        assertEquals(new Counts(0, false), owner.fromJson("{}", Counts.class));
        assertEquals("{}", owner.toJson(new Counts(9, true)));
        Gson ordinary = EngineJson.create(builder -> builder.registerTypeAdapter(Counts.class, caller));
        assertEquals(new Counts(7, true), ordinary.fromJson("{\"count\":7,\"enabled\":true}", Counts.class));
    }

    @Test void builderOptionCombinationsMatchIndependentPublicReplayBeforeAndAfterProbes() {
        Consumer<GsonBuilder> options = builder -> builder.serializeNulls().setPrettyPrinting()
                .disableHtmlEscaping().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .setLongSerializationPolicy(LongSerializationPolicy.STRING).setDateFormat("yyyy-MM-dd")
                .serializeSpecialFloatingPointValues().enableComplexMapKeySerialization()
                .generateNonExecutableJson().setLenient();
        GsonBuilder referenceBuilder = new GsonBuilder();
        options.accept(referenceBuilder);
        Gson reference = referenceBuilder.create();
        Gson owner = EngineJson.create(options);
        Plain plain = new Plain();
        String expected = reference.toJson(plain);
        assertEquals(expected, owner.toJson(plain));
        assertTrue(expected.startsWith(")]}'\n"));
        assertTrue(expected.contains("9007199254740993"));
        Map<List<Integer>, String> map = new LinkedHashMap<>();
        map.put(List.of(1, 2), "<&>");
        Type mapType = TypeToken.getParameterized(Map.class,
                TypeToken.getParameterized(List.class, Integer.class).getType(), String.class).getType();
        assertEquals(reference.toJson(map, mapType), owner.toJson(map, mapType));
        assertEquals("ok", owner.fromJson("'ok'", String.class));
        owner.toJson(new Other(Instant.EPOCH, 7));
        owner.fromJson("{}", Empty.class);
        EngineJson.derive(owner, builder -> builder.setDateFormat("yyyy")).toJson(plain);
        assertEquals(expected, owner.toJson(plain));
        assertEquals(reference.toJson(map, mapType), owner.toJson(map, mapType));
    }

    @Test void metricsOptionalOverlayRetainsCallerAdapterAndOwnerByteShape() {
        TypeAdapter<Instant> custom = new TypeAdapter<>() {
            @Override public void write(JsonWriter out, Instant value) throws IOException { out.value("custom:" + value); }
            @Override public Instant read(JsonReader in) throws IOException { return Instant.parse(in.nextString().substring(7)); }
        };
        Gson owner = EngineJson.create(builder -> builder.registerTypeAdapter(Instant.class, custom).disableHtmlEscaping());
        String before = owner.toJson(new Text("<&>", null));
        ContextSnapshotMetricsJson metrics = new ContextSnapshotMetricsJson(owner);
        assertEquals(owner.toJson(Instant.EPOCH).getBytes(StandardCharsets.UTF_8).length, metrics.bytes(Optional.of(Instant.EPOCH)));
        assertEquals(4, metrics.bytes(Optional.empty()));
        assertEquals(0, metrics.bytes(null));
        assertEquals(before, owner.toJson(new Text("<&>", null)));
        assertSame(owner, EngineJson.withInstant(owner));
        Gson optionalOwner = EngineJson.derive(owner, builder -> builder.registerTypeHierarchyAdapter(Optional.class,
                (JsonSerializer<Optional<?>>) (value, type, context) ->
                        value.isPresent() ? context.serialize(value.orElseThrow()) : JsonNull.INSTANCE));
        assertSame(optionalOwner, EngineJson.withInstant(optionalOwner));
        assertEquals("\"custom:1970-01-01T00:00:00Z\"", optionalOwner.toJson(Optional.of(Instant.EPOCH)));
    }

    private static com.google.gson.JsonElement tree(String json) { return new JsonParser().parse(json); }

    private static ExclusionStrategy skipCount() {
        return new ExclusionStrategy() {
            @Override public boolean shouldSkipClass(Class<?> type) { return false; }
            @Override public boolean shouldSkipField(FieldAttributes field) { return field.getName().equals("count"); }
        };
    }

    private static TypeAdapter<Tag> tagAdapter(String prefix) {
        return new TypeAdapter<>() {
            @Override public void write(JsonWriter out, Tag value) throws IOException { out.value(prefix + value.text()); }
            @Override public Tag read(JsonReader in) throws IOException {
                String value = in.nextString();
                if (!value.startsWith(prefix)) throw new JsonParseException("caller tag rejected");
                return new Tag(value.substring(prefix.length()));
            }
        };
    }
}
