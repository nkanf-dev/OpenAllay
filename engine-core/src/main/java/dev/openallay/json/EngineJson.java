package dev.openallay.json;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.google.gson.ReflectionAccessFilter;
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

    /** Add the engine default only when the supplied Gson has no explicit Instant adapter. */
    public static Gson withInstant(Gson source) {
        Objects.requireNonNull(source, "source");
        boolean[] reflectionReached = {false};
        Gson probe = source.newBuilder().addReflectionAccessFilter(type -> {
            if (type == Instant.class) {
                reflectionReached[0] = true;
                return ReflectionAccessFilter.FilterResult.BLOCK_ALL;
            }
            return ReflectionAccessFilter.FilterResult.INDECISIVE;
        }).create();
        try {
            probe.getAdapter(Instant.class);
            return source;
        } catch (JsonIOException missingAdapter) {
            if (!reflectionReached[0]) throw missingAdapter;
            return source.newBuilder().registerTypeAdapter(Instant.class, new InstantAdapter().nullSafe()).create();
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
