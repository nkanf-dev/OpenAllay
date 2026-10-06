package dev.openallay.runtime.forge1122;
import java.lang.instrument.ClassFileTransformer;import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;import java.security.MessageDigest;
import java.nio.file.Files;import java.nio.file.Paths;import java.nio.charset.StandardCharsets;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
public final class DimensionEnumBridge {
    static final String PHASE="d8f302eb3cab19beaacbd134b588f057f09119cb4c8f7769bec7c72cd0f10e50";
    static final String OWNER="net/minecraft/world/DimensionType";
    static final String HELPER="dev/openallay/runtime/forge1122/dimension/DimensionEnumRuntime";
    static void install(Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer(){
            public byte[] transform(ClassLoader loader,String name,Class<?> redefined,ProtectionDomain domain,byte[] bytes) {
                if(!OWNER.equals(name))return null;
                try {
                    if(redefined!=null||loader==null||!"net.minecraft.launchwrapper.LaunchClassLoader".equals(loader.getClass().getName()))throw new IllegalStateException("ExactDimension stockloader required");
                    java.net.URL source=domain.getCodeSource().getLocation();if("jar".equals(source.getProtocol()))source=((java.net.JarURLConnection)source.openConnection()).getJarFileURL();
                    if(!new java.io.File(source.toURI()).getCanonicalFile().equals(new java.io.File(System.getProperty("openallay.objectholder.client")).getCanonicalFile()))throw new IllegalStateException("OfficialclientDimension source required");
                    byte[] after=patch(bytes);
                    Files.write(Paths.get(System.getProperty("openallay.dimension.transformReceipt")),("{\"inputSha256\":\""+sha(bytes)+"\",\"outputSha256\":\""+sha(after)+"\",\"actualEnumConstructorValuesOnly\":true,\"originalConstantFlagsUnchanged\":true}\n").getBytes(StandardCharsets.UTF_8));
                    return after;
                }catch(Exception error){error.printStackTrace();return new byte[]{0};}
            }
        },false);
    }
    static byte[] patch(byte[] bytes)throws Exception {
        if(!PHASE.equals(sha(bytes)))throw new IllegalStateException("ExactactualDimensionType phase differs");
        ClassNode node=new ClassNode(Opcodes.ASM5);new ClassReader(bytes).accept(node,0);
        int array=0,test=0,add=0,ctor=0;
        for(Object value:node.fields) {FieldNode field=(FieldNode)value;
            if("$VALUES".equals(field.name)&&("[L"+OWNER+";").equals(field.desc)&&field.access==4122){field.access&=~Opcodes.ACC_FINAL;array++;}}
        for(Object value:node.methods) {MethodNode method=(MethodNode)value;
            if("<init>".equals(method.name)&&"(Ljava/lang/String;IILjava/lang/String;Ljava/lang/String;Ljava/lang/Class;)V".equals(method.desc)&&method.access==Opcodes.ACC_PRIVATE)ctor++;
            for(AbstractInsnNode instruction=method.instructions.getFirst();instruction!=null;instruction=instruction.getNext()) {
                if(!(instruction instanceof MethodInsnNode))continue;MethodInsnNode call=(MethodInsnNode)instruction;
                if(!"net/minecraftforge/common/util/EnumHelper".equals(call.owner)||call.getOpcode()!=Opcodes.INVOKESTATIC)continue;
                if("testEnum".equals(call.name)&&"(Ljava/lang/Class;[Ljava/lang/Class;)V".equals(call.desc)){
                    call.owner=OWNER;call.name="openallay$testEnum";test++;
                }else if("addEnum".equals(call.name)&&"(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;[Ljava/lang/Object;)Ljava/lang/Enum;".equals(call.desc)){
                    call.owner=HELPER;add++;
                }else throw new IllegalStateException("UnexpectedDimension EnumHelpercall");
            }
        }
        if(array!=1||test!=1||add!=1||ctor!=1)throw new IllegalStateException("ExactDimension enum seam shape differs");
        MethodNode wrapper=new MethodNode(Opcodes.ASM5,Opcodes.ACC_PRIVATE|Opcodes.ACC_STATIC|Opcodes.ACC_SYNTHETIC,"openallay$testEnum","(Ljava/lang/Class;[Ljava/lang/Class;)V",null,null);
        Label begin=new Label(),end=new Label(),caught=new Label();wrapper.visitTryCatchBlock(begin,end,caught,"java/lang/Exception");wrapper.visitCode();wrapper.visitLabel(begin);
        wrapper.visitVarInsn(Opcodes.ALOAD,0);wrapper.visitVarInsn(Opcodes.ALOAD,1);wrapper.visitMethodInsn(Opcodes.INVOKESTATIC,HELPER,"testEnum","(Ljava/lang/Class;[Ljava/lang/Class;)V",false);
        wrapper.visitLabel(end);wrapper.visitInsn(Opcodes.RETURN);wrapper.visitLabel(caught);
        wrapper.visitFrame(Opcodes.F_SAME1,0,null,1,new Object[]{"java/lang/Exception"});wrapper.visitVarInsn(Opcodes.ASTORE,2);
        wrapper.visitTypeInsn(Opcodes.NEW,"dev/openallay/runtime/forge1122/pack200/DimensionConstructorFailure");wrapper.visitInsn(Opcodes.DUP);wrapper.visitVarInsn(Opcodes.ALOAD,0);wrapper.visitVarInsn(Opcodes.ALOAD,1);wrapper.visitVarInsn(Opcodes.ALOAD,2);
        wrapper.visitMethodInsn(Opcodes.INVOKESPECIAL,"dev/openallay/runtime/forge1122/pack200/DimensionConstructorFailure","<init>","(Ljava/lang/Class;[Ljava/lang/Class;Ljava/lang/Throwable;)V",false);wrapper.visitInsn(Opcodes.ATHROW);wrapper.visitMaxs(5,3);wrapper.visitEnd();node.methods.add(wrapper);
        // Real positive/negative fixture runs once after original classinit/selftest completes; restoresactualarrays/caches.
        for(Object value:node.methods){MethodNode method=(MethodNode)value;if(!"<clinit>".equals(method.name))continue;
            for(AbstractInsnNode instruction=method.instructions.getFirst();instruction!=null;instruction=instruction.getNext())if(instruction.getOpcode()==Opcodes.RETURN){
                InsnList hook=new InsnList();hook.add(new LdcInsnNode(Type.getObjectType(OWNER)));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HELPER,"fixture","(Ljava/lang/Class;)V",false));method.instructions.insertBefore(instruction,hook);method.maxStack=Math.max(method.maxStack,1);}}
        ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();
    }
    static String sha(byte[] bytes)throws Exception {StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hex.append(String.format("%02x",b&255));return hex.toString();}
}
