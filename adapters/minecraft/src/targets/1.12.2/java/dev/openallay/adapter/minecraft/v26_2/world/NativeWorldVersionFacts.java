package dev.openallay.adapter.minecraft.v26_2.world;

import dev.openallay.api.extension.ExtensionException;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.server.MinecraftServer;

/** Game save DataVersion from native WorldInfo serialization, not a Forge mod fixer epoch. */
final class NativeWorldVersionFacts {
    private NativeWorldVersionFacts() {}
    static int dataVersion(MinecraftServer server) {
        var tag=server.getWorld(0).getWorldInfo().cloneNBTCompound(null);
        if(!(tag.getTag("DataVersion") instanceof NBTTagInt))
            throw new ExtensionException("native_version_unavailable","Native world save metadata has no integer DataVersion");
        return tag.getInteger("DataVersion");
    }
}
