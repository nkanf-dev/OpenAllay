package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.tileentity.LockableLootTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Exact native inheritance, including shulker's real loot-table loader. */
final class NativeContainerLootTables {
    private NativeContainerLootTables() {}
    static boolean supports(TileEntity entity) { return entity instanceof LockableLootTileEntity; }
}
