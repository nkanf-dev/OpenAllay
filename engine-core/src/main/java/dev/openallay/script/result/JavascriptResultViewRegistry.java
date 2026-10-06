package dev.openallay.script.result;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.Set;

/**
 * Resolves a complete canonical result and its Java-owned shape into one closed player view.
 *
 * <p>Recipe and item views require trusted host metadata. Ordinary structural views are inferred
 * from canonical Gson and therefore cannot acquire domain authority.
 */
public final class JavascriptResultViewRegistry {
    private JavascriptResultViewRegistry() {}

    public static JavascriptSemanticKind classify(
            JsonElement canonical, JavascriptResultShape shape) {
        java.util.Objects.requireNonNull(shape, "shape");
        if (shape.trusted()
                && (shape.kind() == JavascriptSemanticKind.RECIPE
                        || shape.kind() == JavascriptSemanticKind.ITEM)) {
            return shape.kind();
        }
        if (canonical == null || canonical.isJsonNull() || canonical.isJsonPrimitive()) {
            return JavascriptSemanticKind.SCALAR;
        }
        if (canonical.isJsonObject()) {
            return JavascriptSemanticKind.KEY_VALUE;
        }
        JsonArray rows = canonical.getAsJsonArray();
        if ((rows.size() == 0)) {
            return JavascriptSemanticKind.GENERIC;
        }
        Set<String> fields = null;
        for (JsonElement row : rows) {
            if (!row.isJsonObject()) {
                return JavascriptSemanticKind.GENERIC;
            }
            Set<String> rowFields = Set.copyOf(dev.openallay.json.JsonTrees.keys(row.getAsJsonObject()));
            if (fields == null) {
                fields = rowFields;
            } else if (!fields.equals(rowFields)) {
                return JavascriptSemanticKind.GENERIC;
            }
        }
        return fields == null || fields.isEmpty()
                ? JavascriptSemanticKind.GENERIC
                : JavascriptSemanticKind.TABLE;
    }
}
