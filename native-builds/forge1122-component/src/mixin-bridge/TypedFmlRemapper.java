package dev.openallay.forge1122.mixinbridge;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.objectweb.asm.commons.Remapper;
import org.spongepowered.asm.bridge.RemapperAdapter;
import org.spongepowered.asm.mixin.extensibility.IRemapper;

/** Typed adapter across product-private ASM and genuine host FML; Mixin owns remapping algorithm. */
public final class TypedFmlRemapper extends RemapperAdapter {
    private final FMLDeobfuscatingRemapper fml;
    private TypedFmlRemapper(final FMLDeobfuscatingRemapper fml) {
        super(new Remapper() {
            public String map(String name){return fml.map(name);}
            public String mapMethodName(String owner,String name,String descriptor){return fml.mapMethodName(owner,name,descriptor);}
            public String mapFieldName(String owner,String name,String descriptor){return fml.mapFieldName(owner,name,descriptor);}
            public String mapDesc(String descriptor){return fml.mapDesc(descriptor);}
        });
        this.fml=fml;
        if(!"net.minecraft.launchwrapper.LaunchClassLoader".equals(fml.getClass().getClassLoader().getClass().getName())
                || getClass().getClassLoader()!=fml.getClass().getClassLoader())throw new IllegalStateException("Typed real FML singleton must share stock loader");
    }
    public String unmap(String name){return fml.unmap(name);}
    public static IRemapper create(){return new TypedFmlRemapper(FMLDeobfuscatingRemapper.INSTANCE);}
}
