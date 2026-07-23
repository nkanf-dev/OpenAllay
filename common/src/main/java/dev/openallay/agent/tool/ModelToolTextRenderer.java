package dev.openallay.agent.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

/**
 * Renders canonical normalized Tool JSON as compact, CLI-like text for model consumption.
 *
 * <p>Normalized JSON remains the internal source of truth for validation, traces, and UI. This
 * projection removes JSON punctuation and quoting without dropping named fields.
 */
final class ModelToolTextRenderer {
    private ModelToolTextRenderer() {}

    static String render(JsonObject normalized) {
        JsonElement custom = normalized.get("modelText");
        if (custom != null && custom.isJsonPrimitive() && custom.getAsJsonPrimitive().isString()) {
            return custom.getAsString();
        }
        String status = string(normalized.get("status"));
        if ("failure".equals(status)) {
            return "status: failure\ncode: " + string(normalized.get("code"))
                    + "\nmessage: " + string(normalized.get("message"));
        }
        StringBuilder output = new StringBuilder("status: success\nresult:\n");
        append(output, normalized.get("value"), 2, null);
        return output.toString();
    }

    private static void append(
            StringBuilder output, JsonElement value, int indent, String listPrefix) {
        String padding = " ".repeat(indent);
        if (value == null || value.isJsonNull() || value.isJsonPrimitive()) {
            output.append(padding);
            if (listPrefix != null) {
                output.append(listPrefix);
            }
            appendScalar(output, value, indent + (listPrefix == null ? 0 : 2));
            return;
        }
        if (value.isJsonArray()) {
            if (value.getAsJsonArray().isEmpty()) {
                output.append(padding)
                        .append(listPrefix == null ? "" : listPrefix)
                        .append("(empty)");
                return;
            }
            boolean first = true;
            for (JsonElement element : value.getAsJsonArray()) {
                if (!first) {
                    output.append('\n');
                }
                append(output, element, indent, "- ");
                first = false;
            }
            return;
        }

        int childIndent = listPrefix == null ? indent : indent + 2;
        if (listPrefix != null) {
            output.append(padding).append(listPrefix);
        }
        boolean first = true;
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
            if (!first) {
                output.append('\n');
            }
            output.append(" ".repeat(first && listPrefix == null ? indent : childIndent))
                    .append(entry.getKey())
                    .append(':');
            JsonElement child = entry.getValue();
            if (child == null || child.isJsonNull() || child.isJsonPrimitive()) {
                String scalar = string(child);
                if (scalar.contains("\n")) {
                    appendMultiline(output, scalar, childIndent + 2);
                } else {
                    output.append(' ').append(scalar);
                }
            } else {
                output.append('\n');
                append(output, child, childIndent + 2, null);
            }
            first = false;
        }
    }

    private static void appendScalar(StringBuilder output, JsonElement value, int indent) {
        String scalar = string(value);
        if (!scalar.contains("\n")) {
            output.append(scalar);
            return;
        }
        appendMultiline(output, scalar, indent);
    }

    private static void appendMultiline(StringBuilder output, String scalar, int indent) {
        String[] lines = scalar.split("\\R", -1);
        output.append('\n');
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                output.append('\n');
            }
            output.append(" ".repeat(indent)).append(lines[index]);
        }
    }

    private static String string(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "null";
        }
        return value.isJsonPrimitive()
                ? value.getAsJsonPrimitive().getAsString()
                : value.toString();
    }
}
