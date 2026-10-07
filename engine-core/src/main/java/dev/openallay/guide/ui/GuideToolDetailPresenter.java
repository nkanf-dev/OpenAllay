package dev.openallay.guide.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.guide.GuideToolActivity;
import dev.openallay.guide.GuideToolInvocationView;
import dev.openallay.guide.GuideToolMessage;
import dev.openallay.guide.GuideToolPresentation;
import dev.openallay.guide.GuideToolStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Converts normalized tool activity into player cards, with opt-in technical detail. */
public final class GuideToolDetailPresenter {
    private GuideToolDetailPresenter() {}

    public static GuideToolDetailView project(GuideToolActivity activity, boolean debugMode) {
        JsonObject normalized = activity.normalized();
        Projection projection = projectCards(activity.toolId(), normalized);
        List<GuideToolMessage> narration = GuideToolPresentation.messages(
                activity.toolId(), normalized, activity.status());
        Optional<GuideToolDetailView.Debug> debug = debugMode
                ? Optional.of(new GuideToolDetailView.Debug(
                        activity.invocationId(),
                        activity.toolId(),
                        activity.invocationArguments(),
                        normalized,
                        normalized == null && activity.status() == GuideToolStatus.RUNNING
                                ? "tool is still running" : projection.diagnostic()))
                : Optional.empty();
        return new GuideToolDetailView(
                titleKey(activity.toolId()),
                activity.status(),
                debugMode ? activity.invocation() : GuideToolInvocationView.none(),
                activity.intent(),
                projection.cards(),
                narration,
                debug,
                GuideToolDisplayStatus.from(activity.status(), false),
                normalized != null && "failure".equals(string(normalized, "status"))
                        ? Optional.of(new GuideToolDetailView.Failure(
                                string(normalized, "code"), string(normalized, "message")))
                        : Optional.empty());
    }

    private static Projection projectCards(String toolId, JsonObject normalized) {
        if (normalized == null) {
            return new Projection(List.of(), "restored result detail is unavailable");
        }
        if (!"success".equals(string(normalized, "status"))) {
            return new Projection(List.of(), "tool returned a normalized failure");
        }
        JsonObject value = object(normalized, "value");
        if (value == null) {
            return new Projection(List.of(), "value is missing");
        }
        String name = toolName(toolId);
        try {
            {
dev.openallay.guide.ui.GuideToolDetailPresenter.Projection $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((name)) {
case "run_javascript":
{
$oaSwitch0_exit_result = javascriptCards(value); break $oaSwitch0_exit;
}
default:
{
$oaSwitch0_exit_result = new Projection(List.of(), "generic tool projection"); break $oaSwitch0_exit;
}
}
}
return $oaSwitch0_exit_result;
}
        } catch (RuntimeException exception) {
            return new Projection(List.of(), "malformed semantic result");
        }
    }

    private static Projection javascriptCards(JsonObject value) {
        JsonElement preview = value.get("preview");
        if (preview == null || preview.isJsonNull()) {
            return new Projection(List.of(), "analysis preview is missing");
        }
        String viewKind = string(value, "viewKind");
        try {
            {
dev.openallay.guide.ui.GuideToolDetailPresenter.Projection $oaSwitch2_exit_result;
$oaSwitch2_exit: {
switch ((viewKind)) {
case "RECIPE":
{
$oaSwitch2_exit_result = javascriptRecipeCards(value, preview); break $oaSwitch2_exit;
}
case "ITEM":
{
$oaSwitch2_exit_result = javascriptItemCards(value, preview); break $oaSwitch2_exit;
}
case "TABLE":
{
$oaSwitch2_exit_result = javascriptTableCard(value, preview); break $oaSwitch2_exit;
}
case "KEY_VALUE":
{
$oaSwitch2_exit_result = javascriptKeyValueCard(value, preview); break $oaSwitch2_exit;
}
case "SCALAR":
{
$oaSwitch2_exit_result = new Projection(List.of(new GuideDetailCard.Text(
                        "screen.openallay.detail.analysis",
                        List.of(displayValue(preview)))), ""); break $oaSwitch2_exit;
}
default:
{
$oaSwitch2_exit_result = javascriptFallbackCard(value, preview); break $oaSwitch2_exit;
}
}
}
return $oaSwitch2_exit_result;
}
        } catch (RuntimeException malformed) {
            return javascriptFallbackCard(value, preview);
        }
    }

    private static Projection javascriptRecipeCards(JsonObject value, JsonElement preview) {
        JsonArray recipes = new JsonArray();
        if (preview.isJsonArray()) {
            preview.getAsJsonArray().forEach(recipes::add);
        } else if (preview.isJsonObject()) {
            recipes.add(preview);
        }
        List<GuideDetailCard> cards = GuideRecipePresenter
                .cards(recipes)
                .stream()
                .<GuideDetailCard>map(GuideDetailCard.Recipe::new)
                .toList();
        return cards.isEmpty()
                ? javascriptFallbackCard(value, preview)
                : new Projection(cards, "");
    }

    private static Projection javascriptItemCards(JsonObject value, JsonElement preview) {
        List<JsonElement> encoded = preview.isJsonArray()
                ? dev.openallay.json.JsonReaders.elements(preview.getAsJsonArray())
                : List.of(preview);
        List<GuideItemView> items = new ArrayList<>();
        for (JsonElement element : encoded) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String id = string(object, "itemId");
            if (id.isBlank()) {
                id = string(object, "id");
            }
            if (id.isBlank()) {
                continue;
            }
            String displayName = string(object, "displayName");
            long count = optionalPositiveLong(object.get("count"), 1);
            items.add(new GuideItemView(id, displayName, count));
        }
        return items.isEmpty()
                ? javascriptFallbackCard(value, preview)
                : new Projection(List.of(new GuideDetailCard.ItemGrid(
                        "screen.openallay.detail.analysis.items", items)), "");
    }

    private static Projection javascriptTableCard(JsonObject value, JsonElement preview) {
        if (!preview.isJsonArray() || (preview.getAsJsonArray().size() == 0)) {
            return javascriptFallbackCard(value, preview);
        }
        if (!preview.getAsJsonArray().get(0).isJsonObject()) {
            return javascriptFallbackCard(value, preview);
        }
        java.util.Set<String> previewFields =
                dev.openallay.json.JsonTrees.keys(preview.getAsJsonArray().get(0).getAsJsonObject());
        List<String> columns = new ArrayList<>();
        JsonElement fields = value.get("fields");
        if (fields != null && fields.isJsonArray()) {
            for (JsonElement field : fields.getAsJsonArray()) {
                if (field.isJsonPrimitive() && previewFields.contains(field.getAsString())) {
                    // Keep the canonical lookup key intact. Rendering wraps the visible header,
                    // while clipping here would make row.get(column) miss long original keys.
                    columns.add(field.getAsString());
                }
            }
        }
        for (String field : previewFields) {
            if (!columns.contains(field)) {
                columns.add(field);
            }
        }
        if (columns.isEmpty()) {
            return javascriptFallbackCard(value, preview);
        }
        List<List<String>> rows = new ArrayList<>();
        for (JsonElement element : preview.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                return javascriptFallbackCard(value, preview);
            }
            JsonObject row = element.getAsJsonObject();
            rows.add(columns.stream()
                    .map(column -> displayValue(row.get(column)))
                    .toList());
        }
        return new Projection(List.of(new GuideDetailCard.Table(
                "screen.openallay.detail.analysis.table",
                columns,
                rows,
                bool(value, "complete"),
                optionalNonnegativeInt(value, "omittedRows"),
                optionalNonnegativeInt(value, "omittedFields"))), "");
    }

    private static Projection javascriptKeyValueCard(JsonObject value, JsonElement preview) {
        if (!preview.isJsonObject()) {
            return javascriptFallbackCard(value, preview);
        }
        List<GuideDetailCard.DataCell> entries = preview.getAsJsonObject().entrySet().stream()
                .map(entry -> new GuideDetailCard.DataCell(
                        clip(entry.getKey(), 80), displayValue(entry.getValue())))
                .toList();
        return entries.isEmpty()
                ? javascriptFallbackCard(value, preview)
                : new Projection(List.of(new GuideDetailCard.KeyValue(
                        "screen.openallay.detail.analysis.fields",
                        entries,
                        bool(value, "complete"),
                        optionalNonnegativeInt(value, "omittedFields"))), "");
    }

    private static Projection javascriptFallbackCard(JsonObject value, JsonElement preview) {
        List<GuideDetailCard.DataRow> rows = new ArrayList<>();
        if (preview.isJsonArray()) {
            int index = 0;
            for (JsonElement element : preview.getAsJsonArray()) {
                rows.add(dataRow(element, Integer.toString(++index)));
            }
        } else {
            rows.add(dataRow(preview, "value"));
        }
        if (rows.isEmpty()) {
            rows.add(new GuideDetailCard.DataRow(List.of(
                    new GuideDetailCard.DataCell("result", "(empty)"))));
        }
        return new Projection(List.of(new GuideDetailCard.DataPreview(
                "screen.openallay.detail.analysis",
                requiredString(value, "resultType"),
                nonnegativeLong(value.get("cardinality")),
                rows,
                bool(value, "complete"),
                optionalNonnegativeInt(value, "omittedRows"),
                optionalNonnegativeInt(value, "omittedFields"))), "");
    }

    private static GuideDetailCard.DataRow dataRow(JsonElement value, String fallbackKey) {
        List<GuideDetailCard.DataCell> cells = new ArrayList<>();
        if (value != null && value.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                cells.add(new GuideDetailCard.DataCell(
                        clip(entry.getKey(), 80), displayValue(entry.getValue())));
            }
        } else {
            cells.add(new GuideDetailCard.DataCell(fallbackKey, displayValue(value)));
        }
        if (cells.isEmpty()) {
            cells.add(new GuideDetailCard.DataCell(fallbackKey, "(empty)"));
        }
        return new GuideDetailCard.DataRow(cells);
    }

    private static String displayValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "null";
        }
        if (value.isJsonPrimitive()) {
            return clip(value.getAsString().replaceAll("\\p{Cntrl}", " "), 260);
        }
        return clip(value.toString().replaceAll("\\p{Cntrl}", " "), 260);
    }

    private static String titleKey(String toolId) {
        {
java.lang.String $oaSwitch1_exit_result;
$oaSwitch1_exit: {
switch ((toolName(toolId))) {
case "run_javascript":
{
$oaSwitch1_exit_result = "screen.openallay.tool.run_javascript"; break $oaSwitch1_exit;
}
case "load_skill":
{
$oaSwitch1_exit_result = "screen.openallay.tool.load_skill"; break $oaSwitch1_exit;
}
default:
{
$oaSwitch1_exit_result = "screen.openallay.tool.result"; break $oaSwitch1_exit;
}
}
}
return $oaSwitch1_exit_result;
}
    }

    private static String toolName(String toolId) {
        int separator = toolId.indexOf(':');
        return separator < 0 ? toolId : toolId.substring(separator + 1);
    }

    private static JsonObject object(JsonObject value, String field) {
        return value != null && value.has(field) && value.get(field).isJsonObject()
                ? value.getAsJsonObject(field) : null;
    }

    private static String requiredString(JsonObject value, String field) {
        String result = string(value, field);
        if (result.isBlank()) throw new IllegalArgumentException(field + " is required");
        return result;
    }

    private static String string(JsonObject value, String field) {
        return value != null && value.has(field) && value.get(field).isJsonPrimitive()
                ? value.get(field).getAsString() : "";
    }

    private static boolean bool(JsonObject value, String field) {
        return value != null && value.has(field) && value.get(field).getAsBoolean();
    }

    private static long nonnegativeLong(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("count must be numeric");
        }
        long result = value.getAsLong();
        if (result < 0) throw new IllegalArgumentException("count must not be negative");
        return result;
    }

    private static long optionalPositiveLong(JsonElement value, long fallback) {
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        long result = value.getAsLong();
        return result > 0 ? result : fallback;
    }

    private static int optionalNonnegativeInt(JsonObject value, String field) {
        JsonElement element = value.get(field);
        if (element == null || !element.isJsonPrimitive()) {
            return 0;
        }
        int result = element.getAsInt();
        return Math.max(0, result);
    }

    private static String clip(String value, int maximum) {
        if (value.length() <= maximum) {
            return value;
        }
        int end = maximum;
        if (Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end) + "…";
    }

    @dev.openallay.value.ValueType(Projection.ValueSchemaProvider.class)
private static final class Projection {
    private final List<GuideDetailCard> cards;
    private final String diagnostic;
    private Projection(List<GuideDetailCard> cards, String diagnostic) {

            cards = List.copyOf(cards);
            diagnostic = diagnostic == null ? "" : diagnostic;

        this.cards = cards;
        this.diagnostic = diagnostic;
    }
    public List<GuideDetailCard> cards() { return cards; }
    public String diagnostic() { return diagnostic; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Projection)) return false;
        Projection that = (Projection) other;
        return java.util.Objects.equals(cards, that.cards) && java.util.Objects.equals(diagnostic, that.diagnostic);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(cards);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostic);
        return hash;
    }
    @Override public String toString() { return "Projection[cards=" + cards + ", diagnostic=" + diagnostic + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Projection> schema() {
            return new dev.openallay.value.ValueSchema<>(Projection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Projection>>asList(new dev.openallay.value.ValueSchema.Component<>(Projection.class, "cards", Projection::cards), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "diagnostic", Projection::diagnostic)), arguments -> new Projection((List) arguments[0], (String) arguments[1]));
        }
    }
}

}
