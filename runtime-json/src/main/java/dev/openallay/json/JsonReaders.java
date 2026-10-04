package dev.openallay.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.MalformedJsonException;
import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

/** Strict Gson streaming on the shared host ABI. Gson still owns tokens and JSON values. */
public final class JsonReaders {
    private static final TypeAdapter<JsonElement> ELEMENT = new Gson().getAdapter(JsonElement.class);
    private JsonReaders() {}

    public static JsonReader strict(Reader source) {
        JsonReader reader = new JsonReader(new StrictCharacters(Objects.requireNonNull(source, "source")));
        reader.setLenient(false);
        return reader;
    }

    /** The public adapter does not temporarily enable lenient parsing, unlike older JsonParser. */
    public static JsonElement read(JsonReader reader) throws IOException {
        return ELEMENT.read(reader);
    }

    /** Only the four departures documented by Gson's legacy-strict mode, not a JSON parser. */
    private static final class StrictCharacters extends FilterReader {
        private boolean string;
        private boolean escaped;
        private boolean token;
        private boolean number;

        private StrictCharacters(Reader source) { super(source); }

        @Override public int read() throws IOException {
            int value = in.read();
            if (value != -1) check((char) value);
            return value;
        }

        @Override public int read(char[] buffer, int offset, int length) throws IOException {
            int count = in.read(buffer, offset, length);
            for (int index = offset; index < offset + count; index++) check(buffer[index]);
            return count;
        }

        private void check(char value) throws IOException {
            if (string) {
                if (value < 0x20) throw invalid("Unescaped control character in JSON string");
                if (escaped) {
                    if (value == '\'') throw invalid("Invalid apostrophe escape in JSON string");
                    escaped = false;
                } else if (value == '\\') {
                    escaped = true;
                } else if (value == '"') {
                    string = false;
                    token = false;
                }
            } else if (value == '"') {
                string = true;
                token = false;
            } else if (value == ' ' || value == '\t' || value == '\r' || value == '\n'
                    || value == '{' || value == '}' || value == '[' || value == ']'
                    || value == ':' || value == ',' || value == '\uFEFF') {
                token = false;
            } else {
                if (!token) {
                    token = true;
                    number = value == '-' || value >= '0' && value <= '9';
                }
                if (value >= 'A' && value <= 'Z' && !(number && value == 'E')) {
                    throw invalid("JSON literals must be lowercase");
                }
            }
        }

        private static MalformedJsonException invalid(String message) {
            return new MalformedJsonException(message);
        }
    }
}
