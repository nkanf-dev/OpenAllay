package dev.openallay.adapter.minecraft.v26_2.world.mixin;

import net.minecraft.util.datafix.DataFixer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Exact native MCPConfig version field; preparation must retain named native source/API. */
@Mixin(DataFixer.class)
public interface NativeDataFixerVersionAccessor {
    @Accessor("version") int openallay$getVersion();
}
