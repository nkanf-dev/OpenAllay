package dev.openallay.world;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Current detached input-reference shape. Gson owns typed records and JavaTime serialization. */
public final class ClientObservationAnchorJson {
    private static final Gson GSON = new Gson().newBuilder().serializeNulls().create();

    private ClientObservationAnchorJson() {}

    /** Nullable image at this one wire seam avoids reflecting into Optional internals. */
    private record Shape(UUID associationId, Instant capturedAt, WorldFocusObservation focus,
                         WorldViewCapture image) {}

    public static JsonElement encode(Optional<ClientObservationAnchor> observation) {
        return observation.map(anchor -> GSON.toJsonTree(new Shape(anchor.associationId(),
                anchor.capturedAt(), anchor.focus(), anchor.image().orElse(null))))
                .orElse(JsonNull.INSTANCE);
    }

    public static Optional<ClientObservationAnchor> decode(JsonElement json) {
        if (json == null) throw new IllegalArgumentException("inputObservation is required");
        if (json.isJsonNull()) return Optional.empty();
        try {
            Shape shape = GSON.fromJson(json, Shape.class);
            ClientObservationAnchor anchor = new ClientObservationAnchor(shape.associationId(),
                    shape.capturedAt(), shape.focus(), Optional.ofNullable(shape.image()));
            anchor.image().ifPresent(image -> {
                if (!image.actorId().equals(anchor.focus().actorId())
                        || !image.dimension().equals(anchor.focus().dimension())) {
                    throw new IllegalArgumentException("Input reference source identities differ");
                }
            });
            exact(json, encode(Optional.of(anchor)));
            return Optional.of(anchor);
        } catch (IllegalArgumentException invalid) {
            throw invalid;
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("Invalid input observation metadata", malformed);
        }
    }

    /** Compare against Gson's typed current shape; no native-object parser or inferred image refs. */
    private static void exact(JsonElement supplied, JsonElement typed) {
        if (supplied == null) throw new IllegalArgumentException("Missing input observation metadata");
        if (typed.isJsonObject()) {
            if (!supplied.isJsonObject() || !supplied.getAsJsonObject().keySet().equals(typed.getAsJsonObject().keySet())) {
                throw new IllegalArgumentException("Input metadata fields do not match the current shape");
            }
            for (var entry : typed.getAsJsonObject().entrySet()) {
                exact(supplied.getAsJsonObject().get(entry.getKey()), entry.getValue());
            }
        } else if (typed.isJsonArray()) {
            if (!supplied.isJsonArray() || supplied.getAsJsonArray().size() != typed.getAsJsonArray().size()) {
                throw new IllegalArgumentException("Input metadata array shape differs");
            }
            for (int i = 0; i < typed.getAsJsonArray().size(); i++) {
                exact(supplied.getAsJsonArray().get(i), typed.getAsJsonArray().get(i));
            }
        } else if (typed.isJsonPrimitive()) {
            if (!supplied.isJsonPrimitive()) throw new IllegalArgumentException("Input metadata must be scalar");
            var expected = typed.getAsJsonPrimitive();
            var actual = supplied.getAsJsonPrimitive();
            boolean equal = expected.isNumber() && actual.isNumber()
                    ? expected.getAsBigDecimal().compareTo(actual.getAsBigDecimal()) == 0 : expected.equals(actual);
            if (expected.isString() != actual.isString() || expected.isBoolean() != actual.isBoolean()
                    || expected.isNumber() != actual.isNumber() || !equal) {
                throw new IllegalArgumentException("Input metadata scalar type or value differs");
            }
            if (expected.isNumber() && expected.getAsString().matches("-?(0|[1-9][0-9]*)")
                    && !actual.getAsString().matches("-?(0|[1-9][0-9]*)")) {
                throw new IllegalArgumentException("Input metadata must be an integer");
            }
        } else if (!supplied.isJsonNull()) throw new IllegalArgumentException("Input metadata must be null");
    }

    /** Local adapter for the model/bridge Optional field, not a global Gson Optional policy. */
    public static final class OptionalAdapter extends TypeAdapter<Optional<ClientObservationAnchor>> {
        @Override public void write(JsonWriter out, Optional<ClientObservationAnchor> value) throws IOException {
            boolean previous = out.getSerializeNulls();
            out.setSerializeNulls(true);
            try { GSON.toJson(encode(java.util.Objects.requireNonNull(value, "inputObservation")), out); }
            finally { out.setSerializeNulls(previous); }
        }
        @Override public Optional<ClientObservationAnchor> read(JsonReader in) throws IOException {
            return decode(JsonParser.parseReader(in));
        }
    }
}
