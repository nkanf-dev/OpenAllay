package dev.openallay.adapter.minecraft.v26_2.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;

/** Minecraft 1.21.4 native SNBT and exact string-id access; no optional-NBT emulation. */
final class NativeBlockEntityTags {
    private NativeBlockEntityTags() {}

    static CompoundTag parseCompound(String snbt) throws CommandSyntaxException {
        return TagParser.parseTag(snbt);
    }

    static String requiredId(CompoundTag tag) {
        if (!(tag.get("id") instanceof StringTag))
            throw new IllegalArgumentException("blockEntity requires a string id");
        return tag.getString("id");
    }

    static String containerTransformId(CompoundTag tag) { return tag.getString("id"); }

    static boolean hasOnlyContainerFields(CompoundTag tag, Set<String> allowedFields) {
        return allowedFields.containsAll(tag.getAllKeys());
    }
}
