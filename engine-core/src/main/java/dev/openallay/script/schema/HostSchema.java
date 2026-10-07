package dev.openallay.script.schema;

import java.util.List;
import java.util.Map;

/** Closed, script-visible description of values accepted by the Rhino host adapter. */
public interface HostSchema {
    /** Closed schema algebra; foreign implementations cannot enter host discovery/validation. */
    static void requireKnown(HostSchema schema) {
        java.util.Objects.requireNonNull(schema, "schema");
        Class<?> type = schema.getClass();
        if (type != Scalar.class && type != Enumeration.class && type != Sequence.class
                && type != OptionalValue.class && type != Dictionary.class && type != RecordValue.class
                && type != DynamicJson.class && type != DynamicDetached.class) {
            throw new IncompatibleClassChangeError("Unknown host schema subtype");
        }
    }
    String kind();

    @dev.openallay.value.ValueType(Scalar.ValueSchemaProvider.class)
public static final class Scalar implements HostSchema {
    private final String kind;
    public Scalar(String kind) {
        this.kind = kind;
    }
    public String kind() { return kind; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Scalar)) return false;
        Scalar that = (Scalar) other;
        return java.util.Objects.equals(kind, that.kind);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        return hash;
    }
    @Override public String toString() { return "Scalar[kind=" + kind + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Scalar> schema() {
            return new dev.openallay.value.ValueSchema<>(Scalar.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Scalar>>asList(new dev.openallay.value.ValueSchema.Component<>(Scalar.class, "kind", Scalar::kind)), arguments -> new Scalar((String) arguments[0]));
        }
    }
}

    @dev.openallay.value.ValueType(Enumeration.ValueSchemaProvider.class)
public static final class Enumeration implements HostSchema {
    private final String kind;
    private final List<String> values;
    public Enumeration(String kind, List<String> values) {

            values = dev.openallay.util.Java8Collections.listCopyOf(values);

        this.kind = kind;
        this.values = values;
    }
    public String kind() { return kind; }
    public List<String> values() { return values; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Enumeration)) return false;
        Enumeration that = (Enumeration) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(values, that.values);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(values);
        return hash;
    }
    @Override public String toString() { return "Enumeration[kind=" + kind + ", values=" + values + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Enumeration> schema() {
            return new dev.openallay.value.ValueSchema<>(Enumeration.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Enumeration>>asList(new dev.openallay.value.ValueSchema.Component<>(Enumeration.class, "kind", Enumeration::kind), new dev.openallay.value.ValueSchema.Component<>(Enumeration.class, "values", Enumeration::values)), arguments -> new Enumeration((String) arguments[0], (List) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Sequence.ValueSchemaProvider.class)
public static final class Sequence implements HostSchema {
    private final String kind;
    private final HostSchema elements;
    public Sequence(String kind, HostSchema elements) {

            java.util.Objects.requireNonNull(elements, "elements");
            HostSchema.requireKnown(elements);

        this.kind = kind;
        this.elements = elements;
    }
    public String kind() { return kind; }
    public HostSchema elements() { return elements; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Sequence)) return false;
        Sequence that = (Sequence) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(elements, that.elements);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(elements);
        return hash;
    }
    @Override public String toString() { return "Sequence[kind=" + kind + ", elements=" + elements + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Sequence> schema() {
            return new dev.openallay.value.ValueSchema<>(Sequence.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Sequence>>asList(new dev.openallay.value.ValueSchema.Component<>(Sequence.class, "kind", Sequence::kind), new dev.openallay.value.ValueSchema.Component<>(Sequence.class, "elements", Sequence::elements)), arguments -> new Sequence((String) arguments[0], (HostSchema) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(OptionalValue.ValueSchemaProvider.class)
public static final class OptionalValue implements HostSchema {
    private final String kind;
    private final HostSchema value;
    public OptionalValue(String kind, HostSchema value) {

            java.util.Objects.requireNonNull(value, "value");
            HostSchema.requireKnown(value);

        this.kind = kind;
        this.value = value;
    }
    public String kind() { return kind; }
    public HostSchema value() { return value; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OptionalValue)) return false;
        OptionalValue that = (OptionalValue) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(value, that.value);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(value);
        return hash;
    }
    @Override public String toString() { return "OptionalValue[kind=" + kind + ", value=" + value + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<OptionalValue> schema() {
            return new dev.openallay.value.ValueSchema<>(OptionalValue.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<OptionalValue>>asList(new dev.openallay.value.ValueSchema.Component<>(OptionalValue.class, "kind", OptionalValue::kind), new dev.openallay.value.ValueSchema.Component<>(OptionalValue.class, "value", OptionalValue::value)), arguments -> new OptionalValue((String) arguments[0], (HostSchema) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Dictionary.ValueSchemaProvider.class)
public static final class Dictionary implements HostSchema {
    private final String kind;
    private final HostSchema values;
    private final boolean dynamicKeys;
    public Dictionary(String kind, HostSchema values, boolean dynamicKeys) {

            java.util.Objects.requireNonNull(values, "values");
            HostSchema.requireKnown(values);

        this.kind = kind;
        this.values = values;
        this.dynamicKeys = dynamicKeys;
    }
    public String kind() { return kind; }
    public HostSchema values() { return values; }
    public boolean dynamicKeys() { return dynamicKeys; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Dictionary)) return false;
        Dictionary that = (Dictionary) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(values, that.values) && dynamicKeys == that.dynamicKeys;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(values);
        hash = 31 * hash + Boolean.hashCode(dynamicKeys);
        return hash;
    }
    @Override public String toString() { return "Dictionary[kind=" + kind + ", values=" + values + ", dynamicKeys=" + dynamicKeys + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Dictionary> schema() {
            return new dev.openallay.value.ValueSchema<>(Dictionary.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Dictionary>>asList(new dev.openallay.value.ValueSchema.Component<>(Dictionary.class, "kind", Dictionary::kind), new dev.openallay.value.ValueSchema.Component<>(Dictionary.class, "values", Dictionary::values), new dev.openallay.value.ValueSchema.Component<>(Dictionary.class, "dynamicKeys", Dictionary::dynamicKeys)), arguments -> new Dictionary((String) arguments[0], (HostSchema) arguments[1], (Boolean) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(RecordValue.ValueSchemaProvider.class)
public static final class RecordValue implements HostSchema {
    private final String kind;
    private final String javaType;
    private final Map<String, HostSchema> fields;
    public RecordValue(String kind, String javaType, Map<String, HostSchema> fields) {

            if (javaType == null || dev.openallay.util.Java8Strings.isBlank(javaType)) {
                throw new IllegalArgumentException("javaType must not be blank");
            }
            fields = dev.openallay.util.Java8Collections.mapCopyOf(fields);
            fields.values().forEach(HostSchema::requireKnown);

        this.kind = kind;
        this.javaType = javaType;
        this.fields = fields;
    }
    public String kind() { return kind; }
    public String javaType() { return javaType; }
    public Map<String, HostSchema> fields() { return fields; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RecordValue)) return false;
        RecordValue that = (RecordValue) other;
        return java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(javaType, that.javaType) && java.util.Objects.equals(fields, that.fields);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(javaType);
        hash = 31 * hash + java.util.Objects.hashCode(fields);
        return hash;
    }
    @Override public String toString() { return "RecordValue[kind=" + kind + ", javaType=" + javaType + ", fields=" + fields + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RecordValue> schema() {
            return new dev.openallay.value.ValueSchema<>(RecordValue.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RecordValue>>asList(new dev.openallay.value.ValueSchema.Component<>(RecordValue.class, "kind", RecordValue::kind), new dev.openallay.value.ValueSchema.Component<>(RecordValue.class, "javaType", RecordValue::javaType), new dev.openallay.value.ValueSchema.Component<>(RecordValue.class, "fields", RecordValue::fields)), arguments -> new RecordValue((String) arguments[0], (String) arguments[1], (Map) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(DynamicJson.ValueSchemaProvider.class)
public static final class DynamicJson implements HostSchema {
    private final String kind;
    public DynamicJson(String kind) {
        this.kind = kind;
    }
    public String kind() { return kind; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DynamicJson)) return false;
        DynamicJson that = (DynamicJson) other;
        return java.util.Objects.equals(kind, that.kind);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        return hash;
    }
    @Override public String toString() { return "DynamicJson[kind=" + kind + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DynamicJson> schema() {
            return new dev.openallay.value.ValueSchema<>(DynamicJson.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DynamicJson>>asList(new dev.openallay.value.ValueSchema.Component<>(DynamicJson.class, "kind", DynamicJson::kind)), arguments -> new DynamicJson((String) arguments[0]));
        }
    }
}

    /**
     * Heterogeneous values whose individual schemas are declared in a sibling catalog.
     * This is reserved for the extension aggregate and does not authorize arbitrary Java access.
     */
    @dev.openallay.value.ValueType(DynamicDetached.ValueSchemaProvider.class)
public static final class DynamicDetached implements HostSchema {
    private final String kind;
    public DynamicDetached(String kind) {
        this.kind = kind;
    }
    public String kind() { return kind; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DynamicDetached)) return false;
        DynamicDetached that = (DynamicDetached) other;
        return java.util.Objects.equals(kind, that.kind);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        return hash;
    }
    @Override public String toString() { return "DynamicDetached[kind=" + kind + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DynamicDetached> schema() {
            return new dev.openallay.value.ValueSchema<>(DynamicDetached.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DynamicDetached>>asList(new dev.openallay.value.ValueSchema.Component<>(DynamicDetached.class, "kind", DynamicDetached::kind)), arguments -> new DynamicDetached((String) arguments[0]));
        }
    }
}
}
