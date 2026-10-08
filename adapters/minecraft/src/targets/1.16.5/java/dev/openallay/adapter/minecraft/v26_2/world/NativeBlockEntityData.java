package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.server.ServerWorld;

/** Real native direct NBT including ForgeData, ForgeCaps and item defaults. */
final class NativeBlockEntityData {
    private NativeBlockEntityData() {}
    static void load(ServerWorld level, TileEntity entity, CompoundNBT tag) {
        try {
            // Detached intended state was bound before this native loader. The native
            // item decoder may log/default invalid content; preview/readback retain its actual image.
            entity.load(entity.getBlockState(), tag);
        } catch (RuntimeException failure) {
            throw new ExtensionException("invalid_block_entity", "Native block-entity decoding failed", failure);
        }
    }
    static CompoundNBT save(ServerWorld level, TileEntity entity) {
        try { return entity.save(new CompoundNBT()); }
        catch (RuntimeException failure) {
            throw new ExtensionException("invalid_block_entity", "Native block-entity encoding failed", failure);
        }
    }
}
