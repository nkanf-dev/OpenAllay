package dev.openallay.internal.forge1122.bridge;
import org.spongepowered.asm.mixin.extensibility.IRemapper;
/** Shared parent-owned typed bridge; registration precedes genuine Mixin remapper injection. */
public final class FmlRemapperHolder {
    private static IRemapper remapper;
    public static void register(IRemapper value) {
        if(value==null||remapper!=null||FmlRemapperHolder.class.getClassLoader()!=ClassLoader.getSystemClassLoader()
            || !"net.minecraft.launchwrapper.LaunchClassLoader".equals(value.getClass().getClassLoader().getClass().getName()))throw new IllegalStateException("One typed FML remapper registration required");
        remapper=value;
    }
    public static IRemapper get(){if(remapper==null)throw new IllegalStateException("Typed FML remapper not registered");return remapper;}
}
