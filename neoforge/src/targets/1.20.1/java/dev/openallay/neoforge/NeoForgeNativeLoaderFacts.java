package dev.openallay.neoforge;

import java.nio.file.Path;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

/** Config/metadata values detached from actual loader API. */
public final class NeoForgeNativeLoaderFacts {
    private NeoForgeNativeLoaderFacts() {}
    public static Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    static String modVersion() {
        return ModList.get().getModContainerById("openallay")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("unknown");
    }
}
