package dev.openallay.neoforge;

import dev.openallay.platform.PlatformService;
import dev.openallay.platform.InstalledModMetadata;
import java.util.List;

public final class NeoForgePlatformService implements PlatformService {
    @Override
    public String platformName() {
        return "NeoForge";
    }

    @Override
    public String gameVersion() {
        return dev.openallay.platform.minecraft.MinecraftGameVersionFacts.name();
    }

    @Override
    public String productVersion() {
        return NeoForgeNativeModMetadata.productVersion();
    }

    @Override
    public java.nio.file.Path extensionDirectory() {
        return NeoForgeNativeLoaderFacts.configDir().resolve("openallay/extensions");
    }

    @Override
    public java.util.Optional<dev.openallay.api.extension.MinecraftWorldAccess> minecraftWorldAccess() {
        if (!NeoForgeNativeEnvironment.isClient()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new dev.openallay.adapter.minecraft.v26_2.world.Minecraft26WorldAccess());
    }

    @Override
    public boolean isModLoaded(String modId) {
        return NeoForgeNativeModMetadata.isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return NeoForgeNativeEnvironment.isDevelopment();
    }

    @Override
    public List<InstalledModMetadata> installedMods() {
        return NeoForgeNativeModMetadata.installedMods();
    }
}
