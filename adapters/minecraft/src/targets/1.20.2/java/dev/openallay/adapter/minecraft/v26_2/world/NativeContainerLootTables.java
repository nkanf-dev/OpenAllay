package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Exact native loot-container identity; NBT field gates remain shared. */
final class NativeContainerLootTables {
    private NativeContainerLootTables() {}
    static boolean supports(BlockEntity entity) { return entity instanceof RandomizableContainerBlockEntity; }
}
