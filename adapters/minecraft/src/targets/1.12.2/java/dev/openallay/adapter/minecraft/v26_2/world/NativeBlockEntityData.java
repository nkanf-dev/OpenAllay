package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
/** Direct full native NBT; does not use the failure-swallowing TileEntity.create. */
final class NativeBlockEntityData {
    private NativeBlockEntityData() {}
    static void load(WorldServer level,TileEntity entity,NBTTagCompound tag) {
        try { entity.readFromNBT(tag); }
        catch(RuntimeException failure) { throw new ExtensionException("invalid_block_entity","Native block-entity decoding failed",failure); }
    }
    static NBTTagCompound save(WorldServer level,TileEntity entity) {
        try { return entity.writeToNBT(new NBTTagCompound()); }
        catch(RuntimeException failure) { throw new ExtensionException("invalid_block_entity","Native block-entity encoding failed",failure); }
    }
}
