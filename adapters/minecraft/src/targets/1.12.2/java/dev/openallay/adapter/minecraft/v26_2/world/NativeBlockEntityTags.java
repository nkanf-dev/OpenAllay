package dev.openallay.adapter.minecraft.v26_2.world;

import java.util.Set;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
final class NativeBlockEntityTags {
    private NativeBlockEntityTags() {}
    static NBTTagCompound parseCompound(String snbt) throws NBTException { return JsonToNBT.getTagFromJson(snbt); }
    static String requiredId(NBTTagCompound tag) {
        if(!(tag.getTag("id") instanceof NBTTagString)) throw new IllegalArgumentException("blockEntity requires a string id");
        return tag.getString("id");
    }
    static String containerTransformId(NBTTagCompound tag) { return requiredId(tag); }
    static boolean hasOnlyContainerFields(NBTTagCompound tag,Set<String> allowed) { return allowed.containsAll(tag.getKeySet()); }
}
