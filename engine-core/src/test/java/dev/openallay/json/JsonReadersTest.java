package dev.openallay.json;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

final class JsonReadersTest {
    private static JsonElement parse(String text) throws IOException {
        try (var reader = JsonReaders.strict(new StringReader(text))) {
            JsonElement value = JsonReaders.read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing JSON");
            return value;
        }
    }

    @Test void acceptsStandardLiteralsNumbersAndEscapedStringCharacters() throws IOException {
        assertTrue(parse("true").getAsBoolean());
        assertFalse(parse("false").getAsBoolean());
        assertTrue(parse("null").isJsonNull());
        for (String number : List.of("0", "-1", "1.25", "1E10", "-1.5E-3", "1e+2")) {
            assertEquals(number, parse(number).getAsString());
        }
        // The host owns numeric representation; older Gson canonicalizes integer negative zero.
        JsonElement negativeZero = parse("-0");
        assertTrue(negativeZero.getAsJsonPrimitive().isNumber());
        assertEquals(0, negativeZero.getAsBigDecimal().compareTo(java.math.BigDecimal.ZERO));
        assertEquals("ABC'\n\t\r\b\f\\/\"\u0000",
                parse("\"ABC'\\n\\t\\r\\b\\f\\\\\\/\\\"\\u0000\"").getAsString());
        assertEquals("{\"TRUE\":\"NULL\",\"value\":true}",
                parse("{\"TRUE\":\"NULL\",\"value\":true}").toString());
    }

    @Test void rejectsAllDocumentedLegacyStrictDepartures() {
        for (String literal : List.of("TRUE", "True", "tRue", "FALSE", "fAlSe", "NULL", "nUll")) {
            assertThrows(IOException.class, () -> parse("[" + literal + "]"), literal);
        }
        assertThrows(IOException.class, () -> parse("\"bad\\'escape\""));
        assertThrows(IOException.class, () -> parse("\"bad\\\ncontinuation\""));
        for (char control = 0; control < 0x20; control++) {
            String value = "\"raw" + control + "control\"";
            assertThrows(IOException.class, () -> parse(value), Integer.toString(control));
        }
    }

    @Test void treeAdapterNeverEnablesGsonLenientExtensions() {
        for (String malformed : List.of("/*comment*/{}", "//comment\n{}", "{unquoted:1}",
                "{'quoted':1}", "[1,]", "[1;2]", "NaN", "Infinity", "+1", "01",
                "{} {}", ")]}'\n{}", "\"bad\\x\"")) {
            assertThrows(IOException.class, () -> parse(malformed), malformed);
        }
    }

    @Test void streamingGuardWorksAcrossReaderChunkBoundariesAndClosesTheOriginal() throws IOException {
        final class Chunks extends Reader {
            private final String text;
            private int position;
            private boolean closed;
            Chunks(String text) { this.text = text; }
            @Override public int read(char[] buffer, int offset, int length) {
                if (position == text.length()) return -1;
                if (length == 0) return 0;
                buffer[offset] = text.charAt(position++);
                return 1;
            }
            @Override public void close() { closed = true; }
        }
        Chunks source = new Chunks("{\"value\":\"A\\u0042C\",\"n\":1E-2}");
        try (var reader = JsonReaders.strict(source)) {
            assertEquals("ABC", JsonReaders.read(reader).getAsJsonObject().get("value").getAsString());
            assertEquals(JsonToken.END_DOCUMENT, reader.peek());
        }
        assertTrue(source.closed);
        Chunks invalid = new Chunks("{\"value\":\"bad\\'escape\"}");
        try (var reader = JsonReaders.strict(invalid)) {
            assertThrows(IOException.class, () -> JsonReaders.read(reader));
        }
        assertTrue(invalid.closed);
    }
}
