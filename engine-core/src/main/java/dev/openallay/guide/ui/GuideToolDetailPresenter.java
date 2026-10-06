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
            return switch (name) {
                case "run_javascript" -> javascriptCards(value);
                default -> new Projection(List.of(), "generic tool projection");
            };
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
            return switch (viewKind) {
                case "RECIPE" -> javascriptRecipeCards(value, preview);
                case "ITEM" -> javascriptItemCards(value, preview);
                case "TABLE" -> javascriptTableCard(value, preview);
                case "KEY_VALUE" -> javascriptKeyValueCard(value, preview);
                case "SCALAR" -> new Projection(List.of(new GuideDetailCard.Text(
                        "screen.openallay.detail.analysis",
                        List.of(displayValue(preview)))), "");
                default -> javascriptFallbackCard(value, preview);
            };
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
        return switch (toolName(toolId)) {
            case "run_javascript" -> "screen.openallay.tool.run_javascript";
            case "load_skill" -> "screen.openallay.tool.load_skill";
            default -> "screen.openallay.tool.result";
        };
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

    private record Projection(List<GuideDetailCard> cards, String diagnostic) {
        private Projection {
            cards = List.copyOf(cards);
            diagnostic = diagnostic == null ? "" : diagnostic;
        }
    }

}
