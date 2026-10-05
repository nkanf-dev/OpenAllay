package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.world.level.block.entity.BlockEntityType;

/** Native container identity constants; codec behavior is shared across source families. */
final class NativeContainerEntityTypes {
    private NativeContainerEntityTypes() {}
    static BlockEntityType<?> chest() { return BlockEntityType.CHEST; }
    static BlockEntityType<?> trappedChest() { return BlockEntityType.TRAPPED_CHEST; }
    static BlockEntityType<?> barrel() { return BlockEntityType.BARREL; }
    static BlockEntityType<?> shulkerBox() { return BlockEntityType.SHULKER_BOX; }
}
