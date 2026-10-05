package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

/** Registry-aware direct NBT calls, separate from the shared container validation loop. */
final class NativeDirectBlockEntityCalls {
    private NativeDirectBlockEntityCalls() {}

    static void load(ServerLevel level, BlockEntity entity, CompoundTag tag) {
        entity.loadWithComponents(tag, level.registryAccess());
    }
    static void validateFields(ServerLevel level, BlockEntity entity, CompoundTag tag) {
        var components = tag.get("components");
        if (components != null)
            DataComponentMap.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), components).getOrThrow();
        if (entity instanceof BaseContainerBlockEntity && tag.contains("CustomName", Tag.TAG_STRING))
            Component.Serializer.fromJson(tag.getString("CustomName"), level.registryAccess());
    }
    static void validateItem(ServerLevel level, CompoundTag item) {
        ItemStack.CODEC.parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), item).getOrThrow();
    }
    static CompoundTag save(ServerLevel level, BlockEntity entity) {
        // Native saveWithoutMetadata logs and retains partial component output.
        DataComponentMap.CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), entity.components()).getOrThrow();
        return entity.saveWithFullMetadata(level.registryAccess());
    }
}
