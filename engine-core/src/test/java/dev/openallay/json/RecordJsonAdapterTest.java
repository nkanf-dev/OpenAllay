package dev.openallay.json;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RecordJsonAdapterTest {
    private record Empty() {}
    private record Counts(int count, boolean enabled, String label) {}
    private record Generic<T>(T value, List<T> history) {}
    private record Renamed(@com.google.gson.annotations.SerializedName(value = "main", alternate = {"alias"}) String text) {}
    private record Annotated(@com.google.gson.annotations.JsonAdapter(UpperAdapter.class) String text) {}
    public static final class UpperAdapter extends TypeAdapter<String> {
        @Override public void write(JsonWriter out, String value) throws IOException { out.value(value.toUpperCase(java.util.Locale.ROOT)); }
        @Override public String read(JsonReader in) throws IOException { return in.nextString().toLowerCase(java.util.Locale.ROOT); }
    }
    private record Validated(int count) {
        private Validated { if (count < 1) throw new IllegalArgumentException("count must be positive"); }
    }
    private static int constructions;
    private record Counted(int count) { private Counted { constructions++; } }

    @Test void canonicalConstructionValidatesAndUsesPrimitiveDefaults() {
        Gson gson = EngineJson.create();
        assertEquals(new Counts(0, false, null), gson.fromJson("{}", Counts.class));
        assertEquals(new Counts(7, true, "a"), gson.fromJson("{\"count\":7,\"enabled\":true,\"label\":\"a\"}", Counts.class));
        assertThrows(JsonParseException.class, () -> gson.fromJson("{\"count\":null}", Counts.class));
        assertThrows(JsonParseException.class, () -> gson.fromJson("{\"count\":1,\"count\":2}", Counts.class));
        assertThrows(JsonParseException.class, () -> gson.fromJson("{\"count\":0}", Validated.class));
        assertEquals(new Validated(1), gson.fromJson("{\"count\":1}", Validated.class));
        constructions = 0;
        gson.fromJson("{\"count\":7}", Counted.class);
        assertEquals(1, constructions);
    }

    @Test void genericComponentsKeepDeclaredInstantAdapters() {
        Gson gson = EngineJson.create();
        var type = TypeToken.getParameterized(Generic.class, Instant.class).getType();
        var value = new Generic<>(Instant.ofEpochSecond(-1, 17), List.of(Instant.EPOCH));
        assertEquals(value, gson.fromJson(gson.toJson(value, type), type));
    }

    @Test void namingAnnotationsAndPartialExclusionsKeepConfiguredShape() {
        Gson gson = EngineJson.create(builder -> builder
                .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
                .addSerializationExclusionStrategy(skip(false))
                .addDeserializationExclusionStrategy(skip(false)));
        assertEquals("{\"Enabled\":true,\"Label\":\"a\"}", gson.toJson(new Counts(8, true, "a")));
        assertEquals(new Counts(0, true, "a"), gson.fromJson("{\"Count\":9,\"Enabled\":true,\"Label\":\"a\"}", Counts.class));
        assertEquals("{\"main\":\"a\"}", gson.toJson(new Renamed("a")));
        assertEquals(new Renamed("b"), gson.fromJson("{\"alias\":\"b\"}", Renamed.class));
        assertThrows(JsonParseException.class, () -> gson.fromJson("{\"main\":\"a\",\"alias\":\"b\"}", Renamed.class));
        assertEquals(new Renamed("a"), gson.fromJson("{\"main\":\"a\",\"unknown\":7}", Renamed.class));
        assertEquals("{\"Text\":\"A\"}", gson.toJson(new Annotated("a")));
        assertEquals(new Annotated("b"), gson.fromJson("{\"Text\":\"B\"}", Annotated.class));
    }

    private static ExclusionStrategy skip(boolean all) {
        return new ExclusionStrategy() {
            @Override public boolean shouldSkipClass(Class<?> type) { return false; }
            @Override public boolean shouldSkipField(FieldAttributes field) { return all || field.getName().equals("count"); }
        };
    }

    @Test void emptyAndFullyExcludedRecordsUseCanonicalDefaults() {
        Gson gson = EngineJson.create(builder -> builder.addSerializationExclusionStrategy(skip(true))
                .addDeserializationExclusionStrategy(skip(true)));
        assertEquals(new Empty(), gson.fromJson("{}", Empty.class));
        assertEquals(new Counts(0, false, null), gson.fromJson("{\"count\":7}", Counts.class));
        assertEquals("{}", gson.toJson(new Counts(7, true, "ignored")));
        assertThrows(JsonParseException.class, () -> gson.fromJson("{}", Validated.class));
        constructions = 0;
        gson.fromJson("{}", Counted.class);
        assertEquals(1, constructions);
        Gson ordinary = EngineJson.create();
        assertEquals(new Empty(), ordinary.fromJson(ordinary.toJson(new Empty()), Empty.class));
    }

    @Test void callerEmptyRecordFactoryAndItsErrorsKeepPrecedence() {
        TypeAdapter<Empty> empty = new TypeAdapter<>() {
            @Override public void write(JsonWriter out, Empty value) throws IOException { out.value("custom-empty"); }
            @Override public Empty read(JsonReader in) throws IOException {
                if (!"custom-empty".equals(in.nextString())) throw new JsonParseException("caller rejected empty");
                return new Empty();
            }
        };
        TypeAdapterFactory factory = new TypeAdapterFactory() {
            @SuppressWarnings("unchecked")
            @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                return type.getRawType() == Empty.class ? (TypeAdapter<T>) empty : null;
            }
        };
        Gson gson = EngineJson.create(builder -> builder.registerTypeAdapterFactory(factory));
        assertEquals("\"custom-empty\"", gson.toJson(new Empty()));
        assertEquals(new Empty(), gson.fromJson("\"custom-empty\"", Empty.class));
        var error = assertThrows(JsonParseException.class, () -> gson.fromJson("\"wrong\"", Empty.class));
        assertEquals("caller rejected empty", error.getMessage());
    }

    @Test void callerNonemptyRecordAdapterKeepsPrecedence() {
        TypeAdapter<Counts> custom = new TypeAdapter<>() {
            @Override public void write(JsonWriter out, Counts value) throws IOException { out.value(value.count()); }
            @Override public Counts read(JsonReader in) throws IOException { return new Counts(in.nextInt(), true, "caller"); }
        };
        Gson gson = EngineJson.create(builder -> builder.registerTypeAdapter(Counts.class, custom));
        assertEquals("7", gson.toJson(new Counts(7, false, "ignored")));
        assertEquals(new Counts(7, true, "caller"), gson.fromJson("7", Counts.class));
    }

    @Test void productionOptionalComponentAnnotationKeepsTypedAdapter() {
        Gson gson = EngineJson.create();
        var value = dev.openallay.model.ModelMessage.userText("hello");
        var tree = gson.toJsonTree(value).getAsJsonObject();
        assertTrue(gson.toJson(value).contains("hello"));
        assertTrue(!tree.has("inputObservation") || tree.get("inputObservation").isJsonNull());
    }
}
