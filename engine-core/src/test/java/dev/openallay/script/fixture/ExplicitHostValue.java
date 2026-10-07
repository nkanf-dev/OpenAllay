package dev.openallay.script.fixture;

import dev.openallay.agent.tool.ToolDescription;
import dev.openallay.agent.tool.ToolOptional;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import dev.openallay.value.ValueType;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Java8 typed-metadata subset fixture; the real Rhino runtime tests run on the supported JVM. */
@ValueType(ExplicitHostValue.Schema.class)
public final class ExplicitHostValue {
    @ToolDescription("Field metadata only") @ToolOptional
    private final String name;
    private final List<Leaf> rows;
    private final Map<String, List<Leaf>> properties;
    private final Optional<String> note;
    // Deliberately absent from the schema: neither fields nor methods authorize script access.
    private final Thread privateAuthority = new Thread();

    public ExplicitHostValue(String name, List<Leaf> rows,
            Map<String, List<Leaf>> properties, Optional<String> note) {
        this.name = Objects.requireNonNull(name, "name").trim();
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        LinkedHashMap<String, List<Leaf>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Leaf>> entry : properties.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        this.properties = Collections.unmodifiableMap(copy);
        this.note = Objects.requireNonNull(note, "note");
    }

    @ToolDescription("Getter metadata must not override the field")
    public String name() { return name; }
    public List<Leaf> rows() { return rows; }
    public Map<String, List<Leaf>> properties() { return properties; }
    public Optional<String> note() { return note; }
    public Thread secretGetter() { return privateAuthority; }

    public static final class Schema implements ValueSchema.Provider {
        public Schema() {}
        @Override public ValueSchema<ExplicitHostValue> schema() {
            return new ValueSchema<>(ExplicitHostValue.class, Arrays.asList(
                    new ValueSchema.Component<>(ExplicitHostValue.class, "name", ExplicitHostValue::name),
                    new ValueSchema.Component<>(ExplicitHostValue.class, "rows", ExplicitHostValue::rows),
                    new ValueSchema.Component<>(ExplicitHostValue.class, "properties", ExplicitHostValue::properties),
                    new ValueSchema.Component<>(ExplicitHostValue.class, "note", ExplicitHostValue::note)),
                    arguments -> new ExplicitHostValue((String) arguments[0], rows(arguments[1]),
                            properties(arguments[2]), note(arguments[3])));
        }
        @SuppressWarnings("unchecked") private static List<Leaf> rows(Object value) { return (List<Leaf>) value; }
        @SuppressWarnings("unchecked") private static Map<String, List<Leaf>> properties(Object value) {
            return (Map<String, List<Leaf>>) value;
        }
        @SuppressWarnings("unchecked") private static Optional<String> note(Object value) { return (Optional<String>) value; }
    }

    @ValueType(Leaf.Schema.class)
    public static final class Leaf {
        private final String id;
        public Leaf(String id) { this.id = Objects.requireNonNull(id, "id").trim(); }
        public String id() { return id; }
        public static final class Schema implements ValueSchema.Provider {
            public Schema() {}
            @Override public ValueSchema<Leaf> schema() {
                return new ValueSchema<>(Leaf.class,
                        Collections.singletonList(new ValueSchema.Component<>(Leaf.class, "id", Leaf::id)),
                        arguments -> new Leaf((String) arguments[0]));
            }
        }
    }

    /** Actual Java8 smoke entry point; no Rhino classes or fabricated TypeInfo are involved. */
    public static void main(String[] arguments) {
        ValueSchema<ExplicitHostValue> schema = ValueSchemas.of(ExplicitHostValue.class);
        List<Leaf> rows = new ArrayList<>(Collections.singletonList(new Leaf(" nested ")));
        ExplicitHostValue value = schema.construct(new Object[] {
                " normalized ", rows, Collections.singletonMap("nested", rows), Optional.empty()});
        rows.clear();
        if (!"normalized".equals(value.name()) || value.rows().size() != 1
                || !"nested".equals(value.rows().get(0).id())) throw new AssertionError("Constructor normalization");
        List<String> names = new ArrayList<>();
        for (ValueSchema.Component<ExplicitHostValue> component : schema.components()) names.add(component.name());
        if (!names.equals(Arrays.asList("name", "rows", "properties", "note"))) throw new AssertionError("Component order");
        if (!"Field metadata only".equals(schema.components().get(0).annotation(ToolDescription.class).value())
                || schema.components().get(0).annotation(ToolOptional.class) == null) throw new AssertionError("Field metadata");
        ParameterizedType listType = (ParameterizedType) schema.components().get(1).genericType();
        if (listType.getActualTypeArguments()[0] != Leaf.class) throw new AssertionError("List generic metadata");
        ParameterizedType mapType = (ParameterizedType) schema.components().get(2).genericType();
        if (mapType.getActualTypeArguments()[0] != String.class
                || !mapType.getActualTypeArguments()[1].equals(listType)) throw new AssertionError("Nested map metadata");
        if (schema.components().get(1).read(value) != value.rows()) throw new AssertionError("Typed accessor");
        try { value.rows().clear(); throw new AssertionError("Mutable rows"); }
        catch (UnsupportedOperationException expected) { }
        System.out.println("PASS explicit host fixture Java8 typed metadata/constructor subset");
    }
}
