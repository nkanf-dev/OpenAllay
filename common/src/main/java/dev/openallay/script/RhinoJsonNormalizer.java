package dev.openallay.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.NativeArray;
import dev.latvian.mods.rhino.NativeObject;
import dev.latvian.mods.rhino.NativePromise;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.Symbol;
import dev.latvian.mods.rhino.Undefined;
import dev.latvian.mods.rhino.Wrapper;
import dev.openallay.script.host.HostListView;
import dev.openallay.script.host.HostObjectView;
import dev.openallay.script.result.JavascriptResultShape;
import dev.openallay.script.result.JavascriptSemanticKind;
import java.util.IdentityHashMap;
import java.util.Map;

final class RhinoJsonNormalizer {
    private final JavascriptRuntimeLimits limits;

    RhinoJsonNormalizer(JavascriptRuntimeLimits limits) {
        this.limits = java.util.Objects.requireNonNull(limits, "limits");
    }

    Result normalize(Object value, Context context) {
        return normalize(value, context, new IdentityHashMap<>(), 0, new Budget());
    }

    Result normalizeUnrestricted(Object value, Context context) {
        RhinoJsonNormalizer unlimited = new RhinoJsonNormalizer(new JavascriptRuntimeLimits(
                Integer.MAX_VALUE, Integer.MAX_VALUE,
                Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        return unlimited.normalize(value, context, new IdentityHashMap<>(), 0,
                unlimited.new Budget(true));
    }

    private Result normalize(
            Object value,
            Context context,
            IdentityHashMap<Object, Boolean> ancestors,
            int depth,
            Budget budget) {
        if (depth > limits.maxResultDepth()) {
            throw exceeded("JavaScript result exceeds the nesting-depth budget");
        }
        budget.node();
        if (value == null) {
            return ordinary(JsonNull.INSTANCE, JavascriptSemanticKind.SCALAR);
        }
        if (value == Undefined.INSTANCE || value == Undefined.SCRIPTABLE_INSTANCE) {
            throw invalid("JavaScript result is undefined. Return an explicit JSON value; check the "
                    + "selected roots and documented fields. This alone does not mean game data is unavailable.");
        }
        if (value instanceof Boolean booleanValue) {
            return ordinary(new JsonPrimitive(booleanValue), JavascriptSemanticKind.SCALAR);
        }
        if (value instanceof CharSequence sequence) {
            String text = sequence.toString();
            budget.string(text.length());
            return ordinary(new JsonPrimitive(text), JavascriptSemanticKind.SCALAR);
        }
        if (value instanceof Number number) {
            double numeric = number.doubleValue();
            if (!Double.isFinite(numeric)) {
                throw invalid("JavaScript result contains a non-finite number");
            }
            return ordinary(new JsonPrimitive(number), JavascriptSemanticKind.SCALAR);
        }
        if (value instanceof Wrapper wrapper) {
            Object unwrapped = wrapper.unwrap();
            if (unwrapped instanceof CharSequence text) return ordinary(new JsonPrimitive(text.toString()), JavascriptSemanticKind.SCALAR);
            if (unwrapped instanceof Number number && Double.isFinite(number.doubleValue())) return ordinary(new JsonPrimitive(number), JavascriptSemanticKind.SCALAR);
            if (unwrapped instanceof Boolean bool) return ordinary(new JsonPrimitive(bool), JavascriptSemanticKind.SCALAR);
            if (unwrapped == null) return ordinary(JsonNull.INSTANCE, JavascriptSemanticKind.SCALAR);
            return ordinary(new JsonPrimitive(String.valueOf(unwrapped)), JavascriptSemanticKind.SCALAR);
        }
        if (value instanceof BaseFunction) {
            throw invalid("JavaScript result contains a function. Return JSON data from the operation, "
                    + "not the function or module itself; omit function properties. "
                    + "This result error does not mean the operation is unavailable.");
        }
        if (value instanceof NativePromise || value instanceof Symbol) {
            throw invalid("JavaScript result contains an unsupported host or executable value");
        }
        if (value instanceof NativeArray array) {
            long length = array.getLength();
            if (length > limits.maxArrayLength()) {
                throw exceeded("JavaScript result exceeds the array-length budget");
            }
            enter(value, ancestors);
            try {
                JsonArray result = new JsonArray();
                JavascriptResultShape aggregate = null;
                for (Object item : array) {
                    Result child = item == Undefined.INSTANCE
                            ? ordinary(JsonNull.INSTANCE, JavascriptSemanticKind.SCALAR)
                            : normalize(item, context, ancestors, depth + 1, budget);
                    result.add(child.value());
                    aggregate = aggregate(aggregate, child.shape());
                }
                return new Result(result, arrayShape(aggregate));
            } finally {
                ancestors.remove(value);
            }
        }
        if (value instanceof NativeObject object) {
            if (object.entrySet().size() > limits.maxObjectFields()) {
                throw exceeded("JavaScript result exceeds the object-field budget");
            }
            enter(value, ancestors);
            try {
                JsonObject result = new JsonObject();
                for (Object rawEntry : object.entrySet()) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) rawEntry;
                    String key = String.valueOf(entry.getKey());
                    budget.string(key.length());
                    Object child = entry.getValue();
                    if (child != Undefined.INSTANCE) {
                        result.add(
                                key,
                                normalize(child, context, ancestors, depth + 1, budget).value());
                    }
                }
                return ordinary(result, JavascriptSemanticKind.KEY_VALUE);
            } finally {
                ancestors.remove(value);
            }
        }
        if (value instanceof HostListView list) {
            if (list.length() > limits.maxArrayLength()) {
                throw exceeded("JavaScript result exceeds the array-length budget");
            }
            enter(value, ancestors);
            try {
                JsonArray result = new JsonArray();
                JavascriptResultShape aggregate = null;
                for (int index = 0; index < list.length(); index++) {
                    Result child = normalize(
                            list.get(context, index, list),
                            context,
                            ancestors,
                            depth + 1,
                            budget);
                    result.add(child.value());
                    aggregate = aggregate(aggregate, child.shape());
                }
                return new Result(result, arrayShape(aggregate));
            } finally {
                ancestors.remove(value);
            }
        }
        if (value instanceof HostObjectView object) {
            Object[] ids = object.getIds(context);
            if (ids.length > limits.maxObjectFields()) {
                throw exceeded("JavaScript result exceeds the object-field budget");
            }
            enter(value, ancestors);
            try {
                JsonObject result = new JsonObject();
                for (Object id : ids) {
                    String key = String.valueOf(id);
                    budget.string(key.length());
                    Object child = object.get(context, key, object);
                    if (child != Undefined.INSTANCE) {
                        result.add(key, normalize(
                                child, context, ancestors, depth + 1, budget).value());
                    }
                }
                return new Result(result, object.resultShape());
            } finally {
                ancestors.remove(value);
            }
        }
        if (value instanceof Scriptable) {
            throw invalid("JavaScript result contains an unsupported script object");
        }
        throw invalid("JavaScript result contains an unsupported value");
    }

    private static Result ordinary(JsonElement value, JavascriptSemanticKind kind) {
        return new Result(value, JavascriptResultShape.ordinary(kind));
    }

    private static JavascriptResultShape aggregate(
            JavascriptResultShape current, JavascriptResultShape next) {
        if (current == null) {
            return next;
        }
        if (current.trusted() && next.trusted() && current.kind() == next.kind()) {
            return current;
        }
        return JavascriptResultShape.ordinary(JavascriptSemanticKind.GENERIC);
    }

    private static JavascriptResultShape arrayShape(JavascriptResultShape elements) {
        if (elements != null && elements.trusted()
                && (elements.kind() == JavascriptSemanticKind.RECIPE
                        || elements.kind() == JavascriptSemanticKind.ITEM)) {
            return elements;
        }
        return JavascriptResultShape.ordinary(JavascriptSemanticKind.GENERIC);
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> ancestors) {
        if (ancestors.put(value, Boolean.TRUE) != null) {
            throw invalid("JavaScript result contains a cycle");
        }
    }

    private static JavascriptExecutionException invalid(String message) {
        return new JavascriptExecutionException("javascript_result_invalid", message);
    }

    private static JavascriptExecutionException exceeded(String message) {
        return new JavascriptExecutionException("javascript_result_budget_exceeded", message);
    }

    private final class Budget {
        private final boolean unrestricted;
        private long nodes;
        private long stringCharacters;

        private Budget() { this(false); }
        private Budget(boolean unrestricted) { this.unrestricted = unrestricted; }

        private void node() {
            if (!unrestricted && ++nodes > limits.maxResultNodes()) {
                throw exceeded("JavaScript result exceeds the node budget");
            }
        }

        private void string(int characters) {
            if (!unrestricted && characters > limits.maxStringCharacters()) {
                throw exceeded("JavaScript result contains an oversized string");
            }
            if (unrestricted) return;
            stringCharacters = saturatedAdd(stringCharacters, characters);
            if (stringCharacters > limits.maxResultNodes() * 8L) {
                throw exceeded("JavaScript result exceeds the aggregate text budget");
            }
        }
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    record Result(JsonElement value, JavascriptResultShape shape) {
        Result {
            value = java.util.Objects.requireNonNull(value, "value");
            shape = java.util.Objects.requireNonNull(shape, "shape");
        }
    }
}
