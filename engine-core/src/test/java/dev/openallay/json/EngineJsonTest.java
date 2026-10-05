package dev.openallay.json;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class EngineJsonTest {
    private record Detached(UUID id, Instant capturedAt, List<Instant> history) {}

    @Test void typedTimestampKeepsSecondsAndNanosAndRoundTripsNestedRecords() {
        Gson gson = EngineJson.withInstant(new Gson());
        for (Instant time : List.of(Instant.EPOCH, Instant.ofEpochSecond(-1, 999_999_999),
                Instant.parse("2026-10-04T11:27:00.125Z"), Instant.MIN, Instant.MAX)) {
            String encoded = gson.toJson(time);
            assertEquals("{\"seconds\":" + time.getEpochSecond() + ",\"nanos\":" + time.getNano() + "}", encoded);
            assertEquals(time, gson.fromJson(encoded, Instant.class));
            Detached value = new Detached(UUID.fromString("00000000-0000-0000-0000-000000000041"),
                    time, List.of(time, Instant.EPOCH));
            assertEquals(value, gson.fromJson(gson.toJson(value), Detached.class));
            assertEquals(value.id().toString(), gson.toJsonTree(value).getAsJsonObject().get("id").getAsString());
        }
        assertEquals(Instant.ofEpochSecond(-1, 9), gson.fromJson("{\"nanos\":9,\"seconds\":-1}", Instant.class));
        assertNull(gson.fromJson("null", Instant.class));
    }

    @Test void timestampRequiresTheExactCurrentIntegerShape() {
        Gson gson = EngineJson.withInstant(new Gson());
        for (String invalid : List.of(
                "\"1970-01-01T00:00:00Z\"", "0", "[]", "{}",
                "{\"seconds\":0}", "{\"nanos\":0}",
                "{\"seconds\":0,\"nanos\":0,\"extra\":1}",
                "{\"seconds\":0,\"seconds\":0,\"nanos\":0}",
                "{\"seconds\":0,\"nanos\":0,\"nanos\":0}",
                "{\"seconds\":\"0\",\"nanos\":0}", "{\"seconds\":0,\"nanos\":\"0\"}",
                "{\"seconds\":null,\"nanos\":0}", "{\"seconds\":0,\"nanos\":null}",
                "{\"seconds\":true,\"nanos\":0}", "{\"seconds\":0,\"nanos\":false}",
                "{\"seconds\":0.5,\"nanos\":0}", "{\"seconds\":0,\"nanos\":1.0}",
                "{\"seconds\":1e0,\"nanos\":0}", "{\"seconds\":0,\"nanos\":-1}",
                "{\"seconds\":0,\"nanos\":1000000000}",
                "{\"seconds\":9223372036854775808,\"nanos\":0}",
                "{\"seconds\":0,\"nanos\":9223372036854775808}",
                "{\"seconds\":31556889864403200,\"nanos\":0}",
                "{\"seconds\":-31557014167219201,\"nanos\":0}")) {
            assertThrows(JsonParseException.class, () -> gson.fromJson(invalid, Instant.class), invalid);
        }
    }

    @Test void customTimestampFactoryKeepsItsWriteAndReadSemantics() {
        TypeAdapter<Instant> custom = new TypeAdapter<>() {
            @Override public void write(JsonWriter out, Instant value) throws IOException {
                out.value("caller:" + value);
            }
            @Override public Instant read(JsonReader in) throws IOException {
                return Instant.parse(in.nextString().substring("caller:".length()));
            }
        };
        TypeAdapterFactory factory = new TypeAdapterFactory() {
            @SuppressWarnings("unchecked")
            @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                return type.getRawType() == Instant.class ? (TypeAdapter<T>) custom.nullSafe() : null;
            }
        };
        Gson source = new GsonBuilder().serializeNulls().disableHtmlEscaping()
                .registerTypeAdapterFactory(factory).create();
        Gson gson = EngineJson.withInstant(source);
        assertNotSame(source, gson);
        assertSame(gson, EngineJson.withInstant(gson));
        var value = new Detached(UUID.randomUUID(), Instant.ofEpochSecond(-1, 7), List.of(Instant.EPOCH));
        assertEquals(value, gson.fromJson(gson.toJson(value), Detached.class));
        assertEquals("caller:" + value.capturedAt(), gson.toJsonTree(value).getAsJsonObject().get("capturedAt").getAsString());
        assertEquals("\"<&>\"", gson.toJson("<&>"));
        assertTrue(gson.toJson(new Detached(value.id(), null, List.of())).contains("\"capturedAt\":null"));
    }

    @Test void defaultBindingRetainsBuilderOptionsWithoutChangingTheSourceGson() {
        Gson source = new GsonBuilder().serializeNulls().setPrettyPrinting().disableHtmlEscaping().create();
        Gson gson = EngineJson.withInstant(source);
        var tree = JsonParser.parseString(gson.toJson(new Detached(null, Instant.EPOCH, List.of()))).getAsJsonObject();
        assertTrue(tree.get("id").isJsonNull());
        assertEquals(JsonParser.parseString("{\"seconds\":0,\"nanos\":0}"), tree.get("capturedAt"));
        assertTrue(gson.toJson(Instant.EPOCH).contains("\n"));
        assertEquals("\"<&>\"", gson.toJson("<&>"));
        assertNotSame(source, gson);
        assertSame(gson, EngineJson.withInstant(gson));
    }
}
