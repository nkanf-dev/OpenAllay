package dev.openallay.json;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.openallay.util.Java8Collections;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import dev.openallay.value.ValueType;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Canonical EngineJson owner subset, never a proxy owner or a full-engine Java 8 claim. */
public final class EngineJsonJava8Fixture {
    private EngineJsonJava8Fixture() {}
    private interface Action { void run() throws Exception; }
    private static int checks;

    public static void main(String[] arguments) throws Exception {
        equal("1.8", System.getProperty("java.specification.version"));
        for (Class<?> owner : new Class<?>[] {EngineJson.class, RecordJsonAdapter.class,
                ConstructorValueJsonAdapter.class, ValueSchema.class, ValueSchemas.class,
                ValueType.class, Java8Collections.class, JsonReaders.class, JsonTrees.class,
                EngineJsonJava8Fixture.class}) {
            try (DataInputStream input = new DataInputStream(owner.getResourceAsStream(
                    "/" + owner.getName().replace('.', '/') + ".class"))) {
                equal(0xcafebabe, input.readInt()); input.readUnsignedShort(); equal(52, input.readUnsignedShort());
            }
        }
        check(!ValueSchemas.isValue(String.class), "foreign record fact absent on Java 8");
        check(ValueSchemas.isValue(Sample.class), "explicit schemas survive absent record fact");
        run();
        System.out.println("PASS canonical EngineJson subset " + checks + " checks; java="
                + System.getProperty("java.version") + "; Gson="
                + Gson.class.getProtectionDomain().getCodeSource().getLocation());
    }

    public static void run() throws Exception {
        ValueSchemaJava8Fixture.run(EngineJson::create);
        Gson owner = EngineJson.create();
        same(owner, EngineJson.withInstant(owner));
        fails(JsonIOException.class, () -> EngineJson.withInstant(new Gson()));
        fails(NullPointerException.class, () -> EngineJson.create(null));
        instants(owner);
        recipes();
        precedence();
        hierarchy();
        components(owner);
    }

    private static void instants(Gson owner) throws Exception {
        Instant time = Instant.ofEpochSecond(-1, 123456789);
        equal("{\"seconds\":-1,\"nanos\":123456789}", owner.toJson(time));
        equal(time, owner.fromJson(owner.toJson(time), Instant.class));
        equal("null", owner.toJson(null, Instant.class));
        equal(null, owner.fromJson("null", Instant.class));
        for (String invalid : Arrays.asList("{}", "[]", "{\"seconds\":0}",
                "{\"seconds\":0,\"nanos\":0,\"extra\":1}",
                "{\"seconds\":0,\"seconds\":1,\"nanos\":0}",
                "{\"seconds\":0,\"nanos\":0,\"nanos\":1}",
                "{\"seconds\":0,\"nanos\":-1}", "{\"seconds\":0,\"nanos\":1000000000}",
                "{\"seconds\":1e0,\"nanos\":0}", "{\"seconds\":0,\"nanos\":1.0}",
                "{\"seconds\":\"0\",\"nanos\":0}", "{\"seconds\":null,\"nanos\":0}",
                "{\"seconds\":9223372036854775808,\"nanos\":0}",
                "{\"seconds\":9223372036854775807,\"nanos\":0}",
                owner.toJson(time) + " true")) {
            fails(JsonParseException.class, () -> owner.fromJson(invalid, Instant.class));
        }
        try (JsonReader reader = JsonReaders.strict(new StringReader(owner.toJson(time)))) {
            equal(time, owner.getAdapter(Instant.class).read(reader));
            equal(JsonToken.END_DOCUMENT, reader.peek());
        }
        fails(JsonParseException.class, () -> {
            try (JsonReader reader = JsonReaders.strict(new StringReader("{\"seconds\":0,\"nanos\":0} true"))) {
                owner.getAdapter(Instant.class).read(reader);
                try { reader.peek(); } catch (IOException failure) { throw new JsonParseException(failure); }
            }
        });
    }

    private static void recipes() throws Exception {
        Sample value = new Sample("<&>", 2);
        Gson original = EngineJson.create(builder -> builder.setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE));
        equal("{\"main\":\"\\u003c\\u0026\\u003e\",\"Count\":2}", original.toJson(value));
        String before = original.toJson(value);
        Gson overlay = EngineJson.derive(original, builder -> builder.disableHtmlEscaping()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES));
        equal("{\"main\":\"<&>\",\"count\":2}", overlay.toJson(value));
        equal(before, original.toJson(value));
        same(overlay, EngineJson.withInstant(overlay));
        ExclusionStrategy count = new ExclusionStrategy() {
            @Override public boolean shouldSkipClass(Class<?> type) { return false; }
            @Override public boolean shouldSkipField(FieldAttributes field) { return field.getName().equals("count"); }
        };
        Gson writeExcluded = EngineJson.create(builder -> builder.addSerializationExclusionStrategy(count));
        equal("{\"main\":\"a\"}", writeExcluded.toJson(new Sample("a", 2)));
        equal(2, writeExcluded.fromJson("{\"main\":\"a\",\"count\":2}", Sample.class).count());
        Gson readExcluded = EngineJson.create(builder -> builder.addDeserializationExclusionStrategy(count));
        equal(0, readExcluded.fromJson("{\"main\":\"a\",\"count\":2}", Sample.class).count());
        equal("{\"main\":\"a\",\"count\":2}", readExcluded.toJson(new Sample("a", 2)));
        ExclusionStrategy classes = new ExclusionStrategy() {
            @Override public boolean shouldSkipClass(Class<?> type) { return type == Sample.class; }
            @Override public boolean shouldSkipField(FieldAttributes field) { return false; }
        };
        Gson excluded = EngineJson.create(builder -> builder.setExclusionStrategies(classes));
        equal("null", excluded.toJson(new Sample("a", 2)));
        equal(null, excluded.fromJson("{}", Sample.class));
        same(excluded, EngineJson.withInstant(excluded));
    }

    private static void precedence() throws Exception {
        Gson direct = EngineJson.create(builder -> builder.registerTypeAdapter(Sample.class, new SampleAdapter()));
        equal("7", direct.toJson(new Sample("a", 7)));
        equal(7, direct.fromJson("7", Sample.class).count());
        Gson factory = EngineJson.create(builder -> builder.registerTypeAdapterFactory(new TypeAdapterFactory() {
            @SuppressWarnings("unchecked")
            @Override public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                return type.getRawType() == Sample.class ? (TypeAdapter<T>) new SampleAdapter() : null;
            }
        }));
        equal("8", factory.toJson(new Sample("a", 8)));
        Gson instant = EngineJson.create(builder -> builder.registerTypeAdapter(Instant.class, new TypeAdapter<Instant>() {
            @Override public void write(JsonWriter out, Instant value) throws IOException { out.value("time"); }
            @Override public Instant read(JsonReader in) throws IOException { in.nextString(); return Instant.EPOCH; }
        }));
        equal("\"time\"", instant.toJson(Instant.EPOCH));
        equal(Instant.EPOCH, instant.fromJson("\"time\"", Instant.class));
        Gson annotated = EngineJson.create();
        equal("4", annotated.toJson(new ClassCustom(4)));
        equal(4, annotated.fromJson("4", ClassCustom.class).count());
    }

    private static void hierarchy() throws Exception {
        JsonSerializer<Tagged> serializer = (value, type, context) -> new JsonPrimitive("first");
        Gson first = EngineJson.deriveHierarchy(EngineJson.create(), Tagged.class, serializer);
        equal("\"first\"", first.toJson(new Sample("a", 7)));
        equal(7, first.fromJson("{\"alias\":\"a\",\"count\":7}", Sample.class).count());
        fails(JsonParseException.class, () -> first.fromJson("{\"count\":1,\"count\":2}", Sample.class));
        Gson second = EngineJson.deriveHierarchy(first, Tagged.class,
                (JsonDeserializer<Tagged>) (tree, type, context) -> new Sample("decoded", tree.getAsInt()));
        equal("\"first\"", second.toJson(new Sample("a", 7)));
        equal(9, second.fromJson("9", Sample.class).count());
        Gson third = EngineJson.deriveHierarchy(second, Tagged.class,
                (JsonSerializer<Tagged>) (value, type, context) -> new JsonPrimitive("third"));
        equal("\"third\"", third.toJson(new Sample("a", 7)));
        equal("\"first\"", first.toJson(new Sample("a", 7)));
        Gson exact = EngineJson.derive(third, builder -> builder.registerTypeAdapter(Sample.class, new SampleAdapter()));
        equal("7", exact.toJson(new Sample("a", 7)));
        equal(7, exact.fromJson("7", Sample.class).count());
        Gson wrong = EngineJson.deriveHierarchy(EngineJson.create(), Tagged.class,
                (JsonDeserializer<Tagged>) (tree, type, context) -> new ClassCustom(4));
        fails(JsonSyntaxException.class, () -> wrong.fromJson("7", Sample.class));
        Gson instant = EngineJson.deriveHierarchy(EngineJson.create(), Instant.class,
                (JsonSerializer<Instant>) (value, type, context) -> new JsonPrimitive("hierarchy-time"));
        equal("\"hierarchy-time\"", instant.toJson(Instant.EPOCH));
        equal(Instant.EPOCH, instant.fromJson("{\"seconds\":0,\"nanos\":0}", Instant.class));
        fails(JsonParseException.class, () -> instant.fromJson("{\"seconds\":0,\"seconds\":1,\"nanos\":0}", Instant.class));
    }

    private static void components(Gson owner) throws Exception {
        Sample value = owner.fromJson("{\"alias\":\"a\",\"count\":7}", Sample.class);
        equal("a", value.text()); equal(7, value.count());
        equal(0, owner.fromJson("{}", Sample.class).count());
        fails(JsonParseException.class, () -> owner.fromJson("{\"main\":\"a\",\"alias\":\"b\"}", Sample.class));
        fails(JsonParseException.class, () -> owner.fromJson("{\"count\":null}", Sample.class));
        JsonParseException failure = fails(JsonParseException.class,
                () -> owner.fromJson("{\"count\":-1}", Sample.class));
        check(failure.getCause() instanceof IllegalArgumentException, "constructor cause retained");
        equal("negative count", failure.getCause().getMessage());
        java.lang.reflect.Type type = TypeToken.getParameterized(Box.class, Instant.class).getType();
        Box<Instant> box = new Box<>(Instant.ofEpochSecond(-2, 7), Arrays.asList(Instant.EPOCH));
        Box<Instant> decoded = owner.fromJson(owner.toJson(box, type), type);
        equal(box.value(), decoded.value()); equal(box.history(), decoded.history());
        Node child = new Node(Instant.EPOCH, null);
        Node parent = new Node(Instant.ofEpochSecond(1, 2), child);
        Node node = owner.fromJson(owner.toJson(parent), Node.class);
        equal(parent.time(), node.time()); equal(child.time(), node.child().time());
        Node self = new Node(Instant.EPOCH, null); self.child = self;
        equal("{\"time\":{\"seconds\":0,\"nanos\":0}}", owner.toJson(self));
    }

    private interface Tagged {}
    @ValueType(Sample.Schema.class)
    public static final class Sample implements Tagged {
        @SerializedName(value = "main", alternate = {"alias"}) private final String text;
        private final int count;
        public Sample(String text, int count) {
            if (count < 0) throw new IllegalArgumentException("negative count");
            this.text = text; this.count = count;
        }
        public String text() { return text; }
        public int count() { return count; }
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<Sample> schema() {
                return new ValueSchema<>(Sample.class, Arrays.asList(
                        new ValueSchema.Component<>(Sample.class, "text", Sample::text),
                        new ValueSchema.Component<>(Sample.class, "count", Sample::count)),
                        values -> new Sample((String) values[0], (Integer) values[1]));
            }
        }
    }
    public static final class SampleAdapter extends TypeAdapter<Sample> {
        public SampleAdapter() {}
        @Override public void write(JsonWriter out, Sample value) throws IOException { out.value(value.count()); }
        @Override public Sample read(JsonReader in) throws IOException { return new Sample("custom", in.nextInt()); }
    }
    @JsonAdapter(ClassAdapter.class) @ValueType(ClassCustom.Schema.class)
    public static final class ClassCustom implements Tagged {
        private final int count;
        public ClassCustom(int count) { this.count = count; }
        public int count() { return count; }
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<ClassCustom> schema() {
                return new ValueSchema<>(ClassCustom.class, Arrays.asList(
                        new ValueSchema.Component<>(ClassCustom.class, "count", ClassCustom::count)),
                        values -> new ClassCustom((Integer) values[0]));
            }
        }
    }
    public static final class ClassAdapter extends TypeAdapter<ClassCustom> {
        public ClassAdapter() {}
        @Override public void write(JsonWriter out, ClassCustom value) throws IOException { out.value(value.count()); }
        @Override public ClassCustom read(JsonReader in) throws IOException { return new ClassCustom(in.nextInt()); }
    }
    @ValueType(Box.Schema.class)
    public static final class Box<T> {
        private final T value; private final List<T> history;
        public Box(T value, List<T> history) { this.value = value; this.history = history; }
        public T value() { return value; } public List<T> history() { return history; }
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @SuppressWarnings("unchecked")
            @Override public ValueSchema<Box> schema() {
                return new ValueSchema<>(Box.class, Arrays.asList(
                        new ValueSchema.Component<>(Box.class, "value", Box::value),
                        new ValueSchema.Component<>(Box.class, "history", Box::history)),
                        values -> new Box<Object>(values[0], (List<Object>) values[1]));
            }
        }
    }
    @ValueType(Node.Schema.class)
    public static final class Node {
        private final Instant time;
        private Node child; // Test-only mutable cycle; schema execution still uses constructor/accessors.
        public Node(Instant time, Node child) { this.time = time; this.child = child; }
        public Instant time() { return time; } public Node child() { return child; }
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<Node> schema() {
                return new ValueSchema<>(Node.class, Arrays.asList(
                        new ValueSchema.Component<>(Node.class, "time", Node::time),
                        new ValueSchema.Component<>(Node.class, "child", Node::child)),
                        values -> new Node((Instant) values[0], (Node) values[1]));
            }
        }
    }
    private static <T extends Throwable> T fails(Class<T> type, Action action) throws Exception {
        try { action.run(); } catch (Throwable failure) {
            if (type.isInstance(failure)) { checks++; return type.cast(failure); }
            throw new AssertionError("Expected " + type + ", got " + failure, failure);
        }
        throw new AssertionError("Expected " + type);
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) { check(Objects.equals(expected, actual), "Expected " + expected + ", got " + actual); }
    private static void same(Object expected, Object actual) { check(expected == actual, "Identity differs"); }
}
