package dev.openallay.extension.universal;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.openallay.bridge.protocol.BridgeJsonCodec;
import java.io.IOException;
import java.io.StringReader;

/** Reuses Gson and the existing duplicate-field scan, without a new JSON value parser or policy. */
final class UniversalExtensionJson {
    private UniversalExtensionJson() {}

    static JsonElement parse(String json) {
        if (json == null) throw new IllegalArgumentException("JSON text is required");
        BridgeJsonCodec.rejectDuplicateFields(json);
        try (JsonReader reader = dev.openallay.json.JsonReaders.strict(new java.io.StringReader(json))) {

            JsonElement value = dev.openallay.json.JsonReaders.read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("JSON must contain exactly one value");
            }
            return value;
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Invalid JSON value");
        }
    }
}
