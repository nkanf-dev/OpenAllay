package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.tileentity.TileEntityType;

/** Exact native type constants; state validity in this family takes a Block. */
final class NativeContainerEntityTypes {
    private NativeContainerEntityTypes() {}
    static TileEntityType<?> chest() { return TileEntityType.CHEST; }
    static TileEntityType<?> trappedChest() { return TileEntityType.TRAPPED_CHEST; }
    static TileEntityType<?> barrel() { return TileEntityType.BARREL; }
    static TileEntityType<?> shulkerBox() { return TileEntityType.SHULKER_BOX; }
}
