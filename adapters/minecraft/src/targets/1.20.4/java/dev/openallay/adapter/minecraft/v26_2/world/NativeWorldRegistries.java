package dev.openallay.adapter.minecraft.v26_2.world;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Minecraft 1.20.4 through 1.20.1 typed holder lookups; unknown IDs must never use a defaulted registry value. */
final class NativeWorldRegistries {
    private NativeWorldRegistries() {}

    static Iterable<Block> blocks() { return BuiltInRegistries.BLOCK; }
    static ResourceLocation blockId(Block block) { return BuiltInRegistries.BLOCK.getKey(block); }

    static Optional<Block> block(String id) {
        return BuiltInRegistries.BLOCK.getHolder(ResourceKey.create(Registries.BLOCK, NativeWorldResourceIds.parse(id, "block id")))
                .map(holder -> holder.value());
    }

    static Optional<BlockEntityType<?>> blockEntity(String id) {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getHolder(ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, NativeWorldResourceIds.parse(id, "blockEntity id")))
                .map(holder -> holder.value());
    }
}
