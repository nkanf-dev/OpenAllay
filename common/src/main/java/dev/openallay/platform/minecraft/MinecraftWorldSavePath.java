package dev.openallay.platform.minecraft;

import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Actual root save path of the bound server, not a client profile or world-name guess. */
public final class MinecraftWorldSavePath {
    private MinecraftWorldSavePath() {}
    public static Path root(MinecraftServer server) { return server.getWorldPath(LevelResource.ROOT); }
}
