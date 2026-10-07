package dev.latvian.mods.rhino.classfile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class VerifierTypeSwitchTest {
    @Test void primitiveDescriptorTypesKeepGroupedVerifierKindsAndErrors() {
        ConstantPool pool = new ConstantPool(new ClassFileWriter("Test", "java/lang/Object", "Test.java"));
        for (String type : new String[]{"B", "C", "S", "Z", "I"}) assertEquals(TypeInfo.INTEGER, TypeInfo.fromType(type, pool));
        assertEquals(TypeInfo.DOUBLE, TypeInfo.fromType("D", pool));
        assertEquals(TypeInfo.FLOAT, TypeInfo.fromType("F", pool));
        assertEquals(TypeInfo.LONG, TypeInfo.fromType("J", pool));
        assertEquals("bad type", assertThrows(IllegalArgumentException.class, () -> TypeInfo.fromType("V", pool)).getMessage());
        int reference = TypeInfo.fromType("java/lang/String", pool);
        assertEquals(TypeInfo.OBJECT_TAG, TypeInfo.getTag(reference));
        assertEquals("java/lang/String", TypeInfo.getPayloadAsType(reference, pool));
    }
}
