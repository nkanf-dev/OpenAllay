package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import dev.openallay.adapter.minecraft.v26_2.world.mixin.NativeTileEntityStateAccessor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
/** Detached Forge factory. A factory that needs a live World is rejected, never granted one. */
final class NativeBlockEntityLifecycle {
    private NativeBlockEntityLifecycle() {}
    static boolean hasEntity(IBlockState state) { return state.getBlock().hasTileEntity(state); }
    static TileEntity live(WorldServer level,BlockPos pos) {
        // Even CHECK can remove invalid entries. Read the actual live map instead.
        TileEntity entity=NativeLoadedChunks.get(level,pos).getTileEntityMap().get(pos);
        if(entity!=null && (entity.isInvalid() || entity.getWorld()!=level || !entity.getPos().equals(pos)))
            throw new ExtensionException("invalid_block_entity","Live block-entity ownership is invalid at "+pos);
        return entity;
    }
    static TileEntity createDetached(WorldServer level,BlockPos pos,IBlockState state) {
        NativeLoadedChunks.require(level,pos);
        TileEntity entity;
        try { entity=state.getBlock().createTileEntity(null,state); }
        catch(RuntimeException failure) { throw new ExtensionException("unsupported_detached_factory","Native factory cannot create without a live world",failure); }
        if(entity==null || entity.hasWorld() || entity.isInvalid() || TileEntity.getKey(entity.getClass())==null)
            throw new ExtensionException("invalid_block_entity","Native factory did not return a registered detached entity");
        if(level.loadedTileEntityList.contains(entity) || level.tickableTileEntities.contains(entity)
                || NativeLoadedChunks.get(level,pos).getTileEntityMap().containsValue(entity))
            throw new ExtensionException("invalid_block_entity","Native factory reused a live entity");
        entity.setPos(pos);
        bind(entity,state);
        validateDetached(entity,pos,state);
        return entity;
    }
    private static void bind(TileEntity entity,IBlockState state) {
        NativeTileEntityStateAccessor access=(NativeTileEntityStateAccessor)entity;
        access.openallay$setBlockType(state.getBlock());
        access.openallay$setBlockMetadata(state.getBlock().getMetaFromState(state));
    }
    static void afterLoad(TileEntity entity,BlockPos pos,IBlockState state) {
        if(entity.hasWorld() || entity.isInvalid() || !entity.getPos().equals(pos))
            throw new ExtensionException("invalid_block_entity","Native load changed detached entity ownership or coordinates");
        bind(entity,state);
        validateDetached(entity,pos,state);
    }
    static void validateDetached(TileEntity entity,BlockPos pos,IBlockState state) {
        if(entity.hasWorld() || entity.isInvalid() || !entity.getPos().equals(pos)
                || entity.getBlockType()!=state.getBlock() || entity.getBlockMetadata()!=state.getBlock().getMetaFromState(state))
            throw new ExtensionException("invalid_block_entity","Native preparation did not retain detached intended metadata");
    }
    static void install(WorldServer level,BlockPos pos,TileEntity entity) {
        entity.updateContainingBlockInfo();
        level.setTileEntity(pos,entity);
        // World.setTileEntity calls addTileEntity/onLoad; never invoke onLoad twice.
    }
    static void markDirty(WorldServer level,BlockPos pos) { NativeLoadedChunks.get(level,pos).markDirty(); }
}
