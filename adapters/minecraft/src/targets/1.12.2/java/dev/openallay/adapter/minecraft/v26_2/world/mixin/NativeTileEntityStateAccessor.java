package dev.openallay.adapter.minecraft.v26_2.world.mixin;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(TileEntity.class)
public interface NativeTileEntityStateAccessor {
    @Accessor("blockType") void openallay$setBlockType(Block block);
    @Accessor("blockMetadata") void openallay$setBlockMetadata(int metadata);
}
