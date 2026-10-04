package dev.openallay.agent.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.openallay.tool.result.JsonResultProjection;

/** Adapts an authoritative tool result to a caller-owned model transport budget. */
public final class ModelToolResultProjection {
    private ModelToolResultProjection() {}

    public static JsonElement project(String toolId, JsonObject normalized, int maximumUtf8Bytes) {
        return prepare(normalized).project(maximumUtf8Bytes);
    }

    /** The normalized tree must be privately owned and never mutated after this call. */
    static Prepared prepare(JsonObject normalized) {
        JsonElement value = normalized.get("value");
        value = value == null ? JsonNull.INSTANCE : value;
        JsonElement custom = normalized.get("modelText");
        String original = custom != null && custom.isJsonPrimitive()
                && custom.getAsJsonPrimitive().isString() ? custom.getAsString() : null;
        boolean failure = "failure".equals(text(normalized.get("status")));
        boolean instructions = outputTypeImplements(normalized, dev.openallay.tool.InstructionModelFacingToolOutput.class);
        if (failure) original = ModelToolTextRenderer.render(normalized);
        dev.openallay.tool.ModelResultView descriptor = null;
        if (!failure && outputTypeImplements(normalized, dev.openallay.tool.WorkspaceModelFacingToolOutput.class)
                && value.isJsonObject() && value.getAsJsonObject().has("modelView")) {
            JsonObject workspace = value.getAsJsonObject().getAsJsonObject("modelView");
            descriptor = new dev.openallay.tool.ModelResultView(text(workspace.get("handle")),
                    text(workspace.get("type")), workspace.get("cardinality").getAsLong(),
                    workspace.get("canonicalUtf8Bytes").getAsLong(), workspace.get("complete").getAsBoolean(),
                    text(workspace.get("lifetime")), workspace.has("inputCoverage")
                            ? text(workspace.get("inputCoverage")) : "");
            value = value.getAsJsonObject().get("preview");
            original = null;
        } else if (!failure && original == null) {
            descriptor = new dev.openallay.tool.ModelResultView("", JsonResultProjection.type(value),
                    JsonResultProjection.cardinality(value), JsonResultProjection.serializedBytes(value),
                    true, "original tool result");
        }
        String small = null;
        if (descriptor != null && descriptor.handle().isEmpty()
                && descriptor.canonicalUtf8Bytes() <= JsonResultProjection.DEFAULT_MAXIMUM_UTF8_BYTES)
            small = ModelToolTextRenderer.render(normalized);
        return new Prepared(value, descriptor, original, small, failure || instructions);
    }

    static final class Prepared {
        private final JsonElement value;
        private final dev.openallay.tool.ModelResultView descriptor;
        private final String original;
        private final int originalBytes;
        private final String small;
        private final int smallBytes;
        private final boolean protectedText;

        private Prepared(JsonElement value, dev.openallay.tool.ModelResultView descriptor,
                String original, String small, boolean protectedText) {
            this.value = value == null ? JsonNull.INSTANCE : value;
            this.descriptor = descriptor;
            this.original = original;
            this.originalBytes = original == null ? 0 : JsonResultProjection.encodedBytes(original);
            this.small = small;
            this.smallBytes = small == null ? 0 : JsonResultProjection.encodedBytes(small);
            this.protectedText = protectedText;
        }

        int projectionSizeUpperBound() {
            if (descriptor != null) return JsonResultProjection.projectionSizeUpperBound(descriptor);
            return Math.max(JsonResultProjection.MINIMUM_PROJECTION_BYTES, Math.max(originalBytes, smallBytes));
        }

        JsonElement project(int maximumUtf8Bytes) {
            // Source instruction ranges and exact failure messages belong to their respective
            // context owners. Never silently clip a fingerprinted range or failure evidence.
            if (original != null && (protectedText || originalBytes <= maximumUtf8Bytes))
                return new JsonPrimitive(original);
            if (small != null && smallBytes <= maximumUtf8Bytes) return new JsonPrimitive(small);
            if (descriptor != null)
                return new JsonPrimitive(JsonResultProjection.project(value, descriptor, descriptor.inputCoverage(),
                        maximumUtf8Bytes).modelText());
            return boundedValue(new JsonPrimitive(original), maximumUtf8Bytes);
        }
    }

    /** Reduces an already-recorded successful model value without inventing a live workspace. */
    public static JsonElement boundedValue(JsonElement value, int maximumUtf8Bytes) {
        value = value == null ? JsonNull.INSTANCE : value;
        boolean small = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString().length() < maximumUtf8Bytes
                        && JsonResultProjection.encodedBytes(value.getAsString()) <= maximumUtf8Bytes
                : JsonResultProjection.serializedBytes(value) <= maximumUtf8Bytes;
        if (small) return value.deepCopy();
        if (value.isJsonObject() && value.getAsJsonObject().has("status"))
            return project("", value.getAsJsonObject(), maximumUtf8Bytes);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            if (maximumUtf8Bytes < JsonResultProjection.MINIMUM_PROJECTION_BYTES)
                throw new IllegalArgumentException("Model result budget is too small for a structural receipt");
            String original = value.getAsString();
            String receipt = "scope: archived preview\nsize: "
                    + originalTextBytes(original)
                    + " UTF-8 byte(s) of original model text\n"
                    + "omitted: remainder stays in the stored transcript; no live workspace is implied";
            StringBuilder excerpt = new StringBuilder();
            int offset = 0;
            while (offset < original.length()) {
                int newline = original.indexOf('\n', offset);
                int end = newline < 0 ? original.length() : newline + 1;
                // A huge opaque line has no safe structural cut. Never show a half number or
                // a half field as if it were an observed value.
                if (end - offset > maximumUtf8Bytes) break;
                String line = original.substring(offset, end);
                String candidate = receipt + "\nverbatim complete-line excerpt (past transcript only):\n" + excerpt + line;
                if (JsonResultProjection.encodedBytes(candidate) > maximumUtf8Bytes) break;
                excerpt.append(line);
                offset = end;
            }
            return new JsonPrimitive(excerpt.isEmpty() ? receipt
                    : receipt + "\nverbatim complete-line excerpt (past transcript only):\n" + excerpt);
        }
        return new JsonPrimitive(JsonResultProjection.project(value, (String) null,
                "original value remains in the stored transcript; no live workspace is implied", maximumUtf8Bytes).modelText());
    }

    private static final java.util.Map<String, Long> ORIGINAL_TEXT_BYTES =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static long originalTextBytes(String value) {
        return ORIGINAL_TEXT_BYTES.computeIfAbsent(value,
                text -> (long) text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    private static boolean outputTypeImplements(JsonObject normalized, Class<?> contract) {
        JsonElement outputType = normalized.get("outputType");
        if (outputType == null || !outputType.isJsonPrimitive()
                || !outputType.getAsJsonPrimitive().isString()) return false;
        try {
            Class<?> type = Class.forName(outputType.getAsString(), false,
                    ModelToolResultProjection.class.getClassLoader());
            return contract.isAssignableFrom(type);
        } catch (ClassNotFoundException | LinkageError invalid) {
            return false;
        }
    }

    private static String text(JsonElement value) {
        return value == null || value.isJsonNull() ? "unknown" : value.getAsString();
    }
}
