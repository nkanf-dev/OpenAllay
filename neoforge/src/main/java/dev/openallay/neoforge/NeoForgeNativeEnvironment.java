package dev.openallay.neoforge;

/** Loader environment access is bound once to the actual FML API family. */
final class NeoForgeNativeEnvironment {
    private NeoForgeNativeEnvironment() {}
    static boolean isClient() { return net.neoforged.fml.loading.FMLEnvironment.getDist().isClient(); }
    static boolean isDevelopment() { return !net.neoforged.fml.loading.FMLLoader.getCurrent().isProduction(); }
}
