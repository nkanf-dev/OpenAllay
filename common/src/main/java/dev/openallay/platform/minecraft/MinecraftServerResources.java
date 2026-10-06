package dev.openallay.platform.minecraft;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

/** Real native server resource owner; trace behavior stays shared. */
public final class MinecraftServerResources {
    private MinecraftServerResources() {}
    public static ResourceManager resources(MinecraftServer server) { return server.getResourceManager(); }
}
