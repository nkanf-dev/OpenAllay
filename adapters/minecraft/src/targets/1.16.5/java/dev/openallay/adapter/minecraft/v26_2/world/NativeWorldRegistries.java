package dev.openallay.adapter.minecraft.v26_2.world;

import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/** Actual Forge registries with both native default-fallback paths guarded. */
final class NativeWorldRegistries {
    private NativeWorldRegistries() {}
    static Iterable<Block> blocks() { return ForgeRegistries.BLOCKS; }
    static ResourceLocation blockId(Block block) {
        return ForgeRegistries.BLOCKS.containsValue(block) ? ForgeRegistries.BLOCKS.getKey(block) : null;
    }
    static Optional<Block> block(String value) {
        ResourceLocation id = NativeWorldResourceIds.parse(value, "block id");
        return ForgeRegistries.BLOCKS.containsKey(id)
                ? Optional.ofNullable(ForgeRegistries.BLOCKS.getValue(id)) : Optional.empty();
    }
    static Optional<TileEntityType<?>> blockEntity(String value) {
        ResourceLocation id = NativeWorldResourceIds.parse(value, "blockEntity id");
        return ForgeRegistries.TILE_ENTITIES.containsKey(id)
                ? Optional.ofNullable(ForgeRegistries.TILE_ENTITIES.getValue(id)) : Optional.empty();
    }
}
