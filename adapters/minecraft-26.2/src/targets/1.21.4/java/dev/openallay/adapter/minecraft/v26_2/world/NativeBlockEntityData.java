package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.LockCode;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/** Minecraft 1.21.4 direct NBT, with validation of the fields its native loaders consume. */
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

    // Old direct-NBT loaders can keep partial codec results or silently default.
    // Reject codec errors, but keep their exact type gates, ignored keys and defaults.
    private static void validateDecodedFields(ServerLevel level, BlockEntity entity, CompoundTag tag) {
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var components = tag.get("components");
        if (components != null) DataComponentMap.CODEC.parse(ops, components).getOrThrow();
        if (entity instanceof BaseContainerBlockEntity) {
            if (tag.contains("lock", Tag.TAG_COMPOUND))
                LockCode.CODEC.parse(ops, tag.get("lock")).getOrThrow();
            // 1.21.4 stores CustomName as JSON in a string, not as an NBT component.
            if (tag.contains("CustomName", Tag.TAG_STRING))
                Component.Serializer.fromJson(tag.getString("CustomName"), level.registryAccess());
        }
        // A string loot-table key makes these native containers skip Items. The
        // native loader parses its resource ID and reads only an exact long seed.
        boolean hasLootTable = entity instanceof RandomizableContainer
                && tag.contains("LootTable", Tag.TAG_STRING);
        if (!hasLootTable && (entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity
                || entity instanceof ShulkerBoxBlockEntity)) {
            var container = (BaseContainerBlockEntity) entity;
            var items = tag.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                var item = items.getCompound(i);
                // getByte accepts native numeric tags, otherwise defaults to zero.
                // Out-of-range slots are ignored before ItemStack.parse is called.
                int slot = item.getByte("Slot") & 255;
                if (slot < container.getContainerSize()) ItemStack.CODEC.parse(ops, item).getOrThrow();
            }
        }
    }

    static CompoundTag save(ServerLevel level, BlockEntity entity) {
        try {
            // Native saveWithoutMetadata logs and retains partial component output.
            // Validate that same native codec before delegating the actual save.
            var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            DataComponentMap.CODEC.encodeStart(ops, entity.components()).getOrThrow();
            return entity.saveWithFullMetadata(level.registryAccess());
        } catch (RuntimeException failure) {
            throw new ExtensionException("invalid_block_entity", "Native block-entity encoding failed", failure);
        }
    }
}
