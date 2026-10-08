package dev.latvian.mods.rhino;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class JavaAdapterLanguageTest {
    public interface PrimitiveMethods {
        int ints(byte b, short s, int i); long longs(long v); float floats(float v); double doubles(double v);
        boolean booleans(boolean v); char chars(char v); Object objects(Object v);
        default String untouchedDefault() { return "default"; }
    }
    @Test void adapterSignatureRetainsSuperclassInterfaceOrderAndArities() {
        ObjToIntMap names = new ObjToIntMap(); names.put("x", 2);
        JavaAdapter.JavaAdapterSignature value = new JavaAdapter.JavaAdapterSignature(Object.class, new Class<?>[]{PrimitiveMethods.class}, names);
        ObjToIntMap equalNames = new ObjToIntMap(); equalNames.put("x", 2);
        assertEquals(value, new JavaAdapter.JavaAdapterSignature(Object.class, new Class<?>[]{PrimitiveMethods.class}, equalNames));
        assertNotEquals(value, new JavaAdapter.JavaAdapterSignature(String.class, new Class<?>[]{PrimitiveMethods.class}, equalNames));
        ObjToIntMap different = new ObjToIntMap(); different.put("x", 3);
        assertNotEquals(value, new JavaAdapter.JavaAdapterSignature(Object.class, new Class<?>[]{PrimitiveMethods.class}, different));
        assertFalse(value.equals(null)); assertFalse(value.equals("x"));
        assertEquals(new JavaAdapter.JavaAdapterSignature(Object.class, new Class<?>[]{PrimitiveMethods.class}, equalNames).hashCode(), value.hashCode());
    }
    @Test void legitimateAdapterCodeGenerationPreservesPrimitiveMethodDescriptorsWithoutLoading() throws Exception {
        Context cx = new ContextFactory().enter(); ObjToIntMap names = new ObjToIntMap();
        for (java.lang.reflect.Method method : PrimitiveMethods.class.getMethods()) if (!method.isDefault()) names.put(method.getName(), method.getParameterCount());
        byte[] bytes = JavaAdapter.createAdapterCode(names, "GeneratedAdapter", Object.class, new Class<?>[]{PrimitiveMethods.class}, null, cx);
        Set<String> descriptors = methodDescriptors(bytes);
        assertTrue(descriptors.contains("ints:(BSI)I")); assertTrue(descriptors.contains("longs:(J)J"));
        assertTrue(descriptors.contains("floats:(F)F")); assertTrue(descriptors.contains("doubles:(D)D"));
        assertTrue(descriptors.contains("booleans:(Z)Z")); assertTrue(descriptors.contains("chars:(C)C"));
        assertTrue(descriptors.contains("objects:(Ljava/lang/Object;)Ljava/lang/Object;"));
        assertFalse(descriptors.stream().anyMatch(value -> value.startsWith("untouchedDefault:")));
    }
    static Set<String> methodDescriptors(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes)); assertEquals(0xCAFEBABE, in.readInt());
        in.readUnsignedShort(); in.readUnsignedShort(); String[] utf = new String[in.readUnsignedShort()];
        for (int i = 1; i < utf.length; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1: utf[i] = in.readUTF(); break;
                case 3: case 4: in.skipBytes(4); break;
                case 5: case 6: in.skipBytes(8); i++; break;
                case 7: case 8: case 16: in.skipBytes(2); break;
                case 9: case 10: case 11: case 12: case 18: in.skipBytes(4); break;
                case 15: in.skipBytes(3); break;
                default: throw new IOException("Unexpected constant tag: " + tag);
            }
        }
        in.skipBytes(6); int interfaces = in.readUnsignedShort(); in.skipBytes(2 * interfaces);
        int fields = in.readUnsignedShort(); for (int i = 0; i < fields; i++) { in.skipBytes(6); skipAttributes(in); }
        Set<String> methods = new HashSet<>(); int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) { in.readUnsignedShort(); String name = utf[in.readUnsignedShort()], descriptor = utf[in.readUnsignedShort()]; methods.add(name + ":" + descriptor); skipAttributes(in); }
        return methods;
    }
    static void skipAttributes(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort(); for (int i = 0; i < count; i++) { in.readUnsignedShort(); int n = in.readInt(); in.skipBytes(n); }
    }
}
