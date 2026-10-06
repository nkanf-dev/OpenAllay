package dev.openallay.platform.minecraft;

import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;

/** The native overworld save handler owns the actual integrated or dedicated world directory. */
public final class MinecraftWorldSavePath {
    private MinecraftWorldSavePath() {}
    public static Path root(MinecraftServer server) {
        return java.util.Objects.requireNonNull(server.getWorld(0), "Overworld unavailable")
                .getSaveHandler().getWorldDirectory().toPath();
    }
}
