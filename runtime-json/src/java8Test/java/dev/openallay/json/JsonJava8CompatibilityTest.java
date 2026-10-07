package dev.openallay.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Standalone deterministic proof; no JUnit VM or native-game dependency. */
public final class JsonJava8CompatibilityTest {
    private JsonJava8CompatibilityTest() {}
    private interface Checked { void run() throws Exception; }

    public static void main(String[] arguments) throws Exception {
        equal("1.8", System.getProperty("java.specification.version"));
        classMajor(JsonReaders.class);
        classMajor(JsonTrees.class);
        classMajor(JsonJava8CompatibilityTest.class);
        strictParsing();
        snapshotElements();
        legacyTreeParsing();
        detachedCopies();
        liveKeys();
        System.out.println("PASS runtime-json genuine Java8: " + System.getProperty("java.version"));
    }

    private static void strictParsing() throws Exception {
        check(parseStrict("true").getAsBoolean(), "true");
        check(!parseStrict("false").getAsBoolean(), "false");
        check(parseStrict("null").isJsonNull(), "null");
        for (String number : Arrays.asList("0", "-1", "1.25", "1E10", "-1.5E-3", "1e+2")) {
            equal(number, parseStrict(number).getAsString());
        }
        equal(0, parseStrict("-0").getAsBigDecimal().compareTo(BigDecimal.ZERO));
        equal("ABC'\n\t\r\b\f\\/\"\u0000",
                parseStrict("\"ABC'\\n\\t\\r\\b\\f\\\\\\/\\\"\\u0000\"").getAsString());
        equal("{\"TRUE\":\"NULL\",\"value\":true}",
                parseStrict("{\"TRUE\":\"NULL\",\"value\":true}").toString());
        for (String text : Arrays.asList("TRUE", "True", "tRue", "FALSE", "fAlSe", "NULL", "nUll",
                "/*comment*/{}", "//comment\n{}", "{unquoted:1}", "{'quoted':1}", "[1,]", "[1;2]",
                "NaN", "Infinity", "+1", "01", "{} {}", ")]}'\n{}", "\"bad\\x\"",
                "\"bad\\'escape\"", "\"bad\\\ncontinuation\"")) {
            fails(IOException.class, () -> parseStrict(text));
        }
        for (char control = 0; control < 0x20; control++) {
            final String text = "\"raw" + control + "control\"";
            fails(IOException.class, () -> parseStrict(text));
        }
        ChunkReader good = new ChunkReader("{\"value\":\"A\\u0042C\",\"n\":1E-2}");
        try (JsonReader reader = JsonReaders.strict(good)) {
            equal("ABC", JsonReaders.read(reader).getAsJsonObject().get("value").getAsString());
            equal(JsonToken.END_DOCUMENT, reader.peek());
        }
        check(good.closed, "strict reader closes original");
        ChunkReader bad = new ChunkReader("{\"value\":\"bad\\'escape\"}");
        try (JsonReader reader = JsonReaders.strict(bad)) {
            fails(IOException.class, () -> JsonReaders.read(reader));
        }
        check(bad.closed, "invalid strict reader closes original");
    }

    private static JsonElement parseStrict(String text) throws IOException {
        try (JsonReader reader = JsonReaders.strict(new StringReader(text))) {
            JsonElement value = JsonReaders.read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON");
            return value;
        }
    }

    private static void snapshotElements() throws Exception {
        JsonArray array = new JsonArray();
        JsonObject first = new JsonObject();
        first.addProperty("name", "old");
        array.add(first);
        array.add((JsonElement) null); // The public Gson ABI normalizes null to JsonNull.
        List<JsonElement> snapshot = JsonReaders.elements(array);
        equal(2, snapshot.size());
        same(first, snapshot.get(0));
        same(JsonNull.INSTANCE, snapshot.get(1));
        array.add(new JsonPrimitive(3));
        array.remove(0);
        equal(2, snapshot.size());
        same(first, snapshot.get(0));
        first.addProperty("name", "new");
        equal("new", snapshot.get(0).getAsJsonObject().get("name").getAsString());
        fails(UnsupportedOperationException.class, () -> snapshot.add(JsonNull.INSTANCE));
        fails(UnsupportedOperationException.class, () -> snapshot.set(0, JsonNull.INSTANCE));
        fails(UnsupportedOperationException.class, () -> snapshot.remove(0));
        fails(NullPointerException.class, () -> JsonReaders.elements(null));
        equal(Collections.emptyList(), JsonReaders.elements(new JsonArray()));
        // Fixture-only corruption reaches a Java null, which List.copyOf previously rejected.
        // No production access to private Gson state is introduced.
        JsonArray corrupted = new JsonArray();
        Field elements = JsonArray.class.getDeclaredField("elements");
        elements.setAccessible(true);
        @SuppressWarnings("unchecked") List<JsonElement> backing = (List<JsonElement>) elements.get(corrupted);
        backing.add(null);
        fails(NullPointerException.class, () -> JsonReaders.elements(corrupted));
    }

    private static void legacyTreeParsing() throws Exception {
        String source = "/* comment */ {unquoted:'value', array:[1,2]}";
        equal("value", JsonTrees.parse(source).getAsJsonObject().get("unquoted").getAsString());
        equal(JsonTrees.parse(source), JsonTrees.parse(new StringReader(source)));
        same(JsonNull.INSTANCE, JsonTrees.parse(""));
        same(JsonNull.INSTANCE, JsonTrees.parse("null"));
        fails(JsonSyntaxException.class, () -> JsonTrees.parse("{} {}"));
        fails(JsonSyntaxException.class, () -> JsonTrees.parse("[1"));
        TrackingReader tracked = new TrackingReader("{x:1}");
        equal(1, JsonTrees.parse(tracked).getAsJsonObject().get("x").getAsInt());
        check(!tracked.closed, "tree parser leaves reader open");
        Reader broken = new Reader() {
            @Override public int read(char[] chars, int offset, int length) throws IOException {
                throw new IOException("read failed");
            }
            @Override public void close() {}
        };
        Throwable failure = fails(JsonIOException.class, () -> JsonTrees.parse(broken));
        equal("read failed", failure.getCause().getMessage());
        try (JsonReader reader = new JsonReader(new StringReader("{x:'value'} [2]"))) {
            reader.setLenient(false);
            equal("value", JsonTrees.parse(reader).getAsJsonObject().get("x").getAsString());
            check(!reader.isLenient(), "tree parsing restores strict flag");
            equal(2, JsonTrees.parse(reader).getAsJsonArray().get(0).getAsInt());
            check(!reader.isLenient(), "second tree parsing restores strict flag");
        }
        try (JsonReader reader = new JsonReader(new StringReader("[1"))) {
            reader.setLenient(false);
            fails(JsonSyntaxException.class, () -> JsonTrees.parse(reader));
            check(!reader.isLenient(), "failed tree parsing restores strict flag");
        }
    }

    private static void detachedCopies() throws Exception {
        JsonObject original = JsonTrees.parse("{first:[{name:'old'},null],last:{flag:true}}")
                .getAsJsonObject();
        JsonObject copy = JsonTrees.copy(original);
        JsonElement elementCopy = JsonTrees.copy((JsonElement) original);
        JsonArray arrayCopy = JsonTrees.copy(original.getAsJsonArray("first"));
        equal(original, copy);
        equal(original, elementCopy);
        check(original != copy, "object detached");
        check(original.get("first") != copy.get("first"), "array detached");
        check(original.getAsJsonArray("first").get(0) != copy.getAsJsonArray("first").get(0), "nested detached");
        equal(Arrays.asList("first", "last"), new ArrayList<String>(JsonTrees.keys(copy)));
        copy.getAsJsonArray("first").get(0).getAsJsonObject().addProperty("name", "new");
        arrayCopy.add(new JsonPrimitive("extra"));
        original.getAsJsonObject("last").addProperty("flag", false);
        equal("old", original.getAsJsonArray("first").get(0).getAsJsonObject().get("name").getAsString());
        equal(2, original.getAsJsonArray("first").size());
        check(copy.getAsJsonObject("last").get("flag").getAsBoolean(), "copy mutation isolation");
        same(JsonNull.INSTANCE, JsonTrees.copy(JsonNull.INSTANCE));
        for (String token : Arrays.asList("9223372036854775808123456789", "1.2300", "1e999", "-0")) {
            JsonElement number = JsonTrees.parse(token);
            JsonElement cloned = JsonTrees.copy(number);
            equal(number.toString(), cloned.toString());
            same(number.getAsNumber(), cloned.getAsNumber());
        }
        for (Number value : Arrays.<Number>asList(new BigInteger("123456789012345678901234567890"),
                new BigDecimal("123.4500"), -0.0d, Double.NaN, Double.POSITIVE_INFINITY)) {
            same(value, JsonTrees.copy(new JsonPrimitive(value)).getAsNumber());
        }
        Number opaque = new Number() {
            @Override public int intValue() { throw new AssertionError("narrowed"); }
            @Override public long longValue() { throw new AssertionError("narrowed"); }
            @Override public float floatValue() { throw new AssertionError("narrowed"); }
            @Override public double doubleValue() { throw new AssertionError("narrowed"); }
            @Override public String toString() { return "7.000"; }
        };
        same(opaque, JsonTrees.copy(new JsonPrimitive(opaque)).getAsNumber());
        equal("text", JsonTrees.copy(new JsonPrimitive("text")).getAsString());
        check(JsonTrees.copy(new JsonPrimitive(true)).getAsBoolean(), "boolean copied");
        fails(NullPointerException.class, () -> JsonTrees.copy((JsonElement) null));
    }

    private static void liveKeys() throws Exception {
        JsonObject object = new JsonObject();
        object.addProperty("first", 1);
        object.add("nullable", JsonNull.INSTANCE);
        Set<String> keys = JsonTrees.keys(object);
        object.addProperty("third", 3);
        equal(Arrays.asList("first", "nullable", "third"), new ArrayList<String>(keys));
        check(!keys.contains(null) && !keys.contains(1) && !keys.remove(1), "non-string keys rejected");
        check(keys.remove("nullable") && !object.has("nullable"), "key remove updates object");
        Iterator<String> iterator = keys.iterator();
        equal("first", iterator.next());
        iterator.remove();
        check(!object.has("first"), "iterator remove updates object");
        fails(UnsupportedOperationException.class, () -> keys.add("unsupported"));
        object.addProperty("fourth", 4);
        check(keys.removeIf(key -> key.equals("third")), "removeIf");
        object.addProperty("fifth", 5);
        check(keys.retainAll(Collections.singleton("fourth")), "retainAll");
        equal(Collections.singleton("fourth"), keys);
        object.addProperty("sixth", 6);
        check(keys.removeAll(Collections.singleton("fourth")), "removeAll");
        check(keys.contains("sixth"), "contains live key");
        keys.clear();
        equal(0, object.entrySet().size());
        object.addProperty("later", 7);
        equal(Collections.singleton("later"), keys);
        object.remove("later");
        check(keys.isEmpty(), "live clear");
    }

    private static void classMajor(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getResourceAsStream(resource)) {
            check(stream != null, "class resource: " + resource);
            byte[] header = new byte[8];
            int position = 0;
            while (position < header.length) {
                int count = stream.read(header, position, header.length - position);
                check(count > 0, "class header");
                position += count;
            }
            equal(52, ((header[6] & 255) << 8) | (header[7] & 255));
        }
    }

    private static Throwable fails(Class<? extends Throwable> expected, Checked action) throws Exception {
        try { action.run(); }
        catch (Throwable failure) {
            if (expected.isInstance(failure)) return failure;
            throw new AssertionError("Expected " + expected.getName() + ", got " + failure, failure);
        }
        throw new AssertionError("Expected " + expected.getName());
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void same(Object expected, Object actual) { check(expected == actual, "identity changed"); }
    private static void equal(Object expected, Object actual) {
        check(expected == null ? actual == null : expected.equals(actual), "Expected " + expected + ", got " + actual);
    }
    private static final class TrackingReader extends StringReader {
        private boolean closed;
        private TrackingReader(String text) { super(text); }
        @Override public void close() { closed = true; super.close(); }
    }
    private static final class ChunkReader extends Reader {
        private final String text;
        private int position;
        private boolean closed;
        private ChunkReader(String text) { this.text = text; }
        @Override public int read(char[] buffer, int offset, int length) {
            if (length == 0) return 0;
            if (position == text.length()) return -1;
            buffer[offset] = text.charAt(position++);
            return 1;
        }
        @Override public void close() { closed = true; }
    }
}
