package dev.latvian.mods.rhino;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class BindingEqualityTest {
    @Test void resolvedOverloadsRetainNullWrappedArgumentsAndHash() {
        Wrapper wrapped = () -> "x";
        ResolvedOverload value = new ResolvedOverload(new Object[]{wrapped, null}, 2);
        assertTrue(value.matches(new Object[]{"x", null}));
        assertFalse(value.matches(new Object[]{"x"}));
        assertFalse(value.matches(new Object[]{1, null}));
        assertEquals(value, new ResolvedOverload(new Object[]{"x", null}, 2));
        assertNotEquals(value, new ResolvedOverload(new Object[]{"x", null}, 3));
        assertFalse(value.equals(null)); assertFalse(value.equals("x"));
        assertEquals(new ResolvedOverload(new Object[]{"x", null}, 2).hashCode(), value.hashCode());
    }
    @Test void wrapperRecursionEvaluatesEachActualWrapperOnce() {
        int[] calls = {0}; Object leaf = new Object();
        Wrapper inner = () -> { calls[0]++; return leaf; };
        Wrapper outer = () -> { calls[0]++; return inner; };
        assertSame(leaf, Wrapper.unwrapped(outer)); assertEquals(2, calls[0]);
        assertSame(leaf, Wrapper.unwrapped(leaf)); assertNull(Wrapper.unwrapped(null));
    }
    @Test void nativeJavaArrayEqualityKeepsUnderlyingArrayIdentity() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        String[] array = {"x"};
        NativeJavaArray value = new NativeJavaArray(scope, array, dev.latvian.mods.rhino.type.TypeInfo.STRING_ARRAY, cx);
        assertEquals(value, new NativeJavaArray(scope, array, dev.latvian.mods.rhino.type.TypeInfo.STRING_ARRAY, cx));
        assertNotEquals(value, new NativeJavaArray(scope, array.clone(), dev.latvian.mods.rhino.type.TypeInfo.STRING_ARRAY, cx));
        assertFalse(value.equals(null)); assertFalse(value.equals(array));
        assertEquals(array.hashCode(), value.hashCode());
    }
}
