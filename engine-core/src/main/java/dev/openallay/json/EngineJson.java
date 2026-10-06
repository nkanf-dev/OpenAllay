package dev.openallay.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Typed engine timestamps. The current JSON shape is {seconds, nanos}, not JDK private fields. */
public final class EngineJson {
    private EngineJson() {}

    // Registered last, so public delegate lookup reaches our private binding before user
    // factories and bypasses Gson's class excluder for ownership checks only.
    private static final TypeAdapterFactory BINDING_BOUNDARY = new TypeAdapterFactory() {
        @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) { return null; }
    };

    /** Create a bound engine Gson with the default public builder configuration. */
    public static Gson create() {
        return new Recipe(List.of()).create();
    }

    /** Create a bound engine Gson from repeatable public builder configuration.
     * The callback runs once per fresh builder, including lazy record probes and fallbacks.
     * It must apply the same configuration on every call: do not retain the builder, use
     * counters/time-dependent choices, or mutate captured configuration. Registered adapter
     * and exclusion objects may be shared, as with a Gson clone; this is not a deep copy.
     * Explicit caller adapters and options are retained. Factories wrapping Gson's reflective
     * record implementation are not supported; use an explicit record adapter instead.
     */
    public static Gson create(Consumer<GsonBuilder> configuration) {
        return new Recipe(List.of(Objects.requireNonNull(configuration, "configuration"))).create();
    }

    /** Create a separate bound owner by replaying the original recipe, then this overlay.
     * The overlay has the same repeatability and shared-object contract as create(configuration).
     * The original owner's recipe, adapter cache and builder options are not changed.
     */
    public static Gson derive(Gson bound, Consumer<GsonBuilder> configuration) {
        Recipe owner = owner(bound);
        List<Consumer<GsonBuilder>> steps = new ArrayList<>(owner.steps);
        steps.add(Objects.requireNonNull(configuration, "configuration"));
        return new Recipe(steps).create();
    }

    /** Require an already-bound engine owner, retaining its identity and caller configuration. */
    public static Gson withInstant(Gson source) {
        owner(source);
        return source;
    }

    private static Recipe owner(Gson source) {
        Objects.requireNonNull(source, "source");
        if (source.getDelegateAdapter(BINDING_BOUNDARY, TypeToken.get(Binding.class)) instanceof BindingAdapter binding) return binding.owner;
        throw new JsonIOException("Unbound Gson: use EngineJson.create(configuration) to preserve caller options and adapters");
    }

    private static final class Recipe {
        private final List<Consumer<GsonBuilder>> steps;
        private Recipe(List<Consumer<GsonBuilder>> steps) { this.steps = List.copyOf(steps); }

        private GsonBuilder builder() {
            GsonBuilder builder = new GsonBuilder();
            for (Consumer<GsonBuilder> step : steps) step.accept(builder);
            return builder;
        }

        private Gson create() {
            return builder().registerTypeAdapterFactory(new EngineJsonFactory(this))
                    .registerTypeAdapterFactory(BINDING_BOUNDARY).create();
        }
    }

    private static final class Binding {}
    private static final class BindingAdapter extends TypeAdapter<Binding> {
        private final Recipe owner;
        private BindingAdapter(Recipe owner) { this.owner = owner; }
        @Override public void write(JsonWriter out, Binding value) { throw new UnsupportedOperationException(); }
        @Override public Binding read(JsonReader in) { throw new UnsupportedOperationException(); }
    }

    private static final class EngineJsonFactory implements com.google.gson.TypeAdapterFactory {
        private final Recipe owner;
        private EngineJsonFactory(Recipe owner) { this.owner = owner; }

        @SuppressWarnings("unchecked")
        @Override public <T> TypeAdapter<T> create(Gson gson, com.google.gson.reflect.TypeToken<T> type) {
            Class<?> raw = type.getRawType();
            if (raw == Binding.class) return (TypeAdapter<T>) new BindingAdapter(owner);
            if (raw != Instant.class && !raw.isRecord()) return null;
            RecordJsonAdapter.Fields fields = RecordJsonAdapter.fields(owner.builder(), type);
            if (raw == Instant.class) {
                return fields.reflectionReached()
                        ? (TypeAdapter<T>) new InstantAdapter().nullSafe()
                        : gson.getDelegateAdapter(this, type);
            }
            if (fields.reflectionReached()) return RecordJsonAdapter.create(gson, type, fields).nullSafe();
            // Fresh unbound replay retains explicit caller adapters and excludes native fields.
            // The creator changes only native default construction; nested types use this owner.
            var creator = (com.google.gson.InstanceCreator<T>) ignored -> RecordJsonAdapter.defaults(type);
            com.google.gson.TypeAdapterFactory nested = new com.google.gson.TypeAdapterFactory() {
                @Override public <V> TypeAdapter<V> create(Gson delegate, com.google.gson.reflect.TypeToken<V> candidate) {
                    return candidate.equals(type) ? null : EngineJsonFactory.this.create(gson, candidate);
                }
            };
            return owner.builder().registerTypeAdapter(type.getType(), creator)
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
