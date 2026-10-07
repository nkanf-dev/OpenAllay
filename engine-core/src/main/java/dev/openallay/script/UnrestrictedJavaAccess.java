package dev.openallay.script;

import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.NativeJavaClass;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.latvian.mods.rhino.Undefined;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.openallay.script.host.RhinoHostAdapter;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Exact-signature Java access for an already-authorized unrestricted execution.
 *
 * <p>This facade does not change Rhino member caches, open modules, change final-field rules,
 * or move calls to a game-owning thread. Reflection runs on the calling JavaScript worker.
 */
final class UnrestrictedJavaAccess {
    private final Context context;
    private final ScriptableObject scope;
    private final RhinoHostAdapter metadata;

    private UnrestrictedJavaAccess(Context context, ScriptableObject scope) {
        this.context = context;
        this.scope = scope;
        this.metadata = new RhinoHostAdapter(context, scope);
    }

    static ScriptableObject bind(Context context, ScriptableObject scope) {
        UnrestrictedJavaAccess access = new UnrestrictedJavaAccess(context, scope);
        ScriptableObject java = (ScriptableObject) context.newObject(scope);
        access.define(java, "type", 1, args -> context.wrapJavaClass(scope,
                access.resolveName(access.name(args[0], "class name"),
                        context.getApplicationClassLoader(), true)));
        access.define(java, "classOf", 1, args -> context.wrapJavaClass(scope,
                access.target(args[0]).type()));
        access.define(java, "inspect", 1, args -> access.metadata.adapt(
                access.describe(access.target(args[0]).type())));
        access.define(java, "get", 2, args -> access.get(args[0], args[1]));
        access.define(java, "set", 3, args -> access.set(args[0], args[1], args[2]));
        access.define(java, "invoke", 4, args -> access.invoke(args[0], args[1], args[2], args[3]));
        access.define(java, "construct", 3, args -> access.construct(args[0], args[1], args[2]));
        java.preventExtensions();
        return java;
    }

    @FunctionalInterface
    private interface Operation {
        Object call(Object[] arguments) throws ReflectiveOperationException;
    }

    private void define(ScriptableObject java, String name, int arity, Operation operation) {
        BaseFunction function = new BaseFunction(
                scope, ScriptableObject.getFunctionPrototype(scope, context)) {
            @Override
            public String getFunctionName() {
                return name;
            }

            @Override
            public Object call(Context cx, Scriptable callScope, Scriptable thisObject, Object[] args) {
                checkBudget();
                if (args.length != arity) {
                    throw invalid("Java." + name + " requires " + arity + " arguments");
                }
                try {
                    Object result = operation.call(args);
                    checkBudget();
                    return result;
                } catch (InvocationTargetException failure) {
                    Throwable targetFailure = failure.getCause();
                    JavascriptFailureFormatter.rethrowControlFailure(targetFailure);
                    throw failure("javascript_java_target_error", targetFailure);
                } catch (IllegalAccessException | InaccessibleObjectException | SecurityException failure) {
                    throw failure("javascript_java_inaccessible", failure);
                } catch (ClassNotFoundException failure) {
                    throw failure("javascript_class_unavailable", failure);
                } catch (NoSuchFieldException | NoSuchMethodException failure) {
                    throw failure("javascript_java_member_unavailable", failure);
                } catch (ReflectiveOperationException failure) {
                    throw failure("javascript_java_target_error", failure);
                } catch (ExceptionInInitializerError failure) {
                    throw failure("javascript_java_target_error", failure);
                } catch (LinkageError failure) {
                    throw failure("javascript_class_unavailable", failure);
                } catch (RuntimeException failure) {
                    JavascriptFailureFormatter.rethrowControlFailure(failure);
                    throw failure("javascript_java_invalid", failure);
                }
            }

            @Override
            public Scriptable construct(Context cx, Scriptable callScope, Object[] args) {
                checkBudget();
                throw invalid("Java." + name + " is not a constructor; use Java.construct");
            }
        };
        ScriptableObject.defineProperty(java, name, function,
                ScriptableObject.READONLY | ScriptableObject.PERMANENT, context);
    }

    private void checkBudget() {
        final class $oaPattern0_Holder { dev.latvian.mods.rhino.Context value; OpenAllayRhinoContext bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = context) instanceof dev.openallay.script.OpenAllayRhinoContext && (($oaPattern0_holder.bound = (OpenAllayRhinoContext) $oaPattern0_holder.value) != null))) $oaPattern0_holder.bound.checkBudget();
    }

    private Object get(Object targetValue, Object selectorValue) throws ReflectiveOperationException {
        Target target = target(targetValue);
        Field field = field(target, selector(selectorValue));
        Object receiver = receiver(target, field.getModifiers(), field.toString());
        accessible(field);
        return wrap(field.get(receiver), field.getType());
    }

    private Object set(Object targetValue, Object selectorValue, Object value)
            throws ReflectiveOperationException {
        Target target = target(targetValue);
        Field field = field(target, selector(selectorValue));
        Object receiver = receiver(target, field.getModifiers(), field.toString());
        accessible(field);
        // Field.set owns the JVM's actual final/static-final/record restrictions.
        field.set(receiver, convert(value, field.getType(), field.getDeclaringClass().getClassLoader()));
        return Undefined.INSTANCE;
    }

    private Object invoke(Object targetValue, Object selectorValue, Object typesValue, Object argsValue)
            throws ReflectiveOperationException {
        Target target = target(targetValue);
        Method method = method(target, selector(selectorValue), typesValue);
        Object receiver = receiver(target, method.getModifiers(), method.toString());
        accessible(method);
        Object result = method.invoke(receiver,
                arguments(argsValue, method.getParameterTypes(), method.getDeclaringClass().getClassLoader()));
        return wrap(result, method.getReturnType());
    }

    private Object construct(Object typeValue, Object typesValue, Object argsValue)
            throws ReflectiveOperationException {
        Class<?> type = resolveType(typeValue, context.getApplicationClassLoader());
        Class<?>[] types = parameterTypes(typesValue, type.getClassLoader());
        Constructor<?> constructor = type.getDeclaredConstructor(types);
        accessible(constructor);
        return wrap(constructor.newInstance(arguments(argsValue, types, type.getClassLoader())), type);
    }

    private Object wrap(Object value, Class<?> type) {
        if (type == void.class) return Undefined.INSTANCE;
        final class $oaPattern1_Holder { java.lang.Object value; Character bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = value) instanceof java.lang.Character && (($oaPattern1_holder.bound = (Character) $oaPattern1_holder.value) != null))) return $oaPattern1_holder.bound.toString();
        final class $oaPattern2_Holder { java.lang.Object value; Class<?> bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = value) instanceof java.lang.Class && (($oaPattern2_holder.bound = (Class<?>) $oaPattern2_holder.value) != null))) return context.wrapJavaClass(scope, $oaPattern2_holder.bound);
        return context.wrap(scope, value, TypeInfo.of(type));
    }

    @dev.openallay.value.ValueType(Target.ValueSchemaProvider.class)
private static final class Target {
    private final Class<?> type;
    private final Object instance;
    private Target(Class<?> type, Object instance) {
        this.type = type;
        this.instance = instance;
    }
    public Class<?> type() { return type; }
    public Object instance() { return instance; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Target)) return false;
        Target that = (Target) other;
        return java.util.Objects.equals(type, that.type) && java.util.Objects.equals(instance, that.instance);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + java.util.Objects.hashCode(instance);
        return hash;
    }
    @Override public String toString() { return "Target[type=" + type + ", instance=" + instance + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Target> schema() {
            return new dev.openallay.value.ValueSchema<>(Target.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Target>>asList(new dev.openallay.value.ValueSchema.Component<>(Target.class, "type", Target::type), new dev.openallay.value.ValueSchema.Component<>(Target.class, "instance", Target::instance)), arguments -> new Target((Class) arguments[0], (Object) arguments[1]));
        }
    }
}

    private Target target(Object value) {
        Object raw = unwrap(value);
        if (raw == null || Undefined.isUndefined(raw)) {
            throw invalid("Java target must not be null or undefined");
        }
        if (raw instanceof Scriptable && !(value instanceof Wrapper)) {
            throw invalid("Java target must be a Java class, wrapped Java instance, or scalar, not a JavaScript object");
        }
        final class $oaPattern3_Holder { java.lang.Object value; Class<?> bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
return (($oaPattern3_holder.value = raw) instanceof java.lang.Class && (($oaPattern3_holder.bound = (Class<?>) $oaPattern3_holder.value) != null)) ? new Target($oaPattern3_holder.bound, null) : new Target(raw.getClass(), raw);
    }

    /** Host-side unwrap only; never requests a guest-visible .class/getClass property. */
    private static Object unwrap(Object value) {
        final class $oaPattern4_Holder { java.lang.Object value; NativeJavaClass bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = value) instanceof dev.latvian.mods.rhino.NativeJavaClass && (($oaPattern4_holder.bound = (NativeJavaClass) $oaPattern4_holder.value) != null))) return $oaPattern4_holder.bound.getClassObject();
        final class $oaPattern5_Holder { java.lang.Object value; Wrapper bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
return (($oaPattern5_holder.value = value) instanceof dev.latvian.mods.rhino.Wrapper && (($oaPattern5_holder.bound = (Wrapper) $oaPattern5_holder.value) != null)) ? $oaPattern5_holder.bound.unwrap() : value;
    }

    private Object receiver(Target target, int modifiers, String member) {
        if (Modifier.isStatic(modifiers)) return null;
        if (target.instance() == null) {
            throw invalid("A class target accepts only static members: " + member);
        }
        return target.instance();
    }

    private static void accessible(AccessibleObject member) {
        if (!member.trySetAccessible()) {
            Class<?> declaration = ((Member) member).getDeclaringClass();
            Module owner = declaration.getModule();
            Module bridge = UnrestrictedJavaAccess.class.getModule();
            throw new InaccessibleObjectException("Module "
                    + (owner.isNamed() ? owner.getName() : "<unnamed>")
                    + " does not open package " + declaration.getPackageName()
                    + " to bridge module " + (bridge.isNamed() ? bridge.getName() : "<unnamed>")
                    + ": " + member);
        }
    }

    @dev.openallay.value.ValueType(Selector.ValueSchemaProvider.class)
private static final class Selector {
    private final String name;
    private final Object declaringClass;
    private final Object returnType;
    private final Object parameterTypes;
    private Selector(String name, Object declaringClass, Object returnType, Object parameterTypes) {
        this.name = name;
        this.declaringClass = declaringClass;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes;
    }
    public String name() { return name; }
    public Object declaringClass() { return declaringClass; }
    public Object returnType() { return returnType; }
    public Object parameterTypes() { return parameterTypes; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Selector)) return false;
        Selector that = (Selector) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(declaringClass, that.declaringClass) && java.util.Objects.equals(returnType, that.returnType) && java.util.Objects.equals(parameterTypes, that.parameterTypes);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(declaringClass);
        hash = 31 * hash + java.util.Objects.hashCode(returnType);
        hash = 31 * hash + java.util.Objects.hashCode(parameterTypes);
        return hash;
    }
    @Override public String toString() { return "Selector[name=" + name + ", declaringClass=" + declaringClass + ", returnType=" + returnType + ", parameterTypes=" + parameterTypes + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Selector> schema() {
            return new dev.openallay.value.ValueSchema<>(Selector.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Selector>>asList(new dev.openallay.value.ValueSchema.Component<>(Selector.class, "name", Selector::name), new dev.openallay.value.ValueSchema.Component<>(Selector.class, "declaringClass", Selector::declaringClass), new dev.openallay.value.ValueSchema.Component<>(Selector.class, "returnType", Selector::returnType), new dev.openallay.value.ValueSchema.Component<>(Selector.class, "parameterTypes", Selector::parameterTypes)), arguments -> new Selector((String) arguments[0], (Object) arguments[1], (Object) arguments[2], (Object) arguments[3]));
        }
    }
}

    private Selector selector(Object value) {
        if (value instanceof CharSequence) return new Selector(name(value, "member name"), null, null, null);
        if (!(value instanceof Scriptable) && !(value instanceof Map<?, ?>)) {
            throw invalid("Member selector must be a name or an inspect member descriptor");
        }
        return new Selector(name(property(value, "name"), "descriptor name"),
                property(value, "declaringClass"), property(value, "returnType"),
                property(value, "parameterTypes"));
    }

    private Object property(Object value, String name) {
        final class $oaPattern6_Holder { java.lang.Object value; Scriptable bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
Object result = (($oaPattern6_holder.value = value) instanceof dev.latvian.mods.rhino.Scriptable && (($oaPattern6_holder.bound = (Scriptable) $oaPattern6_holder.value) != null))
                ? ScriptableObject.getProperty($oaPattern6_holder.bound, name, context)
                : ((Map<?, ?>) value).get(name);
        return result == Scriptable.NOT_FOUND || Undefined.isUndefined(result) ? null : result;
    }

    private Field field(Target target, Selector selector) throws ReflectiveOperationException {
        if (selector.declaringClass() != null) {
            return declaringClass(target, selector.declaringClass()).getDeclaredField(selector.name());
        }
        for (Class<?> type : hierarchy(target.type())) {
            try {
                return type.getDeclaredField(selector.name());
            } catch (NoSuchFieldException ignored) {
                // Continue to the nearest declaration in the hierarchy.
            }
        }
        throw new NoSuchFieldException(target.type().getName() + "." + selector.name());
    }

    private Method method(Target target, Selector selector, Object typesValue)
            throws ReflectiveOperationException {
        List<Class<?>> declarations = selector.declaringClass() == null
                ? hierarchy(target.type()) : dev.openallay.util.Java8Collections.listOf(declaringClass(target, selector.declaringClass()));
        ClassLoader loader = selector.declaringClass() == null
                ? target.type().getClassLoader() : declarations.get(0).getClassLoader();
        Class<?>[] types = parameterTypes(typesValue, loader);
        if (selector.parameterTypes() != null
                && !Arrays.equals(types, parameterTypes(selector.parameterTypes(), loader))) {
            throw invalid("Explicit parameterTypes do not match the inspect method descriptor");
        }
        Class<?> returnType = selector.returnType() == null ? null : resolveType(selector.returnType(), loader);
        for (Class<?> declaration : declarations) {
            if (returnType != null) {
                // Covariant and bridge methods can share name/parameters but differ in return type.
                for (Method method : declaration.getDeclaredMethods()) {
                    if (method.getName().equals(selector.name())
                            && Arrays.equals(method.getParameterTypes(), types)
                            && method.getReturnType() == returnType) return method;
                }
            } else {
                try {
                    // Let the JDK select the most-specific return type for an erased signature.
                    return declaration.getDeclaredMethod(selector.name(), types);
                } catch (NoSuchMethodException ignored) {
                    // Continue to the nearest declaration in the hierarchy.
                }
            }
        }
        throw new NoSuchMethodException(target.type().getName() + "." + selector.name()
                + Arrays.toString(types) + (returnType == null ? "" : " -> " + returnType.getTypeName()));
    }

    private Class<?> declaringClass(Target target, Object value) {
        Object raw = unwrap(value);
        for (Class<?> declaration : hierarchy(target.type())) {
            final class $oaPattern7_Holder { java.lang.Object value; Class<?> bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = raw) instanceof java.lang.Class && (($oaPattern7_holder.bound = (Class<?>) $oaPattern7_holder.value) != null)) ? declaration == $oaPattern7_holder.bound
                    : declaration.getName().equals(name(raw, "declaringClass"))) return declaration;
        }
        throw invalid("Descriptor declaringClass is not in the target hierarchy");
    }

    private Class<?>[] parameterTypes(Object value, ClassLoader loader) throws ClassNotFoundException {
        Object[] entries = sequence(value, "parameterTypes");
        Class<?>[] types = new Class<?>[entries.length];
        for (int index = 0; index < entries.length; index++) {
            types[index] = resolveType(entries[index], loader);
            if (types[index] == void.class) throw invalid("void is not a parameter type");
        }
        return types;
    }

    private Object[] arguments(Object value, Class<?>[] types, ClassLoader loader)
            throws ClassNotFoundException {
        Object[] entries = sequence(value, "arguments");
        if (entries.length != types.length) {
            throw invalid("arguments length must match parameterTypes; varargs require one actual array argument");
        }
        Object[] converted = new Object[entries.length];
        for (int index = 0; index < entries.length; index++) {
            converted[index] = convert(entries[index], types[index], loader);
        }
        return converted;
    }

    private Object convert(Object value, Class<?> type, ClassLoader loader) throws ClassNotFoundException {
        Object raw = unwrap(value);
        if (type.isPrimitive() && (raw == null || Undefined.isUndefined(raw))) {
            throw failure("javascript_java_conversion_error", new IllegalArgumentException(
                    "null or undefined cannot convert to primitive " + type.getName()));
        }
        try {
            // Rhino's Class conversion resolves names through its own loader; keep the actual target loader.
            if (type == Class.class && raw != null && !Undefined.isUndefined(raw)) return resolveType(raw, loader);
            // JS numbers have only 53 exact bits. A decimal string preserves the full Java long range.
            if (type == long.class || type == Long.class) {
                if (raw instanceof Long) return raw;
                final class $oaPattern8_Holder { java.lang.Object value; CharSequence bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = raw) instanceof java.lang.CharSequence && (($oaPattern8_holder.bound = (CharSequence) $oaPattern8_holder.value) != null))) return Long.parseLong($oaPattern8_holder.bound.toString());
            }
            return context.jsToJava(value, TypeInfo.of(type));
        } catch (RuntimeException failure) {
            JavascriptFailureFormatter.rethrowControlFailure(failure);
            throw failure("javascript_java_conversion_error", failure);
        }
    }

    private Object[] sequence(Object value, String label) {
        Object raw = unwrap(value);
        if (raw != null && raw.getClass().isArray()) {
            Object[] entries = new Object[Array.getLength(raw)];
            for (int index = 0; index < entries.length; index++) entries[index] = Array.get(raw, index);
            return entries;
        }
        final class $oaPattern9_Holder { java.lang.Object value; List<?> bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = raw) instanceof java.util.List && (($oaPattern9_holder.bound = (List<?>) $oaPattern9_holder.value) != null))) return $oaPattern9_holder.bound.toArray();
        final class $oaPattern10_Holder { java.lang.Object value; Scriptable bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = raw) instanceof dev.latvian.mods.rhino.Scriptable && (($oaPattern10_holder.bound = (Scriptable) $oaPattern10_holder.value) != null))) {
            Object length = ScriptableObject.getProperty($oaPattern10_holder.bound, "length", context);
            final class $oaPattern11_Holder { java.lang.Object value; Number bound; }
final $oaPattern11_Holder $oaPattern11_holder = new $oaPattern11_Holder();
if ((($oaPattern11_holder.value = length) instanceof java.lang.Number && (($oaPattern11_holder.bound = (Number) $oaPattern11_holder.value) != null)) && $oaPattern11_holder.bound.doubleValue() >= 0
                    && $oaPattern11_holder.bound.doubleValue() <= Integer.MAX_VALUE
                    && $oaPattern11_holder.bound.doubleValue() == Math.rint($oaPattern11_holder.bound.doubleValue())) {
                Object[] entries = new Object[$oaPattern11_holder.bound.intValue()];
                for (int index = 0; index < entries.length; index++) {
                    Object entry = ScriptableObject.getProperty($oaPattern10_holder.bound, index, context);
                    entries[index] = entry == Scriptable.NOT_FOUND ? Undefined.INSTANCE : entry;
                }
                return entries;
            }
        }
        throw invalid(label + " must be an array or list");
    }

    private Class<?> resolveType(Object value, ClassLoader loader) throws ClassNotFoundException {
        Object raw = unwrap(value);
        final class $oaPattern12_Holder { java.lang.Object value; Class<?> bound; }
final $oaPattern12_Holder $oaPattern12_holder = new $oaPattern12_Holder();
if ((($oaPattern12_holder.value = raw) instanceof java.lang.Class && (($oaPattern12_holder.bound = (Class<?>) $oaPattern12_holder.value) != null))) return $oaPattern12_holder.bound;
        return resolveName(name(raw, "type"), loader, false);
    }

    private Class<?> resolveName(String name, ClassLoader loader, boolean initialize)
            throws ClassNotFoundException {
        Class<?> primitive = switch (name) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "char" -> char.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "void" -> void.class;
            default -> null;
        };
        if (primitive != null) return primitive;
        if (name.endsWith("[]")) {
            Class<?> component = resolveName(name.substring(0, name.length() - 2), loader, false);
            if (component == void.class) throw invalid("void[] is not a Java type");
            return component.arrayType();
        }
        // Support conventional String[] as well as binary names and JVM [I/[Ljava.lang.String; forms.
        return Class.forName(name.equals("String") ? "java.lang.String" : name, initialize, loader);
    }

    private String name(Object value, String label) {
        final class $oaPattern13_Holder { java.lang.Object value; CharSequence bound; }
final $oaPattern13_Holder $oaPattern13_holder = new $oaPattern13_Holder();
if (!((($oaPattern13_holder.value = value) instanceof java.lang.CharSequence && (($oaPattern13_holder.bound = (CharSequence) $oaPattern13_holder.value) != null))) || (($oaPattern13_holder.bound).length() == 0)) {
            throw invalid(label + " must be a nonempty string");
        }
        return $oaPattern13_holder.bound.toString();
    }

    /** Class chain first, then inherited interfaces. No accessible flags or shared caches are changed. */
    private static List<Class<?>> hierarchy(Class<?> target) {
        LinkedHashSet<Class<?>> types = new LinkedHashSet<>();
        for (Class<?> current = target; current != null; current = current.getSuperclass()) types.add(current);
        for (Class<?> current : dev.openallay.util.Java8Collections.listCopyOf(types)) addInterfaces(current, types);
        return dev.openallay.util.Java8Collections.listCopyOf(types);
    }

    private static void addInterfaces(Class<?> type, LinkedHashSet<Class<?>> types) {
        for (Class<?> implemented : type.getInterfaces()) {
            if (types.add(implemented)) addInterfaces(implemented, types);
        }
    }

    private Map<String, Object> describe(Class<?> type) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", type.getName());
        result.put("typeName", type.getTypeName());
        result.put("superclass", type.getSuperclass() == null ? null : type.getSuperclass().getName());
        result.put("interfaces", typeNames(type.getInterfaces()));
        result.put("primitive", type.isPrimitive());
        result.put("array", type.isArray());
        result.put("componentType", type.isArray() ? type.getComponentType().getTypeName() : null);
        result.put("modifiers", Modifier.toString(type.getModifiers()));
        result.put("modifierBits", type.getModifiers());
        Module module = type.getModule();
        String packageName = type.isPrimitive() || type.isArray() ? null : type.getPackageName();
        Map<String, Object> moduleView = new LinkedHashMap<>();
        moduleView.put("name", module.getName());
        moduleView.put("named", module.isNamed());
        moduleView.put("automatic", module.getDescriptor() != null && module.getDescriptor().isAutomatic());
        moduleView.put("packageName", packageName);
        moduleView.put("packageOpenToBridge", packageName != null
                && module.isOpen(packageName, UnrestrictedJavaAccess.class.getModule()));
        result.put("module", moduleView);
        ClassLoader loader = type.getClassLoader();
        if (loader == null) result.put("classLoader", null);
        else {
            Map<String, Object> loaderView = new LinkedHashMap<>();
            loaderView.put("name", loader.getName());
            loaderView.put("type", loader.getClass().getName());
            loaderView.put("identity", Integer.toHexString(System.identityHashCode(loader)));
            result.put("classLoader", loaderView);
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        List<Map<String, Object>> methods = new ArrayList<>();
        for (Class<?> declaration : hierarchy(type)) {
            Field[] declaredFields = declaration.getDeclaredFields();
            Arrays.sort(declaredFields, Comparator.comparing(Field::getName));
            for (Field field : declaredFields) {
                Map<String, Object> view = member(field.getDeclaringClass(), field.getModifiers());
                view.put("name", field.getName());
                view.put("type", field.getType().getTypeName());
                view.put("final", Modifier.isFinal(field.getModifiers()));
                view.put("synthetic", field.isSynthetic());
                fields.add(view);
            }
            Method[] declaredMethods = declaration.getDeclaredMethods();
            Arrays.sort(declaredMethods, Comparator.comparing(Method::getName)
                    .thenComparing(method -> Arrays.toString(method.getParameterTypes()))
                    .thenComparing(method -> method.getReturnType().getName()));
            for (Method method : declaredMethods) {
                Map<String, Object> view = member(method.getDeclaringClass(), method.getModifiers());
                view.put("name", method.getName());
                view.put("parameterTypes", typeNames(method.getParameterTypes()));
                view.put("returnType", method.getReturnType().getTypeName());
                view.put("varArgs", method.isVarArgs());
                view.put("bridge", method.isBridge());
                view.put("synthetic", method.isSynthetic());
                methods.add(view);
            }
        }
        List<Map<String, Object>> constructors = new ArrayList<>();
        Constructor<?>[] declaredConstructors = type.getDeclaredConstructors();
        Arrays.sort(declaredConstructors, Comparator.comparing(constructor -> Arrays.toString(constructor.getParameterTypes())));
        for (Constructor<?> constructor : declaredConstructors) {
            Map<String, Object> view = member(constructor.getDeclaringClass(), constructor.getModifiers());
            view.remove("static");
            view.put("parameterTypes", typeNames(constructor.getParameterTypes()));
            view.put("varArgs", constructor.isVarArgs());
            view.put("synthetic", constructor.isSynthetic());
            constructors.add(view);
        }
        result.put("fields", fields);
        result.put("methods", methods);
        result.put("constructors", constructors);
        return result;
    }

    private static Map<String, Object> member(Class<?> declaration, int modifiers) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("declaringClass", declaration.getName());
        result.put("modifiers", Modifier.toString(modifiers));
        result.put("modifierBits", modifiers);
        result.put("static", Modifier.isStatic(modifiers));
        return result;
    }

    private static List<String> typeNames(Class<?>[] types) {
        return dev.openallay.util.Java8Collections.toList(Arrays.stream(types).map(Class::getTypeName));
    }

    private static JavascriptExecutionException invalid(String message) {
        return new JavascriptExecutionException("javascript_java_invalid", message);
    }

    private static JavascriptExecutionException failure(String code, Throwable failure) {
        String message = failure.getClass().getName();
        if (failure.getMessage() != null && !dev.openallay.util.Java8Strings.isBlank(failure.getMessage())) message += ": " + failure.getMessage();
        if (failure instanceof ExceptionInInitializerError && failure.getCause() != null) {
            Throwable cause = failure.getCause();
            message += " (caused by " + cause.getClass().getName()
                    + (cause.getMessage() == null ? "" : ": " + cause.getMessage()) + ")";
        }
        return new JavascriptExecutionException(code, message, failure);
    }
}
