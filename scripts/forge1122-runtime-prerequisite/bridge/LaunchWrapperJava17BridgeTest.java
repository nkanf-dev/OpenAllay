package dev.openallay.runtime.forge1122;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/** Remote source-backed test over real official Launch bytes, not a fake game. */
public final class LaunchWrapperJava17BridgeTest {
    private static Map<String, String> methods(byte[] bytes) {
        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(bytes).accept(node, 0);
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode) entry;
            Textifier text = new Textifier();
            method.accept(new TraceMethodVisitor(text));
            result.put(method.name + method.desc, text.text.toString());
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--constructor".equals(args[1])) {
            Class<?> launch = Class.forName("net.minecraft.launchwrapper.Launch");
            java.lang.reflect.Constructor<?> constructor = launch.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
            Object loader = launch.getField("classLoader").get(null);
            if (!"net.minecraft.launchwrapper.LaunchClassLoader".equals(loader.getClass().getName())
                    || Thread.currentThread().getContextClassLoader() != loader)
                throw new AssertionError("Stock LaunchClassLoader must own the context");
            System.out.println("PASS real hash-bound Launch constructor with stock LaunchClassLoader owner");
            return;
        }
        byte[] original;
        try (JarFile jar = new JarFile(args[0]); InputStream input = jar.getInputStream(jar.getJarEntry("net/minecraft/launchwrapper/Launch.class"))) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            original = output.toByteArray();
        }
        byte[] patched = LaunchWrapperJava17Bridge.patchLaunch(original);
        Map<String, String> before = methods(original);
        Map<String, String> after = methods(patched);
        if (before.remove("<init>()V").equals(after.remove("<init>()V")) || !before.equals(after))
            throw new AssertionError("Bridge must change constructor only");
        byte[] invalid = original.clone(); invalid[invalid.length - 1] ^= 1;
        try { LaunchWrapperJava17Bridge.patchLaunch(invalid); throw new AssertionError("Must reject changed bytes"); }
        catch (IllegalStateException expected) { }
        System.out.println("PASS exact official Launch constructor-only patch; unchanged remaining method instructions; rejected altered bytes");
    }
}
