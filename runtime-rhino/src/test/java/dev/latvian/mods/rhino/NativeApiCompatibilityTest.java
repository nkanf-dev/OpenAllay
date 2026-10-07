package dev.latvian.mods.rhino;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.latvian.mods.rhino.util.JavaSetWrapper;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
public final class NativeApiCompatibilityTest {
    public record GenericRow(List<String> values, int count) {}
    public static final class AccessibleTarget {
        public int field = 1; private int hidden = 2; public static int staticField = 3;
    }
    @Test void publicNativeRecordDescriptorsPreserveOrderGenericTypesAndAccessors() throws Exception {
        assertTrue(VMBridge.isRecord(GenericRow.class)); assertFalse(VMBridge.isRecord(String.class));
        assertNull(VMBridge.getRecordComponents(String.class));
        VMBridge.NativeRecordComponent[] actual = VMBridge.getRecordComponents(GenericRow.class);
        RecordComponent[] expected = GenericRow.class.getRecordComponents();
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < actual.length; i++) {
            assertEquals(expected[i].getName(), actual[i].getName()); assertSame(expected[i].getType(), actual[i].getType());
            assertEquals(expected[i].getGenericType(), actual[i].getGenericType()); assertEquals(expected[i].getAccessor(), actual[i].getAccessor());
        }
        GenericRow row = new GenericRow(List.of("x"), 7);
        assertEquals(List.of("x"), actual[0].getAccessor().invoke(row));
        Context cx = new ContextFactory().enter(); cx.factory.registerDefaultRecordProperties(row);
        assertArrayEquals(new Object[]{row.values(), 7}, cx.factory.getDefaultRecordProperties(GenericRow.class));
        assertThrows(IllegalArgumentException.class, () -> cx.factory.registerDefaultRecordProperties("not-record"));
    }
    @Test void accessibleOwnershipAndReceiverRulesMatchGenuinePublicJdkApi() throws Exception {
        AccessibleTarget target = new AccessibleTarget(); Field pub = AccessibleTarget.class.getField("field");
        assertTrue(VMBridge.canAccessMember(target, pub)); assertEquals(pub.canAccess(target), VMBridge.canAccessMember(target, pub));
        Field hidden = AccessibleTarget.class.getDeclaredField("hidden");
        assertFalse(VMBridge.canAccessMember(target, hidden)); assertTrue(VMBridge.tryToMakeAccessible(target, hidden));
        assertEquals(2, hidden.get(target));
        Field statik = AccessibleTarget.class.getField("staticField");
        assertTrue(VMBridge.canAccessMember(null, statik));
        assertThrows(IllegalArgumentException.class, () -> VMBridge.canAccessMember(target, statik));
        assertThrows(IllegalArgumentException.class, () -> VMBridge.canAccessMember(null, pub));
        assertThrows(IllegalArgumentException.class, () -> VMBridge.canAccessMember("wrong", pub));
        Constructor<?> constructor = AccessibleTarget.class.getConstructor();
        assertTrue(VMBridge.canAccessMember(null, constructor));
        assertThrows(IllegalArgumentException.class, () -> VMBridge.canAccessMember(target, constructor));
    }
    @Test void indexExceptionIntegerMessageAndSetWrapperBoundsStayExact() {
        JavaSetWrapper<String> set = new JavaSetWrapper<>(new LinkedHashSet<>(List.of("x")));
        for (int index : new int[]{-1, 1, Integer.MAX_VALUE}) {
            assertEquals(new IndexOutOfBoundsException(index).getMessage(), assertThrows(IndexOutOfBoundsException.class, () -> set.get(index)).getMessage());
            assertEquals(new IndexOutOfBoundsException(index).getMessage(), assertThrows(IndexOutOfBoundsException.class, () -> set.remove(index)).getMessage());
        }
        assertEquals("x", set.get(0));
    }
    @Test void arrayClassDebugAndAnonymousIteratorShapesKeepActualBehavior() {
        Context cx = new ContextFactory().enter();
        StringBuilder builder = new StringBuilder(); CachedClassStorage.GLOBAL_PUBLIC.get(String[][].class).appendDebugType(builder);
        assertEquals("String[][]", builder.toString());
        NativeObject object = new NativeObject(cx.factory); object.put(cx, "x", object, 1);
        assertEquals(Set.of("x"), object.keySet()); assertEquals(List.of(1), new ArrayList<>(object.values()));
        assertEquals(new AbstractMap.SimpleImmutableEntry<>("x", 1), object.entrySet().iterator().next());
    }
}
