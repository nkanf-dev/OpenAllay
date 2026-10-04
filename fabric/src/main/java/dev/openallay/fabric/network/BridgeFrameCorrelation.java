package dev.openallay.fabric.network;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Recovers only unambiguous UUID correlation from a rejected current transport frame.
 * It never accepts the rejected payload or grants another actor request authority.
 */
final class BridgeFrameCorrelation {
    private BridgeFrameCorrelation() {}

    static Optional<UUID> read(String json) {
        if (json == null || json.length() > 32_767) return Optional.empty();
        try (JsonReader reader = dev.openallay.json.JsonReaders.strict(new java.io.StringReader(json))) {

            if (reader.peek() != JsonToken.BEGIN_OBJECT) return Optional.empty();
            reader.beginObject();
            Set<String> fields = new HashSet<>();
            UUID requestId = null;
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (!fields.add(name)) return Optional.empty();
                if (name.equals("requestId")) {
                    if (reader.peek() != JsonToken.STRING) return Optional.empty();
                    String text = reader.nextString();
                    UUID id = UUID.fromString(text);
                    if (!id.toString().equals(text)) return Optional.empty();
                    requestId = id;
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT || requestId == null) return Optional.empty();
            return Optional.of(requestId);
        } catch (IOException | IllegalArgumentException | IllegalStateException invalid) {
            return Optional.empty();
        }
    }
}
