package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

/** Native container identity constants; codec behavior is shared across source families. */
final class NativeContainerEntityTypes {
    private NativeContainerEntityTypes() {}
    static BlockEntityType<?> chest() { return BlockEntityTypes.CHEST; }
    static BlockEntityType<?> trappedChest() { return BlockEntityTypes.TRAPPED_CHEST; }
    static BlockEntityType<?> barrel() { return BlockEntityTypes.BARREL; }
    static BlockEntityType<?> shulkerBox() { return BlockEntityTypes.SHULKER_BOX; }
}
