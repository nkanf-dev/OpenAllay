package dev.openallay.model.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openallay.OpenAllayConstants;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Explicit developer diagnostics. Never renders provider values or exception messages. */
public final class ModelTransportDiagnostics {
    private static final String PROPERTY = "openallay.model.diagnostics";
    private static final AtomicLong EXCHANGES = new AtomicLong();
    private static final Set<String> FRAME_CLASSES = Set.of(
            "dev.openallay.model.openai.OpenAiJsonCodec",
            "dev.openallay.model.openai.OpenAiStreamAccumulator",
            "dev.openallay.model.openai.OpenAiChatClient",
            "dev.openallay.model.http.SseParser",
            "dev.openallay.model.http.HttpModelTransport",
            "dev.openallay.net.JdkHttpTransport");

    private ModelTransportDiagnostics() {}

    public static long beginExchange() {
        return enabled() ? EXCHANGES.incrementAndGet() : -1;
    }

    public static void failure(long exchange, int receivedStatus, Throwable failure) {
        if (exchange >= 0) {
            OpenAllayConstants.LOGGER.warn("OPENALLAY_MODEL_DIAGNOSTIC exchange={} {}",
                    exchange, failureSummary(receivedStatus, failure));
        }
    }

    public static void openAiDecodeFailure(boolean stream, String json, Throwable failure) {
        if (enabled()) {
            OpenAllayConstants.LOGGER.warn("OPENALLAY_MODEL_DIAGNOSTIC decoder={} {} {}",
                    stream ? "openai-stream-chunk" : "openai-json",
                    failureSummary(-1, failure), openAiShape(json, stream));
        }
    }

    public static void openAiStreamFinish(
            Throwable failure, boolean modelPresent, boolean finishReasonPresent,
            int toolCount, int incompleteToolCount) {
        if (enabled()) {
            OpenAllayConstants.LOGGER.warn(
                    "OPENALLAY_MODEL_DIAGNOSTIC decoder=openai-stream-finish {} "
                            + "modelPresent={} finishReasonPresent={} toolCount={} incompleteToolCount={}",
                    failureSummary(-1, failure), modelPresent, finishReasonPresent,
                    toolCount, incompleteToolCount);
        }
    }

    static String failureSummary(int receivedStatus, Throwable failure) {
        StringBuilder output = new StringBuilder("phase=")
                .append(receivedStatus < 0 ? "transport" : "response")
                .append(" receivedStatus=").append(receivedStatus)
                .append(" exceptionClasses=");
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        String frame = "none";
        for (Throwable current = failure; current != null && visited.add(current);
                current = current.getCause()) {
            if (visited.size() > 1) {
                output.append(',');
            }
            output.append(current.getClass().getName());
            if (frame.equals("none")) {
                for (StackTraceElement candidate : current.getStackTrace()) {
                    if (FRAME_CLASSES.contains(candidate.getClassName())) {
                        frame = candidate.getClassName() + "." + candidate.getMethodName()
                                + ":" + candidate.getLineNumber();
                        break;
                    }
                }
            }
        }
        return output.append(" modelFrame=").append(frame).toString();
    }

    static String openAiShape(String json, boolean stream) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            StringBuilder output = new StringBuilder("rootType=").append(type(parsed));
            if (!parsed.isJsonObject()) {
                return output.toString();
            }
            JsonObject root = parsed.getAsJsonObject();
            fields(output, "root", root, "model", "choices", "usage", "error");
            JsonElement choices = root.get("choices");
            if (choices != null && choices.isJsonArray() && !choices.getAsJsonArray().isEmpty()) {
                JsonElement first = choices.getAsJsonArray().get(0);
                output.append(" choiceType=").append(type(first));
                if (first.isJsonObject()) {
                    JsonObject choice = first.getAsJsonObject();
                    fields(output, "choice", choice, "finish_reason", "message", "delta");
                    JsonElement assistant = choice.get(stream ? "delta" : "message");
                    if (assistant != null && assistant.isJsonObject()) {
                        JsonObject message = assistant.getAsJsonObject();
                        fields(output, "assistant", message, "content", "reasoning_content", "tool_calls");
                        JsonElement calls = message.get("tool_calls");
                        if (calls != null && calls.isJsonArray()) {
                            output.append(" toolCount=").append(calls.getAsJsonArray().size());
                            for (JsonElement call : calls.getAsJsonArray()) {
                                output.append(" toolType=").append(type(call));
                                if (call.isJsonObject()) {
                                    JsonObject tool = call.getAsJsonObject();
                                    fields(output, "tool", tool, "index", "id", "type", "function");
                                    JsonElement function = tool.get("function");
                                    if (function != null && function.isJsonObject()) {
                                        fields(output, "function", function.getAsJsonObject(), "name", "arguments");
                                    }
                                }
                            }
                        }
                    }
                }
            }
            JsonElement usage = root.get("usage");
            if (usage != null && usage.isJsonObject()) {
                JsonObject object = usage.getAsJsonObject();
                fields(output, "usage", object, "prompt_tokens", "completion_tokens", "prompt_tokens_details");
                JsonElement details = object.get("prompt_tokens_details");
                if (details != null && details.isJsonObject()) {
                    fields(output, "promptTokensDetails", details.getAsJsonObject(), "cached_tokens");
                }
            }
            return output.toString();
        } catch (RuntimeException ignored) {
            return "rootType=invalid-json";
        }
    }

    private static void fields(StringBuilder output, String label, JsonObject object, String... fields) {
        output.append(' ').append(label).append("Types=");
        for (int index = 0; index < fields.length; index++) {
            if (index != 0) {
                output.append(',');
            }
            String field = fields[index];
            output.append(field).append(':').append(type(object.get(field)));
        }
    }

    private static String type(JsonElement value) {
        if (value == null) {
            return "missing";
        }
        if (value.isJsonNull()) {
            return "null";
        }
        if (value.isJsonObject()) {
            return "object";
        }
        if (value.isJsonArray()) {
            return "array";
        }
        if (value.getAsJsonPrimitive().isString()) {
            return "string";
        }
        return value.getAsJsonPrimitive().isBoolean() ? "boolean" : "number";
    }

    private static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }
}
