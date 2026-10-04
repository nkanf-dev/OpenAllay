package dev.openallay.script;

import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.LambdaFunction;
import dev.latvian.mods.rhino.ScriptRuntime;
import dev.latvian.mods.rhino.ScriptRuntimeES6;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.latvian.mods.rhino.Undefined;

/** Native engine bindings. No host objects or JVM classes are added to the guest scope. */
final class RhinoBuiltinBindings {
    private RhinoBuiltinBindings() {}

    static void install(Context context, ScriptableObject scope, JavascriptRuntimeLimits limits, boolean unrestricted) {
        repairArrayCall(context, scope);
        if (!unrestricted) {
            guardStringAllocations(context, scope, limits.maxStringCharacters());
        }
    }

    private static void repairArrayCall(Context context, ScriptableObject scope) {
        BaseFunction nativeArray = (BaseFunction) ScriptableObject.getProperty(scope, "Array", context);
        Scriptable prototype = (Scriptable) ScriptableObject.getProperty(nativeArray, "prototype", context);
        // This Rhino build's LambdaConstructor.call omits the prototype/parent installation
        // performed by construct. Array has identical native call and construct semantics.
        BaseFunction bridge = new BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope, context)) {
            @Override public String getFunctionName() { return nativeArray.getFunctionName(); }
            @Override public int getLength() { return nativeArray.getLength(); }
            @Override public int getArity() { return nativeArray.getArity(); }

            @Override
            public Object call(Context cx, Scriptable callScope, Scriptable thisObject, Object[] arguments) {
                return nativeArray.construct(cx, callScope, arguments);
            }

            @Override
            public Scriptable construct(Context cx, Scriptable callScope, Object[] arguments) {
                return nativeArray.construct(cx, callScope, arguments);
            }
        };
        bridge.setStandardPropertyAttributes(ScriptableObject.DONTENUM | ScriptableObject.READONLY);
        bridge.setImmunePrototypeProperty(prototype);
        Scriptable object = (Scriptable) ScriptableObject.getProperty(scope, "Object", context);
        Function describe = (Function) ScriptableObject.getProperty(object, "getOwnPropertyDescriptors", context);
        ScriptableObject descriptors = (ScriptableObject) describe.call(
                context, scope, object, new Object[]{nativeArray});
        descriptors.delete(context, "prototype");
        bridge.defineOwnProperties(context, descriptors);
        ScriptableObject.defineProperty(scope, "Array", bridge, scope.getAttributes(context, "Array"), context);
        ScriptableObject.putProperty(prototype, "constructor", bridge, context);
    }

    private static void guardStringAllocations(Context context, ScriptableObject scope, int maxCharacters) {
        Scriptable constructor = (Scriptable) ScriptableObject.getProperty(scope, "String", context);
        Scriptable prototype = (Scriptable) ScriptableObject.getProperty(constructor, "prototype", context);
        for (String name : new String[]{"repeat", "padStart", "padEnd"}) {
            Function nativeMethod = (Function) ScriptableObject.getProperty(prototype, name, context);
            LambdaFunction guard = new LambdaFunction(context, scope, name,
                    ((BaseFunction) nativeMethod).getLength(), (cx, callScope, thisObject, arguments) ->
                            guardedStringCall(cx, scope, callScope, thisObject, arguments,
                                    name, nativeMethod, maxCharacters));
            guard.setStandardPropertyAttributes(ScriptableObject.DONTENUM | ScriptableObject.READONLY);
            ScriptableObject.defineProperty(prototype, name, guard,
                    ((ScriptableObject) prototype).getAttributes(context, name), context);
        }
    }

    private static Object guardedStringCall(
            Context context, Scriptable scope, Scriptable callScope, Scriptable thisObject,
            Object[] arguments, String name, Function nativeMethod, int maxCharacters) {
        // Use the engine's coercions in native order, once, before checking UTF-16 allocation.
        String value = ScriptRuntime.toString(context,
                ScriptRuntimeES6.requireObjectCoercible(context, thisObject, "String", name));
        Scriptable receiver = ScriptRuntime.toObject(context, scope, value);
        if ("repeat".equals(name)) {
            double count = ScriptRuntime.toInteger(context, arguments, 0);
            if (count < 0 || count == Double.POSITIVE_INFINITY) {
                // Preserve the native RangeError without attempting an allocation.
                return nativeMethod.call(context, callScope, receiver, new Object[]{count});
            }
            if (!value.isEmpty() && count > (double) maxCharacters / value.length()) {
                throw stringBudgetExceeded();
            }
            return nativeMethod.call(context, callScope, receiver, new Object[]{count});
        }
        long length = ScriptRuntime.toLength(context, arguments, 0);
        if (length <= value.length()) {
            return nativeMethod.call(context, callScope, receiver, new Object[]{length});
        }
        String fill = arguments.length < 2 || Undefined.isUndefined(arguments[1])
                ? " " : ScriptRuntime.toString(context, arguments[1]);
        if (!fill.isEmpty() && length > maxCharacters) {
            throw stringBudgetExceeded();
        }
        return nativeMethod.call(context, callScope, receiver, new Object[]{length, fill});
    }

    private static JavascriptExecutionException stringBudgetExceeded() {
        return new JavascriptExecutionException("javascript_result_budget_exceeded",
                "Requested string allocation exceeds the execution budget");
    }
}
