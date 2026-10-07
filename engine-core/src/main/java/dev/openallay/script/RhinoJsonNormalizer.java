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

    /** Internal host transport is exact, not a model preview or workspace result projection. */
    JsonElement normalizeHostValue(Object value, Context context) {
        RhinoJsonNormalizer transport = new RhinoJsonNormalizer(new JavascriptRuntimeLimits(
                Integer.MAX_VALUE, limits.maxResultDepth(), Long.MAX_VALUE,
                Long.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        return transport.normalize(value, context, new IdentityHashMap<>(), 0,
                transport.new Budget(true, true)).value();
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
        final class $oaPattern0_Holder { java.lang.Object value; Boolean bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = value) instanceof java.lang.Boolean && (($oaPattern0_holder.bound = (Boolean) $oaPattern0_holder.value) != null))) {
            return ordinary(new JsonPrimitive($oaPattern0_holder.bound), JavascriptSemanticKind.SCALAR);
        }
        final class $oaPattern1_Holder { java.lang.Object value; CharSequence bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = value) instanceof java.lang.CharSequence && (($oaPattern1_holder.bound = (CharSequence) $oaPattern1_holder.value) != null))) {
            String text = $oaPattern1_holder.bound.toString();
            budget.string(text.length());
            return ordinary(new JsonPrimitive(text), JavascriptSemanticKind.SCALAR);
        }
        final class $oaPattern2_Holder { java.lang.Object value; Number bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = value) instanceof java.lang.Number && (($oaPattern2_holder.bound = (Number) $oaPattern2_holder.value) != null))) {
            double numeric = $oaPattern2_holder.bound.doubleValue();
            if (!Double.isFinite(numeric)) {
                throw invalid("JavaScript result contains a non-finite number");
            }
            return ordinary(new JsonPrimitive($oaPattern2_holder.bound), JavascriptSemanticKind.SCALAR);
        }
        final class $oaPattern3_Holder { java.lang.Object value; Wrapper bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = value) instanceof dev.latvian.mods.rhino.Wrapper && (($oaPattern3_holder.bound = (Wrapper) $oaPattern3_holder.value) != null))) {
            if (budget.hostTransport) throw invalid("Extension host values cannot contain Java wrappers");
            Object unwrapped = $oaPattern3_holder.bound.unwrap();
            final class $oaPattern4_Holder { java.lang.Object value; CharSequence bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = unwrapped) instanceof java.lang.CharSequence && (($oaPattern4_holder.bound = (CharSequence) $oaPattern4_holder.value) != null))) return ordinary(new JsonPrimitive($oaPattern4_holder.bound.toString()), JavascriptSemanticKind.SCALAR);
            final class $oaPattern5_Holder { java.lang.Object value; Number bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = unwrapped) instanceof java.lang.Number && (($oaPattern5_holder.bound = (Number) $oaPattern5_holder.value) != null)) && Double.isFinite($oaPattern5_holder.bound.doubleValue())) return ordinary(new JsonPrimitive($oaPattern5_holder.bound), JavascriptSemanticKind.SCALAR);
            final class $oaPattern6_Holder { java.lang.Object value; Boolean bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = unwrapped) instanceof java.lang.Boolean && (($oaPattern6_holder.bound = (Boolean) $oaPattern6_holder.value) != null))) return ordinary(new JsonPrimitive($oaPattern6_holder.bound), JavascriptSemanticKind.SCALAR);
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
        final class $oaPattern7_Holder { java.lang.Object value; NativeArray bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = value) instanceof dev.latvian.mods.rhino.NativeArray && (($oaPattern7_holder.bound = (NativeArray) $oaPattern7_holder.value) != null))) {
            long length = $oaPattern7_holder.bound.getLength();
            if (length > limits.maxArrayLength()) {
                throw exceeded("JavaScript result exceeds the array-length budget");
            }
            enter(value, ancestors);
            try {
                JsonArray result = new JsonArray();
                JavascriptResultShape aggregate = null;
                if (budget.hostTransport) {
                    // NativeArray's Iterable Java view maps undefined/hole entries to null.
                    // Read actual indexed guest values instead, preserving invalidity.
                    for (int index = 0; index < length; index++) {
                        Object item = $oaPattern7_holder.bound.get(context, index, $oaPattern7_holder.bound);
                        if (item == Scriptable.NOT_FOUND) throw invalid(
                                "Extension host arguments cannot contain array holes");
                        Result child = normalize(item, context, ancestors, depth + 1, budget);
                        result.add(child.value());
                        aggregate = aggregate(aggregate, child.shape());
                    }
                    return new Result(result, arrayShape(aggregate));
                }
                for (Object item : $oaPattern7_holder.bound) {
                    if (budget.hostTransport && item == Undefined.INSTANCE) {
                        throw invalid("Extension host arguments cannot contain undefined array values");
                    }
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
        final class $oaPattern8_Holder { java.lang.Object value; NativeObject bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = value) instanceof dev.latvian.mods.rhino.NativeObject && (($oaPattern8_holder.bound = (NativeObject) $oaPattern8_holder.value) != null))) {
            if ($oaPattern8_holder.bound.entrySet().size() > limits.maxObjectFields()) {
                throw exceeded("JavaScript result exceeds the object-field budget");
            }
            enter(value, ancestors);
            try {
                JsonObject result = new JsonObject();
                if (budget.hostTransport) {
                    // NativeObject's Java Map view omits undefined entries. Use actual own
                    // JavaScript keys so transport cannot silently remove malformed fields.
                    for (Object id : $oaPattern8_holder.bound.getIds(context)) {
                        if (!(id instanceof String) && !(id instanceof Integer)) {
                            throw invalid("Extension host arguments require ordinary JSON keys");
                        }
                        String key = id.toString();
                        final class $oaPattern9_Holder { java.lang.Object value; Integer bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
Object child = (($oaPattern9_holder.value = id) instanceof java.lang.Integer && (($oaPattern9_holder.bound = (Integer) $oaPattern9_holder.value) != null))
                                ? $oaPattern8_holder.bound.get(context, $oaPattern9_holder.bound, $oaPattern8_holder.bound) : $oaPattern8_holder.bound.get(context, key, $oaPattern8_holder.bound);
                        result.add(key, normalize(child, context, ancestors, depth + 1, budget).value());
                    }
                    return ordinary(result, JavascriptSemanticKind.KEY_VALUE);
                }
                for (Object rawEntry : $oaPattern8_holder.bound.entrySet()) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) rawEntry;
                    String key = String.valueOf(entry.getKey());
                    budget.string(key.length());
                    Object child = entry.getValue();
                    if (budget.hostTransport && child == Undefined.INSTANCE) {
                        throw invalid("Extension host arguments cannot contain undefined fields");
                    }
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
        final class $oaPattern10_Holder { java.lang.Object value; HostListView bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = value) instanceof dev.openallay.script.host.HostListView && (($oaPattern10_holder.bound = (HostListView) $oaPattern10_holder.value) != null))) {
            if ($oaPattern10_holder.bound.length() > limits.maxArrayLength()) {
                throw exceeded("JavaScript result exceeds the array-length budget");
            }
            enter(value, ancestors);
            try {
                JsonArray result = new JsonArray();
                JavascriptResultShape aggregate = null;
                for (int index = 0; index < $oaPattern10_holder.bound.length(); index++) {
                    Result child = normalize(
                            $oaPattern10_holder.bound.get(context, index, $oaPattern10_holder.bound),
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
        final class $oaPattern11_Holder { java.lang.Object value; HostObjectView bound; }
final $oaPattern11_Holder $oaPattern11_holder = new $oaPattern11_Holder();
if ((($oaPattern11_holder.value = value) instanceof dev.openallay.script.host.HostObjectView && (($oaPattern11_holder.bound = (HostObjectView) $oaPattern11_holder.value) != null))) {
            Object[] ids = $oaPattern11_holder.bound.getIds(context);
            if (ids.length > limits.maxObjectFields()) {
                throw exceeded("JavaScript result exceeds the object-field budget");
            }
            enter(value, ancestors);
            try {
                JsonObject result = new JsonObject();
                for (Object id : ids) {
                    String key = String.valueOf(id);
                    budget.string(key.length());
                    Object child = $oaPattern11_holder.bound.get(context, key, $oaPattern11_holder.bound);
                    if (budget.hostTransport && child == Undefined.INSTANCE) {
                        throw invalid("Extension host arguments cannot contain undefined fields");
                    }
                    if (child != Undefined.INSTANCE) {
                        result.add(key, normalize(
                                child, context, ancestors, depth + 1, budget).value());
                    }
                }
                return new Result(result, $oaPattern11_holder.bound.resultShape());
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
        private final boolean hostTransport;
        private long nodes;
        private long stringCharacters;

        private Budget() { this(false, false); }
        private Budget(boolean unrestricted) { this(unrestricted, false); }
        private Budget(boolean unrestricted, boolean hostTransport) {
            this.unrestricted = unrestricted;
            this.hostTransport = hostTransport;
        }

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

    @dev.openallay.value.ValueType(Result.ValueSchemaProvider.class)
static final class Result {
    private final JsonElement value;
    private final JavascriptResultShape shape;
    Result(JsonElement value, JavascriptResultShape shape) {

            value = java.util.Objects.requireNonNull(value, "value");
            shape = java.util.Objects.requireNonNull(shape, "shape");

        this.value = value;
        this.shape = shape;
    }
    public JsonElement value() { return value; }
    public JavascriptResultShape shape() { return shape; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Result)) return false;
        Result that = (Result) other;
        return java.util.Objects.equals(value, that.value) && java.util.Objects.equals(shape, that.shape);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(value);
        hash = 31 * hash + java.util.Objects.hashCode(shape);
        return hash;
    }
    @Override public String toString() { return "Result[value=" + value + ", shape=" + shape + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Result> schema() {
            return new dev.openallay.value.ValueSchema<>(Result.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Result>>asList(new dev.openallay.value.ValueSchema.Component<>(Result.class, "value", Result::value), new dev.openallay.value.ValueSchema.Component<>(Result.class, "shape", Result::shape)), arguments -> new Result((JsonElement) arguments[0], (JavascriptResultShape) arguments[1]));
        }
    }
}
}
