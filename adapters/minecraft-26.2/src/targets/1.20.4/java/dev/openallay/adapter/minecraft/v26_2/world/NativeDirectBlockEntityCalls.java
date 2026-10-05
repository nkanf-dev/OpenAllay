package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;

/** Pre-component direct NBT calls used by Minecraft 1.20.4 through 1.20.1. */
final class NativeDirectBlockEntityCalls {
    private NativeDirectBlockEntityCalls() {}

    static void load(ServerLevel level, BlockEntity entity, CompoundTag tag) {
        entity.load(tag);
    }
    static void validateFields(ServerLevel level, BlockEntity entity, CompoundTag tag) {
        // Native attached components do not exist in this family. Only consumed fields apply.
        if (entity instanceof BaseContainerBlockEntity && tag.contains("CustomName", Tag.TAG_STRING))
            Component.Serializer.fromJson(tag.getString("CustomName"));
    }
    static void validateItem(ServerLevel level, CompoundTag item) {
        // Preserve the actual native Count/tag defaults, not a guessed CODEC equivalence.
        ItemStack.of(item);
    }
    static CompoundTag save(ServerLevel level, BlockEntity entity) {
        return entity.saveWithFullMetadata();
    }
}
