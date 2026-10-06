package dev.openallay.json;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class EngineJsonHierarchyTest {
    private interface Named {}
    private record Alias(@SerializedName(value = "main", alternate = {"alias"}) int value) implements Named {}
    private record Other(int value) implements Named {}
    private record Nested(Instant time) implements Named {}
    private record Empty() implements Named {}
    private record Validated(int value) implements Named {
        private Validated { if (value < 1) throw new IllegalArgumentException("positive value required"); }
    }
    private static final class Plain { int value = 7; }
    private static final class Mixed extends TypeAdapter<Alias> implements JsonSerializer<Alias> {
        @Override public void write(JsonWriter out, Alias value) throws IOException { out.value(value.value()); }
        @Override public Alias read(JsonReader in) throws IOException { return new Alias(in.nextInt()); }
        @Override public JsonElement serialize(Alias value, java.lang.reflect.Type type, JsonSerializationContext context) {
            return new JsonPrimitive(value.value());
        }
    }

    @Test void serializerOnlyRecordHierarchyKeepsStreamingReadValidationAndCanonicalDefaults() {
        JsonSerializer<Named> serializer = (value, type, context) -> new JsonPrimitive("custom");
        Gson owner = EngineJson.deriveHierarchy(EngineJson.create(), Named.class, serializer);
        assertEquals("\"custom\"", owner.toJson(new Alias(7)));
        assertEquals(new Alias(7), owner.fromJson("{\"alias\":7}", Alias.class));
        assertEquals(new Empty(), owner.fromJson("{}", Empty.class));
        assertThrows(JsonParseException.class, () -> owner.fromJson("{\"main\":7,\"main\":8}", Alias.class));
        assertThrows(JsonParseException.class, () -> owner.fromJson("{\"main\":7,\"alias\":8}", Alias.class));
        assertThrows(JsonParseException.class, () -> owner.fromJson("{\"main\":null}", Alias.class));
        assertThrows(JsonParseException.class, () -> owner.fromJson("{}", Validated.class));
        ExclusionStrategy allFields = new ExclusionStrategy() {
            @Override public boolean shouldSkipClass(Class<?> type) { return false; }
            @Override public boolean shouldSkipField(FieldAttributes field) { return true; }
        };
        Gson excluded = EngineJson.deriveHierarchy(EngineJson.create(builder -> builder.setExclusionStrategies(allFields)),
                Named.class, serializer);
        assertEquals(new Alias(0), excluded.fromJson("{\"main\":7}", Alias.class));
        assertThrows(JsonParseException.class, () -> excluded.fromJson("{}", Validated.class));
    }

    @Test void serializerOnlyInstantHierarchyKeepsStrictIntegerReadFromOriginalStream() {
        Gson owner = EngineJson.deriveHierarchy(EngineJson.create(), Instant.class,
                (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive("custom-time"));
        assertEquals("\"custom-time\"", owner.toJson(Instant.EPOCH));
        assertEquals(Instant.EPOCH, owner.fromJson("{\"seconds\":0,\"nanos\":0}", Instant.class));
        for (String json : List.of("{}", "{\"seconds\":0,\"seconds\":1,\"nanos\":0}",
                "{\"seconds\":0,\"nanos\":1.0}", "{\"seconds\":1e0,\"nanos\":0}",
                "{\"seconds\":0,\"nanos\":0,\"extra\":1}")) {
            assertThrows(JsonParseException.class, () -> owner.fromJson(json, Instant.class), json);
        }
    }

    @Test void deserializerOnlyHierarchiesUseCanonicalAndStrictDefaultWriters() {
        Gson records = EngineJson.deriveHierarchy(EngineJson.create(), Named.class,
                (JsonDeserializer<Named>) (tree, type, context) -> new Alias(tree.getAsInt()));
        assertEquals(new Alias(7), records.fromJson("7", Alias.class));
        assertEquals("{\"main\":7}", records.toJson(new Alias(7)));
        Gson instants = EngineJson.deriveHierarchy(EngineJson.create(), Instant.class,
                (JsonDeserializer<Instant>) (tree, type, context) -> Instant.ofEpochSecond(tree.getAsLong()));
        assertEquals(Instant.ofEpochSecond(7), instants.fromJson("7", Instant.class));
        assertEquals("{\"seconds\":0,\"nanos\":0}", instants.toJson(Instant.EPOCH));
    }

    @Test void aCombinedTreeAdapterUsesBothDirectionsAndCustomNullTrees() {
        final class Both implements JsonSerializer<Named>, JsonDeserializer<Named> {
            @Override public JsonElement serialize(Named value, java.lang.reflect.Type type, JsonSerializationContext context) {
                return new JsonPrimitive(((Alias) value).value());
            }
            @Override public Named deserialize(JsonElement tree, java.lang.reflect.Type type, JsonDeserializationContext context) {
                return new Alias(tree.getAsInt());
            }
        }
        Gson both = EngineJson.deriveHierarchy(EngineJson.create(), Named.class, new Both());
        assertEquals("7", both.toJson(new Alias(7)));
        assertEquals(new Alias(7), both.fromJson("7", Alias.class));
        Gson nullTree = EngineJson.deriveHierarchy(EngineJson.create(), Named.class,
                (JsonSerializer<Named>) (value, type, context) -> null);
        assertEquals("null", nullTree.toJson(new Alias(7)));
    }

    @Test void lowerSameBaseEntriesSupplyMissingDirectionsInReverseRegistrationOrder() {
        Gson first = EngineJson.deriveHierarchy(EngineJson.create(), Named.class,
                (JsonSerializer<Named>) (value, type, context) -> new JsonPrimitive("first"));
        Gson second = EngineJson.deriveHierarchy(first, Named.class,
                (JsonDeserializer<Named>) (tree, type, context) -> new Alias(tree.getAsInt()));
        assertEquals("\"first\"", second.toJson(new Alias(7)));
        assertEquals(new Alias(7), second.fromJson("7", Alias.class));
        Gson third = EngineJson.deriveHierarchy(second, Named.class,
                (JsonSerializer<Named>) (value, type, context) -> new JsonPrimitive("third"));
        assertEquals("\"third\"", third.toJson(new Alias(7)));
        assertEquals(new Alias(7), third.fromJson("7", Alias.class));
        assertEquals("\"first\"", first.toJson(new Alias(7)));
        assertSame(first, EngineJson.withInstant(first));
    }

    @Test void exactOrdinaryAdaptersWinBeforeAndAfterHierarchyAndOverlayRegistration() {
        TypeAdapter<Alias> exact = new TypeAdapter<>() {
            @Override public void write(JsonWriter out, Alias value) throws IOException { out.value(value.value()); }
            @Override public Alias read(JsonReader in) throws IOException { return new Alias(in.nextInt()); }
        };
        JsonSerializer<Named> hierarchy = (value, type, context) -> new JsonPrimitive("hierarchy");
        Gson before = EngineJson.create(builder -> builder.registerTypeAdapter(Alias.class, exact));
        Gson after = EngineJson.deriveHierarchy(before, Named.class, hierarchy);
        assertEquals("7", after.toJson(new Alias(7)));
        assertEquals(new Alias(7), after.fromJson("7", Alias.class));
        Gson reversed = EngineJson.derive(EngineJson.deriveHierarchy(EngineJson.create(), Named.class, hierarchy),
                builder -> builder.registerTypeAdapter(Alias.class, exact));
        assertEquals("7", reversed.toJson(new Alias(7)));
        assertEquals("7", EngineJson.derive(after, GsonBuilder::serializeNulls).toJson(new Alias(7)));
        JsonSerializer<Optional<?>> optional = (value, type, context) -> new JsonPrimitive("hierarchy");
        Gson optionalOwner = EngineJson.deriveHierarchy(EngineJson.create(builder -> builder.registerTypeAdapter(Optional.class,
                (JsonSerializer<Optional<?>>) (value, type, context) -> new JsonPrimitive("exact"))), Optional.class, optional);
        assertEquals("\"exact\"", optionalOwner.toJson(Optional.empty()));
    }

    @Test void nestedCustomContextsUseBoundOwnerCanonicalAndInstantAdapters() {
        JsonSerializer<Named> serializer = (value, type, context) -> {
            JsonObject tree = new JsonObject();
            tree.add("time", context.serialize(((Nested) value).time(), Instant.class));
            return tree;
        };
        JsonDeserializer<Named> deserializer = (tree, type, context) ->
                new Nested(context.deserialize(tree.getAsJsonObject().get("time"), Instant.class));
        Gson serialized = EngineJson.deriveHierarchy(EngineJson.create(), Named.class, serializer);
        Gson owner = EngineJson.deriveHierarchy(serialized, Named.class, deserializer);
        Nested value = new Nested(Instant.ofEpochSecond(-1, 7));
        assertEquals(value, owner.fromJson(owner.toJson(value), Nested.class));
        assertThrows(JsonParseException.class, () -> owner.fromJson("{\"time\":{}}", Nested.class));
    }

    @Test void nonEngineMissingDirectionsUseNativeDelegatesAndRequestedSubtypeIsChecked() {
        Gson serializer = EngineJson.deriveHierarchy(EngineJson.create(), Plain.class,
                (JsonSerializer<Plain>) (value, type, context) -> new JsonPrimitive("plain"));
        assertEquals(9, serializer.fromJson("{\"value\":9}", Plain.class).value);
        Gson deserializer = EngineJson.deriveHierarchy(EngineJson.create(), Plain.class,
                (JsonDeserializer<Plain>) (tree, type, context) -> new Plain());
        assertEquals("{\"value\":7}", deserializer.toJson(new Plain()));
        Gson wrong = EngineJson.deriveHierarchy(EngineJson.create(), Named.class,
                (JsonDeserializer<Named>) (tree, type, context) -> new Other(7));
        assertThrows(JsonSyntaxException.class, () -> wrong.fromJson("7", Alias.class));
    }

    @Test void nullBroadClassExclusionAndHelperValidationRetainOwnership() {
        JsonSerializer<Named> serializer = (value, type, context) -> { fail("null must not invoke serializer"); return null; };
        JsonDeserializer<Named> deserializer = (tree, type, context) -> { fail("null must not invoke deserializer"); return null; };
        Gson owner = EngineJson.deriveHierarchy(EngineJson.deriveHierarchy(EngineJson.create(), Named.class, serializer),
                Named.class, deserializer);
        assertEquals("null", owner.toJson(null, Alias.class));
        assertNull(owner.fromJson("null", Alias.class));
        ExclusionStrategy allClasses = new ExclusionStrategy() {
            @Override public boolean shouldSkipClass(Class<?> type) { return true; }
            @Override public boolean shouldSkipField(FieldAttributes field) { return false; }
        };
        Gson excluded = EngineJson.deriveHierarchy(EngineJson.create(builder -> builder.setExclusionStrategies(allClasses)),
                Named.class, serializer);
        assertSame(excluded, EngineJson.withInstant(excluded));
        assertEquals("null", excluded.toJson(new Alias(7)));
        assertThrows(IllegalArgumentException.class, () -> EngineJson.deriveHierarchy(owner, Named.class, new Object()));
        IllegalArgumentException mixed = assertThrows(IllegalArgumentException.class,
                () -> EngineJson.deriveHierarchy(owner, Named.class, new Mixed()));
        assertTrue(mixed.getMessage().contains("ordinary builder registration"));
        assertThrows(NullPointerException.class, () -> EngineJson.deriveHierarchy(owner, null, serializer));
        assertThrows(NullPointerException.class, () -> EngineJson.deriveHierarchy(owner, Named.class, null));
        assertThrows(JsonIOException.class, () -> EngineJson.deriveHierarchy(new Gson(), Named.class, serializer));
    }

    @Test void ordinaryTypeAdapterHierarchyKeepsNativeFactoryOrderAndNullSemantics() {
        TypeAdapter<Named> stream = new TypeAdapter<>() {
            @Override public void write(JsonWriter out, Named value) throws IOException { out.value(value == null ? "stream-null" : "stream"); }
            @Override public Named read(JsonReader in) throws IOException { in.nextString(); return new Alias(7); }
        };
        Gson owner = EngineJson.create(builder -> builder.registerTypeHierarchyAdapter(Named.class, stream));
        assertEquals("\"stream\"", owner.toJson(new Alias(7)));
        assertEquals("\"stream-null\"", owner.toJson(null, Alias.class));
        assertEquals(new Alias(7), owner.fromJson("\"stream\"", Alias.class));
        assertThrows(IllegalArgumentException.class, () -> EngineJson.deriveHierarchy(owner, Named.class, stream));
        Gson tree = EngineJson.deriveHierarchy(owner, Named.class,
                (JsonSerializer<Named>) (value, type, context) -> new JsonPrimitive("tree"));
        assertEquals("\"stream\"", tree.toJson(new Alias(7)));
        assertThrows(JsonSyntaxException.class, () -> tree.fromJson("\"stream\"", Other.class));
    }

    @Test void directionSpecificClassExclusionDoesNotReplaceAllowedCanonicalSide() {
        ExclusionStrategy skip = new ExclusionStrategy() {
            @Override public boolean shouldSkipClass(Class<?> type) { return type == Alias.class; }
            @Override public boolean shouldSkipField(FieldAttributes field) { return false; }
        };
        Gson writeExcluded = EngineJson.create(builder -> builder.addSerializationExclusionStrategy(skip));
        assertEquals("null", writeExcluded.toJson(new Alias(7)));
        assertEquals(new Alias(7), writeExcluded.fromJson("{\"main\":7}", Alias.class));
        assertThrows(JsonParseException.class, () -> writeExcluded.fromJson("{\"main\":7,\"main\":8}", Alias.class));
        Gson readExcluded = EngineJson.create(builder -> builder.addDeserializationExclusionStrategy(skip));
        assertNull(readExcluded.fromJson("{\"main\":7}", Alias.class));
        assertEquals("{\"main\":7}", readExcluded.toJson(new Alias(7)));
    }
}
