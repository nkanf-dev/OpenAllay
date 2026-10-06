package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.LockableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.server.ServerWorld;

/** Real public direct calls retained for exact source-family compilation closure. */
final class NativeDirectBlockEntityCalls {
    private NativeDirectBlockEntityCalls() {}
    static void load(ServerWorld level, TileEntity entity, CompoundNBT tag) { entity.load(entity.getBlockState(), tag); }
    static void validateFields(ServerWorld level, TileEntity entity, CompoundNBT tag) {
        if (entity instanceof LockableTileEntity && tag.contains("CustomName", 8))
            ITextComponent.Serializer.fromJson(tag.getString("CustomName"));
    }
    static void validateItem(ServerWorld level, CompoundNBT item) { ItemStack.of(item); }
    static CompoundNBT save(ServerWorld level, TileEntity entity) { return entity.save(new CompoundNBT()); }
}
