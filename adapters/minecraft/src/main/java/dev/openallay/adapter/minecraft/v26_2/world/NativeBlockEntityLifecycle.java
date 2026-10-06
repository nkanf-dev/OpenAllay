package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Exact native lifecycle calls. Preparation/write/readback behavior stays in one codec. */
final class NativeBlockEntityLifecycle {
    private NativeBlockEntityLifecycle() {}
    static boolean hasEntity(BlockState state) { return state.hasBlockEntity(); }
    static boolean valid(BlockEntityType<?> type, BlockState state) { return type.isValid(state); }
    static BlockEntity createDetached(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof EntityBlock block))
            throw new ExtensionException("invalid_block_entity", "Block has no native block-entity factory");
        BlockEntity entity = block.newBlockEntity(pos, state);
        if (entity == null || !valid(entity.getType(), state))
            throw new ExtensionException("invalid_block_entity", "Native block-entity factory did not create a valid entity");
        validateDetached(entity, pos, state);
        return entity;
    }
    static void afterLoad(BlockEntity entity, BlockPos pos, BlockState state) {
        validateDetached(entity, pos, state);
    }
    static void validateDetached(BlockEntity entity, BlockPos pos, BlockState state) {
        if (entity.hasLevel() || entity.isRemoved() || !entity.getBlockPos().equals(pos)
                || !valid(entity.getType(), state) || !entity.getBlockState().equals(state))
            throw new ExtensionException("invalid_block_entity", "Native preparation did not retain detached intended metadata");
    }
    static void install(ServerLevel level, BlockPos pos, BlockEntity entity) { level.setBlockEntity(entity); }
    static void markDirty(ServerLevel level, BlockPos pos) { NativeChunkDirty.mark(level, pos); }
}
