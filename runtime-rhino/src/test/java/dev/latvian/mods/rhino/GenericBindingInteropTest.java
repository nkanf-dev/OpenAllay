package dev.latvian.mods.rhino;
import dev.latvian.mods.rhino.type.TypeInfo;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class GenericBindingInteropTest {
    @Test void complexKeyHashEqualityRetainsNullAndWrongTypeGuards() {
        Object first = Kit.makeHashKeyFromPair("a", "b"), equal = Kit.makeHashKeyFromPair("a", "b");
        assertEquals(first, equal); assertEquals(first.hashCode(), equal.hashCode());
        assertNotEquals(first, Kit.makeHashKeyFromPair("a", "c")); assertFalse(first.equals(null)); assertFalse(first.equals("a"));
    }
    @Test void nativeArrayStringIndexDeletionAndGenericJavaMethodConversionStayExact() {
        Context cx = new ContextFactory().enter(); ScriptableObject scope = cx.initStandardObjects();
        ArrayList<String> javaList = new ArrayList<>(List.of("x"));
        ScriptableObject.putProperty(scope, "javaList", cx.wrapAsJavaObject(scope, javaList, TypeInfo.RAW_LIST.withParams(TypeInfo.STRING)), cx);
        Object result = cx.evaluateString(scope,
            "var a=[1,2,3];a.length=1;[javaList.get(0),a.length,String(a[1])].join('|');", "generic.js", 1, null);
        assertEquals("x|1|undefined", ScriptRuntime.toString(cx, result));
    }
}
