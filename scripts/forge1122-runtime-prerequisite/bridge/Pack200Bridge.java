package dev.openallay.runtime.forge1122;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.File;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Exact removed-JDK Pack200 seam; leaves Forge patch discovery/application intact. */
public final class Pack200Bridge {
    static final String CLASS_SHA = "8181ada44b11d47d2345dc72439622996e81da3e101e77452e07efd60340f1bd";
    static final String FORGE_SHA = "ff578d670d2c720a72f8fff31ea3d6868595c7e980ecdecba3254f307ef2c2a9";
    static final String HELPER = "dev/openallay/runtime/forge1122/pack200/Pack200Runtime";
    private static int transformations;

    static void install(Instrumentation instrumentation) throws Exception {
        final File forge = new File(System.getProperty("openallay.pack200.forge")).getCanonicalFile();
        if (!FORGE_SHA.equals(sha(Files.readAllBytes(forge.toPath()))))
            throw new IllegalStateException("Exact official Forge JAR differs");
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                                    ProtectionDomain domain, byte[] bytes) {
                if (!"net/minecraftforge/fml/common/patcher/ClassPatchManager".equals(name)) return null;
                try {
                    if (redefining != null || ++transformations != 1 || loader == null
                            || !"net.minecraft.launchwrapper.LaunchClassLoader".equals(loader.getClass().getName())
                            || domain == null || domain.getCodeSource() == null)
                        throw new IllegalStateException("Unexpected ClassPatchManager owner");
                    java.net.URL location = domain.getCodeSource().getLocation();
                    String external = location.toExternalForm();
                    String official = forge.toURI().toURL().toExternalForm();
                    if (!external.equals(official) && !external.startsWith("jar:" + official + "!/"))
                        throw new IllegalStateException("Unexpected ClassPatchManager source: " + external);
                    byte[] patched = patch(bytes);
                    String receipt = "{\"target\":\"ClassPatchManager.setup\",\"inputSha256\":\"" + sha(bytes)
                            + "\",\"outputSha256\":\"" + sha(patched) + "\",\"stockLaunchClassLoader\":true,"
                            + "\"removedJdkPack200SeamOnly\":true,\"forgeArchiveUnchanged\":true}\n";
                    Files.write(Paths.get(System.getProperty("openallay.pack200.transformReceipt")), receipt.getBytes(StandardCharsets.UTF_8));
                    System.err.println("OPENALLAY_PACK200_SETUP_BRIDGE " + receipt.trim());
                    return patched;
                } catch (Throwable error) {
                    error.printStackTrace();
                    Runtime.getRuntime().halt(78);
                    return null;
                }
            }
        }, false);
    }

    static byte[] patch(byte[] bytes) throws Exception {
        if (!CLASS_SHA.equals(sha(bytes))) throw new IllegalStateException("Exact ClassPatchManager.class differs");
        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(bytes).accept(node, 0);
        int replacements = 0;
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            if (!"setup".equals(method.name) || !"(Lnet/minecraftforge/fml/relauncher/Side;)V".equals(method.desc)) continue;
            MethodInsnNode creator = null;
            MethodInsnNode unpack = null;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKESTATIC && "java/util/jar/Pack200".equals(call.owner)
                        && "newUnpacker".equals(call.name) && "()Ljava/util/jar/Pack200$Unpacker;".equals(call.desc)) {
                    if (creator != null) throw new IllegalStateException("Duplicate Pack200 creator");
                    creator = call;
                }
                if (call.getOpcode() == Opcodes.INVOKEINTERFACE && "java/util/jar/Pack200$Unpacker".equals(call.owner)
                        && "unpack".equals(call.name) && "(Ljava/io/InputStream;Ljava/util/jar/JarOutputStream;)V".equals(call.desc)) {
                    if (unpack != null) throw new IllegalStateException("Duplicate Pack200 unpack");
                    unpack = call;
                }
            }
            if (creator == null || unpack == null) throw new IllegalStateException("Exact removed-JDK calls absent");
            AbstractInsnNode loadInput = creator.getNext();
            while (loadInput != null && loadInput.getOpcode() < 0) loadInput = loadInput.getNext();
            AbstractInsnNode loadOutput = loadInput == null ? null : loadInput.getNext();
            while (loadOutput != null && loadOutput.getOpcode() < 0) loadOutput = loadOutput.getNext();
            AbstractInsnNode next = loadOutput == null ? null : loadOutput.getNext();
            while (next != null && next.getOpcode() < 0) next = next.getNext();
            if (loadInput == null || loadOutput == null || loadInput.getOpcode() != Opcodes.ALOAD
                    || loadOutput.getOpcode() != Opcodes.ALOAD || next != unpack)
                throw new IllegalStateException("Exact four-instruction Pack200 seam differs");
            method.instructions.remove(creator);
            method.instructions.set(unpack, new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "unpack",
                    "(Ljava/io/InputStream;Ljava/util/jar/JarOutputStream;)V", false));
            replacements++;
        }
        if (replacements != 1) throw new IllegalStateException("Expected one Pack200 seam");
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    static String sha(byte[] bytes) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) hex.append(String.format("%02x", value & 255));
        return hex.toString();
    }
}
