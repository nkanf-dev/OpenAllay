package dev.latvian.mods.rhino;
import dev.latvian.mods.rhino.type.TypeInfo;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class NativeGraphMemberBindingTest {
    public static final class Bean {
        public int field = 2; private int value = 3;
        public int getValue() { return value; } public void setValue(int value) { this.value = value; }
        public String method(String argument) { return "method:" + argument; }
    }
    @Test void javaMembersKeepBeanGetterSetterFieldAndMethodDispatch() {
        Context cx = new ContextFactory().enter(); ScriptableObject scope = cx.initStandardObjects(); Bean bean = new Bean();
        ScriptableObject.putProperty(scope, "bean", cx.wrapAsJavaObject(scope, bean, TypeInfo.of(Bean.class)), cx);
        Object result = cx.evaluateString(scope, "bean.field=7;bean.value=9;[bean.field,bean.value,bean.method('x')].join('|');", "bean.js", 1, null);
        assertEquals("7|9|method:x", ScriptRuntime.toString(cx, result)); assertEquals(7, bean.field); assertEquals(9, bean.getValue());
    }
    @Test void nativeJsonReviverAndFallbackStringifierKeepKeysAndOneCallbackPerProperty() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects(); List<String> seen = new ArrayList<>();
        Callable reviver = (context, callScope, holder, args) -> { seen.add(String.valueOf(args[0])); return args[1]; };
        Scriptable value = (Scriptable) NativeJSON.parse(cx, scope, "{\"a\":[1,2],\"0\":3}", reviver);
        assertEquals(5, seen.size()); assertEquals("", seen.get(seen.size()-1));
        assertEquals(3, ((Number) value.get(cx, 0, value)).intValue());
        Map<String,Object> map = new LinkedHashMap<>(); map.put("a", List.of(1, 2));
        assertEquals("{\"a\":[1,2]}", NativeJSON.stringify(map, null, null, cx));
    }
    @Test void ecmaStringIndexSymbolPropertiesKeepWriteAndHasOwnDispatch() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects(); Scriptable object = cx.newObject(scope);
        AbstractEcmaObjectOperations.put(cx, object, "x", 1, false);
        AbstractEcmaObjectOperations.put(cx, object, 2, "index", false);
        AbstractEcmaObjectOperations.put(cx, object, SymbolKey.TO_STRING_TAG, "tag", false);
        assertTrue(AbstractEcmaObjectOperations.hasOwnProperty(cx, object, "x"));
        assertTrue(AbstractEcmaObjectOperations.hasOwnProperty(cx, object, 2));
        assertTrue(AbstractEcmaObjectOperations.hasOwnProperty(cx, object, SymbolKey.TO_STRING_TAG));
    }
    @Test void graphSpecialFunctionsAndDefaultArityScopeTransformRetainPaths() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        ScriptableObject object = (ScriptableObject) cx.evaluateString(scope, "({a:1, nested:{b:2}})", "graph.js", 1, null);
        assertTrue(new EqualObjectGraphs().equalGraphs(cx, object, object));
        Object function = cx.evaluateString(scope, "(x)=>x+1", "graph.js", 1, null);
        assertTrue(new EqualObjectGraphs().equalGraphs(cx, function, function));
        Object result = cx.evaluateString(scope, "function f(a,b=2,c){return a+b;}var total=0;{let x=3;total=x;}[f.length,f(1),total].join('|');", "arity.js", 1, null);
        assertEquals("1|3|3", ScriptRuntime.toString(cx, result));
    }
}
