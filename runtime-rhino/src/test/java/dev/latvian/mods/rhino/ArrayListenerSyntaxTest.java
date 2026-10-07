package dev.latvian.mods.rhino;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.latvian.mods.rhino.util.ArrayValueProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ArrayListenerSyntaxTest {
    @Test void contextObjectArrayFastPathKeepsIdentityElementConversionsAndEmptyArrays() {
        Context cx = new ContextFactory().enter(); Object[] input = {"a", "b"};
        assertSame(input, cx.arrayOf(input, null));
        assertArrayEquals(new String[]{"a", "b"}, (String[]) cx.arrayOf(input, TypeInfo.STRING));
        ArrayValueProvider provider = cx.arrayValueProviderOf(input);
        assertEquals(2, provider.getLength(cx)); assertSame(input, provider.getErrorSource(cx));
        assertSame(ArrayValueProvider.EMPTY, cx.arrayValueProviderOf(new Object[0]));
    }
    @Test void listenerBagAddRemoveReadKeepsBoundariesOrderAndOriginalArrays() {
        Object a = new Object(), b = new Object(), c = new Object();
        Object one = Kit.addListener(null, a); assertSame(a, one); assertSame(a, Kit.getListener(one, 0)); assertNull(Kit.getListener(one, 1));
        Object two = Kit.addListener(one, b); assertSame(a, Kit.getListener(two, 0)); assertSame(b, Kit.getListener(two, 1));
        Object three = Kit.addListener(two, c); assertSame(c, Kit.getListener(three, 2)); assertNull(Kit.getListener(three, 3));
        assertSame(b, Kit.removeListener(two, a)); assertSame(a, Kit.removeListener(two, b));
        Object removed = Kit.removeListener(three, b); assertSame(a, Kit.getListener(removed, 0)); assertSame(c, Kit.getListener(removed, 1));
        assertSame(b, Kit.getListener(three, 1));
        assertThrows(IllegalArgumentException.class, () -> Kit.getListener(new Object[1], 0));
        assertThrows(IllegalArgumentException.class, () -> Kit.addListener(null, null));
    }
    @Test void ordinaryAndOptionalPropertyLabelsKeepOneReceiverAndShortCircuit() {
        Context cx = new ContextFactory().enter(); Scriptable scope = cx.initStandardObjects();
        Object value = cx.evaluateString(scope, "var n=0,o={x:7};function get(){n++;return o;}[get().x,get()?.x,n,String(null?.x)].join('|');", "property.js", 1, null);
        assertEquals("7|7|2|undefined", ScriptRuntime.toString(cx, value));
    }
}
