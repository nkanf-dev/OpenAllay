package dev.openallay.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSyntaxException;
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
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import dev.openallay.util.Java8Collections;

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
        return new Recipe(Java8Collections.listOf(), Java8Collections.listOf()).create();
    }

    /** Create a bound engine Gson from repeatable public builder configuration.
     * The callback runs once per fresh builder, including lazy record probes and fallbacks.
     * It must apply the same configuration on every call: do not retain the builder, use
     * counters/time-dependent choices, or mutate captured configuration. Registered adapter
     * and exclusion objects may be shared, as with a Gson clone; this is not a deep copy.
     * Explicit caller adapters and options are retained. JsonSerializer/JsonDeserializer
     * hierarchy registrations must use deriveHierarchy, not a hidden builder registration.
     * Ordinary TypeAdapter hierarchy registrations may use the builder directly.
     * Factories wrapping Gson's reflective
     * record implementation are not supported; use an explicit record adapter instead.
     */
    public static Gson create(Consumer<GsonBuilder> configuration) {
        return new Recipe(Java8Collections.listOf(Objects.requireNonNull(configuration, "configuration")), Java8Collections.listOf()).create();
    }

    /** Create a separate bound owner by replaying the original recipe, then this overlay.
     * The overlay has the same repeatability and shared-object contract as create(configuration).
     * The original owner's recipe, adapter cache and builder options are not changed.
     */
    public static Gson derive(Gson bound, Consumer<GsonBuilder> configuration) {
        Recipe owner = owner(bound);
        List<Consumer<GsonBuilder>> steps = new ArrayList<>(owner.steps);
        steps.add(Objects.requireNonNull(configuration, "configuration"));
        return new Recipe(steps, owner.hierarchy).create();
    }

    /** Append an owned tree hierarchy adapter, retaining exact ordinary adapter precedence.
     * Only JsonSerializer and/or JsonDeserializer objects are accepted. TypeAdapter hierarchy
     * objects, including mixed tree/stream objects, must use ordinary builder registration.
     * Adapter objects are shared; the repeatability and state contract of create still applies.
     */
    public static Gson deriveHierarchy(Gson bound, Class<?> baseType, Object adapter) {
        Recipe owner = owner(bound);
        Objects.requireNonNull(baseType, "baseType");
        Objects.requireNonNull(adapter, "adapter");
        if (adapter instanceof TypeAdapter<?> || !(adapter instanceof JsonSerializer<?> || adapter instanceof JsonDeserializer<?>)) {
            throw new IllegalArgumentException("deriveHierarchy requires JsonSerializer/JsonDeserializer; use ordinary builder registration for TypeAdapter hierarchy objects");
        }
        List<Hierarchy> entries = new ArrayList<>(owner.hierarchy);
        entries.add(new Hierarchy(baseType, adapter));
        return new Recipe(owner.steps, entries).create();
    }

    /** Require an already-bound engine owner, retaining its identity and caller configuration. */
    public static Gson withInstant(Gson source) {
        owner(source);
        return source;
    }

    private static Recipe owner(Gson source) {
        Objects.requireNonNull(source, "source");
        TypeAdapter<Binding> binding = source.getDelegateAdapter(BINDING_BOUNDARY, TypeToken.get(Binding.class));
        if (binding instanceof BindingAdapter) return ((BindingAdapter) binding).owner;
        throw new JsonIOException("Unbound Gson: use EngineJson.create(configuration) to preserve caller options and adapters");
    }

    private static final class Hierarchy {
        private final Class<?> baseType;
        private final Object adapter;
        private Hierarchy(Class<?> baseType, Object adapter) {
            this.baseType = baseType;
            this.adapter = adapter;
        }
        private Class<?> baseType() { return baseType; }
        private Object adapter() { return adapter; }
        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Hierarchy)) return false;
            Hierarchy other = (Hierarchy) value;
            return Objects.equals(baseType, other.baseType) && Objects.equals(adapter, other.adapter);
        }
        @Override public int hashCode() {
            return 31 * Objects.hashCode(baseType) + Objects.hashCode(adapter);
        }
        @Override public String toString() {
            return "Hierarchy[baseType=" + baseType + ", adapter=" + adapter + "]";
        }
    }

    private static final class Recipe {
        private final List<Consumer<GsonBuilder>> steps;
        private final List<Hierarchy> hierarchy;
        private Recipe(List<Consumer<GsonBuilder>> steps, List<Hierarchy> hierarchy) {
            this.steps = Java8Collections.listCopyOf(steps);
            this.hierarchy = Java8Collections.listCopyOf(hierarchy);
        }

        private GsonBuilder builder(TypeAdapterFactory lower) {
            GsonBuilder builder = new GsonBuilder();
            if (lower != null) builder.registerTypeAdapterFactory(lower);
            for (Consumer<GsonBuilder> step : steps) step.accept(builder);
            return builder;
        }

        private Gson create() {
            return builder(new HierarchyFactory(this))
                    .registerTypeAdapterFactory(new EngineJsonFactory(this))
                    .registerTypeAdapterFactory(BINDING_BOUNDARY).create();
        }

        private boolean nativeReached(Gson bound, TypeToken<?> target) {
            boolean[] reached = {false};
            TypeAdapterFactory boundary = new TypeAdapterFactory() {
                @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                    if (type.equals(target) && type.getRawType().getAnnotation(com.google.gson.annotations.JsonAdapter.class) == null) reached[0] = true;
                    return null;
                }
            };
            // No owned hierarchy dispatcher: selection tests only the ordinary caller lane.
            // Nested requests use the bound engine so custom factories retain typed components.
            TypeAdapterFactory nested = new TypeAdapterFactory() {
                @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                    return type.equals(target) ? null : bound.getAdapter(type);
                }
            };
            TypeAdapterFactory selection = new TypeAdapterFactory() {
                @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) { return null; }
            };
            // Skip only the probe's class excluder; the actual bound adapter retains it.
            // This also lets a direction-specific excluded wrapper keep its allowed strict side.
            builder(boundary).registerTypeAdapterFactory(nested).registerTypeAdapterFactory(selection)
                    .addSerializationExclusionStrategy(RecordJsonAdapter.sentinel(target.getRawType(), new java.util.HashSet<>()))
                    .addDeserializationExclusionStrategy(RecordJsonAdapter.sentinel(target.getRawType(), new java.util.HashSet<>()))
                    .create().getDelegateAdapter(selection, target);
            return reached[0];
        }

        private <T> TypeAdapter<T> defaultAdapter(Gson bound, TypeToken<T> type) {
            if (type.getRawType() == Instant.class) {
                @SuppressWarnings("unchecked") TypeAdapter<T> instant = (TypeAdapter<T>) new InstantAdapter().nullSafe();
                return instant;
            }
            // Force native inclusion metadata only. Actual DTO resolution keeps its excluder.
            TypeAdapterFactory boundary = new TypeAdapterFactory() {
                @Override public <V> TypeAdapter<V> create(Gson gson, TypeToken<V> candidate) { return null; }
            };
            TypeAdapterFactory metadata = new TypeAdapterFactory() {
                @Override public <V> TypeAdapter<V> create(Gson gson, TypeToken<V> candidate) {
                    return candidate.equals(type) ? gson.getDelegateAdapter(boundary, candidate) : null;
                }
            };
            RecordJsonAdapter.Fields fields = RecordJsonAdapter.fields(
                    builder(boundary).registerTypeAdapterFactory(metadata), type);
            return dev.openallay.value.ValueSchemas.supports(type.getRawType())
                    ? ConstructorValueJsonAdapter.create(bound, type, fields).nullSafe()
                    : RecordJsonAdapter.create(bound, type, fields).nullSafe();
        }

        private <T> TypeAdapter<T> hierarchyAdapter(Gson bound, TypeToken<T> type, int start,
                java.util.function.Supplier<TypeAdapter<T>> nativeFallback) {
            for (int i = start; i >= 0; i--) {
                Hierarchy entry = hierarchy.get(i);
                if (entry.baseType().isAssignableFrom(type.getRawType())) {
                    int next = i - 1;
                    return new HierarchyAdapter<>(bound, type, entry.adapter(),
                            () -> hierarchyAdapter(bound, type, next, nativeFallback));
                }
            }
            return nativeFallback.get();
        }
    }

    private static final class HierarchyFactory implements TypeAdapterFactory {
        private final Recipe owner;
        private HierarchyFactory(Recipe owner) { this.owner = owner; }
        @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            boolean matched = owner.hierarchy.stream().anyMatch(entry -> entry.baseType().isAssignableFrom(type.getRawType()));
            return matched ? owner.hierarchyAdapter(gson, type, owner.hierarchy.size() - 1,
                    () -> gson.getDelegateAdapter(this, type)) : null;
        }
    }

    private static final class HierarchyAdapter<T> extends TypeAdapter<T> {
        private final Gson bound;
        private final TypeToken<T> type;
        private final Object adapter;
        private final java.util.function.Supplier<TypeAdapter<T>> fallback;
        private volatile TypeAdapter<T> delegate;
        private HierarchyAdapter(Gson bound, TypeToken<T> type, Object adapter,
                java.util.function.Supplier<TypeAdapter<T>> fallback) {
            this.bound = bound;
            this.type = type;
            this.adapter = adapter;
            this.fallback = fallback;
        }
        private TypeAdapter<T> delegate() {
            // Resolving lazily avoids recursive record component construction at factory time.
            if (delegate == null) delegate = fallback.get();
            return delegate;
        }
        private JsonSerializationContext serializationContext() {
            return new JsonSerializationContext() {
                @Override public JsonElement serialize(Object value) { return bound.toJsonTree(value); }
                @Override public JsonElement serialize(Object value, Type declared) { return bound.toJsonTree(value, declared); }
            };
        }
        private JsonDeserializationContext deserializationContext() {
            return new JsonDeserializationContext() {
                @Override public <V> V deserialize(JsonElement value, Type declared) { return bound.fromJson(value, declared); }
            };
        }
        @SuppressWarnings("unchecked")
        @Override public void write(JsonWriter out, T value) throws IOException {
            if (!(adapter instanceof JsonSerializer<?>)) { delegate().write(out, value); return; }
            if (value == null) { out.nullValue(); return; }
            JsonElement tree = ((JsonSerializer<T>) adapter).serialize(value, type.getType(), serializationContext());
            bound.getAdapter(JsonElement.class).write(out, tree);
        }
        @SuppressWarnings("unchecked")
        @Override public T read(JsonReader in) throws IOException {
            if (!(adapter instanceof JsonDeserializer<?>)) return delegate().read(in);
            if (in.peek() == JsonToken.NULL) { in.nextNull(); return null; }
            T value = ((JsonDeserializer<T>) adapter).deserialize(JsonTrees.parse(in), type.getType(), deserializationContext());
            if (value != null && !type.getRawType().isInstance(value))
                throw new JsonSyntaxException("Hierarchy adapter returned incompatible value for " + type.getRawType().getName());
            return value;
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
            if (raw != Instant.class && !dev.openallay.value.ValueSchemas.isValue(raw)) return null;
            if (!owner.nativeReached(gson, type)) return gson.getDelegateAdapter(this, type);
            return owner.hierarchyAdapter(gson, type, owner.hierarchy.size() - 1,
                    () -> owner.defaultAdapter(gson, type));
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
                    case "seconds":
                        if (seconds != null) throw invalid("Duplicate Instant seconds");
                        seconds = integer(in);
                        break;
                    case "nanos":
                        if (nanos != null) throw invalid("Duplicate Instant nanos");
                        nanos = integer(in);
                        break;
                    default: throw invalid("Unknown Instant field");
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
