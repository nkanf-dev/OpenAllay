package dev.latvian.mods.rhino;
import dev.latvian.mods.rhino.type.TypeInfo;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

/** Actual complete canonical runtime fixture, usable with genuine javac8/java8. */
public final class CanonicalDefaultGraphFixture {
    public interface Parent {
        String callback(String value);
        default String text() { return "parent"; }
        default Object self() { return this; }
        default String call() { return callback("value"); }
        default int add(int a, int b) { return a + b; }
        default long wide(long value) { return value + 1; }
        default double fraction(double value) { return value / 2; }
        default boolean flag(boolean value) { return !value; }
        default void fatal() { throw new AssertionError("fatal"); }
        default String many(String... values) { return String.join(",", values); }
        default void runtimeFailure() { throw new IllegalStateException("runtime"); }
        default void checkedFailure() throws IOException { throw new IOException("checked"); }
    }
    public interface Child extends Parent { default String text() { return "child"; } }
    public interface Left extends Parent {}
    public interface Right extends Parent {}
    public interface Diamond extends Left, Right {}
    public interface Reabstract extends Parent { String text(); }
    public interface First { default String conflict() { return "first"; } }
    public interface Second { default String conflict() { return "second"; } }
    public interface Generic<T> { default T identity(T value) { return value; } }
    public interface Specialized extends Generic<String> { default String identity(String value) { return "special:" + value; } }
    public interface OnlyDefault { default String text() { return "default-only"; } }
    interface Hidden { default String text() { return "hidden"; } }
    static int checks;
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("check=" + checks); }
    static Object adapt(Context cx, ScriptableObject scope, Class<?> type, String guestSource) {
        BaseFunction make = new BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope, cx)) {
            @Override public Object call(Context context, Scriptable callScope, Scriptable thisObject, Object[] args) {
                return context.jsToJava(args[0], TypeInfo.of(type));
            }
        };
        ScriptableObject.putProperty(scope, "make", make, cx);
        return Wrapper.unwrapped(cx.evaluateString(scope, "make(" + guestSource + ")", "default.js", 1, null));
    }
    static void parentGraph(Class<?> type, String expected) throws Throwable {
        Context cx = new ContextFactory().enter(); ScriptableObject scope = cx.initStandardObjects();
        cx.evaluateString(scope, "var defaultCallbackCount=0;", "counter.js", 1, null);
        Parent value = (Parent) adapt(cx, scope, type, "({callback:function(v){defaultCallbackCount++;return 'js:'+v;}})");
        check(value.text().equals(expected)); check(value.self() == value); check(value.call().equals("js:value"));
        check(((Number) cx.evaluateString(scope, "defaultCallbackCount", "counter.js", 1, null)).intValue() == 1);
        check(value.add(2, 3) == 5); check(value.many("a", "b").equals("a,b"));
        check(value.wide(9L) == 10L); check(value.fraction(8D) == 4D); check(!value.flag(true));
        try { value.fatal(); throw new AssertionError("Missing fatal"); }
        catch (AssertionError failure) { check(failure.getMessage().equals("fatal")); }
        try { value.runtimeFailure(); throw new AssertionError("missing RuntimeException"); }
        catch (IllegalStateException failure) { check(failure.getMessage().equals("runtime")); }
        try { value.checkedFailure(); throw new AssertionError("missing checked exception"); }
        catch (IOException failure) { check(failure.getMessage().equals("checked")); }
        check(Proxy.isProxyClass(value.getClass())); check(Proxy.getInvocationHandler(value) instanceof VMBridge.AdapterInvocationHandler);
        VMBridge.AdapterInvocationHandler handler = (VMBridge.AdapterInvocationHandler) Proxy.getInvocationHandler(value);
        check(value.equals(value)); check(!value.equals(new Object())); check(value.hashCode() == handler.target().hashCode());
        check(value.toString().equals("Proxy[" + handler.target() + "]"));
        boolean java8 = "1.8".equals(System.getProperty("java.specification.version"));
        check(value.getClass().getInterfaces().length == (java8 ? 2 : 1));
        // Same associated guest object conversion reuses existing cached proxy identity.
        BaseFunction reuse = new BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope, cx)) {
            @Override public Object call(Context context, Scriptable callScope, Scriptable thisObject, Object[] args) {
                Object a = context.jsToJava(args[0], TypeInfo.of(type)); Object b = context.jsToJava(args[0], TypeInfo.of(type)); return a == b;
            }
        };
        ScriptableObject.putProperty(scope, "reuse", reuse, cx);
        check(Boolean.TRUE.equals(cx.evaluateString(scope, "reuse({callback:function(v){return v;}})", "reuse.js", 1, null)));
        Parent override = (Parent) adapt(cx, scope, type, "({callback:function(v){return v;},text:function(){return 'override';}})");
        check(override.text().equals("override"));
        if (type == Child.class) {
            try { handler.invoke(value, Parent.class.getMethod("text"), null); throw new AssertionError("overridden parent default accepted"); }
            catch (IllegalArgumentException expectedFailure) { checks++; }
        }
    }
    static void conflictGraph() throws Throwable {
        Context cx = new ContextFactory().enter(); ScriptableObject scope = cx.initStandardObjects(); Scriptable target = cx.newObject(scope);
        Object helper = VMBridge.getInterfaceProxyHelper(cx, new Class<?>[]{First.class, Second.class});
        Object value = VMBridge.newInterfaceProxy(helper, null, target, scope, cx);
        check(((First) value).conflict().equals("first")); check(((Second) value).conflict().equals("first"));
        helper = VMBridge.getInterfaceProxyHelper(cx, new Class<?>[]{Second.class, First.class});
        value = VMBridge.newInterfaceProxy(helper, null, target, scope, cx);
        check(((First) value).conflict().equals("second"));
    }

    static void rejectionGraph() throws Throwable {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        try { VMBridge.getInterfaceProxyHelper(cx, new Class<?>[]{String.class}); throw new AssertionError("class accepted as interface"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { VMBridge.getInterfaceProxyHelper(cx, new Class<?>[]{Parent.class, Parent.class}); throw new AssertionError("duplicate interface accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        if ("1.8".equals(System.getProperty("java.specification.version"))) {
            Context denied = new Context(new ContextFactory()) {
                @Override public GeneratedClassLoader createClassLoader(ClassLoader parent) { throw new SecurityException("denied"); }
            };
            try { VMBridge.getInterfaceProxyHelper(denied, new Class<?>[]{Parent.class}); throw new AssertionError("security denial ignored"); }
            catch (SecurityException expected) { checks++; }
        }
    }
    public static void main(String[] args) throws Throwable {
        parentGraph(Parent.class, "parent"); parentGraph(Child.class, "child"); parentGraph(Diamond.class, "parent");
        // Re-abstraction uses an explicit JS text method, not an inherited default.
        Context cx = new ContextFactory().enter(); ScriptableObject scope = cx.initStandardObjects();
        Reabstract abstracted = (Reabstract) adapt(cx, scope, Reabstract.class, "({callback:function(v){return v;},text:function(){return 'abstract-js';}})");
        check(abstracted.text().equals("abstract-js"));
        Specialized specialized = (Specialized) adapt(cx, scope, Specialized.class, "({})");
        check(specialized.identity("x").equals("special:x"));
        check(((Generic<String>) specialized).identity("x").equals("special:x"));
        OnlyDefault callable = (OnlyDefault) adapt(cx, scope, OnlyDefault.class, "function(){return 'not-default';}");
        check(callable.text().equals("default-only"));
        conflictGraph();
        rejectionGraph();
        if ("1.8".equals(System.getProperty("java.specification.version"))) {
            try { VMBridge.getInterfaceProxyHelper(cx, new Class<?>[]{Hidden.class}); throw new AssertionError("hidden interface accepted"); }
            catch (IllegalAccessError expected) { checks++; }
        }
        Object ordinary = cx.evaluateString(scope, "[1,2,3].map(function(x){return x*2;}).join(',')", "ordinary.js", 1, null);
        check(ScriptRuntime.toString(cx, ordinary).equals("2,4,6"));
        check(!VMBridge.isRecord(String.class));
        System.out.println("PASS canonical default graph runtime=" + System.getProperty("java.version") + " checks=" + checks);
    }
}
