package dev.openallay.model.http;

import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelFailure;
import dev.openallay.model.ModelRateLimitException;
import dev.openallay.model.ModelUpstreamException;
import dev.openallay.net.HttpResponseHeaders;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class ModelHttpErrors {
    private static final int ERROR_BODY_LIMIT_BYTES = 8192;

    private ModelHttpErrors() {}

    public static void requireSuccess(int status, HttpResponseHeaders headers, InputStream body)
            throws IOException {
        if (status >= 200 && status < 300) {
            return;
        }
        String message = "Model endpoint returned HTTP " + status;
        if (status == 429) {
            throw new ModelRateLimitException(message, retryAfter(headers));
        }
        if (status == 400) {
            throw rejectedBadRequest(status, body);
        }
        if (status == 502 || status == 503 || status == 504) {
            throw new ModelUpstreamException(status, retryAfter(headers));
        }
        throw new ModelClientException(new ModelFailure("model_http_error", message, status));
    }

    private static ModelClientException rejectedBadRequest(int status, InputStream body)
            throws IOException {
        BadRequestClassifier details = readBounded(body);
        String classifier = details.text().toLowerCase(java.util.Locale.ROOT);
        String code;
        String message;
        if (containsAny(classifier,
                "context_length", "context window", "maximum context", "too many tokens",
                "max_tokens", "max_completion_tokens")) {
            code = "model_context_rejected";
            message = "Model context was rejected by the endpoint";
        } else if (details.toolCallIdSchemaRejection() || containsAny(classifier,
                "tool_call", "tool call", "tool_use", "tool result", "tool_result",
                "function call", "function_call")) {
            code = "model_protocol_rejected";
            message = "Model tool-call history was rejected by the endpoint";
        } else {
            code = "model_request_rejected";
            message = "Model request was rejected by the endpoint";
        }
        return new ModelClientException(new ModelFailure(code, message, status));
    }

    @dev.openallay.value.ValueType(BadRequestClassifier.ValueSchemaProvider.class)
private static final class BadRequestClassifier {
    private final String text;
    private final boolean toolCallIdSchemaRejection;
    private BadRequestClassifier(String text, boolean toolCallIdSchemaRejection) {
        this.text = text;
        this.toolCallIdSchemaRejection = toolCallIdSchemaRejection;
    }
    public String text() { return text; }
    public boolean toolCallIdSchemaRejection() { return toolCallIdSchemaRejection; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BadRequestClassifier)) return false;
        BadRequestClassifier that = (BadRequestClassifier) other;
        return java.util.Objects.equals(text, that.text) && toolCallIdSchemaRejection == that.toolCallIdSchemaRejection;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + Boolean.hashCode(toolCallIdSchemaRejection);
        return hash;
    }
    @Override public String toString() { return "BadRequestClassifier[text=" + text + ", toolCallIdSchemaRejection=" + toolCallIdSchemaRejection + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<BadRequestClassifier> schema() {
            return new dev.openallay.value.ValueSchema<>(BadRequestClassifier.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<BadRequestClassifier>>asList(new dev.openallay.value.ValueSchema.Component<>(BadRequestClassifier.class, "text", BadRequestClassifier::text), new dev.openallay.value.ValueSchema.Component<>(BadRequestClassifier.class, "toolCallIdSchemaRejection", BadRequestClassifier::toolCallIdSchemaRejection)), arguments -> new BadRequestClassifier((String) arguments[0], (Boolean) arguments[1]));
        }
    }
}

    /** Returns only classifier fields and never preserves the provider body itself. */
    private static BadRequestClassifier readBounded(InputStream body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (bytes.size() < ERROR_BODY_LIMIT_BYTES) {
            int allowed = Math.min(buffer.length, ERROR_BODY_LIMIT_BYTES - bytes.size());
            int read = body.read(buffer, 0, allowed);
            if (read < 0) {
                break;
            }
            bytes.write(buffer, 0, read);
        }
        String encoded = dev.openallay.util.Java8ApiSupport.toString(bytes, java.nio.charset.StandardCharsets.UTF_8);
        try {
            JsonElement parsed = dev.openallay.json.JsonTrees.parse(encoded);
            if (!parsed.isJsonObject()) {
                return new BadRequestClassifier("", false);
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonObject error = root.has("error") && root.get("error").isJsonObject()
                    ? root.getAsJsonObject("error")
                    : root;
            StringBuilder classifier = new StringBuilder();
            appendString(error, "type", classifier);
            appendString(error, "code", classifier);
            appendString(error, "message", classifier);
            return new BadRequestClassifier(classifier.toString(), toolCallIdSchemaRejection(error));
        } catch (RuntimeException ignored) {
            return new BadRequestClassifier("", false);
        }
    }

    private static boolean toolCallIdSchemaRejection(JsonObject error) {
        String code = stringField(error, "code");
        if (!code.equals("string_above_max_length") && !code.equals("string_pattern_mismatch")) {
            return false;
        }
        // Inspect only known protocol field paths. Do not retain or expose a provider's
        // arbitrary parameter value, body, or message as a diagnostic.
        String parameter = stringField(error, "param");
        return parameter.equals("call_id")
                || parameter.equals("tool_call_id")
                || parameter.equals("tool_use_id")
                || parameter.matches("input\\[\\d+\\]\\.call_id")
                || parameter.matches("messages\\[\\d+\\]\\.tool_call_id")
                || parameter.matches("messages\\[\\d+\\]\\.tool_calls\\[\\d+\\]\\.id")
                || parameter.matches("messages\\[\\d+\\]\\.content\\[\\d+\\]\\.tool_use_id");
    }

    private static String stringField(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : "";
    }

    private static void appendString(JsonObject object, String field, StringBuilder output) {
        if (object.has(field) && !object.get(field).isJsonNull()
                && object.get(field).isJsonPrimitive()) {
            output.append(' ').append(object.get(field).getAsString());
        }
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static Duration retryAfter(HttpResponseHeaders headers) {
        String value = headers.firstValue("retry-after").orElse(null);
        if (value == null || dev.openallay.util.Java8Strings.isBlank(value)) {
            return null;
        }
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value.trim())));
        } catch (NumberFormatException ignored) {
            try {
                Duration duration = Duration.between(
                        java.time.Instant.now(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return duration.isNegative() ? Duration.ZERO : duration;
            } catch (DateTimeParseException invalidDate) {
                return null;
            }
        }
    }
}
