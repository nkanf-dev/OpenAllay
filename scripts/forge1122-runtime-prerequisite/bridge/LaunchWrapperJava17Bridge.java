package dev.openallay.runtime.forge1122;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Adapts only LaunchWrapper1.12's observed pre-Java9 application-loader URL seam. */
public final class LaunchWrapperJava17Bridge {
    private static final String INPUT_CLASS_SHA256 = "29dc2e65bebe95d9fcc595b57cec0b4e10cb55bdfd54cfc628cb86227a7d3dfc";
    private static final String INPUT_JAR_SHA256 = "57f402b626d16cc2705bf2a37add7adbb074f0ca3b3102fa6e23aa303dae682f";
    private static File officialJar;
    private static int transformations;

    public static void premain(String jarPath, Instrumentation instrumentation) throws Exception {
        if (!"17".equals(System.getProperty("java.specification.version")))
            throw new IllegalStateException("Bridge selects Java17 only");
        officialJar = new File(jarPath).getCanonicalFile();
        if (!INPUT_JAR_SHA256.equals(sha(Files.readAllBytes(officialJar.toPath()))))
            throw new IllegalStateException("Official LaunchWrapper1.12 JAR differs");
        if (Boolean.getBoolean("openallay.pack200.enabled")) Pack200Bridge.install(instrumentation);
        if (Boolean.getBoolean("openallay.objectholder.enabled")) ObjectHolderBridge.install(instrumentation);
        if (Boolean.getBoolean("openallay.capability.enabled")) CapabilityBridge.install(instrumentation);
        if (System.getProperty("openallay.dimension.capture")!=null) DimensionEnumPhaseCapture.install(instrumentation);
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                                    ProtectionDomain domain, byte[] bytes) {
                if (!"net/minecraft/launchwrapper/Launch".equals(name)) return null;
                try {
                    if (redefining != null || ++transformations != 1 || loader != ClassLoader.getSystemClassLoader()
                            || domain == null || domain.getCodeSource() == null
                            || !officialJar.toURI().toURL().equals(domain.getCodeSource().getLocation()))
                        throw new IllegalStateException("Unexpected Launch loading owner or source");
                    byte[] patched = patchLaunch(bytes);
                    String receipt = "{\"target\":\"net.minecraft.launchwrapper.Launch\","
                            + "\"inputSha256\":\"" + sha(bytes) + "\",\"outputSha256\":\"" + sha(patched)
                            + "\",\"stockLaunchClassLoaderUnchanged\":true,\"customClassLoader\":false,"
                            + "\"libraryReplacement\":false,\"constructorUrlBridge\":true}\n";
                    Files.write(Paths.get(System.getProperty("openallay.bridge.receipt")),
                                receipt.getBytes(StandardCharsets.UTF_8));
                    System.err.println("OPENALLAY_LAUNCHWRAPPER_URL_BRIDGE " + receipt.trim());
                    return patched;
                } catch (Throwable error) {
                    // Transformer exceptions normally fall back to original bytes. Refuse that silently.
                    error.printStackTrace();
                    Runtime.getRuntime().halt(78);
                    return null;
                }
            }
        }, false);
    }

    static byte[] patchLaunch(byte[] bytes) throws Exception {
        if (!INPUT_CLASS_SHA256.equals(sha(bytes)))
            throw new IllegalStateException("Launch.class exact authenticated bytes differ");
        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(bytes).accept(node, 0);
        int found = 0;
        for (int i = 0; i < node.methods.size(); i++) {
            MethodNode old = (MethodNode) node.methods.get(i);
            if (!"<init>".equals(old.name) || !"()V".equals(old.desc)) continue;
            if (old.access != Opcodes.ACC_PRIVATE) throw new IllegalStateException("Constructor shape differs");
            int appLoaderCasts = 0;
            int urlCalls = 0;
            for (AbstractInsnNode instruction = old.instructions.getFirst(); instruction != null;
                    instruction = instruction.getNext()) {
                if (instruction instanceof TypeInsnNode && instruction.getOpcode() == Opcodes.CHECKCAST
                        && "java/net/URLClassLoader".equals(((TypeInsnNode) instruction).desc)) appLoaderCasts++;
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && "java/net/URLClassLoader".equals(call.owner)
                            && "getURLs".equals(call.name) && "()[Ljava/net/URL;".equals(call.desc)) urlCalls++;
                }
            }
            if (appLoaderCasts != 1 || urlCalls != 1)
                throw new IllegalStateException("Exact observed URLClassLoader seam instructions differ");
            found++;
            MethodNode ctor = new MethodNode(Opcodes.ASM5, old.access, old.name, old.desc, null, null);
            ctor.visitCode();
            ctor.visitVarInsn(Opcodes.ALOAD, 0);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            ctor.visitTypeInsn(Opcodes.NEW, "net/minecraft/launchwrapper/LaunchClassLoader");
            ctor.visitInsn(Opcodes.DUP);
            ctor.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "dev/openallay/runtime/forge1122/LaunchWrapperJava17Bridge", "classpathUrls", "()[Ljava/net/URL;", false);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/launchwrapper/LaunchClassLoader",
                    "<init>", "([Ljava/net/URL;)V", false);
            ctor.visitFieldInsn(Opcodes.PUTSTATIC, "net/minecraft/launchwrapper/Launch", "classLoader",
                    "Lnet/minecraft/launchwrapper/LaunchClassLoader;");
            ctor.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
            ctor.visitInsn(Opcodes.DUP);
            ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
            ctor.visitFieldInsn(Opcodes.PUTSTATIC, "net/minecraft/launchwrapper/Launch", "blackboard", "Ljava/util/Map;");
            ctor.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            ctor.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/launchwrapper/Launch", "classLoader",
                    "Lnet/minecraft/launchwrapper/LaunchClassLoader;");
            ctor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Thread", "setContextClassLoader", "(Ljava/lang/ClassLoader;)V", false);
            ctor.visitInsn(Opcodes.RETURN);
            ctor.visitMaxs(3, 1);
            ctor.visitEnd();
            node.methods.set(i, ctor);
        }
        if (found != 1) throw new IllegalStateException("Expected one exact Launch constructor");
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    public static URL[] classpathUrls() throws Exception {
        List<URL> urls = new ArrayList<URL>();
        for (String path : System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator), -1)) {
            File file = new File(path).getCanonicalFile();
            if (path.isEmpty() || !file.isFile() || !file.getName().endsWith(".jar"))
                throw new IllegalStateException("Bridge requires explicit installed JAR classpath: " + path);
            urls.add(file.toURI().toURL());
        }
        return urls.toArray(new URL[urls.size()]);
    }

    private static String sha(byte[] bytes) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
            hex.append(String.format("%02x", value & 255));
        return hex.toString();
    }
}
