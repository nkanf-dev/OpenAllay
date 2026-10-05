package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

/** Direct NBT family, with validation of the fields its native loaders consume. */
final class NativeBlockEntityData {
    private NativeBlockEntityData() {}

    static void load(ServerLevel level, BlockEntity entity, CompoundTag tag) {
        try {
            validateDecodedFields(level, entity, tag);
            NativeDirectBlockEntityCalls.load(level, entity, tag);
        } catch (RuntimeException failure) {
            throw new ExtensionException("invalid_block_entity", "Native block-entity decoding failed", failure);
        }
    }

    // Old direct-NBT loaders can keep partial codec results or silently default.
    // Reject codec errors, but keep their exact type gates, ignored keys and defaults.
    private static void validateDecodedFields(ServerLevel level, BlockEntity entity, CompoundTag tag) {
        NativeDirectBlockEntityCalls.validateFields(level, entity, tag);
        if (entity instanceof BaseContainerBlockEntity)
            NativeContainerLocks.validateTag(level, tag);
        // A string loot-table key makes these native containers skip Items. The
        // native loader parses its resource ID and reads only an exact long seed.
        boolean hasLootTable = NativeContainerLootTables.supports(entity)
                && tag.contains("LootTable", Tag.TAG_STRING);
        if (!hasLootTable && (entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity
                || entity instanceof ShulkerBoxBlockEntity)) {
            var container = (BaseContainerBlockEntity) entity;
            var items = tag.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                var item = items.getCompound(i);
                // getByte accepts native numeric tags, otherwise defaults to zero.
                // Out-of-range slots are ignored before the native item decoder is called.
                int slot = item.getByte("Slot") & 255;
                if (slot < container.getContainerSize()) NativeDirectBlockEntityCalls.validateItem(level, item);
            }
        }
    }

    static CompoundTag save(ServerLevel level, BlockEntity entity) {
        try {
            return NativeDirectBlockEntityCalls.save(level, entity);
        } catch (RuntimeException failure) {
            throw new ExtensionException("invalid_block_entity", "Native block-entity encoding failed", failure);
        }
    }
}
