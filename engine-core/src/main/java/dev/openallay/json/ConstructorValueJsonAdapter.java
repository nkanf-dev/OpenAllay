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
import java.lang.reflect.ParameterizedType;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Component accessors and canonical constructors; never final-field assignment or Unsafe. */
class ConstructorValueJsonAdapter<T> extends TypeAdapter<T> {
    static final class Fields {
        private final Set<String> write;
        private final Set<String> read;
        Fields(Set<String> write, Set<String> read) { this.write = write; this.read = read; }
        Set<String> write() { return write; }
        Set<String> read() { return read; }
    }

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
        return new Fields(java.util.Collections.unmodifiableSet(new HashSet<>(write)), java.util.Collections.unmodifiableSet(new HashSet<>(read)));
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

    private final ValueSchema<T> schema;
    private final Object[] defaults;
    private final java.util.List<Component<T>> components;
    private final Map<String, Integer> names;

    private static final class Component<T> {
        private final String name;
        private final ValueSchema.Component<T> accessor;
        private final TypeAdapter<Object> adapter;
        private final Type type;
        private final Class<?> raw;
        private final boolean write;
        private final boolean read;
        private final boolean annotated;
        Component(String name, ValueSchema.Component<T> accessor, TypeAdapter<Object> adapter,
                Type type, Class<?> raw, boolean write, boolean read, boolean annotated) {
            this.name = name; this.accessor = accessor; this.adapter = adapter;
            this.type = type; this.raw = raw; this.write = write; this.read = read; this.annotated = annotated;
        }
        String name() { return name; }
        ValueSchema.Component<T> accessor() { return accessor; }
        TypeAdapter<Object> adapter() { return adapter; }
        Type type() { return type; }
        Class<?> raw() { return raw; }
        boolean write() { return write; }
        boolean read() { return read; }
        boolean annotated() { return annotated; }
    }

    @SuppressWarnings("unchecked")
    static <T> ConstructorValueJsonAdapter<T> create(Gson gson, TypeToken<T> type, Fields fields) {
        return new ConstructorValueJsonAdapter<>(gson, type, fields, ValueSchemas.of((Class<T>) type.getRawType()));
    }

    private final Gson gson;
    protected ConstructorValueJsonAdapter(Gson gson, TypeToken<T> type, Fields fields, ValueSchema<T> schema) {
        this.gson = gson;
        Class<?> raw = type.getRawType();
        this.schema = schema;
        defaults = defaultArguments(schema.components());
        components = new java.util.ArrayList<>();
        names = new LinkedHashMap<>();
        Map<TypeVariable<?>, Type> variables = new LinkedHashMap<>();
        if (type.getType() instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) type.getType();
            TypeVariable<?>[] parameters = raw.getTypeParameters();
            Type[] arguments = parameterized.getActualTypeArguments();
            for (int i = 0; i < parameters.length; i++) variables.put(parameters[i], arguments[i]);
        }
        try {
            for (int i = 0; i < schema.components().size(); i++) {
                ValueSchema.Component<T> component = schema.components().get(i);
                Field field = component.fieldMetadata(); // Metadata only; no field access.
                Type resolved = resolve(component.genericType(), variables);
                JsonAdapter annotation = field.getAnnotation(JsonAdapter.class);
                boolean write = fields.write().contains(component.name());
                boolean read = fields.read().contains(component.name());
                TypeAdapter<Object> adapter = write || read ? componentAdapter(gson, resolved, annotation) : null;
                SerializedName named = field.getAnnotation(SerializedName.class);
                String name = named == null ? gson.fieldNamingStrategy().translateName(field) : named.value();
                components.add(new Component<>(name, component, adapter, resolved,
                        TypeToken.get(resolved).getRawType(), write, read, annotation != null));
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
    private TypeAdapter<Object> componentAdapter(Gson gson, Type type, JsonAdapter annotation)
            throws ReflectiveOperationException {
        if (annotation == null) return (TypeAdapter<Object>) gson.getAdapter(TypeToken.get(type));
        Object value = annotationAdapter(annotation.value());
        TypeAdapter<?> adapter;
        if (value instanceof TypeAdapter<?>) adapter = (TypeAdapter<?>) value;
        else if (value instanceof com.google.gson.TypeAdapterFactory) adapter = ((com.google.gson.TypeAdapterFactory) value).create(gson, TypeToken.get(type));
        else throw new JsonIOException("Record component @JsonAdapter requires a TypeAdapter or factory: " + annotation.value());
        if (adapter == null) throw new JsonIOException("Record component adapter factory returned null");
        return (TypeAdapter<Object>) (annotation.nullSafe() ? adapter.nullSafe() : adapter);
    }

    protected Object annotationAdapter(Class<?> type) throws ReflectiveOperationException {
        return type.getConstructor().newInstance();
    }

    @Override public void write(JsonWriter out, T value) throws IOException {
        out.beginObject();
        for (Component<T> component : components) {
            if (!component.write()) continue;
            Object item;
            try { item = component.accessor().read(value); }
            catch (RuntimeException failure) { throw new JsonIOException("Cannot read record component " + component.name(), failure); }
            if (item == value) continue;
            out.name(component.name());
            TypeAdapter<Object> adapter = component.adapter();
            // Preserve parameterized collection adapters. Detached polymorphic record values
            // use their actual record schema, as Gson's native runtime-type writer does.
            if (!component.annotated() && item != null && ValueSchemas.isValue(item.getClass())
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
            if (index == null || !components.get(index).read()) { in.skipValue(); continue; }
            if (!assigned.add(index)) throw new JsonParseException("Duplicate record component: " + name);
            Object value = components.get(index).adapter().read(in);
            if (value == null && components.get(index).raw().isPrimitive())
                throw new JsonParseException("Primitive record component cannot be null: " + name);
            values[index] = value;
        }
        in.endObject();
        try { return schema.construct(values); }
        catch (RuntimeException failure) {
            if (failure instanceof JsonParseException) throw (JsonParseException) failure;
            throw new JsonParseException("Invalid record " + schema.owner().getName(), failure);
        }
    }

    private static Object[] defaultArguments(java.util.List<? extends ValueSchema.Component<?>> components) {
        Object[] values = new Object[components.size()];
        for (int i = 0; i < values.length; i++) {
            Class<?> type = components.get(i).rawType();
            if (type.isPrimitive()) values[i] = Array.get(Array.newInstance(type, 1), 0);
        }
        return values;
    }

    private static Type resolve(Type type, Map<TypeVariable<?>, Type> variables) {
        if (type instanceof TypeVariable<?>) return variables.getOrDefault(type, Object.class);
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) type;
            Type[] arguments = java.util.Arrays.stream(parameterized.getActualTypeArguments())
                    .map(argument -> resolve(argument, variables)).toArray(Type[]::new);
            return TypeToken.getParameterized(parameterized.getRawType(), arguments).getType();
        }
        if (type instanceof GenericArrayType) return TypeToken.getArray(resolve(((GenericArrayType) type).getGenericComponentType(), variables)).getType();
        if (type instanceof WildcardType) return resolve(((WildcardType) type).getUpperBounds()[0], variables);
        return type;
    }
}
