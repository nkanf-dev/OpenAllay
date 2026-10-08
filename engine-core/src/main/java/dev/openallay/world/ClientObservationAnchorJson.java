package dev.openallay.world;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.openallay.json.JsonTrees;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.openallay.json.EngineJson;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Current detached input-reference shape, with explicit typed timestamp serialization. */
public final class ClientObservationAnchorJson {
    private static final Gson GSON = EngineJson.derive(EngineJson.create(), com.google.gson.GsonBuilder::serializeNulls);

    private ClientObservationAnchorJson() {}

    /** Nullable image at this one wire seam avoids reflecting into Optional internals. */
    @dev.openallay.value.ValueType(Shape.ValueSchemaProvider.class)
private static final class Shape {
    private final UUID associationId;
    private final Instant capturedAt;
    private final WorldFocusObservation focus;
    private final WorldViewCapture image;
    private Shape(UUID associationId, Instant capturedAt, WorldFocusObservation focus, WorldViewCapture image) {
        this.associationId = associationId;
        this.capturedAt = capturedAt;
        this.focus = focus;
        this.image = image;
    }
    public UUID associationId() { return associationId; }
    public Instant capturedAt() { return capturedAt; }
    public WorldFocusObservation focus() { return focus; }
    public WorldViewCapture image() { return image; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Shape)) return false;
        Shape that = (Shape) other;
        return java.util.Objects.equals(associationId, that.associationId) && java.util.Objects.equals(capturedAt, that.capturedAt) && java.util.Objects.equals(focus, that.focus) && java.util.Objects.equals(image, that.image);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(associationId);
        hash = 31 * hash + java.util.Objects.hashCode(capturedAt);
        hash = 31 * hash + java.util.Objects.hashCode(focus);
        hash = 31 * hash + java.util.Objects.hashCode(image);
        return hash;
    }
    @Override public String toString() { return "Shape[associationId=" + associationId + ", capturedAt=" + capturedAt + ", focus=" + focus + ", image=" + image + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Shape> schema() {
            return new dev.openallay.value.ValueSchema<>(Shape.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Shape>>asList(new dev.openallay.value.ValueSchema.Component<>(Shape.class, "associationId", Shape::associationId), new dev.openallay.value.ValueSchema.Component<>(Shape.class, "capturedAt", Shape::capturedAt), new dev.openallay.value.ValueSchema.Component<>(Shape.class, "focus", Shape::focus), new dev.openallay.value.ValueSchema.Component<>(Shape.class, "image", Shape::image)), arguments -> new Shape((UUID) arguments[0], (Instant) arguments[1], (WorldFocusObservation) arguments[2], (WorldViewCapture) arguments[3]));
        }
    }
}

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
            if (!supplied.isJsonObject() || !JsonTrees.keys(supplied.getAsJsonObject()).equals(JsonTrees.keys(typed.getAsJsonObject()))) {
                throw new IllegalArgumentException("Input metadata fields do not match the current shape");
            }
            for (java.util.Map.Entry<String, JsonElement> entry : typed.getAsJsonObject().entrySet()) {
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
            com.google.gson.JsonPrimitive expected = typed.getAsJsonPrimitive();
            com.google.gson.JsonPrimitive actual = supplied.getAsJsonPrimitive();
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
            return decode(JsonTrees.parse(in));
        }
    }
}
