package dev.latvian.mods.rhino;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.latvian.mods.rhino.util.CustomJavaToJsWrapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ContextConversionBindingTest {
    @Test void scalarCoercionAndNullPrimitiveErrorsKeepBranches() {
        Context cx = new ContextFactory().enter();
        assertNull(cx.jsToJava(null, TypeInfo.STRING));
        assertNull(cx.jsToJava(null, TypeInfo.PRIMITIVE_INT));
        assertThrows(EvaluatorException.class, () -> cx.internalJsToJava(null, TypeInfo.PRIMITIVE_INT));
        assertEquals("undefined", cx.jsToJava(Undefined.INSTANCE, TypeInfo.STRING));
        assertEquals("true", cx.jsToJava(Boolean.TRUE, TypeInfo.STRING));
        assertEquals(Boolean.TRUE, cx.jsToJava(Boolean.TRUE, TypeInfo.BOOLEAN));
        assertEquals(Integer.valueOf(12), cx.jsToJava("12", TypeInfo.INT));
        assertEquals(Character.valueOf('x'), cx.jsToJava("x", TypeInfo.CHARACTER));
        assertEquals("7", cx.jsToJava(7, TypeInfo.STRING));
        assertFalse(cx.canConvert("12", TypeInfo.INT));
        assertTrue(cx.canConvert("12", TypeInfo.PRIMITIVE_INT));
        assertEquals(99, cx.getConversionWeight("12", TypeInfo.INT));
        assertEquals(4, cx.getConversionWeight("12", TypeInfo.PRIMITIVE_INT));
        assertFalse(cx.canConvert(null, TypeInfo.PRIMITIVE_INT));
    }
    @Test void nativeAndWrappedArraysListsMapsKeepElementConversionsAndIdentity() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        NativeArray array = new NativeArray(cx, new Object[]{"a", "b"});
        assertArrayEquals(new String[]{"a", "b"}, (String[]) cx.jsToJava(array, TypeInfo.STRING_ARRAY));
        assertEquals(List.of("a", "b"), cx.jsToJava(array, TypeInfo.RAW_LIST.withParams(TypeInfo.STRING)));
        Map<String,Object> input = new LinkedHashMap<>(); input.put("a", "1");
        assertEquals(Map.of("a", 1), cx.mapOf(input, TypeInfo.STRING, TypeInfo.INT));
        Scriptable wrapped = cx.wrapAsJavaObject(scope, input, TypeInfo.RAW_MAP.withParams(TypeInfo.STRING, TypeInfo.OBJECT));
        assertTrue(wrapped instanceof NativeJavaMap);
        Scriptable list = cx.wrapAsJavaObject(scope, new ArrayList<>(List.of("a")), TypeInfo.RAW_LIST.withParams(TypeInfo.STRING));
        assertTrue(list instanceof NativeJavaList);
        Scriptable set = cx.wrapAsJavaObject(scope, new LinkedHashSet<>(List.of("a")), TypeInfo.RAW_SET.withParams(TypeInfo.STRING));
        assertTrue(set instanceof NativeJavaList);
    }
    @Test void customWrapperCallbacksAndClassConversionsStaySingleAndExact() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects(); int[] calls = {0};
        Scriptable target = cx.newObject(scope);
        CustomJavaToJsWrapper wrapper = (context, callScope, type) -> { calls[0]++; assertSame(cx, context); assertSame(scope, callScope); return target; };
        assertSame(target, cx.wrapAsJavaObject(scope, wrapper, TypeInfo.OBJECT)); assertEquals(1, calls[0]);
        assertSame(String.class, cx.jsToJava(String.class, TypeInfo.CLASS));
        assertEquals(Character.valueOf('x').toString(), cx.javaToJS('x', scope));
    }
    @Test void exceptionDispatchStillUnwrapsReflectionAndPreservesErrors() throws Exception {
        Context cx = new ContextFactory().enter(); Error error = new AssertionError("fatal");
        assertSame(error, assertThrows(AssertionError.class, () -> Context.throwAsScriptRuntimeEx(error, cx)));
        EvaluatorException rhino = new EvaluatorException(cx, "guest");
        assertSame(rhino, assertThrows(EvaluatorException.class, () -> Context.throwAsScriptRuntimeEx(rhino, cx)));
        assertSame(rhino, assertThrows(EvaluatorException.class, () -> Context.throwAsScriptRuntimeEx(new java.lang.reflect.InvocationTargetException(rhino), cx)));
    }
}
