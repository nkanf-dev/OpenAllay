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
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.VarInsnNode;
import java.util.Arrays;
import java.util.HashSet;
import java.nio.file.StandardOpenOption;

/** Exact removed ReflectionFactory seam; changes only admitted holder final metadata before definition. */
public final class ObjectHolderBridge {
    static final String PHASE_CLASS_SHA = "27cabecd423b1e752c2494b588a9aa8647531fdd55b378e618ced4b18e75a242";
    static final String PHASE_REGISTRY_SHA = "7757518c879e00a9799a3f997f4a3ca183c9b517a180346b5d3788730ad8d992";
    static final String CLASS_SHA = "81c886b7bbc4f13d233491982b1ef8ae0eaba43565f5917f539380d8de4481b6";
    static final String FORGE_SHA = "ff578d670d2c720a72f8fff31ea3d6868595c7e980ecdecba3254f307ef2c2a9";
    static final String HELPER = "dev/openallay/runtime/forge1122/pack200/ObjectHolderRuntime";
    private static final String FARMER_OWNER="net/minecraftforge/fml/common/registry/VillagerRegistry";
    private static final java.util.Set<String> HOLDERS = new HashSet<String>(Arrays.asList(
        "net/minecraft/init/Blocks", "net/minecraft/init/Items", "net/minecraft/init/MobEffects",
        "net/minecraft/init/Biomes", "net/minecraft/init/Enchantments", "net/minecraft/init/SoundEvents", "net/minecraft/init/PotionTypes"));
    private static final java.util.Set<String> seen = new HashSet<String>();

    static void install(Instrumentation instrumentation) throws Exception {
        final File forge = new File(System.getProperty("openallay.pack200.forge")).getCanonicalFile();
        if (!FORGE_SHA.equals(sha(Files.readAllBytes(forge.toPath()))))
            throw new IllegalStateException("Exact official Forge JAR differs");
        final File client = new File(System.getProperty("openallay.objectholder.client")).getCanonicalFile();
        { // Exact official client SHA1 metadata pin
            StringBuilder digest = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(client.toPath()))) digest.append(String.format("%02x", value & 255));
            if (!"0f275bc1547d01fa5f56ba34bdc87d981ee12daf".equals(digest.toString())) throw new IllegalStateException("Exact client differs");
        }
        Files.write(Paths.get(System.getProperty("openallay.objectholder.fields")), new byte[0]);
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                                    ProtectionDomain domain, byte[] bytes) {
                if (!HOLDERS.contains(name) && !FARMER_OWNER.equals(name) && !"net/minecraftforge/registries/ObjectHolderRef$FinalFieldHelper".equals(name)
                        && !"net/minecraftforge/registries/ObjectHolderRegistry".equals(name)) return null;
                try {
                    if (redefining != null || !seen.add(name) || loader == null
                            || !"net.minecraft.launchwrapper.LaunchClassLoader".equals(loader.getClass().getName())
                            || domain == null || domain.getCodeSource() == null)
                        throw new IllegalStateException("Unexpected ObjectHolderRef$FinalFieldHelper owner");
                    java.net.URL location = domain.getCodeSource().getLocation();
                    String external = location.toExternalForm();
                    String official = (HOLDERS.contains(name) ? client : forge).toURI().toURL().toExternalForm();
                    if (!external.equals(official) && !external.startsWith("jar:" + official + "!/"))
                        throw new IllegalStateException("Unexpected ObjectHolderRef$FinalFieldHelper source: " + external);
                    byte[] patched;
                    if (HOLDERS.contains(name)) patched=patchHolder(name,bytes);
                    else if(FARMER_OWNER.equals(name)) {
                        try(java.util.jar.JarFile archive=new java.util.jar.JarFile(forge);java.io.InputStream stream=archive.getInputStream(archive.getJarEntry(FARMER_OWNER+".class"))) {
                            java.io.ByteArrayOutputStream raw=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
                            while((n=stream.read(buffer))!=-1)raw.write(buffer,0,n);
                            if(!"ca8bd837d411f4804ea56269304e54bfa097597ab41acb3ceac12a6fee79a596".equals(sha(raw.toByteArray())))throw new IllegalStateException("Exact original farmer class resource differs");
                        }
                        patched=patchFarmer(bytes);
                    }
                    else {
                        String rawName=name+".class";
                        try(java.util.jar.JarFile archive=new java.util.jar.JarFile(forge);
                            java.io.InputStream stream=archive.getInputStream(archive.getJarEntry(rawName))) {
                            java.io.ByteArrayOutputStream content=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
                            while((n=stream.read(buffer))!=-1)content.write(buffer,0,n);
                            String expected=name.endsWith("ObjectHolderRegistry")?"59d7d436f9f11d3b7899adb55603a3ff5d11dc3ec06cc03053ed078f9de25206":CLASS_SHA;
                            if(!expected.equals(sha(content.toByteArray())))throw new IllegalStateException("Exact original Forge resource differs");
                        }
                        patched=name.endsWith("ObjectHolderRegistry")?patchRegistry(bytes,true):patch(bytes,true);
                    }
                    String receipt = "{\"target\":\"" + name + "\",\"inputSha256\":\"" + sha(bytes)
                            + "\",\"outputSha256\":\"" + sha(patched) + "\",\"stockLaunchClassLoader\":true,"
                            + "\"realJava17StaticFinalReferenceWrite\":true,\"forgeArchiveUnchanged\":true}\n";
                    Files.write(Paths.get(System.getProperty("openallay.objectholder.transformReceipt")), receipt.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    System.err.println("OPENALLAY_OBJECTHOLDER_REFERENCE_BRIDGE " + receipt.trim());
                    return patched;
                } catch (Throwable error) {
                    error.printStackTrace();
                    try {
                        java.nio.file.Path directory=Paths.get(System.getProperty("openallay.objectholder.rejected"));
                        Files.createDirectories(directory);
                        String simple=name.substring(name.lastIndexOf('/')+1);
                        Files.write(directory.resolve(simple+".class"),bytes);
                        String receipt="{\"target\":\""+name+"\",\"sha256\":\""+sha(bytes)
                            +"\",\"originalCodeSource\":\""+domain.getCodeSource().getLocation().toExternalForm()
                            +"\",\"stockLaunchClassLoader\":true,\"accepted\":false,\"error\":\""+error.getMessage()+"\"}\n";
                        Files.write(directory.resolve(simple+".json"),receipt.getBytes(StandardCharsets.UTF_8));
                    } catch(Exception captureError) { captureError.printStackTrace(); }
                    // FMLSecurityManager can reject Runtime.halt. Returning invalid bytes forces ClassFormatError,
                    // never the instrumentation exception-to-original fallback. Coordinator also marks failure.
                    return Boolean.getBoolean("openallay.objectholder.phaseDiagnostic") ? null : new byte[]{0};
                }
            }
        }, false);
    }

    static java.util.Map<String,String> methodInstructions(byte[] bytes) {
        ClassNode node=new ClassNode(Opcodes.ASM5);new ClassReader(bytes).accept(node,0);
        java.util.Map<String,String> result=new java.util.LinkedHashMap<String,String>();
        for(Object object:node.methods) { MethodNode method=(MethodNode)object;
            org.objectweb.asm.util.Textifier text=new org.objectweb.asm.util.Textifier();
            method.accept(new org.objectweb.asm.util.TraceMethodVisitor(text));result.put(method.name+method.desc,text.text.toString()); }
        return result;
    }
    static byte[] patchFarmer(byte[] bytes) throws Exception {
        ClassNode node=new ClassNode(Opcodes.ASM5);new ClassReader(bytes).accept(node,0);
        if(!FARMER_OWNER.equals(node.name))throw new IllegalStateException("Farmer holder owner differs");
        int found=0;
        for(Object object:node.fields) {
            FieldNode field=(FieldNode)object;
            if(!"FARMER".equals(field.name))continue;
            if(!"Lnet/minecraftforge/fml/common/registry/VillagerRegistry$VillagerProfession;".equals(field.desc)
                    || field.access!=(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_FINAL) || field.value!=null)
                throw new IllegalStateException("Exact farmer registry field differs");
            int annotations=0;
            if(field.visibleAnnotations!=null)for(Object value:field.visibleAnnotations) {
                org.objectweb.asm.tree.AnnotationNode annotation=(org.objectweb.asm.tree.AnnotationNode)value;
                if("Lnet/minecraftforge/fml/common/registry/GameRegistry$ObjectHolder;".equals(annotation.desc)
                        && annotation.values!=null && annotation.values.equals(Arrays.asList("value","minecraft:farmer")))annotations++;
            }
            if(annotations!=1)throw new IllegalStateException("Exact genuine farmer ObjectHolder annotation absent");
            String identity=FARMER_OWNER.replace('/','.')+"\tFARMER\tnet.minecraftforge.fml.common.registry.VillagerRegistry$VillagerProfession\n";
            Files.write(Paths.get(System.getProperty("openallay.objectholder.fields")),identity.getBytes(StandardCharsets.UTF_8),StandardOpenOption.APPEND);
            field.access&=~Opcodes.ACC_FINAL;found++;
            Files.write(Paths.get(System.getProperty("openallay.objectholder.metadata")),
                (identity.trim()+"\toriginalAccess=25\ttransformedAccess=9\tObjectHolder=minecraft:farmer\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }
        if(found!=1)throw new IllegalStateException("Exactly one FARMER field required");
        ClassWriter writer=new ClassWriter(0);node.accept(writer);byte[] result=writer.toByteArray();
        if(!methodInstructions(bytes).equals(methodInstructions(result)))throw new IllegalStateException("Farmer original initialization changed");
        return result;
    }

    static byte[] patchHolder(String name, byte[] bytes) throws Exception {
        ClassNode node = new ClassNode(Opcodes.ASM5); new ClassReader(bytes).accept(node, 0);
        if (!name.equals(node.name)) throw new IllegalStateException("Holder name differs");
        String prefix;
        if (name.endsWith("Blocks")) prefix = "Lnet/minecraft/block/";
        else if (name.endsWith("Items")) prefix = "Lnet/minecraft/item/";
        else if (name.endsWith("MobEffects")) prefix = "Lnet/minecraft/potion/Potion;";
        else if (name.endsWith("Biomes")) prefix = "Lnet/minecraft/world/biome/Biome;";
        else if (name.endsWith("Enchantments")) prefix = "Lnet/minecraft/enchantment/Enchantment;";
        else if (name.endsWith("SoundEvents")) prefix = "Lnet/minecraft/util/SoundEvent;";
        else prefix = "Lnet/minecraft/potion/PotionType;";
        int count = 0;
        for (Object object : node.fields) {
            FieldNode field = (FieldNode) object;
            if ((field.access & (Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_FINAL)) != (Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_FINAL)) continue;
            if (!field.desc.startsWith(prefix)) continue;
            if (field.value != null) throw new IllegalStateException("Registry holder cannot be compiletime constant");
            String type = field.desc.substring(1, field.desc.length()-1).replace('/', '.');
            String identity = name.replace('/', '.') + "\t" + field.name + "\t" + type + "\n";
            Files.write(Paths.get(System.getProperty("openallay.objectholder.fields")), identity.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
            int originalAccess=field.access;
            field.access &= ~Opcodes.ACC_FINAL;
            String metadata=identity.trim()+"\toriginalAccess="+originalAccess+"\ttransformedAccess="+field.access+"\n";
            Files.write(Paths.get(System.getProperty("openallay.objectholder.metadata")),metadata.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            count++;
        }
        if (count == 0) throw new IllegalStateException("Holder admitted fields empty: " + name);
        ClassWriter writer = new ClassWriter(0); node.accept(writer); byte[] result=writer.toByteArray();
        if(!methodInstructions(bytes).equals(methodInstructions(result))) throw new IllegalStateException("Holder initialization instructions changed");
        return result;
    }
    static byte[] patchRegistry(byte[] bytes) throws Exception { return patchRegistry(bytes,false); }
    static byte[] patchRegistry(byte[] bytes,boolean postForge) throws Exception {
        if (!(postForge?PHASE_REGISTRY_SHA:"59d7d436f9f11d3b7899adb55603a3ff5d11dc3ec06cc03053ed078f9de25206").equals(sha(bytes))) throw new IllegalStateException("Exact registry scan class differs");
        ClassNode node = new ClassNode(Opcodes.ASM5); new ClassReader(bytes).accept(node, 0); int count = 0;
        for (Object object : node.methods) {
            MethodNode method = (MethodNode) object;
            if (!"scanClassForFields".equals(method.name)) continue;
            int fieldLocal = -1;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if ("java/lang/reflect/Field".equals(call.owner) && "getModifiers".equals(call.name)) {
                    AbstractInsnNode previous = insn.getPrevious(); while (previous.getOpcode()<0) previous=previous.getPrevious();
                    if (!(previous instanceof VarInsnNode) || previous.getOpcode()!=Opcodes.ALOAD) throw new IllegalStateException("Exact scan field local differs");
                    fieldLocal=((VarInsnNode)previous).var;
                }
                if ("java/lang/reflect/Modifier".equals(call.owner) && "isFinal".equals(call.name) && "(I)Z".equals(call.desc)) {
                    if (fieldLocal<0) throw new IllegalStateException("Scan field identity absent");
                    method.instructions.insertBefore(insn,new VarInsnNode(Opcodes.ALOAD,fieldLocal));
                    method.instructions.set(insn,new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"isFinal","(ILjava/lang/reflect/Field;)Z",false));
                    method.maxStack++; count++; break;
                }
            }
        }
        if(count!=1) throw new IllegalStateException("Exact one final predicate required");
        ClassWriter writer=new ClassWriter(0); node.accept(writer); return writer.toByteArray();
    }

    static byte[] patch(byte[] bytes) throws Exception { return patch(bytes,false); }
    static byte[] patch(byte[] bytes,boolean postForge) throws Exception {
        if (!(postForge?PHASE_CLASS_SHA:CLASS_SHA).equals(sha(bytes))) throw new IllegalStateException("Exact ObjectHolderRef$FinalFieldHelper.class differs");
        ClassNode node = new ClassNode(Opcodes.ASM5);
        new ClassReader(bytes).accept(node, 0);
        int replacements = 0;
        for (int i = 0; i < node.methods.size(); i++) {
            MethodNode old = (MethodNode) node.methods.get(i);
            boolean make = "makeWritable".equals(old.name) && "(Ljava/lang/reflect/Field;)Ljava/lang/reflect/Field;".equals(old.desc);
            boolean set = "setField".equals(old.name) && "(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)V".equals(old.desc);
            if (!make && !set) continue;
            if (old.access != Opcodes.ACC_STATIC) throw new IllegalStateException("Exact helper access differs");
            MethodNode method = new MethodNode(Opcodes.ASM5, old.access, old.name, old.desc, null,
                    new String[] { "java/lang/ReflectiveOperationException" });
            method.visitCode();
            method.visitVarInsn(Opcodes.ALOAD, 0);
            if (set) { method.visitVarInsn(Opcodes.ALOAD, 1); method.visitVarInsn(Opcodes.ALOAD, 2); }
            method.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, old.name, old.desc, false);
            method.visitInsn(make ? Opcodes.ARETURN : Opcodes.RETURN);
            method.visitMaxs(set ? 3 : 1, set ? 3 : 1);
            method.visitEnd();
            node.methods.set(i, method);
            replacements++;
        }
        if (replacements != 2) throw new IllegalStateException("Expected only exact2 ObjectHolder final-field helpers");
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
