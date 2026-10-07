package dev.latvian.mods.rhino.classfile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ClassfileBindingEqualityTest {
    @Test void constantKindsPreserveEqualityBranchesAndUnsupportedError() {
        for (int kind : new int[]{ConstantPool.CONSTANT_Integer, ConstantPool.CONSTANT_Float,
                ConstantPool.CONSTANT_Long, ConstantPool.CONSTANT_Double,
                ConstantPool.CONSTANT_NameAndType, ConstantPool.CONSTANT_InvokeDynamic}) {
            ConstantEntry value = new ConstantEntry(kind, 1, "a", "b");
            assertEquals(value, new ConstantEntry(kind, 1, "a", "b"));
            assertFalse(value.equals(null)); assertFalse(value.equals("x"));
            assertEquals(new ConstantEntry(kind, 1, "a", "b").hashCode(), value.hashCode());
        }
        assertNotEquals(new ConstantEntry(ConstantPool.CONSTANT_Integer, 1, "a", "b"),
                new ConstantEntry(ConstantPool.CONSTANT_Integer, 2, "a", "b"));
        assertNotEquals(new ConstantEntry(ConstantPool.CONSTANT_NameAndType, 1, "a", "b"),
                new ConstantEntry(ConstantPool.CONSTANT_NameAndType, 1, "c", "b"));
        ConstantEntry unsupported = new ConstantEntry(-1, 1, "a", "b");
        assertEquals("unsupported constant type", assertThrows(RuntimeException.class,
                () -> unsupported.equals(new ConstantEntry(-1, 1, "a", "b"))).getMessage());
    }
    @Test void fieldAndMethodReferencePreservesAllThreeFieldsAndCachedHash() {
        FieldOrMethodRef value = new FieldOrMethodRef("Owner", "name", "()V");
        assertEquals(value, new FieldOrMethodRef("Owner", "name", "()V"));
        assertNotEquals(value, new FieldOrMethodRef("Other", "name", "()V"));
        assertNotEquals(value, new FieldOrMethodRef("Owner", "other", "()V"));
        assertNotEquals(value, new FieldOrMethodRef("Owner", "name", "()I"));
        assertFalse(value.equals(null)); assertFalse(value.equals("x"));
        assertEquals("Owner".hashCode() ^ "name".hashCode() ^ "()V".hashCode(), value.hashCode());
        assertEquals(value.hashCode(), value.hashCode());
    }
}
