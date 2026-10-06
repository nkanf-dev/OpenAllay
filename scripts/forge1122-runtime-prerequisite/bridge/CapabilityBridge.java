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

/** Exact setter seam for five native nonfinal CapabilityInject fields. */
public final class CapabilityBridge {
    static final String PHASE_SHA="03a642bc1c1b45c500aba9e08b834d7614b2285056de630bf82eddd87a166561";
    static final String CLASS_SHA = "7bffb0137bf96289b7682c41e4d178c07b74df2d948a975480673fef1ce994af";
    static final String FORGE_SHA = "ff578d670d2c720a72f8fff31ea3d6868595c7e980ecdecba3254f307ef2c2a9";
    static final String HELPER = "dev/openallay/runtime/forge1122/pack200/CapabilityRuntime";
    private static int transformations;

    static void install(Instrumentation instrumentation) throws Exception {
        final File forge = new File(System.getProperty("openallay.pack200.forge")).getCanonicalFile();
        if (!FORGE_SHA.equals(sha(Files.readAllBytes(forge.toPath()))))
            throw new IllegalStateException("Exact official Forge JAR differs");
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                                    ProtectionDomain domain, byte[] bytes) {
                if (!"net/minecraftforge/common/capabilities/CapabilityManager$2".equals(name)) return null;
                try {
                    if (redefining != null || ++transformations != 1 || loader == null
                            || !"net.minecraft.launchwrapper.LaunchClassLoader".equals(loader.getClass().getName())
                            || domain == null || domain.getCodeSource() == null)
                        throw new IllegalStateException("Unexpected CapabilityManager$2 owner");
                    java.net.URL location = domain.getCodeSource().getLocation();
                    String external = location.toExternalForm();
                    String official = forge.toURI().toURL().toExternalForm();
                    if (!external.equals(official) && !external.startsWith("jar:" + official + "!/"))
                        throw new IllegalStateException("Unexpected CapabilityManager$2 source: " + external);
                    try(java.util.jar.JarFile archive=new java.util.jar.JarFile(forge);java.io.InputStream input=archive.getInputStream(archive.getJarEntry(name+".class"))) {
                        java.io.ByteArrayOutputStream raw=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
                        while((count=input.read(buffer))!=-1)raw.write(buffer,0,count);
                        if(!CLASS_SHA.equals(sha(raw.toByteArray())))throw new IllegalStateException("Exact original capability class resource differs");
                    }
                    byte[] patched = patch(bytes,true);
                    String receipt = "{\"target\":\"CapabilityManager$2.setup\",\"inputSha256\":\"" + sha(bytes)
                            + "\",\"outputSha256\":\"" + sha(patched) + "\",\"stockLaunchClassLoader\":true,"
                            + "\"capabilityNonfinalFieldSetterOnly\":true,\"forgeArchiveUnchanged\":true}\n";
                    Files.write(Paths.get(System.getProperty("openallay.capability.transformReceipt")), receipt.getBytes(StandardCharsets.UTF_8));
                    System.err.println("OPENALLAY_CAPABILITY_FIELD_BRIDGE " + receipt.trim());
                    return patched;
                } catch (Throwable error) {
                    error.printStackTrace();
                    try {
                        java.nio.file.Path directory=Paths.get(System.getProperty("openallay.capability.rejected"));Files.createDirectories(directory);
                        Files.write(directory.resolve("CapabilityManager$2.class"),bytes);
                        Files.write(directory.resolve("phase.json"),("{\"sha256\":\""+sha(bytes)+"\",\"accepted\":false,\"source\":\""+domain.getCodeSource().getLocation().toExternalForm()+"\"}\n").getBytes(StandardCharsets.UTF_8));
                    }catch(Exception capture){capture.printStackTrace();}
                    return new byte[]{0};
                }
            }
        }, false);
    }

    static byte[] patch(byte[] bytes) throws Exception {return patch(bytes,false);}
    static byte[] patch(byte[] bytes,boolean postForge) throws Exception {
        if (!(postForge?PHASE_SHA:CLASS_SHA).equals(sha(bytes))) throw new IllegalStateException("Exact CapabilityManager$2.class differs");
        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(bytes).accept(node, 0);
        int replacements=0;
        for(Object object:node.methods) {
            MethodNode method=(MethodNode)object;
            for(AbstractInsnNode instruction=method.instructions.getFirst();instruction!=null;instruction=instruction.getNext()) {
                if(!(instruction instanceof MethodInsnNode))continue;
                MethodInsnNode call=(MethodInsnNode)instruction;
                if(call.getOpcode()==Opcodes.INVOKESTATIC&&"net/minecraftforge/common/util/EnumHelper".equals(call.owner)
                        &&"setFailsafeFieldValue".equals(call.name)&&"(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)V".equals(call.desc)) {
                    method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"setField",call.desc,false));replacements++;
                }
            }
        }
        if(replacements!=1)throw new IllegalStateException("Exactly one capability field setter call required");
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
