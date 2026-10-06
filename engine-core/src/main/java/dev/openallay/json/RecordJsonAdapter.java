package dev.openallay.json;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Component accessors and canonical constructors; never final-field assignment or Unsafe. */
final class RecordJsonAdapter<T> extends TypeAdapter<T> {
    record Fields(Set<String> write, Set<String> read) {}

    /** Existing exclusions run before these sentinels. Every reached field is suppressed,
     * so a probe never opens private timestamp or record fields. The owner supplies a fresh
     * unbound builder for every metadata probe; no adapter cache or sentinel state is reused.
     * Public delegate lookup skips only the probe class wrapper; field/type exclusions still
     * run natively. The actual bound Gson retains its class-level exclusion wrapper.
     */
    static Fields fields(GsonBuilder unbound, TypeToken<?> type) {
        Set<String> write = new HashSet<>();
        Set<String> read = new HashSet<>();
        com.google.gson.TypeAdapterFactory selection = new com.google.gson.TypeAdapterFactory() {
            @Override public <V> TypeAdapter<V> create(Gson gson, TypeToken<V> candidate) { return null; }
        };
        unbound.registerTypeAdapterFactory(selection).addSerializationExclusionStrategy(sentinel(type.getRawType(), write))
                .addDeserializationExclusionStrategy(sentinel(type.getRawType(), read))
                .create().getDelegateAdapter(selection, type);
        return new Fields(Set.copyOf(write), Set.copyOf(read));
    }

    static ExclusionStrategy sentinel(Class<?> owner, Set<String> names) {
        return new ExclusionStrategy() {
            @Override public boolean shouldSkipClass(Class<?> type) { return false; }
            @Override public boolean shouldSkipField(FieldAttributes field) {
                if (field.getDeclaringClass() != owner) return false;
                names.add(field.getName());
                return true;
            }
        };
    }

    private final Constructor<?> constructor;
    private final Object[] defaults;
    private final Component[] components;
    private final Map<String, Integer> names;

    private record Component(String name, Method accessor, TypeAdapter<Object> adapter,
            Type type, Class<?> raw, boolean write, boolean read, boolean annotated) {}

    static <T> RecordJsonAdapter<T> create(Gson gson, TypeToken<T> type, Fields fields) {
        return new RecordJsonAdapter<>(gson, type, fields);
    }

    private final Gson gson;
    private RecordJsonAdapter(Gson gson, TypeToken<T> type, Fields fields) {
        this.gson = gson;
        Class<?> raw = type.getRawType();
        RecordComponent[] schema = raw.getRecordComponents();
        constructor = constructor(raw, schema);
        defaults = defaultArguments(schema);
        components = new Component[schema.length];
        names = new LinkedHashMap<>();
        Map<TypeVariable<?>, Type> variables = new LinkedHashMap<>();
        if (type.getType() instanceof ParameterizedType parameterized) {
            TypeVariable<?>[] parameters = raw.getTypeParameters();
            Type[] arguments = parameterized.getActualTypeArguments();
            for (int i = 0; i < parameters.length; i++) variables.put(parameters[i], arguments[i]);
        }
        try {
            for (int i = 0; i < schema.length; i++) {
                RecordComponent component = schema[i];
                Field field = raw.getDeclaredField(component.getName()); // Metadata only; no field access.
                Method accessor = component.getAccessor();
                if (!accessor.trySetAccessible()) throw new JsonIOException("Record accessor is inaccessible: " + accessor);
                Type resolved = resolve(component.getGenericType(), variables);
                JsonAdapter annotation = field.getAnnotation(JsonAdapter.class);
                boolean write = fields.write().contains(component.getName());
                boolean read = fields.read().contains(component.getName());
                TypeAdapter<Object> adapter = write || read ? componentAdapter(gson, resolved, annotation) : null;
                SerializedName named = field.getAnnotation(SerializedName.class);
                String name = named == null ? gson.fieldNamingStrategy().translateName(field) : named.value();
                components[i] = new Component(name, accessor, adapter, resolved,
                        TypeToken.get(resolved).getRawType(), write, read, annotation != null);
                if (write || read) {
                    bind(name, i);
                    if (named != null) for (String alternate : named.alternate()) bind(alternate, i);
                }
            }
        } catch (ReflectiveOperationException failure) {
            throw new JsonIOException("Cannot bind record " + raw.getName(), failure);
        }
    }

    private void bind(String name, int index) {
        if (names.putIfAbsent(name, index) != null) throw new JsonIOException("Duplicate record JSON name: " + name);
    }

    @SuppressWarnings("unchecked")
    private static TypeAdapter<Object> componentAdapter(Gson gson, Type type, JsonAdapter annotation)
            throws ReflectiveOperationException {
        if (annotation == null) return (TypeAdapter<Object>) gson.getAdapter(TypeToken.get(type));
        Constructor<?> constructor = annotation.value().getDeclaredConstructor();
        if (!constructor.trySetAccessible()) throw new JsonIOException("JSON adapter constructor is inaccessible");
        Object value = constructor.newInstance();
        TypeAdapter<?> adapter;
        if (value instanceof TypeAdapter<?> typed) adapter = typed;
        else if (value instanceof com.google.gson.TypeAdapterFactory factory) adapter = factory.create(gson, TypeToken.get(type));
        else throw new JsonIOException("Record component @JsonAdapter requires a TypeAdapter or factory: " + annotation.value());
        if (adapter == null) throw new JsonIOException("Record component adapter factory returned null");
        return (TypeAdapter<Object>) (annotation.nullSafe() ? adapter.nullSafe() : adapter);
    }

    @Override public void write(JsonWriter out, T value) throws IOException {
        out.beginObject();
        for (Component component : components) {
            if (!component.write()) continue;
            Object item;
            try { item = component.accessor().invoke(value); }
            catch (ReflectiveOperationException failure) { throw new JsonIOException("Cannot read record component " + component.name(), cause(failure)); }
            if (item == value) continue;
            out.name(component.name());
            TypeAdapter<Object> adapter = component.adapter();
            // Preserve parameterized collection adapters. Detached polymorphic record values
            // use their actual record schema, as Gson's native runtime-type writer does.
            if (!component.annotated() && item != null && item.getClass().isRecord()
                    && component.raw() != item.getClass()) {
                @SuppressWarnings("unchecked") TypeAdapter<Object> runtime =
                        (TypeAdapter<Object>) gson.getAdapter(item.getClass());
                adapter = runtime;
            }
            adapter.write(out, item);
        }
        out.endObject();
    }

    @Override public T read(JsonReader in) throws IOException {
        if (in.peek() != JsonToken.BEGIN_OBJECT) throw new JsonParseException("Record must be an object");
        Object[] values = defaults.clone();
        Set<String> seen = new HashSet<>();
        Set<Integer> assigned = new HashSet<>();
        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            if (!seen.add(name)) throw new JsonParseException("Duplicate record field: " + name);
            Integer index = names.get(name);
            if (index == null || !components[index].read()) { in.skipValue(); continue; }
            if (!assigned.add(index)) throw new JsonParseException("Duplicate record component: " + name);
            Object value = components[index].adapter().read(in);
            if (value == null && components[index].raw().isPrimitive())
                throw new JsonParseException("Primitive record component cannot be null: " + name);
            values[index] = value;
        }
        in.endObject();
        return construct(constructor, values);
    }

    private static Constructor<?> constructor(Class<?> raw, RecordComponent[] components) {
        try {
            Class<?>[] parameters = java.util.Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
            Constructor<?> constructor = raw.getDeclaredConstructor(parameters);
            if (!constructor.trySetAccessible()) throw new JsonIOException("Record constructor is inaccessible: " + raw.getName());
            return constructor;
        } catch (ReflectiveOperationException failure) {
            throw new JsonIOException("Cannot bind canonical record constructor: " + raw.getName(), failure);
        }
    }

    private static Object[] defaultArguments(RecordComponent[] components) {
        Object[] values = new Object[components.length];
        for (int i = 0; i < values.length; i++) {
            Class<?> type = components[i].getType();
            if (type.isPrimitive()) values[i] = Array.get(Array.newInstance(type, 1), 0);
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static <T> T construct(Constructor<?> constructor, Object[] values) {
        try { return (T) constructor.newInstance(values); }
        catch (InvocationTargetException failure) { throw new JsonParseException("Invalid record " + constructor.getDeclaringClass().getName(), cause(failure)); }
        catch (ReflectiveOperationException failure) { throw new JsonIOException("Cannot construct record " + constructor.getDeclaringClass().getName(), failure); }
    }

    private static Throwable cause(ReflectiveOperationException failure) {
        return failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure;
    }

    private static Type resolve(Type type, Map<TypeVariable<?>, Type> variables) {
        if (type instanceof TypeVariable<?> variable) return variables.getOrDefault(variable, Object.class);
        if (type instanceof ParameterizedType parameterized) {
            Type[] arguments = java.util.Arrays.stream(parameterized.getActualTypeArguments())
                    .map(argument -> resolve(argument, variables)).toArray(Type[]::new);
            return TypeToken.getParameterized(parameterized.getRawType(), arguments).getType();
        }
        if (type instanceof GenericArrayType array) return TypeToken.getArray(resolve(array.getGenericComponentType(), variables)).getType();
        if (type instanceof WildcardType wildcard) return resolve(wildcard.getUpperBounds()[0], variables);
        return type;
    }
}
