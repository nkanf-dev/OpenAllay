package dev.openallay.json;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.openallay.agent.tool.ToolDescription;
import dev.openallay.agent.tool.ToolOptional;
import dev.openallay.tool.query.QueryOperation;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import dev.openallay.value.ValueType;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Same behavior vectors run through the actual EngineJson owner on the modern VM. */
public final class ValueSchemaJava8Fixture {
    private ValueSchemaJava8Fixture() {}
    private interface Checked { void run() throws Exception; }

    public static void main(String[] arguments) throws Exception {
        equal("1.8", System.getProperty("java.specification.version"));
        for (Class<?> owner : Arrays.<Class<?>>asList(ValueSchema.class, ValueType.class, ValueSchemas.class,
                ConstructorValueJsonAdapter.class, QueryOperation.class, ValueSchemaJava8Fixture.class)) classMajor(owner);
        run(ValueSchemaJava8Fixture::standaloneGson);
        System.out.println("PASS canonical explicit value schema genuine Java8 " + System.getProperty("java.version"));
    }

    private static Gson standaloneGson(Consumer<GsonBuilder> configuration) {
        TypeAdapterFactory factory = new TypeAdapterFactory() {
            @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                if (!ValueSchemas.supports(type.getRawType())) return null;
                final boolean[] reached = {false};
                TypeAdapterFactory boundary = new TypeAdapterFactory() {
                    @Override public <V> TypeAdapter<V> create(Gson owner, TypeToken<V> candidate) {
                        if (candidate.equals(type)) reached[0] = true;
                        return null;
                    }
                };
                GsonBuilder selection = new GsonBuilder().registerTypeAdapterFactory(boundary);
                configuration.accept(selection);
                selection.addSerializationExclusionStrategy(ConstructorValueJsonAdapter.sentinel(type.getRawType(), new java.util.HashSet<String>()))
                        .addDeserializationExclusionStrategy(ConstructorValueJsonAdapter.sentinel(type.getRawType(), new java.util.HashSet<String>()))
                        .create().getAdapter(type);
                if (!reached[0]) return gson.getDelegateAdapter(this, type);
                GsonBuilder metadata = new GsonBuilder();
                configuration.accept(metadata);
                return ConstructorValueJsonAdapter.create(gson, type,
                        ConstructorValueJsonAdapter.fields(metadata, type)).nullSafe();
            }
        };
        GsonBuilder builder = new GsonBuilder();
        configuration.accept(builder);
        return builder.registerTypeAdapterFactory(factory).create();
    }

    public static void run(Function<Consumer<GsonBuilder>, Gson> create) throws Exception {
        Gson gson = create.apply(builder -> {});
        ValueSchema<QueryOperation> schema = ValueSchemas.of(QueryOperation.class);
        equal(Arrays.asList("op", "field", "operator", "value", "fields", "direction", "aggregate", "groupBy", "count"), names(schema));
        check(schema.components().get(4).genericType() instanceof ParameterizedType, "generic list metadata");
        ParameterizedType list = (ParameterizedType) schema.components().get(4).genericType();
        equal(List.class, list.getRawType());
        equal(String.class, list.getActualTypeArguments()[0]);
        equal("Fields retained by SELECT", schema.components().get(4).annotation(ToolDescription.class).value());
        check(schema.components().get(4).annotation(ToolOptional.class) != null, "optional metadata");
        check(schema.components().get(0).annotation(ToolOptional.class) == null, "required metadata");
        List<String> source = new ArrayList<>(Arrays.asList("a", "b"));
        QueryOperation operation = new QueryOperation(QueryOperation.Op.SELECT, null, null, null,
                source, null, null, null, null);
        source.add("later");
        equal(Arrays.asList("a", "b"), operation.fields());
        fails(UnsupportedOperationException.class, () -> operation.fields().add("later"));
        fails(NullPointerException.class, () -> new QueryOperation(null, null, null, null,
                Arrays.asList("a", null), null, null, null, null));
        String expected = "{\"op\":\"SELECT\",\"fields\":[\"a\",\"b\"]}";
        equal(expected, gson.toJson(operation));
        QueryOperation decoded = gson.fromJson(expected, QueryOperation.class);
        equal(operation, decoded);
        equal(operation.hashCode(), decoded.hashCode());
        int hash = 0;
        for (Object item : new Object[] { operation.op(), operation.field(), operation.operator(), operation.value(),
                operation.fields(), operation.direction(), operation.aggregate(), operation.groupBy(), operation.count() }) {
            hash = 31 * hash + java.util.Objects.hashCode(item);
        }
        equal(hash, operation.hashCode());
        equal("QueryOperation[op=SELECT, field=null, operator=null, value=null, fields=[a, b], direction=null, aggregate=null, groupBy=null, count=null]", operation.toString());
        equal(null, gson.fromJson("{}", QueryOperation.class).fields());
        fails(JsonParseException.class, () -> gson.fromJson("{\"fields\":[null]}", QueryOperation.class));
        fails(JsonParseException.class, () -> gson.fromJson("{\"op\":\"SELECT\",\"op\":\"TAKE\"}", QueryOperation.class));
        fails(JsonParseException.class, () -> gson.fromJson("[]", QueryOperation.class));
        QueryOperation constructed = schema.construct(new Object[] {QueryOperation.Op.SELECT, null, null, null,
                Arrays.asList("a", "b"), null, null, null, null});
        equal(operation, constructed);
        fails(IllegalArgumentException.class, () -> schema.construct(new Object[0]));
        fails(UnsupportedOperationException.class, () -> schema.components().clear());
        Gson naming = create.apply(builder -> builder.setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE));
        equal("{\"Op\":\"SELECT\",\"Fields\":[\"a\",\"b\"]}", naming.toJson(operation));
        equal(operation, naming.fromJson("{\"Op\":\"SELECT\",\"Fields\":[\"a\",\"b\"]}", QueryOperation.class));
        Gson excluded = create.apply(builder -> builder.addSerializationExclusionStrategy(new ExclusionStrategy() {
            @Override public boolean shouldSkipClass(Class<?> type) { return false; }
            @Override public boolean shouldSkipField(FieldAttributes field) { return field.getName().equals("fields"); }
        }));
        equal("{\"op\":\"SELECT\"}", excluded.toJson(operation));
        Gson custom = create.apply(builder -> builder.registerTypeAdapter(QueryOperation.class, new QueryAdapter()));
        equal("\"custom\"", custom.toJson(operation));
        equal(QueryOperation.Op.TAKE, custom.fromJson("\"custom\"", QueryOperation.class).op());
        Named<String> value = new Named<>("literal", Arrays.asList("x", "y"), 2);
        java.lang.reflect.Type namedType = new TypeToken<Named<String>>() {}.getType();
        equal("{\"alias\":\"wrapped:literal\",\"items\":[\"x\",\"y\"],\"count\":2}", gson.toJson(value, namedType));
        Named<String> restored = gson.fromJson("{\"oldAlias\":\"wrapped:literal\",\"items\":[\"x\",\"y\"],\"count\":2}", namedType);
        equal("literal", restored.label());
        equal(Arrays.asList("x", "y"), restored.items());
        equal(2, restored.count());
        fails(JsonParseException.class, () -> gson.fromJson("{\"alias\":\"wrapped:a\",\"oldAlias\":\"wrapped:b\"}", namedType));
        fails(JsonParseException.class, () -> gson.fromJson("{\"count\":null}", namedType));
        equal(null, gson.fromJson("null", QueryOperation.class));
        fails(IllegalArgumentException.class, () -> ValueSchemas.of(String.class));
    }

    @ValueType(Named.Schema.class)
    public static final class Named<T> {
        @SerializedName(value = "alias", alternate = {"oldAlias"}) @JsonAdapter(Prefix.class)
        private final String label;
        private final List<T> items;
        private final int count;
        public Named(String label, List<T> items, int count) { this.label = label; this.items = items; this.count = count; }
        public String label() { return label; }
        public List<T> items() { return items; }
        public int count() { return count; }
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<Named> schema() {
                return new ValueSchema<>(Named.class, Arrays.asList(
                        new ValueSchema.Component<>(Named.class, "label", Named::label),
                        new ValueSchema.Component<>(Named.class, "items", Named::items),
                        new ValueSchema.Component<>(Named.class, "count", Named::count)),
                        values -> new Named<>((String) values[0], (List<?>) values[1], (Integer) values[2]));
            }
        }
    }
    public static final class Prefix extends TypeAdapter<String> {
        public Prefix() {}
        @Override public void write(JsonWriter out, String value) throws IOException { out.value("wrapped:" + value); }
        @Override public String read(JsonReader in) throws IOException { return in.nextString().substring("wrapped:".length()); }
    }
    public static final class QueryAdapter extends TypeAdapter<QueryOperation> {
        @Override public void write(JsonWriter out, QueryOperation value) throws IOException { out.value("custom"); }
        @Override public QueryOperation read(JsonReader in) throws IOException {
            in.nextString(); return new QueryOperation(QueryOperation.Op.TAKE, null, null, null, null, null, null, null, null);
        }
    }
    private static List<String> names(ValueSchema<?> schema) {
        List<String> names = new ArrayList<>();
        for (ValueSchema.Component<?> component : schema.components()) names.add(component.name());
        return names;
    }
    private static void classMajor(Class<?> type) throws IOException {
        try (InputStream stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            byte[] header = new byte[8]; int position = 0;
            while (position < header.length) { int count = stream.read(header, position, header.length - position); check(count > 0, "header"); position += count; }
            equal(52, ((header[6] & 255) << 8) | (header[7] & 255));
        }
    }
    private static void fails(Class<? extends Throwable> expected, Checked operation) throws Exception {
        try { operation.run(); } catch (Throwable failure) {
            if (expected.isInstance(failure)) return;
            throw new AssertionError("Expected " + expected.getName() + ", got " + failure, failure);
        }
        throw new AssertionError("Expected " + expected.getName());
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) { check(java.util.Objects.equals(expected, actual), "Expected " + expected + ", got " + actual); }
}
