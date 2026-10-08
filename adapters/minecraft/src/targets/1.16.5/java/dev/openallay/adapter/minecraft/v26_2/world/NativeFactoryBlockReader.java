package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

/** A real native read interface without additional live world or block-entity handles. */
final class NativeFactoryBlockReader implements IBlockReader {
    private final ServerWorld level;
    private final BlockPos target;
    private final BlockState intended;
    NativeFactoryBlockReader(ServerWorld level, BlockPos target, BlockState intended) {
        this.level = level; this.target = target.immutable(); this.intended = intended;
    }
    private void check(BlockPos pos) {
        if (!level.getServer().isSameThread())
            throw new ExtensionException("wrong_owner", "Native factory reads require the server owner thread");
        if (!World.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos))
            throw new ExtensionException("invalid_bounds", "Native factory read is outside active native bounds: " + pos);
        NativeLoadedChunks.require(level, pos);
    }
    @Override public BlockState getBlockState(BlockPos pos) {
        check(pos);
        return pos.equals(target) ? intended : NativeLoadedChunks.get(level, pos).getBlockState(pos);
    }
    @Override public FluidState getFluidState(BlockPos pos) {
        check(pos);
        return pos.equals(target) ? intended.getFluidState() : NativeLoadedChunks.get(level, pos).getFluidState(pos);
    }
    @Override public TileEntity getBlockEntity(BlockPos pos) {
        check(pos);
        if (pos.equals(target)) return null; // The intended detached-new target has no live entity.
        // The real chunk union includes pending NBT and live entities. Inspecting
        // that union cannot promote pending entities or insert a new entity.
        if (!NativeLoadedChunks.get(level, pos).getBlockEntitiesPos().contains(pos)) return null;
        throw new ExtensionException("unsupported_factory_reader", "A native factory requested another live block entity: " + pos);
    }
}
