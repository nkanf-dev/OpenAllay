package dev.openallay.adapter.minecraft.v26_2.world;

import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Minecraft 1.19.2 owns built-in registries and registry keys in Registry. */
final class NativeWorldRegistries {
    private NativeWorldRegistries() {}

    static Iterable<Block> blocks() { return Registry.BLOCK; }
    static ResourceLocation blockId(Block block) { return Registry.BLOCK.getKey(block); }

    static Optional<Block> block(String id) {
        return Registry.BLOCK.getHolder(ResourceKey.create(Registry.BLOCK_REGISTRY, NativeWorldResourceIds.parse(id, "block id")))
                .map(holder -> holder.value());
    }

    static Optional<BlockEntityType<?>> blockEntity(String id) {
        return Registry.BLOCK_ENTITY_TYPE.getHolder(ResourceKey.create(Registry.BLOCK_ENTITY_TYPE_REGISTRY, NativeWorldResourceIds.parse(id, "blockEntity id")))
                .map(holder -> holder.value());
    }
}
