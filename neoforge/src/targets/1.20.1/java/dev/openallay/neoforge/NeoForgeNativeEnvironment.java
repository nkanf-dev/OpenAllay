package dev.openallay.neoforge;

/** Loader environment access is bound once to the actual FML API family. */
final class NeoForgeNativeEnvironment {
    private NeoForgeNativeEnvironment() {}
    static boolean isClient() { return net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient(); }
    static boolean isDevelopment() { return !net.minecraftforge.fml.loading.FMLLoader.isProduction(); }
}
