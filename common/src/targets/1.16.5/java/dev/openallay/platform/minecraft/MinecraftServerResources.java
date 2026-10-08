package dev.openallay.platform.minecraft;

import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.IResourceManager;

/** Actual old server retained DataPackRegistries resource manager. */
public final class MinecraftServerResources {
    private MinecraftServerResources() {}
    public static IResourceManager resources(MinecraftServer server) {
        return server.getDataPackRegistries().getResourceManager();
    }
    /** Capture the actual native resource owner once for this typed callback. */
    public static <T> T withResources(MinecraftServer server,
            java.util.function.Function<? super IResourceManager, ? extends T> action) {
        IResourceManager resources = resources(server);
        return java.util.Objects.requireNonNull(action, "action").apply(resources);
    }
}
