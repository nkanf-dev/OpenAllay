package dev.openallay.tool;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.openallay.agent.tool.ToolAtLeastOne;
import dev.openallay.agent.tool.ToolDescription;
import dev.openallay.agent.tool.ToolOptional;
import dev.openallay.agent.tool.ToolPattern;
import dev.openallay.agent.tool.ToolSchemaGenerator;
import dev.openallay.json.EngineJson;
import dev.openallay.json.JsonTrees;
import dev.openallay.tool.query.QueryOperation;
import dev.openallay.trace.replay.ToolArgumentCodec;
import dev.openallay.util.Java8Strings;
import dev.openallay.value.RecordMetadata;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import dev.openallay.value.ValueType;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Actual Java8 schema/codec dependency frontier, including the bound production EngineJson. */
public final class ToolSchemaJava8Fixture {
    private ToolSchemaJava8Fixture() {}
    private interface Checked { void run() throws Exception; }
    public static void main(String[] arguments) throws Exception {
        equal("1.8", System.getProperty("java.specification.version"));
        for (Class<?> owner : Arrays.<Class<?>>asList(ToolSchemaGenerator.class, ToolArgumentCodec.class,
                RecordMetadata.class, RecordMetadata.Component.class, ToolPattern.class, ToolAtLeastOne.class,
                ToolDescription.class, ToolOptional.class, QueryOperation.class, ToolResult.class,
                ValueSchema.class, ValueSchemas.class, ValueType.class, Java8Strings.class,
                EngineJson.class, ToolSchemaJava8Fixture.class)) classMajor(owner);
        for (String name : new String[] {"dev.openallay.json.RecordJsonAdapter", "dev.openallay.json.ConstructorValueJsonAdapter",
                "dev.openallay.json.JsonTrees", "dev.openallay.json.JsonReaders"}) classMajor(Class.forName(name));
        check(!RecordMetadata.isRecord(String.class), "no Java8 record fact");
        fails(IllegalArgumentException.class, () -> RecordMetadata.components(String.class));
        run();
        System.out.println("PASS actual ToolSchemaGenerator/ToolArgumentCodec/EngineJson Java8 " + System.getProperty("java.version"));
    }

    public static void run() throws Exception {
        Gson gson = EngineJson.create();
        ToolSchemaGenerator generator = new ToolSchemaGenerator();
        JsonObject query = generator.generate(QueryOperation.class);
        equal(Arrays.asList("op", "field", "operator", "value", "fields", "direction", "aggregate", "groupBy", "count"),
                new ArrayList<>(JsonTrees.keys(query.getAsJsonObject("properties"))));
        equal("[\"op\"]", query.getAsJsonArray("required").toString());
        check(!query.get("additionalProperties").getAsBoolean(), "strict query schema");
        equal("[\"SEARCH\",\"FILTER\",\"SELECT\",\"SORT\",\"GROUP\",\"AGGREGATE\",\"EXPAND\",\"TAKE\"]",
                query.getAsJsonObject("properties").getAsJsonObject("op").getAsJsonArray("enum").toString());
        equal("Fields retained by SELECT", query.getAsJsonObject("properties").getAsJsonObject("fields").get("description").getAsString());
        equal("array", query.getAsJsonObject("properties").getAsJsonObject("fields").get("type").getAsString());
        equal("string", query.getAsJsonObject("properties").getAsJsonObject("fields").getAsJsonObject("items").get("type").getAsString());
        equal("[]", generator.generateOutput(QueryOperation.class).getAsJsonArray("required").toString());
        JsonObject lookup = generator.generate(Lookup.class);
        equal("Lookup exact identifier or kind", lookup.get("description").getAsString());
        equal("^[a-z]+:[a-z_]+$", lookup.getAsJsonObject("properties").getAsJsonObject("id").get("pattern").getAsString());
        equal("Exact identifier", lookup.getAsJsonObject("properties").getAsJsonObject("id").get("description").getAsString());
        equal("[]", lookup.getAsJsonArray("required").toString());
        equal("[{\"required\":[\"id\"]},{\"required\":[\"kind\"]}]", lookup.getAsJsonArray("anyOf").toString());
        check(!generator.generateOutput(Lookup.class).has("anyOf"), "output does not require input alternatives");
        JsonObject nested = generator.generate(Nested.class).getAsJsonObject("properties");
        equal("object", nested.getAsJsonObject("rows").get("type").getAsString());
        JsonObject row = nested.getAsJsonObject("rows").getAsJsonObject("additionalProperties");
        equal("array", row.get("type").getAsString());
        equal("object", row.getAsJsonObject("items").get("type").getAsString());
        equal("string", nested.getAsJsonObject("note").get("type").getAsString());
        equal("[\"rows\"]", generator.generate(Nested.class).getAsJsonArray("required").toString());
        check(ValueSchemas.of(Nested.class).components().get(0).genericType() instanceof ParameterizedType, "nested generic metadata");
        fails(IllegalArgumentException.class, () -> generator.generate(PlainPojo.class));
        fails(IllegalArgumentException.class, () -> generator.generate(BadAlternative.class));
        fails(IllegalArgumentException.class, () -> generator.generate(EmptyAlternative.class));
        fails(IllegalArgumentException.class, () -> generator.generate(Recursive.class));
        ToolArgumentCodec codec = new ToolArgumentCodec(gson);
        ToolResult.Success<QueryOperation> valid = success(codec.decode(object("{\"op\":\"SELECT\",\"fields\":[\"a\",\"b\"]}"), QueryOperation.class));
        equal(QueryOperation.Op.SELECT, valid.value().op());
        equal(Arrays.asList("a", "b"), valid.value().fields());
        equal("[]", generator.generateOutput(Lookup.class).getAsJsonArray("required").toString());
        for (String malformed : new String[] {"7", "false", "{}", "[]"}) {
            equal("field must be text", failure(codec.decode(object("{\"field\":" + malformed + "}"), QueryOperation.class)).message());
            equal("invalid_arguments", failure(codec.decode(object("{\"value\":" + malformed + "}"), QueryOperation.class)).code());
        }
        for (String malformed : new String[] {"7", "false", "{}", "\"x\"", "[7]", "[false]", "[null]", "[{}]", "[[]]"}) {
            equal("fields must be an array of text", failure(codec.decode(object("{\"fields\":" + malformed + "}"), QueryOperation.class)).message());
        }
        equal("tool arguments contain an undeclared field",
                failure(codec.decode(object("{\"foreignSecretMarker\":1}"), QueryOperation.class)).message());
        equal(null, success(codec.decode(object("{\"field\":null,\"fields\":null}"), QueryOperation.class)).value().field());
        equal(3, success(codec.decode(object("{\"op\":\"TAKE\",\"count\":3}"), QueryOperation.class)).value().count());
        equal("invalid_arguments", failure(codec.decode(object("{\"count\":{}}"), QueryOperation.class)).code());
        equal("tool arguments decoded to null", failure(codec.decode(null, QueryOperation.class)).message());
        for (String json : new String[] {"{}", "{\"id\":\"not-namespaced\"}", "{\"id\":\"A:B\"}", "{\"id\":\"\u2003\"}"}) {
            equal("invalid_arguments", failure(codec.decode(object(json), Lookup.class)).code());
        }
        equal("mod:item", success(codec.decode(object("{\"id\":\"mod:item\"}"), Lookup.class)).value().id());
        equal("item", success(codec.decode(object("{\"kind\":\"item\"}"), Lookup.class)).value().kind());
        equal("tool input type has no declared tool schema",
                failure(codec.decode(object("{\"name\":\"value\"}"), PlainPojo.class)).message());
        JsonObject detached = object("{\"name\":\"value\"}");
        equal(detached, success(codec.decode(detached, JsonObject.class)).value());
        check(success(codec.decode(detached, Object.class)).value() instanceof Map<?, ?>, "schema-supported Object lane");
        Gson scalar = EngineJson.create(builder -> builder.registerTypeAdapter(String.class,
                (com.google.gson.JsonDeserializer<String>) (json, type, context) -> json.getAsJsonObject().get("name").getAsString()));
        equal("value", success(new ToolArgumentCodec(scalar).decode(detached, String.class)).value());
        Gson array = EngineJson.create(builder -> builder.registerTypeAdapter(String[].class,
                (com.google.gson.JsonDeserializer<String[]>) (json, type, context) -> new String[] {json.getAsJsonObject().get("name").getAsString()}));
        equal(Arrays.asList("value"), Arrays.asList(success(new ToolArgumentCodec(array).decode(detached, String[].class)).value()));
        check(!RecordMetadata.isRecord(QueryOperation.class), "explicit values are ordinary classes");
    }

    @ToolDescription("Lookup exact identifier or kind") @ToolAtLeastOne({"id", "kind"})
    @ValueType(Lookup.Schema.class)
    public static final class Lookup {
        @ToolDescription("Exact identifier") @ToolPattern("^[a-z]+:[a-z_]+$") @ToolOptional
        private final String id;
        @ToolOptional private final String kind;
        public Lookup(String id, String kind) {
            if (id == null && kind == null) throw new IllegalArgumentException("Lookup key required");
            if (id != null && !id.matches("^[a-z]+:[a-z_]+$")) throw new IllegalArgumentException("Invalid lookup id");
            this.id = id; this.kind = kind;
        }
        public String id() { return id; }
        public String kind() { return kind; }
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<Lookup> schema() {
                return new ValueSchema<>(Lookup.class, Arrays.asList(
                        new ValueSchema.Component<>(Lookup.class, "id", Lookup::id),
                        new ValueSchema.Component<>(Lookup.class, "kind", Lookup::kind)),
                        values -> new Lookup((String) values[0], (String) values[1]));
            }
        }
    }
    @ValueType(Nested.Schema.class)
    public static final class Nested {
        private final Map<String, List<QueryOperation>> rows;
        private final Optional<String> note;
        public Nested(Map<String, List<QueryOperation>> rows, Optional<String> note) { this.rows = rows; this.note = note; }
        public Map<String, List<QueryOperation>> rows() { return rows; }
        public Optional<String> note() { return note; }
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @SuppressWarnings("unchecked") @Override public ValueSchema<Nested> schema() {
                return new ValueSchema<>(Nested.class, Arrays.asList(
                        new ValueSchema.Component<>(Nested.class, "rows", Nested::rows),
                        new ValueSchema.Component<>(Nested.class, "note", Nested::note)),
                        values -> new Nested((Map<String, List<QueryOperation>>) values[0], (Optional<String>) values[1]));
            }
        }
    }
    public static final class PlainPojo { public String name; }
    @ToolAtLeastOne("missing") @ValueType(BadAlternative.Schema.class)
    public static final class BadAlternative {
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<BadAlternative> schema() {
                return new ValueSchema<>(BadAlternative.class, Collections.emptyList(), values -> new BadAlternative());
            }
        }
    }
    @ToolAtLeastOne({}) @ValueType(EmptyAlternative.Schema.class)
    public static final class EmptyAlternative {
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<EmptyAlternative> schema() {
                return new ValueSchema<>(EmptyAlternative.class, Collections.emptyList(), values -> new EmptyAlternative());
            }
        }
    }
    @ValueType(Recursive.Schema.class)
    public static final class Recursive {
        private final Recursive next;
        public Recursive(Recursive next) { this.next = next; }
        public Recursive next() { return next; }
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<Recursive> schema() {
                return new ValueSchema<>(Recursive.class, Collections.singletonList(
                        new ValueSchema.Component<>(Recursive.class, "next", Recursive::next)), values -> new Recursive((Recursive) values[0]));
            }
        }
    }
    private static JsonObject object(String json) { return JsonTrees.parse(json).getAsJsonObject(); }
    @SuppressWarnings("unchecked") private static <T> ToolResult.Success<T> success(ToolResult<T> result) {
        check(result instanceof ToolResult.Success<?>, "expected success, got " + result);
        return (ToolResult.Success<T>) result;
    }
    private static ToolResult.Failure<?> failure(ToolResult<?> result) {
        check(result instanceof ToolResult.Failure<?>, "expected failure, got " + result);
        return (ToolResult.Failure<?>) result;
    }
    private static void classMajor(Class<?> type) throws Exception {
        try (InputStream stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            check(stream != null, "class resource"); byte[] header = new byte[8]; int position = 0;
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
