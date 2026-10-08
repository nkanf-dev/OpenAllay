package dev.openallay.adapter.minecraft.v26_2.world;

import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
/** Actual Forge block registry; no default fallback and no fictitious TE-type registry. */
final class NativeWorldRegistries {
    private NativeWorldRegistries() {}
    static Iterable<Block> blocks() { return ForgeRegistries.BLOCKS; }
    static ResourceLocation blockId(Block block) {
        return ForgeRegistries.BLOCKS.containsValue(block) ? ForgeRegistries.BLOCKS.getKey(block) : null;
    }
    static Optional<Block> block(String value) {
        ResourceLocation id=NativeWorldResourceIds.parse(value,"block id");
        return ForgeRegistries.BLOCKS.containsKey(id) ? Optional.ofNullable(ForgeRegistries.BLOCKS.getValue(id)) : Optional.empty();
    }
}
