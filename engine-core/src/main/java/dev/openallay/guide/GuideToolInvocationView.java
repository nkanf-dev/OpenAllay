package dev.openallay.guide;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Closed, durable, player-readable invocation facts; never contains raw source or arguments. */
@dev.openallay.value.ValueType(GuideToolInvocationView.ValueSchemaProvider.class)
public final class GuideToolInvocationView {
    private final List<String> handles;
    private final List<String> modules;
    private final boolean liveArgumentsAvailable;
    public GuideToolInvocationView(List<String> handles, List<String> modules, boolean liveArgumentsAvailable) {

        handles = List.copyOf(handles);
        modules = List.copyOf(modules);

        this.handles = handles;
        this.modules = modules;
        this.liveArgumentsAvailable = liveArgumentsAvailable;
    }
    public List<String> handles() { return handles; }
    public List<String> modules() { return modules; }
    public boolean liveArgumentsAvailable() { return liveArgumentsAvailable; }
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideToolInvocationView)) return false;
        GuideToolInvocationView that = (GuideToolInvocationView) other;
        return java.util.Objects.equals(handles, that.handles) && java.util.Objects.equals(modules, that.modules) && liveArgumentsAvailable == that.liveArgumentsAvailable;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(handles);
        hash = 31 * hash + java.util.Objects.hashCode(modules);
        hash = 31 * hash + Boolean.hashCode(liveArgumentsAvailable);
        return hash;
    }
    @Override public String toString() { return "GuideToolInvocationView[handles=" + handles + ", modules=" + modules + ", liveArgumentsAvailable=" + liveArgumentsAvailable + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideToolInvocationView> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideToolInvocationView.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideToolInvocationView>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideToolInvocationView.class, "handles", GuideToolInvocationView::handles), new dev.openallay.value.ValueSchema.Component<>(GuideToolInvocationView.class, "modules", GuideToolInvocationView::modules), new dev.openallay.value.ValueSchema.Component<>(GuideToolInvocationView.class, "liveArgumentsAvailable", GuideToolInvocationView::liveArgumentsAvailable)), arguments -> new GuideToolInvocationView((List) arguments[0], (List) arguments[1], (Boolean) arguments[2]));
        }
    }
}
