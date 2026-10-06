package dev.openallay.json;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JsonTreesTest {
    @Test void parseRetainsLegacyLeniencyAndCompleteInputChecks() {
        String source = "/* comment */ {unquoted:'value', array:[1,2]}";
        assertEquals("value", JsonTrees.parse(source).getAsJsonObject().get("unquoted").getAsString());
        assertEquals(JsonTrees.parse(source), JsonTrees.parse(new StringReader(source)));
        assertSame(JsonNull.INSTANCE, JsonTrees.parse(""));
        assertSame(JsonNull.INSTANCE, JsonTrees.parse("null"));
        assertThrows(JsonSyntaxException.class, () -> JsonTrees.parse("{} {}"));
        assertThrows(JsonSyntaxException.class, () -> JsonTrees.parse("[1"));
    }

    @Test void parseDoesNotCloseReadersOrHideIoFailures() {
        class TrackingReader extends StringReader {
            boolean closed;
            TrackingReader() { super("{x:1}"); }
            @Override public void close() { closed = true; super.close(); }
        }
        TrackingReader reader = new TrackingReader();
        assertEquals(1, JsonTrees.parse(reader).getAsJsonObject().get("x").getAsInt());
        assertFalse(reader.closed);
        Reader broken = new Reader() {
            @Override public int read(char[] chars, int offset, int length) throws IOException {
                throw new IOException("read failed");
            }
            @Override public void close() {}
        };
        JsonIOException failure = assertThrows(JsonIOException.class, () -> JsonTrees.parse(broken));
        assertEquals("read failed", failure.getCause().getMessage());
    }

    @Test void jsonReaderParsingRestoresLeniencyAndLeavesTheNextValue() throws IOException {
        try (var reader = new com.google.gson.stream.JsonReader(new StringReader("{x:'value'} [2]"))) {
            reader.setLenient(false);
            assertEquals("value", JsonTrees.parse(reader).getAsJsonObject().get("x").getAsString());
            assertFalse(reader.isLenient());
            assertEquals(2, JsonTrees.parse(reader).getAsJsonArray().get(0).getAsInt());
            assertFalse(reader.isLenient());
        }
        try (var reader = new com.google.gson.stream.JsonReader(new StringReader("[1"))) {
            reader.setLenient(false);
            assertThrows(JsonSyntaxException.class, () -> JsonTrees.parse(reader));
            assertFalse(reader.isLenient());
        }
    }

    @Test void typedCopiesDetachEveryMutableContainerAndKeepInsertionOrder() {
        JsonObject original = JsonTrees.parse("{first:[{name:'old'},null],last:{flag:true}}")
                .getAsJsonObject();
        JsonObject copy = JsonTrees.copy(original);
        JsonElement elementCopy = JsonTrees.copy((JsonElement) original);
        JsonArray arrayCopy = JsonTrees.copy(original.getAsJsonArray("first"));
        assertEquals(original, copy);
        assertEquals(original, elementCopy);
        assertNotSame(original, copy);
        assertNotSame(original.get("first"), copy.get("first"));
        assertNotSame(original.getAsJsonArray("first").get(0), copy.getAsJsonArray("first").get(0));
        assertEquals(List.of("first", "last"), List.copyOf(JsonTrees.keys(copy)));
        copy.getAsJsonArray("first").get(0).getAsJsonObject().addProperty("name", "new");
        arrayCopy.add("extra");
        original.getAsJsonObject("last").addProperty("flag", false);
        assertEquals("old", original.getAsJsonArray("first").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(2, original.getAsJsonArray("first").size());
        assertTrue(copy.getAsJsonObject("last").get("flag").getAsBoolean());
        assertSame(JsonNull.INSTANCE, JsonTrees.copy(JsonNull.INSTANCE));
    }

    @Test void copiesRetainNumericObjectsAndLexemesWithoutConversion() {
        for (String token : List.of("9223372036854775808123456789", "1.2300", "1e999", "-0")) {
            JsonElement original = JsonTrees.parse(token);
            JsonElement copy = JsonTrees.copy(original);
            assertEquals(token, copy.toString());
            assertSame(original.getAsNumber(), copy.getAsNumber());
        }
        for (Number value : List.of(new BigInteger("123456789012345678901234567890"),
                new BigDecimal("123.4500"), Double.NaN, Double.POSITIVE_INFINITY)) {
            JsonPrimitive original = new JsonPrimitive(value);
            assertSame(value, JsonTrees.copy(original).getAsNumber());
        }
        Number opaque = new Number() {
            @Override public int intValue() { throw new AssertionError("narrowed"); }
            @Override public long longValue() { throw new AssertionError("narrowed"); }
            @Override public float floatValue() { throw new AssertionError("narrowed"); }
            @Override public double doubleValue() { throw new AssertionError("narrowed"); }
            @Override public String toString() { return "7.000"; }
        };
        assertSame(opaque, JsonTrees.copy(new JsonPrimitive(opaque)).getAsNumber());
        assertEquals("text", JsonTrees.copy(new JsonPrimitive("text")).getAsString());
        assertTrue(JsonTrees.copy(new JsonPrimitive(true)).getAsBoolean());
    }

    @Test void keysRemainLiveAndSupportAllRemovalPaths() {
        JsonObject object = new JsonObject();
        object.addProperty("first", 1);
        object.add("nullable", JsonNull.INSTANCE);
        Set<String> keys = JsonTrees.keys(object);
        object.addProperty("third", 3);
        assertEquals(List.of("first", "nullable", "third"), List.copyOf(keys));
        assertFalse(keys.contains(null));
        assertFalse(keys.contains(1));
        assertFalse(keys.remove(1));
        assertTrue(keys.remove("nullable"));
        assertFalse(object.has("nullable"));
        Iterator<String> iterator = keys.iterator();
        assertEquals("first", iterator.next());
        iterator.remove();
        assertFalse(object.has("first"));
        assertThrows(UnsupportedOperationException.class, () -> keys.add("unsupported"));
        object.addProperty("fourth", 4);
        assertTrue(keys.removeIf(key -> key.equals("third")));
        object.addProperty("fifth", 5);
        assertTrue(keys.retainAll(Set.of("fourth")));
        assertEquals(Set.of("fourth"), keys);
        object.addProperty("sixth", 6);
        assertTrue(keys.removeAll(Set.of("fourth")));
        assertTrue(keys.contains("sixth"));
        keys.clear();
        assertEquals(0, object.entrySet().size());
        object.addProperty("later", 7);
        assertEquals(Set.of("later"), keys);
        object.remove("later");
        assertTrue(keys.isEmpty());
    }

    @Test void strictParserRemainsSeparateAndRejectsLegacyLenientInputs() throws IOException {
        try (var reader = JsonReaders.strict(new StringReader("{unquoted:'value'}"))) {
            assertThrows(IOException.class, () -> JsonReaders.read(reader));
        }
        try (var reader = JsonReaders.strict(new StringReader("{\"number\":1e2,\"value\":true}"))) {
            assertEquals(100, JsonReaders.read(reader).getAsJsonObject().get("number").getAsInt());
        }
    }
}
