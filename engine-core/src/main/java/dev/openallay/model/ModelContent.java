package dev.openallay.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.model.image.ImageReference;
import java.util.Objects;

public interface ModelContent {
    /** Java8 closed-content admission, replacing the modern compiler's sealed boundary. */
    static void requireKnown(ModelContent content) {
        Objects.requireNonNull(content, "content");
        Class<?> type = content.getClass();
        if (type != Text.class && type != Image.class && type != Reasoning.class
                && type != ToolUse.class && type != ToolResult.class) {
            throw new IncompatibleClassChangeError("Unknown model content subtype");
        }
    }

    @dev.openallay.value.ValueType(Text.ValueSchemaProvider.class)
public static final class Text implements ModelContent {
    private final String text;
    public Text(String text) {

            Objects.requireNonNull(text, "text");

        this.text = text;
    }
    public String text() { return text; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Text)) return false;
        Text that = (Text) other;
        return java.util.Objects.equals(text, that.text);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(text);
        return hash;
    }
    @Override public String toString() { return "Text[text=" + text + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Text> schema() {
            return new dev.openallay.value.ValueSchema<>(Text.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Text>>asList(new dev.openallay.value.ValueSchema.Component<>(Text.class, "text", Text::text)), arguments -> new Text((String) arguments[0]));
        }
    }
}

    /** A managed image reference, never a path, URL or binary payload. */
    @dev.openallay.value.ValueType(Image.ValueSchemaProvider.class)
public static final class Image implements ModelContent {
    private final ImageReference reference;
    private final String originToolUseId;
    public Image(ImageReference reference, String originToolUseId) {

            Objects.requireNonNull(reference, "reference");
            if (originToolUseId != null && dev.openallay.util.Java8Strings.isBlank(originToolUseId)) {
                throw new IllegalArgumentException("Image tool origin must not be blank");
            }

        this.reference = reference;
        this.originToolUseId = originToolUseId;
    }
    public ImageReference reference() { return reference; }
    public String originToolUseId() { return originToolUseId; }
public Image(ImageReference reference) {
            this(reference, null);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Image)) return false;
        Image that = (Image) other;
        return java.util.Objects.equals(reference, that.reference) && java.util.Objects.equals(originToolUseId, that.originToolUseId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(reference);
        hash = 31 * hash + java.util.Objects.hashCode(originToolUseId);
        return hash;
    }
    @Override public String toString() { return "Image[reference=" + reference + ", originToolUseId=" + originToolUseId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Image> schema() {
            return new dev.openallay.value.ValueSchema<>(Image.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Image>>asList(new dev.openallay.value.ValueSchema.Component<>(Image.class, "reference", Image::reference), new dev.openallay.value.ValueSchema.Component<>(Image.class, "originToolUseId", Image::originToolUseId)), arguments -> new Image((ImageReference) arguments[0], (String) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(Reasoning.ValueSchemaProvider.class)
public static final class Reasoning implements ModelContent {
    private final String text;
    private final String signature;
    public Reasoning(String text, String signature) {

            Objects.requireNonNull(text, "text");

        this.text = text;
        this.signature = signature;
    }
    public String text() { return text; }
    public String signature() { return signature; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Reasoning)) return false;
        Reasoning that = (Reasoning) other;
        return java.util.Objects.equals(text, that.text) && java.util.Objects.equals(signature, that.signature);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(signature);
        return hash;
    }
    @Override public String toString() { return "Reasoning[text=" + text + ", signature=" + signature + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Reasoning> schema() {
            return new dev.openallay.value.ValueSchema<>(Reasoning.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Reasoning>>asList(new dev.openallay.value.ValueSchema.Component<>(Reasoning.class, "text", Reasoning::text), new dev.openallay.value.ValueSchema.Component<>(Reasoning.class, "signature", Reasoning::signature)), arguments -> new Reasoning((String) arguments[0], (String) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(ToolUse.ValueSchemaProvider.class)
public static final class ToolUse implements ModelContent {
    private final String id;
    private final String name;
    private final JsonObject input;
    public ToolUse(String id, String name, JsonObject input) {

            if (id == null || dev.openallay.util.Java8Strings.isBlank(id) || name == null || dev.openallay.util.Java8Strings.isBlank(name)) {
                throw new IllegalArgumentException("Tool-use id and name are required");
            }
            input = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(input, "input"));

        this.id = id;
        this.name = name;
        this.input = input;
    }
    public String id() { return id; }
    public String name() { return name; }

        public JsonObject input() {
            return dev.openallay.json.JsonTrees.copy(input);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ToolUse)) return false;
        ToolUse that = (ToolUse) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(input, that.input);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(input);
        return hash;
    }
    @Override public String toString() { return "ToolUse[id=" + id + ", name=" + name + ", input=" + input + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ToolUse> schema() {
            return new dev.openallay.value.ValueSchema<>(ToolUse.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ToolUse>>asList(new dev.openallay.value.ValueSchema.Component<>(ToolUse.class, "id", ToolUse::id), new dev.openallay.value.ValueSchema.Component<>(ToolUse.class, "name", ToolUse::name), new dev.openallay.value.ValueSchema.Component<>(ToolUse.class, "input", ToolUse::input)), arguments -> new ToolUse((String) arguments[0], (String) arguments[1], (JsonObject) arguments[2]));
        }
    }
}

    @dev.openallay.value.ValueType(ToolResult.ValueSchemaProvider.class)
public static final class ToolResult implements ModelContent {
    private final String toolUseId;
    private final JsonElement value;
    private final boolean error;
    private final java.util.List<ImageReference> images;
    public ToolResult(String toolUseId, JsonElement value, boolean error, java.util.List<ImageReference> images) {

            if (toolUseId == null || dev.openallay.util.Java8Strings.isBlank(toolUseId)) {
                throw new IllegalArgumentException("Tool result requires toolUseId");
            }
            value = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(value, "value"));
            images = dev.openallay.util.Java8Collections.listCopyOf(images);
            dev.openallay.model.image.ModelImages.unique(images);
            if (error && !images.isEmpty()) {
                throw new IllegalArgumentException("Failed tool results cannot publish images");
            }

        this.toolUseId = toolUseId;
        this.value = value;
        this.error = error;
        this.images = images;
    }
    public String toolUseId() { return toolUseId; }
    public boolean error() { return error; }
    public java.util.List<ImageReference> images() { return images; }
public ToolResult(String toolUseId, JsonElement value, boolean error) {
            this(toolUseId, value, error, dev.openallay.util.Java8Collections.listOf());
        }

        public JsonElement value() {
            return dev.openallay.json.JsonTrees.copy(value);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ToolResult)) return false;
        ToolResult that = (ToolResult) other;
        return java.util.Objects.equals(toolUseId, that.toolUseId) && java.util.Objects.equals(value, that.value) && error == that.error && java.util.Objects.equals(images, that.images);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(toolUseId);
        hash = 31 * hash + java.util.Objects.hashCode(value);
        hash = 31 * hash + Boolean.hashCode(error);
        hash = 31 * hash + java.util.Objects.hashCode(images);
        return hash;
    }
    @Override public String toString() { return "ToolResult[toolUseId=" + toolUseId + ", value=" + value + ", error=" + error + ", images=" + images + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ToolResult> schema() {
            return new dev.openallay.value.ValueSchema<>(ToolResult.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ToolResult>>asList(new dev.openallay.value.ValueSchema.Component<>(ToolResult.class, "toolUseId", ToolResult::toolUseId), new dev.openallay.value.ValueSchema.Component<>(ToolResult.class, "value", ToolResult::value), new dev.openallay.value.ValueSchema.Component<>(ToolResult.class, "error", ToolResult::error), new dev.openallay.value.ValueSchema.Component<>(ToolResult.class, "images", ToolResult::images)), arguments -> new ToolResult((String) arguments[0], (JsonElement) arguments[1], (Boolean) arguments[2], (java.util.List) arguments[3]));
        }
    }
}
}
