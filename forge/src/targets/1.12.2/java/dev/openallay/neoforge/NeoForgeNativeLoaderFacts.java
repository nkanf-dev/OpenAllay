package dev.openallay.neoforge;

import java.nio.file.Path;

/** FML14 supplies config location during pre-initialization, before engine startup. */
public final class NeoForgeNativeLoaderFacts {
    private static Path configDirectory;
    private NeoForgeNativeLoaderFacts() {}
    static void install(Path directory) {
        Path value = java.util.Objects.requireNonNull(directory).toAbsolutePath().normalize();
        if (configDirectory != null && !configDirectory.equals(value)) {
            throw new IllegalStateException("Loader config directory already installed");
        }
        configDirectory = value;
    }
    static String platformName() { return "Forge"; }
    public static Path configDir() {
        return java.util.Objects.requireNonNull(configDirectory, "FML pre-initialization has not run");
    }
    static String modVersion() { return NeoForgeNativeModMetadata.productVersion(); }
}
