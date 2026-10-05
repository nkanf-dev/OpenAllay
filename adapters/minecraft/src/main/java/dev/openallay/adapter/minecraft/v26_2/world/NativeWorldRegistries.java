package dev.openallay.adapter.minecraft.v26_2.world;

import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Exact native holder lookups; unknown IDs must never use a defaulted registry value. */
final class NativeWorldRegistries {
    private NativeWorldRegistries() {}

    static Iterable<Block> blocks() { return BuiltInRegistries.BLOCK; }
    static Identifier blockId(Block block) { return BuiltInRegistries.BLOCK.getKey(block); }

    static Optional<Block> block(String id) {
        return BuiltInRegistries.BLOCK.get(NativeWorldResourceIds.parse(id, "block id"))
                .map(holder -> holder.value());
    }

    static Optional<BlockEntityType<?>> blockEntity(String id) {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.get(NativeWorldResourceIds.parse(id, "blockEntity id"))
                .map(holder -> holder.value());
    }
}
