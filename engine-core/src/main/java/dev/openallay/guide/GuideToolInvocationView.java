package dev.openallay.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Closed, durable, player-readable invocation facts; never contains raw source or arguments. */
public record GuideToolInvocationView(
        List<String> handles,
        List<String> modules,
        boolean liveArgumentsAvailable) {
    public GuideToolInvocationView {
        handles = List.copyOf(handles);
        modules = List.copyOf(modules);
    }

    public static GuideToolInvocationView none() {
        return new GuideToolInvocationView(List.of(), List.of(), false);
    }

    public static GuideToolInvocationView from(
            String toolId, JsonObject arguments, JsonObject normalized) {
        if (toolId == null || !toolId.endsWith(":run_javascript")) {
            return new GuideToolInvocationView(List.of(), List.of(), arguments != null);
        }
        return new GuideToolInvocationView(
                strings(arguments, "handles"),
                strings(value(normalized), "modules"),
                arguments != null);
    }

    public GuideToolInvocationView withNormalized(JsonObject normalized) {
        List<String> observedModules = strings(value(normalized), "modules");
        return observedModules.isEmpty()
                ? this
                : new GuideToolInvocationView(
                        handles, observedModules, liveArgumentsAvailable);
    }

    public GuideToolInvocationView restored() {
        return new GuideToolInvocationView(handles, modules, false);
    }

    public boolean empty() {
        return handles.isEmpty() && modules.isEmpty();
    }

    private static JsonObject value(JsonObject normalized) {
        if (normalized == null || !normalized.has("value")
                || !normalized.get("value").isJsonObject()) {
            return null;
        }
        return normalized.getAsJsonObject("value");
    }

    private static List<String> strings(JsonObject object, String field) {
        if (object == null || !object.has(field) || !object.get(field).isJsonArray()) {
            return List.of();
        }
        ArrayList<String> values = new ArrayList<>();
        for (JsonElement value : object.getAsJsonArray(field)) {
            if (value != null && value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isString()) {
                values.add(value.getAsString());
            }
        }
        return List.copyOf(values);
    }
}
