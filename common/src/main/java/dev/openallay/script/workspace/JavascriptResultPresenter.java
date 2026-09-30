package dev.openallay.script.workspace;

import com.google.gson.JsonElement;
import dev.openallay.script.result.JavascriptResultShape;
import dev.openallay.script.result.JavascriptResultViewRegistry;
import dev.openallay.script.result.JavascriptSemanticKind;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Compact model projection; canonical JSON remains in the workspace. */
public final class JavascriptResultPresenter {
    static final int MODEL_TEXT_TOKEN_BUDGET = 8_192;
    private static final int MINIMUM_PREVIEW_BUDGET = 256;

    public Presentation present(String handle, JsonElement value) {
        return present(
                handle,
                value,
                JavascriptResultShape.ordinary(JavascriptSemanticKind.GENERIC),
                "");
    }

    public Presentation present(String handle, JsonElement value, String suffix) {
        return present(
                handle,
                value,
                JavascriptResultShape.ordinary(JavascriptSemanticKind.GENERIC),
                suffix);
    }

    public Presentation presentUnrestricted(String handle, JsonElement value, JavascriptResultShape shape, String suffix) {
        String type = type(value);
        long cardinality = cardinality(value);
        List<String> fields = fields(value);
        String text = metadata(handle, type, cardinality, fields) + "scope: complete\npreview:\n" + value;
        if (suffix != null && !suffix.isBlank()) text += "\n" + suffix.strip();
        return new Presentation(handle, type, cardinality, fields, value.deepCopy(), text,
                JavascriptResultViewRegistry.classify(value, shape), true, 0, 0);
    }

    public Presentation present(
            String handle,
            JsonElement value,
            JavascriptResultShape shape,
            String suffix) {
        suffix = suffix == null ? "" : suffix.strip();
        JavascriptSemanticKind viewKind = JavascriptResultViewRegistry.classify(value, shape);
        String type = type(value);
        long cardinality = cardinality(value);
        List<String> fields = fields(value);
        int projectionBudget = Math.max(
                MINIMUM_PREVIEW_BUDGET,
                MODEL_TEXT_TOKEN_BUDGET
                        - utf8Bytes(metadata(handle, type, cardinality, fields))
                        - utf8Bytes(suffix)
                        - 512);
        while (projectionBudget >= MINIMUM_PREVIEW_BUDGET) {
            Preview preview = preview(value, projectionBudget);
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
            if (!suffix.isEmpty()) {
                rendered += "\n" + suffix;
            }
            int renderedTokens = utf8Bytes(rendered);
            if (renderedTokens <= MODEL_TEXT_TOKEN_BUDGET) {
                return new Presentation(
                        handle,
                        type,
                        cardinality,
                        fields,
                        preview.value(),
                        rendered,
                        viewKind,
                        complete,
                        preview.omittedRows(),
                        preview.omittedFields());
            }
            int excess = renderedTokens - MODEL_TEXT_TOKEN_BUDGET;
            int next = projectionBudget - Math.max(256, excess);
            if (next >= projectionBudget) {
                break;
            }
            projectionBudget = next;
        }
        return fallback(handle, value, type, cardinality, fields, viewKind, suffix);
    }

    private static Preview preview(JsonElement value, int tokenBudget) {
        PreviewBudget budget = new PreviewBudget(tokenBudget);
        JsonElement projected = project(value, 0, budget);
        return new Preview(
                projected,
                budget.truncated,
                budget.omittedRows,
                budget.omittedFields);
    }

    private static JsonElement project(JsonElement value, int depth, PreviewBudget budget) {
        if (!budget.take(1 + depth * 2)) {
            return new com.google.gson.JsonPrimitive("…");
        }
        if (value == null || value.isJsonNull()) {
            return com.google.gson.JsonNull.INSTANCE;
        }
        if (value.isJsonPrimitive()) {
            if (!value.getAsJsonPrimitive().isString()) {
                budget.take(utf8Bytes(value.getAsString()));
                return value.deepCopy();
            }
            String text = value.getAsString();
            int textTokens = utf8Bytes(text);
            if (budget.canTake(textTokens)) {
                budget.take(textTokens);
                return new com.google.gson.JsonPrimitive(text);
            }
            return new com.google.gson.JsonPrimitive(
                    clipUtf8(text, Math.max(0, budget.takeRemaining())));
        }
        if (value.isJsonArray()) {
            com.google.gson.JsonArray result = new com.google.gson.JsonArray();
            for (int index = 0; index < value.getAsJsonArray().size(); index++) {
                if (!budget.canContinue()) {
                    budget.omittedRows += value.getAsJsonArray().size() - index;
                    budget.truncated = true;
                    break;
                }
                result.add(project(value.getAsJsonArray().get(index), depth + 1, budget));
                if (!budget.canContinue() && index + 1 < value.getAsJsonArray().size()) {
                    budget.omittedRows += value.getAsJsonArray().size() - index - 1;
                    budget.truncated = true;
                    break;
                }
            }
            return result;
        }
        com.google.gson.JsonObject result = new com.google.gson.JsonObject();
        int included = 0;
        for (Map.Entry<String, JsonElement> field : value.getAsJsonObject().entrySet()) {
            if (!budget.take(utf8Bytes(field.getKey()) + 2)) {
                budget.truncated = true;
                budget.omittedFields += value.getAsJsonObject().size() - included;
                break;
            }
            result.add(field.getKey(), project(field.getValue(), depth + 1, budget));
            included++;
        }
        return result;
    }

    private static Presentation fallback(
            String handle,
            JsonElement value,
            String type,
            long cardinality,
            List<String> fields,
            JavascriptSemanticKind viewKind,
            String suffix) {
        JsonElement preview = value == null || value.isJsonNull()
                ? com.google.gson.JsonNull.INSTANCE
                : new com.google.gson.JsonPrimitive("…");
        String text = metadata(handle, type, cardinality, fields)
                + "scope: preview\n"
                + "preview: (omitted by model output budget)\n"
                + "next: use workspace.open(\""
                + handle
                + "\") and filter, aggregate, or project only the required fields";
        if (!suffix.isEmpty()) {
            text += "\n" + suffix;
        }
        text = clipUtf8(text, MODEL_TEXT_TOKEN_BUDGET);
        return new Presentation(
                handle,
                type,
                cardinality,
                fields,
                preview,
                text,
                viewKind,
                false,
                value != null && value.isJsonArray() ? value.getAsJsonArray().size() : 0,
                value != null && value.isJsonObject() ? value.getAsJsonObject().size() : 0);
    }

    private static String metadata(
            String handle, String type, long cardinality, List<String> fields) {
        StringBuilder result = new StringBuilder();
        result.append("result: ").append(handle).append('\n');
        result.append("type: ").append(type).append('\n');
        result.append("cardinality: ").append(cardinality).append('\n');
        if (!fields.isEmpty()) {
            result.append("fields: ").append(String.join(", ", fields)).append('\n');
        }
        return result.toString();
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
        result.append(metadata(handle, type, cardinality, fields));
        result.append("scope: ").append(complete ? "complete" : "preview").append('\n');
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

    private static String clipUtf8(String value, int maximumBytes) {
        if (maximumBytes <= 0) {
            return "";
        }
        if (utf8Bytes(value) <= maximumBytes) {
            return value;
        }
        int low = 0;
        int high = value.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            int safe = middle;
            if (safe > 0
                    && safe < value.length()
                    && Character.isHighSurrogate(value.charAt(safe - 1))
                    && Character.isLowSurrogate(value.charAt(safe))) {
                safe--;
            }
            if (utf8Bytes(value.substring(0, safe) + "…") <= maximumBytes) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        int end = Math.min(low, value.length());
        if (end > 0
                && end < value.length()
                && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end) + "…";
    }

    private static int utf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    public record Presentation(
            String handle,
            String type,
            long cardinality,
            List<String> fields,
            JsonElement preview,
            String modelText,
            JavascriptSemanticKind viewKind,
            boolean complete,
            int omittedRows,
            int omittedFields) {
        public Presentation {
            fields = List.copyOf(fields);
            preview = preview.deepCopy();
            java.util.Objects.requireNonNull(viewKind, "viewKind");
        }

        @Override
        public JsonElement preview() {
            return preview.deepCopy();
        }
    }

    private record Preview(
            JsonElement value, boolean truncated, int omittedRows, int omittedFields) {}

    private static final class PreviewBudget {
        private int remainingTokens;
        private boolean truncated;
        private int omittedRows;
        private int omittedFields;

        private PreviewBudget(int remainingTokens) {
            this.remainingTokens = remainingTokens;
        }

        private boolean take(int tokens) {
            if (tokens <= remainingTokens) {
                remainingTokens -= tokens;
                return true;
            }
            remainingTokens = 0;
            truncated = true;
            return false;
        }

        private boolean canTake(int tokens) {
            return tokens <= remainingTokens;
        }

        private int takeRemaining() {
            int result = remainingTokens;
            remainingTokens = 0;
            truncated = true;
            return result;
        }

        private boolean canContinue() {
            if (remainingTokens <= 0) {
                truncated = true;
                return false;
            }
            return true;
        }
    }
}
