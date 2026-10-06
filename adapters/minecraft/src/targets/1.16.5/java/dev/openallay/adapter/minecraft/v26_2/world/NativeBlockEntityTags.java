package dev.openallay.adapter.minecraft.v26_2.world;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Set;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.StringNBT;

/** Full native SNBT parser and exact persisted string-ID boundary. */
final class NativeBlockEntityTags {
    private NativeBlockEntityTags() {}
    static CompoundNBT parseCompound(String snbt) throws CommandSyntaxException { return JsonToNBT.parseTag(snbt); }
    static String requiredId(CompoundNBT tag) {
        if (!(tag.get("id") instanceof StringNBT))
            throw new IllegalArgumentException("blockEntity requires a string id");
        return tag.getString("id");
    }
    static String containerTransformId(CompoundNBT tag) { return tag.getString("id"); }
    static boolean hasOnlyContainerFields(CompoundNBT tag, Set<String> allowed) {
        return allowed.containsAll(tag.getAllKeys());
    }
}
