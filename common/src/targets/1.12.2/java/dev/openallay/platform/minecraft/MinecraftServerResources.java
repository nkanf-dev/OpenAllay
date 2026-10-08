package dev.openallay.platform.minecraft;

import net.minecraft.server.MinecraftServer;

/** Forge 14 has no server data packs. Traces come from actual bundled data classpath resources. */
public final class MinecraftServerResources {
    private static final MinecraftResourceAccess.Source BUNDLED = new MinecraftBundledResources(
            MinecraftServerResources.class.getClassLoader(), "data");
    private MinecraftServerResources() {}
    public static MinecraftResourceAccess.Source resources(MinecraftServer server) {
        java.util.Objects.requireNonNull(server, "server");
        return BUNDLED;
    }
    /** Capture the actual native resource owner once for this typed callback. */
    public static <T> T withResources(MinecraftServer server,
            java.util.function.Function<? super MinecraftResourceAccess.Source, ? extends T> action) {
        MinecraftResourceAccess.Source resources = resources(server);
        return java.util.Objects.requireNonNull(action, "action").apply(resources);
    }
}
