package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import dev.openallay.adapter.minecraft.v26_2.world.mixin.NativeTileEntityStateAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;

/** State-aware Forge factory and native lifecycle with detached intended metadata. */
final class NativeBlockEntityLifecycle {
    private NativeBlockEntityLifecycle() {}
    static boolean hasEntity(BlockState state) { return state.hasTileEntity(); }
    static boolean valid(TileEntityType<?> type, BlockState state) { return type.isValid(state.getBlock()); }
    static TileEntity createDetached(ServerWorld level, BlockPos pos, BlockState state) {
        NativeLoadedChunks.require(level, pos);
        TileEntity entity;
        try { entity = state.createTileEntity(new NativeFactoryBlockReader(level, pos, state)); }
        catch (ClassCastException failure) {
            throw new ExtensionException("unsupported_factory_reader", "A native factory requires a live world instead of its read-only native reader", failure);
        }
        if (entity == null || entity.hasLevel() || entity.isRemoved() || !valid(entity.getType(), state))
            throw new ExtensionException("invalid_block_entity", "Native factory did not return a valid detached entity");
        // Reject actual native live membership without calling a lookup that can promote pending NBT.
        if (level.blockEntityList.contains(entity) || level.tickableBlockEntities.contains(entity)
                || NativeLoadedChunks.get(level, pos).getBlockEntities().containsValue(entity))
            throw new ExtensionException("invalid_block_entity", "Native factory reused a live block entity");
        entity.setPosition(pos);
        ((NativeTileEntityStateAccessor) entity).openallay$setIntendedState(state);
        validateDetached(entity, pos, state);
        return entity;
    }
    static void afterLoad(TileEntity entity, BlockPos pos, BlockState state) {
        if (entity.hasLevel() || entity.isRemoved() || !entity.getBlockPos().equals(pos) || !valid(entity.getType(), state))
            throw new ExtensionException("invalid_block_entity", "Native load changed detached entity ownership or metadata");
        // Native overrides may clear the cache. Rebind only this detached instance,
        // never a live entity, before full native serialization.
        ((NativeTileEntityStateAccessor) entity).openallay$setIntendedState(state);
        validateDetached(entity, pos, state);
    }
    static void validateDetached(TileEntity entity, BlockPos pos, BlockState state) {
        if (entity.hasLevel() || entity.isRemoved() || !entity.getBlockPos().equals(pos)
                || !valid(entity.getType(), state) || !entity.getBlockState().equals(state))
            throw new ExtensionException("invalid_block_entity", "Native preparation did not retain detached intended metadata");
    }
    static void install(ServerWorld level, BlockPos pos, TileEntity entity) {
        entity.clearCache();
        level.setBlockEntity(pos, entity);
    }
    static void markDirty(ServerWorld level, BlockPos pos) {
        NativeLoadedChunks.get(level, pos).markUnsaved();
    }
}
