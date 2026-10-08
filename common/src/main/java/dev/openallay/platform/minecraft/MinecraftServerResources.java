package dev.openallay.platform.minecraft;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

/** Real native server resource owner; trace behavior stays shared. */
public final class MinecraftServerResources {
    private MinecraftServerResources() {}
    public static ResourceManager resources(MinecraftServer server) { return server.getResourceManager(); }
    /** Capture the actual native resource owner once for this typed callback. */
    public static <T> T withResources(MinecraftServer server,
            java.util.function.Function<? super ResourceManager, ? extends T> action) {
        ResourceManager resources = resources(server);
        return java.util.Objects.requireNonNull(action, "action").apply(resources);
    }
}
