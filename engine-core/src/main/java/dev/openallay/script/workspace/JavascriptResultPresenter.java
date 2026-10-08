package dev.openallay.script.workspace;

import com.google.gson.JsonElement;
import dev.openallay.script.result.JavascriptResultShape;
import dev.openallay.script.result.JavascriptResultViewRegistry;
import dev.openallay.script.result.JavascriptSemanticKind;
import dev.openallay.tool.result.JsonResultProjection;
import java.util.List;

/** Model transport is bounded independently of execution permission and canonical workspace size. */
public final class JavascriptResultPresenter {
    static final int MODEL_TEXT_BYTE_BUDGET = JsonResultProjection.DEFAULT_MAXIMUM_UTF8_BYTES;

    public Presentation present(String handle, JsonElement value) {
        return present(handle, value, JavascriptResultShape.ordinary(JavascriptSemanticKind.GENERIC), "");
    }

    public Presentation present(String handle, JsonElement value, String suffix) {
        return present(handle, value, JavascriptResultShape.ordinary(JavascriptSemanticKind.GENERIC), suffix);
    }

    /** Unrestricted execution grants Java/JVM access, not an unbounded model dump. */
    public Presentation presentUnrestricted(String handle, JsonElement value, JavascriptResultShape shape, String suffix) {
        return present(handle, value, shape, suffix);
    }

    public Presentation present(String handle, JsonElement value, JavascriptResultShape shape, String suffix) {
        return present(handle, value, shape, suffix, MODEL_TEXT_BYTE_BUDGET);
    }

    /** The agent chooses this encoded byte envelope from the actual model request budget. */
    public Presentation present(String handle, JsonElement value, JavascriptResultShape shape,
            String suffix, int maximumUtf8Bytes) {
        dev.openallay.tool.result.JsonResultProjection.Projection view = JsonResultProjection.project(value, handle, suffix, maximumUtf8Bytes);
        return new Presentation(handle, view.type(), view.cardinality(), view.fields(), view.preview(),
                view.modelText(), JavascriptResultViewRegistry.classify(value, shape), view.complete(),
                view.omittedRows(), view.omittedFields(), view.serializedBytes());
    }

    public Presentation presentChosen(String handle, JsonElement value, JsonElement schemaSource,
            dev.openallay.tool.ModelResultView view, JavascriptResultShape shape, String suffix) {
        dev.openallay.tool.result.JsonResultProjection.Projection projected = JsonResultProjection.project(value, view, suffix, MODEL_TEXT_BYTE_BUDGET,
                schemaSource);
        return new Presentation(handle, projected.type(), projected.cardinality(), projected.fields(),
                projected.preview(), projected.modelText(), JavascriptResultViewRegistry.classify(schemaSource, shape),
                projected.complete(), projected.omittedRows(), projected.omittedFields(), projected.serializedBytes());
    }

    @dev.openallay.value.ValueType(Presentation.ValueSchemaProvider.class)
public static final class Presentation {
    private final String handle;
    private final String type;
    private final long cardinality;
    private final List<String> fields;
    private final JsonElement preview;
    private final String modelText;
    private final JavascriptSemanticKind viewKind;
    private final boolean complete;
    private final int omittedRows;
    private final int omittedFields;
    private final long canonicalUtf8Bytes;
    public Presentation(String handle, String type, long cardinality, List<String> fields, JsonElement preview, String modelText, JavascriptSemanticKind viewKind, boolean complete, int omittedRows, int omittedFields, long canonicalUtf8Bytes) {

            fields = dev.openallay.util.Java8Collections.listCopyOf(fields);
            preview = dev.openallay.json.JsonTrees.copy(preview);
            java.util.Objects.requireNonNull(viewKind, "viewKind");

        this.handle = handle;
        this.type = type;
        this.cardinality = cardinality;
        this.fields = fields;
        this.preview = preview;
        this.modelText = modelText;
        this.viewKind = viewKind;
        this.complete = complete;
        this.omittedRows = omittedRows;
        this.omittedFields = omittedFields;
        this.canonicalUtf8Bytes = canonicalUtf8Bytes;
    }
    public String handle() { return handle; }
    public String type() { return type; }
    public long cardinality() { return cardinality; }
    public List<String> fields() { return fields; }
    public String modelText() { return modelText; }
    public JavascriptSemanticKind viewKind() { return viewKind; }
    public boolean complete() { return complete; }
    public int omittedRows() { return omittedRows; }
    public int omittedFields() { return omittedFields; }
    public long canonicalUtf8Bytes() { return canonicalUtf8Bytes; }
 public JsonElement preview() { return dev.openallay.json.JsonTrees.copy(preview); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Presentation)) return false;
        Presentation that = (Presentation) other;
        return java.util.Objects.equals(handle, that.handle) && java.util.Objects.equals(type, that.type) && cardinality == that.cardinality && java.util.Objects.equals(fields, that.fields) && java.util.Objects.equals(preview, that.preview) && java.util.Objects.equals(modelText, that.modelText) && java.util.Objects.equals(viewKind, that.viewKind) && complete == that.complete && omittedRows == that.omittedRows && omittedFields == that.omittedFields && canonicalUtf8Bytes == that.canonicalUtf8Bytes;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(handle);
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + Long.hashCode(cardinality);
        hash = 31 * hash + java.util.Objects.hashCode(fields);
        hash = 31 * hash + java.util.Objects.hashCode(preview);
        hash = 31 * hash + java.util.Objects.hashCode(modelText);
        hash = 31 * hash + java.util.Objects.hashCode(viewKind);
        hash = 31 * hash + Boolean.hashCode(complete);
        hash = 31 * hash + Integer.hashCode(omittedRows);
        hash = 31 * hash + Integer.hashCode(omittedFields);
        hash = 31 * hash + Long.hashCode(canonicalUtf8Bytes);
        return hash;
    }
    @Override public String toString() { return "Presentation[handle=" + handle + ", type=" + type + ", cardinality=" + cardinality + ", fields=" + fields + ", preview=" + preview + ", modelText=" + modelText + ", viewKind=" + viewKind + ", complete=" + complete + ", omittedRows=" + omittedRows + ", omittedFields=" + omittedFields + ", canonicalUtf8Bytes=" + canonicalUtf8Bytes + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Presentation> schema() {
            return new dev.openallay.value.ValueSchema<>(Presentation.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Presentation>>asList(new dev.openallay.value.ValueSchema.Component<>(Presentation.class, "handle", Presentation::handle), new dev.openallay.value.ValueSchema.Component<>(Presentation.class, "type", Presentation::type), new dev.openallay.value.ValueSchema.Component<>(Presentation.class, "cardinality", Presentation::cardinality), new dev.openallay.value.ValueSchema.Component<>(Presentation.class, "fields", Presentation::fields), new dev.openallay.value.ValueSchema.Component<>(Presentation.class, "preview", Presentation::preview), new dev.openallay.value.ValueSchema.Component<>(Presentation.class, "modelText", Presentation::modelText), new dev.openallay.value.ValueSchema.Component<>(Presentation.class, "viewKind", Presentation::viewKind), new dev.openallay.value.ValueSchema.Component<>(Presentation.class, "complete", Presentation::complete), new dev.openallay.value.ValueSchema.Component<>(Presentation.class, "omittedRows", Presentation::omittedRows), new dev.openallay.value.ValueSchema.Component<>(Presentation.class, "omittedFields", Presentation::omittedFields), new dev.openallay.value.ValueSchema.Component<>(Presentation.class, "canonicalUtf8Bytes", Presentation::canonicalUtf8Bytes)), arguments -> new Presentation((String) arguments[0], (String) arguments[1], (Long) arguments[2], (List) arguments[3], (JsonElement) arguments[4], (String) arguments[5], (JavascriptSemanticKind) arguments[6], (Boolean) arguments[7], (Integer) arguments[8], (Integer) arguments[9], (Long) arguments[10]));
        }
    }
}
}
