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
        var view = JsonResultProjection.project(value, handle, suffix, maximumUtf8Bytes);
        return new Presentation(handle, view.type(), view.cardinality(), view.fields(), view.preview(),
                view.modelText(), JavascriptResultViewRegistry.classify(value, shape), view.complete(),
                view.omittedRows(), view.omittedFields(), view.serializedBytes());
    }

    public Presentation presentChosen(String handle, JsonElement value, JsonElement schemaSource,
            dev.openallay.tool.ModelResultView view, JavascriptResultShape shape, String suffix) {
        var projected = JsonResultProjection.project(value, view, suffix, MODEL_TEXT_BYTE_BUDGET,
                schemaSource);
        return new Presentation(handle, projected.type(), projected.cardinality(), projected.fields(),
                projected.preview(), projected.modelText(), JavascriptResultViewRegistry.classify(schemaSource, shape),
                projected.complete(), projected.omittedRows(), projected.omittedFields(), projected.serializedBytes());
    }

    public record Presentation(String handle, String type, long cardinality, List<String> fields,
            JsonElement preview, String modelText, JavascriptSemanticKind viewKind, boolean complete,
            int omittedRows, int omittedFields, long canonicalUtf8Bytes) {
        public Presentation {
            fields = List.copyOf(fields);
            preview = preview.deepCopy();
            java.util.Objects.requireNonNull(viewKind, "viewKind");
        }
        @Override public JsonElement preview() { return preview.deepCopy(); }
    }
}
