package dev.openallay.script.workspace;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Compact model projection; canonical JSON remains in the workspace. */
public final class JavascriptResultPresenter {
    private static final int PREVIEW_ROWS = 6;
    private static final int PREVIEW_FIELDS = 16;
    private static final int PREVIEW_DEPTH = 5;
    private static final int PREVIEW_SCALAR = 240;
    private static final int PREVIEW_NODES = 160;
    private static final int PREVIEW_TEXT = 1_600;

    public Presentation present(String handle, JsonElement value) {
        String type = type(value);
        long cardinality = cardinality(value);
        List<String> fields = fields(value);
        Preview preview = preview(value);
        boolean complete = !preview.truncated();
        String rendered = render(
                handle,
                type,
                cardinality,
                fields,
                preview.value(),
                preview.omittedRows(),
                preview.omittedFields(),
                complete);
        if (rendered.length() > PREVIEW_TEXT) {
            rendered = clip(rendered, PREVIEW_TEXT)
                            .replace("scope: complete", "scope: preview")
                    + "\nnext: preview shortened; reopen the handle and project fewer fields";
            complete = false;
        }
        return new Presentation(
                handle,
                type,
                cardinality,
                fields,
                preview.value(),
                rendered,
                complete,
                preview.omittedRows(),
                preview.omittedFields());
    }

    private static Preview preview(JsonElement value) {
        PreviewBudget budget = new PreviewBudget();
        JsonElement projected = project(value, 0, budget);
        return new Preview(
                projected,
                budget.truncated,
                budget.omittedRows,
                budget.omittedFields);
    }

    private static JsonElement project(JsonElement value, int depth, PreviewBudget budget) {
        if (!budget.takeNode() || depth > PREVIEW_DEPTH) {
            budget.truncated = true;
            return new com.google.gson.JsonPrimitive("…");
        }
        if (value == null || value.isJsonNull()) {
            return com.google.gson.JsonNull.INSTANCE;
        }
        if (value.isJsonPrimitive()) {
            if (!value.getAsJsonPrimitive().isString()) {
                return value.deepCopy();
            }
            String text = value.getAsString();
            String clipped = clip(text, PREVIEW_SCALAR);
            if (clipped.length() != text.length()) {
                budget.truncated = true;
            }
            return new com.google.gson.JsonPrimitive(clipped);
        }
        if (value.isJsonArray()) {
            com.google.gson.JsonArray result = new com.google.gson.JsonArray();
            int count = Math.min(PREVIEW_ROWS, value.getAsJsonArray().size());
            for (int index = 0; index < count; index++) {
                result.add(project(value.getAsJsonArray().get(index), depth + 1, budget));
            }
            int omitted = value.getAsJsonArray().size() - count;
            if (omitted > 0) {
                budget.truncated = true;
                budget.omittedRows += omitted;
            }
            return result;
        }
        com.google.gson.JsonObject result = new com.google.gson.JsonObject();
        int included = 0;
        for (Map.Entry<String, JsonElement> field : value.getAsJsonObject().entrySet()) {
            if (included >= PREVIEW_FIELDS) {
                budget.truncated = true;
                budget.omittedFields += value.getAsJsonObject().size() - included;
                break;
            }
            result.add(field.getKey(), project(field.getValue(), depth + 1, budget));
            included++;
        }
        return result;
    }

    private static String render(
            String handle,
            String type,
            long cardinality,
            List<String> fields,
            JsonElement preview,
            int omitted,
            int omittedFields,
            boolean complete) {
        StringBuilder result = new StringBuilder();
        result.append("result: ").append(handle).append('\n');
        result.append("type: ").append(type).append('\n');
        result.append("cardinality: ").append(cardinality).append('\n');
        result.append("scope: ").append(complete ? "complete" : "preview").append('\n');
        if (!fields.isEmpty()) {
            result.append("fields: ").append(String.join(", ", fields)).append('\n');
        }
        result.append("preview:\n");
        appendValue(result, preview, 0, null);
        if (omitted > 0) {
            result.append('\n')
                    .append("omitted: ")
                    .append(omitted)
                    .append(" row(s); use workspace.open(\"")
                    .append(handle)
                    .append("\") in a later script to filter, aggregate, or project them");
        } else if (omittedFields > 0 || !complete) {
            result.append('\n')
                    .append("omitted: ")
                    .append(omittedFields)
                    .append(" field(s) or nested value(s); use workspace.open(\"")
                    .append(handle)
                    .append("\") and project only the required fields");
        } else {
            result.append('\n')
                    .append("next: answer from this complete result; do not call run_javascript again only to verify it");
        }
        return result.toString();
    }

    /**
     * Produces compact CLI-like text for the model. Gson remains the canonical internal form; this
     * projection deliberately avoids JSON punctuation and quoting.
     */
    private static void appendValue(
            StringBuilder output, JsonElement value, int indent, String listPrefix) {
        String padding = " ".repeat(indent);
        if (value == null || value.isJsonNull() || value.isJsonPrimitive()) {
            output.append(padding);
            if (listPrefix != null) {
                output.append(listPrefix);
            }
            output.append(scalar(value));
            return;
        }
        if (value.isJsonArray()) {
            if (value.getAsJsonArray().isEmpty()) {
                output.append(padding).append(listPrefix == null ? "" : listPrefix).append("(empty)");
                return;
            }
            boolean first = true;
            for (JsonElement row : value.getAsJsonArray()) {
                if (!first) {
                    output.append('\n');
                }
                appendValue(output, row, indent, "- ");
                first = false;
            }
            return;
        }
        if (listPrefix != null) {
            output.append(padding).append(listPrefix);
        }
        boolean first = true;
        for (Map.Entry<String, JsonElement> field : value.getAsJsonObject().entrySet()) {
            if (!first) {
                output.append('\n');
            }
            output.append(listPrefix != null || !first ? " ".repeat(indent + (listPrefix == null ? 0 : 2)) : padding)
                    .append(field.getKey())
                    .append(':');
            JsonElement child = field.getValue();
            if (child == null || child.isJsonNull() || child.isJsonPrimitive()) {
                output.append(' ').append(scalar(child));
            } else {
                output.append('\n');
                appendValue(output, child, indent + (listPrefix == null ? 2 : 4), null);
            }
            first = false;
        }
    }

    private static String scalar(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "null";
        }
        if (value.getAsJsonPrimitive().isString()) {
            String string = value.getAsString();
            return string.contains("\n") ? string.replace("\n", "\\n") : string;
        }
        return value.getAsJsonPrimitive().getAsString();
    }

    private static String type(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "null";
        }
        if (value.isJsonArray()) {
            return "array";
        }
        if (value.isJsonObject()) {
            return "object";
        }
        if (value.getAsJsonPrimitive().isBoolean()) {
            return "boolean";
        }
        if (value.getAsJsonPrimitive().isNumber()) {
            return "number";
        }
        return "string";
    }

    private static long cardinality(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return 0;
        }
        if (value.isJsonArray()) {
            return value.getAsJsonArray().size();
        }
        if (value.isJsonObject()) {
            return value.getAsJsonObject().size();
        }
        return 1;
    }

    private static List<String> fields(JsonElement value) {
        TreeSet<String> fields = new TreeSet<>();
        if (value != null && value.isJsonObject()) {
            value.getAsJsonObject().keySet().stream().limit(32).forEach(fields::add);
        } else if (value != null && value.isJsonArray()) {
            for (JsonElement row : value.getAsJsonArray()) {
                if (row.isJsonObject()) {
                    fields.addAll(row.getAsJsonObject().keySet());
                }
                if (fields.size() >= 32) {
                    break;
                }
            }
        }
        return new ArrayList<>(fields);
    }

    private static String clip(String value, int maximum) {
        if (value.length() <= maximum) {
            return value;
        }
        int end = maximum;
        if (end > 0
                && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end) + "…";
    }

    public record Presentation(
            String handle,
            String type,
            long cardinality,
            List<String> fields,
            JsonElement preview,
            String modelText,
            boolean complete,
            int omittedRows,
            int omittedFields) {
        public Presentation {
            fields = List.copyOf(fields);
            preview = preview.deepCopy();
        }

        @Override
        public JsonElement preview() {
            return preview.deepCopy();
        }
    }

    private record Preview(
            JsonElement value, boolean truncated, int omittedRows, int omittedFields) {}

    private static final class PreviewBudget {
        private int remainingNodes = PREVIEW_NODES;
        private boolean truncated;
        private int omittedRows;
        private int omittedFields;

        private boolean takeNode() {
            if (remainingNodes <= 0) {
                truncated = true;
                return false;
            }
            remainingNodes--;
            return true;
        }
    }
}
