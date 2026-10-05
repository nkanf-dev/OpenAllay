package dev.openallay.neoforge;

import java.nio.file.Path;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/** Config/metadata values detached from actual loader API. */
public final class NeoForgeNativeLoaderFacts {
    private NeoForgeNativeLoaderFacts() {}
    static String platformName() { return "NeoForge"; }
    public static Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    static String modVersion() {
        return ModList.get().getModContainerById("openallay")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
    }
}
