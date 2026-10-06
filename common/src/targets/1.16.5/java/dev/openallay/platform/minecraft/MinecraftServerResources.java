package dev.openallay.platform.minecraft;

import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.IResourceManager;

/** Actual old server retained DataPackRegistries resource manager. */
public final class MinecraftServerResources {
    private MinecraftServerResources() {}
    public static IResourceManager resources(MinecraftServer server) {
        return server.getDataPackRegistries().getResourceManager();
    }
}
