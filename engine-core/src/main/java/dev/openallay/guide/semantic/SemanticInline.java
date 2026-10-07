package dev.openallay.guide.semantic;

import java.util.List;

/** Closed inline subset; links, images, HTML, and actions are not representable. */
public interface SemanticInline {
    /** Runtime admission for the exact canonical closed variant family. */
    static SemanticInline requireKnown(SemanticInline value) {
        java.util.Objects.requireNonNull(value, "value");
        Class<?> type = value.getClass();
        if (type == dev.openallay.guide.semantic.SemanticInline.Text.class || type == dev.openallay.guide.semantic.SemanticInline.Emphasis.class || type == dev.openallay.guide.semantic.SemanticInline.Strong.class || type == dev.openallay.guide.semantic.SemanticInline.Code.class || type == dev.openallay.guide.semantic.SemanticInline.Break.class || type == dev.openallay.guide.semantic.SemanticInline.Reference.class) return value;
        throw new IncompatibleClassChangeError("Unknown SemanticInline subtype");
    }

    String nodeId();

    @dev.openallay.value.ValueType(Text.ValueSchemaProvider.class)
public static final class Text implements SemanticInline {
    private final String nodeId;
    private final String text;
    public Text(String nodeId, String text) {

            SemanticIds.require(nodeId);
            text = text == null ? "" : text;

        this.nodeId = nodeId;
        this.text = text;
    }
    public String nodeId() { return nodeId; }
    public String text() { return text; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Text)) return false;
        Text that = (Text) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(text, that.text);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        return hash;
    }
    @Override public String toString() { return "Text[nodeId=" + nodeId + ", text=" + text + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Text> schema() {
            return new dev.openallay.value.ValueSchema<>(Text.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Text>>asList(new dev.openallay.value.ValueSchema.Component<>(Text.class, "nodeId", Text::nodeId), new dev.openallay.value.ValueSchema.Component<>(Text.class, "text", Text::text)), arguments -> new Text((String) arguments[0], (String) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Emphasis.ValueSchemaProvider.class)
public static final class Emphasis implements SemanticInline {
    private final String nodeId;
    private final List<SemanticInline> children;
    public Emphasis(String nodeId, List<SemanticInline> children) {

            SemanticIds.require(nodeId);
            children = dev.openallay.util.Java8Collections.listCopyOf(children);
            children.forEach(SemanticInline::requireKnown);

        this.nodeId = nodeId;
        this.children = children;
    }
    public String nodeId() { return nodeId; }
    public List<SemanticInline> children() { return children; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Emphasis)) return false;
        Emphasis that = (Emphasis) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(children, that.children);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(children);
        return hash;
    }
    @Override public String toString() { return "Emphasis[nodeId=" + nodeId + ", children=" + children + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Emphasis> schema() {
            return new dev.openallay.value.ValueSchema<>(Emphasis.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Emphasis>>asList(new dev.openallay.value.ValueSchema.Component<>(Emphasis.class, "nodeId", Emphasis::nodeId), new dev.openallay.value.ValueSchema.Component<>(Emphasis.class, "children", Emphasis::children)), arguments -> new Emphasis((String) arguments[0], (List) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Strong.ValueSchemaProvider.class)
public static final class Strong implements SemanticInline {
    private final String nodeId;
    private final List<SemanticInline> children;
    public Strong(String nodeId, List<SemanticInline> children) {

            SemanticIds.require(nodeId);
            children = dev.openallay.util.Java8Collections.listCopyOf(children);
            children.forEach(SemanticInline::requireKnown);

        this.nodeId = nodeId;
        this.children = children;
    }
    public String nodeId() { return nodeId; }
    public List<SemanticInline> children() { return children; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Strong)) return false;
        Strong that = (Strong) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(children, that.children);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(children);
        return hash;
    }
    @Override public String toString() { return "Strong[nodeId=" + nodeId + ", children=" + children + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Strong> schema() {
            return new dev.openallay.value.ValueSchema<>(Strong.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Strong>>asList(new dev.openallay.value.ValueSchema.Component<>(Strong.class, "nodeId", Strong::nodeId), new dev.openallay.value.ValueSchema.Component<>(Strong.class, "children", Strong::children)), arguments -> new Strong((String) arguments[0], (List) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Code.ValueSchemaProvider.class)
public static final class Code implements SemanticInline {
    private final String nodeId;
    private final String text;
    public Code(String nodeId, String text) {

            SemanticIds.require(nodeId);
            text = text == null ? "" : text;

        this.nodeId = nodeId;
        this.text = text;
    }
    public String nodeId() { return nodeId; }
    public String text() { return text; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Code)) return false;
        Code that = (Code) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(text, that.text);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        return hash;
    }
    @Override public String toString() { return "Code[nodeId=" + nodeId + ", text=" + text + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Code> schema() {
            return new dev.openallay.value.ValueSchema<>(Code.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Code>>asList(new dev.openallay.value.ValueSchema.Component<>(Code.class, "nodeId", Code::nodeId), new dev.openallay.value.ValueSchema.Component<>(Code.class, "text", Code::text)), arguments -> new Code((String) arguments[0], (String) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Break.ValueSchemaProvider.class)
public static final class Break implements SemanticInline {
    private final String nodeId;
    private final boolean hard;
    public Break(String nodeId, boolean hard) {

            SemanticIds.require(nodeId);

        this.nodeId = nodeId;
        this.hard = hard;
    }
    public String nodeId() { return nodeId; }
    public boolean hard() { return hard; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Break)) return false;
        Break that = (Break) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && hard == that.hard;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + Boolean.hashCode(hard);
        return hash;
    }
    @Override public String toString() { return "Break[nodeId=" + nodeId + ", hard=" + hard + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Break> schema() {
            return new dev.openallay.value.ValueSchema<>(Break.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Break>>asList(new dev.openallay.value.ValueSchema.Component<>(Break.class, "nodeId", Break::nodeId), new dev.openallay.value.ValueSchema.Component<>(Break.class, "hard", Break::hard)), arguments -> new Break((String) arguments[0], (Boolean) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Reference.ValueSchemaProvider.class)
public static final class Reference implements SemanticInline {
    private final String nodeId;
    private final SemanticReference reference;
    public Reference(String nodeId, SemanticReference reference) {

            SemanticIds.require(nodeId);
            java.util.Objects.requireNonNull(reference, "reference");

        this.nodeId = nodeId;
        this.reference = reference;
    }
    public String nodeId() { return nodeId; }
    public SemanticReference reference() { return reference; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Reference)) return false;
        Reference that = (Reference) other;
        return java.util.Objects.equals(nodeId, that.nodeId) && java.util.Objects.equals(reference, that.reference);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(nodeId);
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        return hash;
    }
    @Override public String toString() { return "Reference[nodeId=" + nodeId + ", reference=" + reference + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Reference> schema() {
            return new dev.openallay.value.ValueSchema<>(Reference.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Reference>>asList(new dev.openallay.value.ValueSchema.Component<>(Reference.class, "nodeId", Reference::nodeId), new dev.openallay.value.ValueSchema.Component<>(Reference.class, "reference", Reference::reference)), arguments -> new Reference((String) arguments[0], (SemanticReference) arguments[1]));
        }
    }
}
}
