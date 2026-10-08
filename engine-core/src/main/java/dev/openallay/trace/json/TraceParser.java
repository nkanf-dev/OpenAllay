package dev.openallay.trace.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.openallay.context.ContextCapability;
import dev.openallay.tool.ToolResult;
import dev.openallay.trace.model.AgentTrace;
import dev.openallay.trace.model.AssistantMessageStep;
import dev.openallay.trace.model.ExpectationMatch;
import dev.openallay.trace.model.ToolCallStep;
import dev.openallay.trace.model.TraceExpectation;
import dev.openallay.trace.model.TraceStep;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TraceParser {
    private static final Set<String> TRACE_FIELDS =
            dev.openallay.util.Java8Collections.setOf("id", "userMessage", "requiredContext", "steps");
    private static final Set<String> TOOL_STEP_FIELDS =
            dev.openallay.util.Java8Collections.setOf("type", "tool", "arguments", "expect");
    private static final Set<String> MESSAGE_STEP_FIELDS = dev.openallay.util.Java8Collections.setOf("type", "content");
    private static final Set<String> EXPECTATION_FIELDS =
            dev.openallay.util.Java8Collections.setOf("status", "match", "value", "outputType");

    public ToolResult<AgentTrace> parse(Reader source) {
        try {
            JsonReader reader = dev.openallay.json.JsonReaders.strict(source);

            JsonElement root = readElement(reader, "$");
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw invalid("Unexpected data after trace document");
            }
            return new ToolResult.Success<>(parseTrace(requireObject(root, "$")));
        } catch (IOException | JsonParseException | IllegalArgumentException exception) {
            String message = exception.getMessage();
            return new ToolResult.Failure<>(
                    "invalid_trace", message == null || dev.openallay.util.Java8Strings.isBlank(message) ? "Invalid trace" : message);
        }
    }

    private AgentTrace parseTrace(JsonObject object) {
        requireFields(object, "$", TRACE_FIELDS, TRACE_FIELDS);
        String id = requireString(object, "id", "$");
        String userMessage = requireString(object, "userMessage", "$");

        EnumSet<ContextCapability> capabilities = EnumSet.noneOf(ContextCapability.class);
        JsonArray requiredContext = requireArray(object.get("requiredContext"), "$.requiredContext");
        for (int index = 0; index < requiredContext.size(); index++) {
            String value = requireString(requiredContext.get(index), "$.requiredContext[" + index + "]");
            dev.openallay.context.ContextCapability $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((value)) {
case "registries":
{
$oaSwitch0_exit_result = ContextCapability.REGISTRIES; break $oaSwitch0_exit;
}
case "recipes":
{
$oaSwitch0_exit_result = ContextCapability.RECIPES; break $oaSwitch0_exit;
}
case "player":
{
$oaSwitch0_exit_result = ContextCapability.PLAYER; break $oaSwitch0_exit;
}
case "observable_game_state":
{
$oaSwitch0_exit_result = ContextCapability.OBSERVABLE_GAME_STATE; break $oaSwitch0_exit;
}
default:
{
throw invalid("Unknown context capability: " + value);
}
}
}
ContextCapability capability = $oaSwitch0_exit_result;
            if (!capabilities.add(capability)) {
                throw invalid("Duplicate context capability: " + value);
            }
        }

        JsonArray stepArray = requireArray(object.get("steps"), "$.steps");
        List<TraceStep> steps = new ArrayList<>();
        for (int index = 0; index < stepArray.size(); index++) {
            steps.add(parseStep(requireObject(stepArray.get(index), "$.steps[" + index + "]"), index));
        }
        return new AgentTrace(id, userMessage, capabilities, steps);
    }

    private TraceStep parseStep(JsonObject object, int index) {
        String path = "$.steps[" + index + "]";
        String type = requireString(object, "type", path);
        {
dev.openallay.trace.model.TraceStep $oaSwitch2_exit_result;
$oaSwitch2_exit: {
switch ((type)) {
case "tool_call":
{
{
                requireFields(object, path, TOOL_STEP_FIELDS, TOOL_STEP_FIELDS);
                { $oaSwitch2_exit_result = new ToolCallStep(
                        requireString(object, "tool", path),
                        requireObject(object.get("arguments"), path + ".arguments"),
                        parseExpectation(requireObject(object.get("expect"), path + ".expect"), path)); break $oaSwitch2_exit; }
            }
}
case "assistant_message":
{
{
                requireFields(object, path, MESSAGE_STEP_FIELDS, MESSAGE_STEP_FIELDS);
                { $oaSwitch2_exit_result = new AssistantMessageStep(requireString(object, "content", path)); break $oaSwitch2_exit; }
            }
}
default:
{
throw invalid("Unknown step type at " + path + ": " + type);
}
}
}
return $oaSwitch2_exit_result;
}
    }

    private TraceExpectation parseExpectation(JsonObject object, String stepPath) {
        String path = stepPath + ".expect";
        requireFields(object, path, EXPECTATION_FIELDS, dev.openallay.util.Java8Collections.setOf("status", "match"));
        String status = requireString(object, "status", path);
        String matchName = requireString(object, "match", path);
        ExpectationMatch match;
        try {
            match = ExpectationMatch.valueOf(matchName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("Unknown expectation match at " + path + ": " + matchName);
        }

        JsonElement value = object.has("value") ? object.get("value") : null;
        String outputType = object.has("outputType")
                ? requireString(object, "outputType", path)
                : null;
        if (match == ExpectationMatch.SCHEMA && object.has("value")) {
            throw invalid("Schema expectation must not declare value at " + path);
        }
        if (match != ExpectationMatch.SCHEMA && object.has("outputType")) {
            throw invalid("Only schema expectation may declare outputType at " + path);
        }
        return new TraceExpectation(status, match, value, outputType);
    }

    private static JsonElement readElement(JsonReader reader, String path) throws IOException {
        {
com.google.gson.JsonElement $oaSwitch1_exit_result;
$oaSwitch1_exit: {
switch ((reader.peek())) {
case BEGIN_OBJECT:
{
$oaSwitch1_exit_result = readObject(reader, path); break $oaSwitch1_exit;
}
case BEGIN_ARRAY:
{
$oaSwitch1_exit_result = readArray(reader, path); break $oaSwitch1_exit;
}
case STRING:
{
$oaSwitch1_exit_result = new JsonPrimitive(reader.nextString()); break $oaSwitch1_exit;
}
case NUMBER:
{
$oaSwitch1_exit_result = new JsonPrimitive(new BigDecimal(reader.nextString())); break $oaSwitch1_exit;
}
case BOOLEAN:
{
$oaSwitch1_exit_result = new JsonPrimitive(reader.nextBoolean()); break $oaSwitch1_exit;
}
case NULL:
{
{
                reader.nextNull();
                { $oaSwitch1_exit_result = JsonNull.INSTANCE; break $oaSwitch1_exit; }
            }
}
default:
{
throw invalid("Unexpected JSON token at " + path + ": " + reader.peek());
}
}
}
return $oaSwitch1_exit_result;
}
    }

    private static JsonObject readObject(JsonReader reader, String path) throws IOException {
        JsonObject object = new JsonObject();
        Set<String> names = new HashSet<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (!names.add(name)) {
                throw invalid("Duplicate field at " + path + ": " + name);
            }
            object.add(name, readElement(reader, path + "." + name));
        }
        reader.endObject();
        return object;
    }

    private static JsonArray readArray(JsonReader reader, String path) throws IOException {
        JsonArray array = new JsonArray();
        reader.beginArray();
        int index = 0;
        while (reader.hasNext()) {
            array.add(readElement(reader, path + "[" + index + "]"));
            index++;
        }
        reader.endArray();
        return array;
    }

    private static void requireFields(
            JsonObject object, String path, Set<String> allowed, Set<String> required) {
        for (String field : dev.openallay.json.JsonTrees.keys(object)) {
            if (!allowed.contains(field)) {
                throw invalid("Unknown field at " + path + ": " + field);
            }
        }
        for (String field : required) {
            if (!object.has(field)) {
                throw invalid("Missing field at " + path + ": " + field);
            }
        }
    }

    private static JsonObject requireObject(JsonElement element, String path) {
        if (element == null || !element.isJsonObject()) {
            throw invalid("Expected object at " + path);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonElement element, String path) {
        if (element == null || !element.isJsonArray()) {
            throw invalid("Expected array at " + path);
        }
        return element.getAsJsonArray();
    }

    private static String requireString(JsonObject object, String field, String path) {
        if (!object.has(field)) {
            throw invalid("Missing field at " + path + ": " + field);
        }
        return requireString(object.get(field), path + "." + field);
    }

    private static String requireString(JsonElement element, String path) {
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw invalid("Expected string at " + path);
        }
        return element.getAsString();
    }

    private static JsonParseException invalid(String message) {
        return new JsonParseException(message);
    }
}
