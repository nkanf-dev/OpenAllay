package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Native block-entity load/save calls; placement, validation and image behavior stay shared. */
final class NativeBlockEntityData {
    private NativeBlockEntityData() {}

    static void load(ServerLevel level, BlockEntity entity, CompoundTag tag) {
        ProblemReporter.Collector reporter = new ProblemReporter.Collector(entity.problemPath());
        try {
            entity.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), tag));
        } catch (RuntimeException failure) {
            throw new ExtensionException("invalid_block_entity", "Native block-entity decoding failed", failure);
        }
        if (!reporter.isEmpty()) {
            throw new ExtensionException("invalid_block_entity", "Native block-entity decoding failed",
                    new IllegalStateException(reporter.getReport()));
        }
    }

    static CompoundTag save(ServerLevel level, BlockEntity entity) {
        ProblemReporter.Collector reporter = new ProblemReporter.Collector(entity.problemPath());
        TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
        entity.saveWithFullMetadata(output);
        if (!reporter.isEmpty()) {
            throw new ExtensionException("invalid_block_entity", "Native block-entity encoding failed",
                    new IllegalStateException(reporter.getReport()));
        }
        return output.buildResult();
    }
}
