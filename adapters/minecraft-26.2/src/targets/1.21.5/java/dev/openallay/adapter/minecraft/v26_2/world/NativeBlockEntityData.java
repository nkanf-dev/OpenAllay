package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.LockCode;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;

/** Minecraft 1.21.5 uses direct NBT and registry-aware native block-entity serialization. */
final class NativeBlockEntityData {
    private NativeBlockEntityData() {}

    static void load(ServerLevel level, BlockEntity entity, CompoundTag tag) {
        try {
            validateDecodedFields(level, entity, tag);
            entity.loadWithComponents(tag, level.registryAccess());
        } catch (RuntimeException failure) {
            throw new ExtensionException("invalid_block_entity", "Native block-entity decoding failed", failure);
        }
    }

    // Direct NBT loaders log/default codec errors. Reject complete native codec errors,
    // as the newer ValueInput reporter does, without reimplementing load/save behavior.
    private static void validateDecodedFields(ServerLevel level, BlockEntity entity, CompoundTag tag) {
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var components = tag.get("components");
        if (components != null) DataComponentMap.CODEC.parse(ops, components).getOrThrow();
        if (entity instanceof BaseContainerBlockEntity) {
            var lock = tag.get("lock");
            if (lock != null) LockCode.CODEC.parse(ops, lock).getOrThrow();
            var name = tag.get("CustomName");
            if (name != null) ComponentSerialization.CODEC.parse(ops, name).getOrThrow();
        }
        boolean hasLootTable = false;
        if (entity instanceof RandomizableContainer) {
            var loot = tag.get("LootTable");
            if (loot != null) {
                LootTable.KEY_CODEC.parse(ops, loot).getOrThrow();
                hasLootTable = true;
            }
            var seed = tag.get("LootTableSeed");
            if (seed != null && !(seed instanceof NumericTag))
                throw new IllegalArgumentException("LootTableSeed must be numeric");
        }
        // These exact native containers skip Items when a loot-table key is present.
        if (!hasLootTable && (entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity
                || entity instanceof ShulkerBoxBlockEntity)) {
            var items = tag.get("Items");
            if (items == null) return;
            if (!(items instanceof ListTag list)) throw new IllegalArgumentException("Items must be a list");
            for (int i = 0; i < list.size(); i++) {
                var item = list.get(i);
                // Preserve the native Slot optional/default codec and the native item codec.
                ExtraCodecs.UNSIGNED_BYTE.fieldOf("Slot").orElse(0).codec().parse(ops, item).getOrThrow();
                ItemStack.CODEC.parse(ops, item).getOrThrow();
            }
        }
    }

    static CompoundTag save(ServerLevel level, BlockEntity entity) {
        try {
            return entity.saveWithFullMetadata(level.registryAccess());
        } catch (RuntimeException failure) {
            throw new ExtensionException("invalid_block_entity", "Native block-entity encoding failed", failure);
        }
    }
}
