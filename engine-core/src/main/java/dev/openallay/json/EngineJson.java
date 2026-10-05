package dev.openallay.json;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;

/** Typed engine timestamps. The current JSON shape is {seconds, nanos}, not JDK private fields. */
public final class EngineJson {
    private EngineJson() {}

    /** Bind typed timestamps and canonical record construction on the shared host Gson ABI.
     * Caller adapters and builder options are retained. Factories wrapping Gson's reflective
     * record implementation are not supported; use an explicit record adapter instead.
     */
    public static Gson withInstant(Gson source) {
        Objects.requireNonNull(source, "source");
        if (source.getAdapter(Binding.class) instanceof BindingAdapter) return source;
        return source.newBuilder().registerTypeAdapterFactory(new EngineJsonFactory(source)).create();
    }

    private static final class Binding {}
    private static final class BindingAdapter extends TypeAdapter<Binding> {
        @Override public void write(JsonWriter out, Binding value) { throw new UnsupportedOperationException(); }
        @Override public Binding read(JsonReader in) { throw new UnsupportedOperationException(); }
    }

    private static final class EngineJsonFactory implements com.google.gson.TypeAdapterFactory {
        private final Gson source;
        private EngineJsonFactory(Gson source) { this.source = source; }

        @SuppressWarnings("unchecked")
        @Override public <T> TypeAdapter<T> create(Gson gson, com.google.gson.reflect.TypeToken<T> type) {
            Class<?> raw = type.getRawType();
            if (raw == Binding.class) return (TypeAdapter<T>) new BindingAdapter();
            if (raw != Instant.class && !raw.isRecord()) return null;
            RecordJsonAdapter.Fields fields = RecordJsonAdapter.fields(source, type);
            if (raw == Instant.class) {
                return fields.reflectionReached()
                        ? (TypeAdapter<T>) new InstantAdapter().nullSafe()
                        : gson.getDelegateAdapter(this, type);
            }
            if (fields.reflectionReached()) return RecordJsonAdapter.create(gson, type, fields).nullSafe();
            // No included record fields: either an explicit caller adapter or an empty/excluded
            // native shape. InstanceCreator changes only the native construction path; explicit
            // adapters retain precedence. Nested types still use this configured Gson.
            var creator = (com.google.gson.InstanceCreator<T>) ignored -> RecordJsonAdapter.defaults(type);
            com.google.gson.TypeAdapterFactory nested = new com.google.gson.TypeAdapterFactory() {
                @Override public <V> TypeAdapter<V> create(Gson delegate, com.google.gson.reflect.TypeToken<V> candidate) {
                    return candidate.equals(type) ? null : EngineJsonFactory.this.create(gson, candidate);
                }
            };
            return source.newBuilder().registerTypeAdapter(type.getType(), creator)
                    .registerTypeAdapterFactory(nested).create().getAdapter(type);
        }
    }

    private static final class InstantAdapter extends TypeAdapter<Instant> {
        @Override public void write(JsonWriter out, Instant value) throws IOException {
            out.beginObject();
            out.name("seconds").value(value.getEpochSecond());
            out.name("nanos").value(value.getNano());
            out.endObject();
        }

        @Override public Instant read(JsonReader in) throws IOException {
            if (in.peek() != JsonToken.BEGIN_OBJECT) throw invalid("Instant must be an object");
            Long seconds = null;
            Long nanos = null;
            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "seconds" -> {
                        if (seconds != null) throw invalid("Duplicate Instant seconds");
                        seconds = integer(in);
                    }
                    case "nanos" -> {
                        if (nanos != null) throw invalid("Duplicate Instant nanos");
                        nanos = integer(in);
                    }
                    default -> throw invalid("Unknown Instant field");
                }
            }
            in.endObject();
            if (seconds == null || nanos == null) throw invalid("Instant requires seconds and nanos");
            if (nanos < 0 || nanos > 999_999_999) throw invalid("Instant nanos are out of range");
            try {
                return Instant.ofEpochSecond(seconds, nanos);
            } catch (DateTimeException invalidTime) {
                throw new JsonParseException("Instant seconds are out of range", invalidTime);
            }
        }

        private static long integer(JsonReader in) throws IOException {
            if (in.peek() != JsonToken.NUMBER) throw invalid("Instant fields must be integer numbers");
            String number = in.nextString();
            if (!number.matches("-?(0|[1-9][0-9]*)")) throw invalid("Instant fields must be integers");
            try {
                return Long.parseLong(number);
            } catch (NumberFormatException overflow) {
                throw new JsonParseException("Instant integer is out of range", overflow);
            }
        }

        private static JsonParseException invalid(String message) { return new JsonParseException(message); }
    }
}
