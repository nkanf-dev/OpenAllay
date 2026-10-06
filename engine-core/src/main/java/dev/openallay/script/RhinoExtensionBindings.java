package dev.openallay.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.openallay.extension.JavascriptHostBinding;
import dev.openallay.extension.JavascriptHostMethod;
import dev.openallay.extension.JavascriptInvocationScope;
import dev.openallay.model.ModelClientException;
import dev.openallay.script.host.RhinoHostAdapter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Closed transport for trusted Extension methods. No generic Java wrappers or script callbacks. */
final class RhinoExtensionBindings {
    private RhinoExtensionBindings() {}

    static Map<String, Scriptable> bind(Context context, ScriptableObject scope,
            RhinoHostAdapter adapter, RhinoJsonNormalizer normalizer,
            JavascriptInvocationScope invocation) {
        if (invocation == null) return Map.of();
        Map<String, Scriptable> bound = new LinkedHashMap<>();
        for (JavascriptHostBinding binding : invocation.hostBindings()) {
            ScriptableObject root = (ScriptableObject) context.newObject(scope);
            root.setPrototype(null);
            for (JavascriptHostMethod method : binding.methods()) {
                BaseFunction function = new BaseFunction(
                        scope, ScriptableObject.getFunctionPrototype(scope, context)) {
                    @Override public String getFunctionName() { return method.name(); }

                    @Override public Object call(Context cx, Scriptable callScope,
                            Scriptable thisObject, Object[] arguments) {
                        invocation.requireActive();
                        if (arguments.length != method.parameters().size()) {
                            throw invalid("Host method requires its exact declared argument count");
                        }
                        List<JsonElement> detached = new ArrayList<>(arguments.length);
                        for (int index = 0; index < arguments.length; index++) {
                            JsonElement value = normalizer.normalizeHostValue(arguments[index], cx);
                            if (!method.parameters().get(index).accepts(value)) {
                                throw invalid("Host method argument does not match its declared type");
                            }
                            detached.add(value);
                        }
                        JsonElement result;
                        try {
                            // Argument/result transport and all interpreter work remain budgeted.
                            // Only the trusted implementation's native waiting/execution is excluded.
                            result = ((OpenAllayRhinoContext) cx).callNative(() ->
                                    invocation.invokeHostMethod(binding.id(), method.name(), List.copyOf(detached)));
                        } catch (JavascriptExecutionException | ModelClientException failure) {
                            throw failure;
                        } catch (Throwable failure) {
                            throw new JavascriptExecutionException("javascript_extension_host_failed",
                                    "Extension host operation failed");
                        }
                        invocation.requireActive();
                        JsonElement copy = copyResult(result, new IdentityHashMap<>(), 0);
                        if (!method.result().accepts(copy)) {
                            throw invalid("Host method result does not match its declared type");
                        }
                        ((OpenAllayRhinoContext) cx).checkBudget();
                        return adapter.adapt(copy);
                    }

                    @Override public Scriptable construct(Context cx, Scriptable callScope,
                            Object[] arguments) {
                        throw new JavascriptExecutionException("javascript_host_access_denied",
                                "Extension host methods are not constructors");
                    }
                };
                function.preventExtensions();
                ScriptableObject.defineProperty(root, method.name(), function,
                        ScriptableObject.READONLY | ScriptableObject.PERMANENT, context);
            }
            root.preventExtensions();
            bound.put(binding.id(), root);
        }
        return Map.copyOf(bound);
    }

    /** Exact detached return shape. This is transport, not a truncated model-result preview. */
    private static JsonElement copyResult(JsonElement value,
            IdentityHashMap<JsonElement, Boolean> ancestors, int depth) {
        if (value == null || depth > JavascriptRuntimeLimits.DEFAULT.maxResultDepth()) {
            throw invalid("Host method must return a detached JSON value within the nesting limit");
        }
        if (value == JsonNull.INSTANCE) return JsonNull.INSTANCE;
        if (value instanceof JsonPrimitive primitive) {
            if (primitive.isBoolean()) return new JsonPrimitive(primitive.getAsBoolean());
            if (primitive.isString()) return new JsonPrimitive(primitive.getAsString());
            if (primitive.isNumber() && Double.isFinite(primitive.getAsDouble())) {
                return new JsonPrimitive(new com.google.gson.internal.LazilyParsedNumber(primitive.getAsString()));
            }
            throw invalid("Host method returned a non-finite or unsupported JSON scalar");
        }
        if (ancestors.put(value, Boolean.TRUE) != null) throw invalid("Host method returned a cycle");
        try {
            if (value instanceof JsonArray array) {
                JsonArray copy = new JsonArray();
                for (JsonElement child : array) copy.add(copyResult(child, ancestors, depth + 1));
                return copy;
            }
            if (value instanceof JsonObject object) {
                JsonObject copy = new JsonObject();
                object.entrySet().forEach(entry -> copy.add(entry.getKey(),
                        copyResult(entry.getValue(), ancestors, depth + 1)));
                return copy;
            }
            throw invalid("Host method returned an unsupported JSON implementation");
        } finally {
            ancestors.remove(value);
        }
    }

    private static JavascriptExecutionException invalid(String message) {
        return new JavascriptExecutionException("javascript_extension_host_invalid", message);
    }
}
