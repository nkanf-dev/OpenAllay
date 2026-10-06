package dev.openallay.server;

import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import dev.openallay.context.minecraft.MinecraftServerPlayerLevel;

/** Typed owner/player facts used by single-source native custody algorithms. */
public final class NativeServerOwner {
    private NativeServerOwner() {}
    public static boolean published(net.minecraft.client.server.IntegratedServer server) { return server.isPublished(); }
    public static String worldName(MinecraftServer server) { return server.getWorldData().getLevelName(); }
    public static boolean survival(MinecraftServer server) { return server.getWorldData().getGameType() == net.minecraft.world.level.GameType.SURVIVAL; }

    public static boolean isOwner(MinecraftServer server) { return server.isSameThread(); }
    public static void execute(MinecraftServer server, Runnable action) { server.execute(action); }
    public static ServerPlayer player(MinecraftServer server, UUID actor) {
        return server.getPlayerList().getPlayer(actor);
    }
    public static UUID actor(ServerPlayer player) { return player.getUUID(); }
    public static MinecraftServer server(ServerPlayer player) {
        return MinecraftServerPlayerLevel.get(player).getServer();
    }
    public static Path worldDirectory(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
    }
}
