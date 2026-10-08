package dev.latvian.mods.rhino;
import com.google.gson.*;
import dev.latvian.mods.rhino.type.EnumTypeInfo;
import dev.latvian.mods.rhino.util.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class BindingDispatchTest {
    enum Named implements RemappedEnumConstant {
        REMAPPED { public String getRemappedEnumConstantName() { return "alias"; } },
        PLAIN { public String getRemappedEnumConstantName() { return ""; } };
    }
    @Test void gsonDispatchKeepsNullScalarsMapsListsAndExistingJsonIdentity() {
        Context cx = new ContextFactory().enter(); JsonPrimitive primitive = new JsonPrimitive("x");
        assertSame(primitive, NativeGSON.stringify0(cx, primitive));
        assertSame(JsonNull.INSTANCE, NativeGSON.stringify0(cx, null));
        assertEquals(new JsonPrimitive(true), NativeGSON.stringify0(cx, true));
        assertEquals(new JsonPrimitive(3), NativeGSON.stringify0(cx, 3));
        assertEquals(new JsonPrimitive("text"), NativeGSON.stringify0(cx, new StringBuilder("text")));
        Map<String,Object> map = new LinkedHashMap<>(); map.put("a", List.of(1, 2));
        assertEquals("{\"a\":[1,2]}", NativeGSON.stringify0(cx, map).toString());
        NativeObject object = new NativeObject(cx.factory); object.put(cx, "x", object, "y"); object.put(cx, 0, object, 7);
        JsonObject json = NativeGSON.stringify0(cx, object).getAsJsonObject();
        assertEquals("y", json.get("x").getAsString()); assertEquals(7, json.get("0").getAsInt());
    }
    @Test void customToStringAndIterableMapDispatchKeepOrderAndSingleCallback() {
        Context cx = new ContextFactory().enter(); int[] count = {0};
        ToStringJS custom = new ToStringJS() { public String toStringJS(Context context) { count[0]++; return "custom"; } };
        assertEquals("custom", ToStringJS.toStringJS(cx, custom)); assertEquals(1, count[0]);
        assertEquals("null", ToStringJS.toStringJS(cx, null));
        assertEquals("[1, 2]", ToStringJS.toStringJS(cx, List.of(1, 2)));
        Map<String,Object> map = new LinkedHashMap<>(); map.put("a", List.of(1));
        assertEquals("{a: [1]}", ToStringJS.toStringJS(cx, map));
    }
    @Test void javascriptExceptionExtractsWrappedThrowableExactlyOnce() {
        Context cx = new ContextFactory().enter(); RuntimeException cause = new RuntimeException("cause"); int[] calls = {0};
        Wrapper wrapped = () -> { calls[0]++; return cause; };
        JavaScriptException exception = new JavaScriptException(cx, wrapped, "test.js", 2);
        assertSame(cause, exception.getCause()); assertSame(wrapped, exception.getValue()); assertEquals(1, calls[0]);
        NativeError error = new NativeError(cx); error.put(cx, "javaException", error, wrapped);
        JavaScriptException fromError = new JavaScriptException(cx, error, "test.js", 3);
        assertSame(cause, fromError.getCause()); assertEquals(2, calls[0]);
        assertNull(new JavaScriptException(cx, null, "test.js", 4).getCause());
    }
    @Test void enumAndSpecialEqualityPreserveCustomCallbackAndFallback() {
        Context cx = new ContextFactory().enter(); int[] calls = {0};
        SpecialEquality special = new SpecialEquality() {
            public boolean specialEquals(Context context, Object value, boolean shallow) { calls[0]++; return "yes".equals(value) && shallow; }
        };
        assertTrue(SpecialEquality.checkSpecialEquality(cx, special, special, false)); assertEquals(0, calls[0]);
        assertTrue(SpecialEquality.checkSpecialEquality(cx, special, "yes", true)); assertEquals(1, calls[0]);
        assertEquals("alias", EnumTypeInfo.getName(Named.REMAPPED)); assertEquals("PLAIN", EnumTypeInfo.getName(Named.PLAIN));
        assertTrue(SpecialEquality.checkSpecialEquality(cx, Named.REMAPPED, "ALIAS", false));
        assertTrue(SpecialEquality.checkSpecialEquality(cx, Named.PLAIN, 1, false));
        assertFalse(SpecialEquality.checkSpecialEquality(cx, Named.PLAIN, null, false));
    }
    @Test void accessorEvalAndWithRecognitionKeepActualGuestBehavior() {
        Context cx = new ContextFactory().enter(); ScriptableObject scope = cx.initStandardObjects();
        Object result = cx.evaluateString(scope,
            "var count=0;var value=0;var o={};Object.defineProperty(o,'x',{get:function(){count++;return value;},set:function(v){count++;value=v;}});o.x=7;var got=o.x;[got,count,eval('2+3')].join(',');",
            "test.js", 1, null);
        assertEquals("7,2,5", ScriptRuntime.toString(cx, result));
        assertTrue(NativeGlobal.isEvalFunction(ScriptableObject.getProperty(scope, "eval", cx)));
        assertFalse(NativeGlobal.isEvalFunction(null)); assertFalse(NativeWith.isWithFunction(null));
    }
}
