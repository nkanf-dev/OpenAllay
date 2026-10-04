package dev.openallay.platform;

import java.util.List;

public interface PlatformService {
    String platformName();

    /** Exact running Minecraft version, supplied by the loader after game bootstrap. */
    String gameVersion();

    /** Exact installed core product version from loader metadata. */
    default String productVersion() {
        throw new UnsupportedOperationException("Product metadata is unavailable");
    }

    /** Core-owned startup Extension packages; never a player world directory. */
    default java.nio.file.Path extensionDirectory() {
        throw new UnsupportedOperationException("Extension directory is unavailable");
    }

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    /** Complete public loader metadata, detached and sorted by mod id. */
    default List<InstalledModMetadata> installedMods() {
        throw new UnsupportedOperationException("Installed mod metadata is unavailable");
    }

}
