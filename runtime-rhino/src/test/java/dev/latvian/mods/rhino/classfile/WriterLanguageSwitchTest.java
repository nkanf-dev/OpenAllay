package dev.latvian.mods.rhino.classfile;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class WriterLanguageSwitchTest {
    static byte[] methodCode(byte[] classBytes, String methodName) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(classBytes));
        assertEquals(0xCAFEBABE, in.readInt()); in.readUnsignedShort(); in.readUnsignedShort();
        String[] utf = new String[in.readUnsignedShort()];
        for (int i = 1; i < utf.length; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1: utf[i] = in.readUTF(); break;
                case 3: case 4: in.skipBytes(4); break;
                case 5: case 6: in.skipBytes(8); i++; break;
                case 7: case 8: case 16: in.skipBytes(2); break;
                case 9: case 10: case 11: case 12: case 18: in.skipBytes(4); break;
                case 15: in.skipBytes(3); break;
                default: throw new IOException("Unexpected constant tag " + tag);
            }
        }
        in.skipBytes(6); int interfaces = in.readUnsignedShort(); in.skipBytes(2 * interfaces);
        int fields = in.readUnsignedShort();
        for (int i = 0; i < fields; i++) { in.skipBytes(6); skipAttributes(in); }
        int methods = in.readUnsignedShort();
        for (int i = 0; i < methods; i++) {
            in.readUnsignedShort(); String name = utf[in.readUnsignedShort()]; in.readUnsignedShort();
            int attributes = in.readUnsignedShort();
            for (int a = 0; a < attributes; a++) {
                String attribute = utf[in.readUnsignedShort()]; int length = in.readInt(); byte[] data = new byte[length]; in.readFully(data);
                if (name.equals(methodName) && attribute.equals("Code")) {
                    DataInputStream code = new DataInputStream(new ByteArrayInputStream(data)); code.readUnsignedShort(); code.readUnsignedShort();
                    byte[] result = new byte[code.readInt()]; code.readFully(result); return result;
                }
            }
        }
        throw new IOException("Method missing: " + methodName);
    }
    static void skipAttributes(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort(); for (int i = 0; i < count; i++) { in.readUnsignedShort(); int n = in.readInt(); in.skipBytes(n); }
    }
    @Test void integerConstantsAndLocalShortOpcodesKeepExactEmission() throws Exception {
        for (int value = 0; value <= 5; value++) {
            ClassFileWriter writer = new ClassFileWriter("Generated", "java/lang/Object", "test.java");
            writer.startMethod("constant", "()I", (short) (ClassFileWriter.ACC_PUBLIC | ClassFileWriter.ACC_STATIC));
            writer.addLoadConstant(value); writer.add(ByteCode.IRETURN); writer.stopMethod((short) 0);
            byte[] code = methodCode(writer.toByteArray(), "constant");
            assertArrayEquals(new byte[]{(byte) (ByteCode.ICONST_0 + value), (byte) ByteCode.IRETURN}, code);
        }
        ClassFileWriter writer = new ClassFileWriter("Locals", "java/lang/Object", "test.java");
        writer.startMethod("local", "(IIII)I", (short) (ClassFileWriter.ACC_PUBLIC | ClassFileWriter.ACC_STATIC));
        writer.addILoad(3); writer.add(ByteCode.IRETURN); writer.stopMethod((short) 4);
        assertArrayEquals(new byte[]{(byte) ByteCode.ILOAD_3, (byte) ByteCode.IRETURN}, methodCode(writer.toByteArray(), "local"));
    }
    @Test void primitiveArrayAndClassReferenceBranchesKeepOpcodesAndValidation() throws Exception {
        int[] types = {ByteCode.T_BOOLEAN, ByteCode.T_CHAR, ByteCode.T_FLOAT, ByteCode.T_DOUBLE, ByteCode.T_BYTE, ByteCode.T_SHORT, ByteCode.T_INT, ByteCode.T_LONG};
        for (int type : types) {
            ClassFileWriter writer = new ClassFileWriter("Arrays", "java/lang/Object", "test.java");
            writer.startMethod("array", "()Ljava/lang/Object;", (short) (ClassFileWriter.ACC_PUBLIC | ClassFileWriter.ACC_STATIC));
            writer.addPush(1); writer.add(ByteCode.NEWARRAY, type); writer.add(ByteCode.ARETURN); writer.stopMethod((short) 0);
            byte[] code = methodCode(writer.toByteArray(), "array");
            assertEquals(ByteCode.NEWARRAY, code[1] & 255); assertEquals(type, code[2] & 255);
        }
        ClassFileWriter writer = new ClassFileWriter("Invalid", "java/lang/Object", "test.java");
        writer.startMethod("invalid", "()V", (short) (ClassFileWriter.ACC_PUBLIC | ClassFileWriter.ACC_STATIC));
        assertThrows(IllegalArgumentException.class, () -> writer.add(ByteCode.NOP, "java/lang/Object"));
    }
    @Test void methodHandleEqualityRetainsTagOwnerNameDescriptorAndHash() {
        ClassFileWriter.MHandle value = new ClassFileWriter.MHandle((byte) 6, "Owner", "method", "()V");
        assertEquals(value, new ClassFileWriter.MHandle((byte) 6, "Owner", "method", "()V"));
        assertNotEquals(value, new ClassFileWriter.MHandle((byte) 5, "Owner", "method", "()V"));
        assertNotEquals(value, new ClassFileWriter.MHandle((byte) 6, "Other", "method", "()V"));
        assertFalse(value.equals(null)); assertFalse(value.equals("x"));
        assertEquals(new ClassFileWriter.MHandle((byte) 6, "Owner", "method", "()V").hashCode(), value.hashCode());
    }
}
