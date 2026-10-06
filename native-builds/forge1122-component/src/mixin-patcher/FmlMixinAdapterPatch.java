package dev.openallay.forge1122.build;
import java.nio.file.Files;import java.nio.file.Paths;import java.security.MessageDigest;
import org.objectweb.asm.ClassReader;import org.objectweb.asm.ClassWriter;import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;import org.objectweb.asm.tree.MethodNode;
public final class FmlMixinAdapterPatch {
    public static void main(String[] args)throws Exception {
        byte[] bytes=Files.readAllBytes(Paths.get(args[0]));StringBuilder hex=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hex.append(String.format("%02x",b&255));
        if(!"adbf418e2bf10363edc5cdc347d61a60660c68caf8756f251a3ed204f2cf6fea".equals(hex.toString()))throw new IllegalStateException("Exact genuine shadedMixin adapter differs");
        ClassNode node=new ClassNode(Opcodes.ASM9);new ClassReader(bytes).accept(node,0);int found=0;
        for(int i=0;i<node.methods.size();i++) {MethodNode old=(MethodNode)node.methods.get(i);
            if(!"create".equals(old.name)||!"()Lorg/spongepowered/asm/mixin/extensibility/IRemapper;".equals(old.desc))continue;
            if(old.access!=(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC))throw new IllegalStateException("Adapter factory access differs");
            MethodNode replacement=new MethodNode(Opcodes.ASM9,old.access,old.name,old.desc,null,null);replacement.visitCode();
            replacement.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/openallay/internal/forge1122/bridge/FmlRemapperHolder","get",old.desc,false);
            replacement.visitInsn(Opcodes.ARETURN);replacement.visitMaxs(1,0);replacement.visitEnd();node.methods.set(i,replacement);found++;
        }
        if(found!=1)throw new IllegalStateException("One genuine adapterfactory required");
        ClassWriter writer=new ClassWriter(0);node.accept(writer);Files.write(Paths.get(args[1]),writer.toByteArray());
        System.out.println("PASS exact Mixin create factory typedadapter seam only");
    }
}
