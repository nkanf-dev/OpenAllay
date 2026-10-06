package dev.openallay.runtime.forge1122;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

public final class Pack200BridgeTest {
    private static Map<String, String> normalized(byte[] bytes, boolean patched) {
        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(bytes).accept(node, 0);
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Object value : node.methods) {
            MethodNode method = (MethodNode) value;
            if ("setup".equals(method.name)) {
                for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;) {
                    AbstractInsnNode next = instruction.getNext();
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if (!patched && "java/util/jar/Pack200".equals(call.owner) && "newUnpacker".equals(call.name))
                            method.instructions.remove(call);
                        else if (!patched && "java/util/jar/Pack200$Unpacker".equals(call.owner) && "unpack".equals(call.name))
                            method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    Pack200Bridge.HELPER, "unpack", "(Ljava/io/InputStream;Ljava/util/jar/JarOutputStream;)V", false));
                    }
                    instruction = next;
                }
            }
            Textifier text = new Textifier();
            method.accept(new TraceMethodVisitor(text));
            result.put(method.name + method.desc, text.text.toString());
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        byte[] original;
        try (JarFile jar = new JarFile(args[0]); InputStream input = jar.getInputStream(jar.getJarEntry(
                "net/minecraftforge/fml/common/patcher/ClassPatchManager.class"))) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            original = output.toByteArray();
        }
        byte[] patched = Pack200Bridge.patch(original);
        if (!normalized(original, false).equals(normalized(patched, true)))
            throw new AssertionError("All instructions except exact Pack200 calls must stay unchanged");
        byte[] wrong = original.clone(); wrong[wrong.length - 1] ^= 1;
        try { Pack200Bridge.patch(wrong); throw new AssertionError("Changed class must reject"); }
        catch (IllegalStateException expected) { }
        System.out.println("PASS genuine CPM exact4instruction seam; all downstream discovery/apply instructions unchanged; altered class rejects");
    }
}
