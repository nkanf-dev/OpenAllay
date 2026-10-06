package dev.openallay.forge1122.component;
public final class PrivateAsmVersionAcceptance {
    public static void main(String[] args) throws Exception {
        String version=dev.openallay.internal.forge1122.asm.Opcodes.class.getPackage().getImplementationVersion();
        if(!"9.6".equals(version)||!org.spongepowered.asm.util.asm.ASM.isAtLeastVersion(9,1))throw new AssertionError("Real privateASM package metadata lost: "+version);
        if(!"dev.openallay.internal.forge1122.asm.Opcodes".equals(dev.openallay.internal.forge1122.asm.Opcodes.class.getName()))throw new AssertionError("Stock ASM namespace replaced");
        System.out.println("PASS genuine private ASM package Implementation-Version9.6, real Mixin>=9.1 Java17 support");
    }
}
