package dev.openallay.adapter.minecraft.v26_2.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

/** Native SNBT and metadata access for the shared block-entity image operations. */
final class NativeBlockEntityTags {
    private NativeBlockEntityTags() {}

    static CompoundTag parseCompound(String snbt) throws CommandSyntaxException {
        return TagParser.parseCompoundFully(snbt);
    }

    static String requiredId(CompoundTag tag) {
        return tag.getString("id").orElseThrow(
                () -> new IllegalArgumentException("blockEntity requires a string id"));
    }

    static String containerTransformId(CompoundTag tag) { return tag.getString("id").orElse(""); }

    static boolean hasOnlyContainerFields(CompoundTag tag, Set<String> allowedFields) {
        return allowedFields.containsAll(tag.keySet());
    }
}
