package dev.openallay.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import java.io.Reader;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Public tree operations shared by every supported host Gson ABI. */
public final class JsonTrees {
    private JsonTrees() {}

    /** Preserve JsonParser's legacy lenient tree parsing and complete-input checks. */
    public static JsonElement parse(String json) {
        return new JsonParser().parse(json);
    }

    /** Preserve JsonParser's Reader ownership and parse-error behavior. */
    public static JsonElement parse(Reader reader) {
        return new JsonParser().parse(reader);
    }

    /** Parse the next stream value; Gson restores the caller's lenient flag. */
    public static JsonElement parse(JsonReader reader) {
        return new JsonParser().parse(reader);
    }

    /** Copy mutable containers without serializing, narrowing, or reparsing numbers. */
    public static JsonElement copy(JsonElement source) {
        Objects.requireNonNull(source, "source");
        if (source.isJsonNull()) return JsonNull.INSTANCE;
        if (source.isJsonObject()) return copy(source.getAsJsonObject());
        if (source.isJsonArray()) return copy(source.getAsJsonArray());
        JsonPrimitive primitive = source.getAsJsonPrimitive();
        if (primitive.isBoolean()) return new JsonPrimitive(primitive.getAsBoolean());
        if (primitive.isNumber()) return new JsonPrimitive(primitive.getAsNumber());
        return new JsonPrimitive(primitive.getAsString());
    }

    public static JsonObject copy(JsonObject source) {
        Objects.requireNonNull(source, "source");
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            result.add(entry.getKey(), copy(entry.getValue()));
        }
        return result;
    }

    public static JsonArray copy(JsonArray source) {
        Objects.requireNonNull(source, "source");
        JsonArray result = new JsonArray();
        for (JsonElement value : source) result.add(copy(value));
        return result;
    }

    /** Live, insertion-ordered key view; removal updates the source object. */
    public static Set<String> keys(JsonObject source) {
        Objects.requireNonNull(source, "source");
        return new AbstractSet<>() {
            @Override public int size() { return source.entrySet().size(); }
            @Override public boolean contains(Object key) {
                return key instanceof String && source.has((String) key);
            }
            @Override public boolean remove(Object key) {
                return key instanceof String && source.remove((String) key) != null;
            }
            @Override public void clear() { source.entrySet().clear(); }
            @Override public Iterator<String> iterator() {
                Iterator<Map.Entry<String, JsonElement>> entries = source.entrySet().iterator();
                return new Iterator<>() {
                    @Override public boolean hasNext() { return entries.hasNext(); }
                    @Override public String next() { return entries.next().getKey(); }
                    @Override public void remove() { entries.remove(); }
                };
            }
        };
    }
}
