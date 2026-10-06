package dev.openallay.adapter.minecraft.v26_2.world.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact native detached-instance metadata binding; never used to place a world block. */
@Mixin(TileEntity.class)
public interface NativeTileEntityStateAccessor {
    @Accessor("blockState")
    void openallay$setIntendedState(BlockState state);
}
