package dev.openallay.server;

import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.EntityPlayerMP;

/** Actual MCP1.12 owner/player facts; no request/admission algorithm is target-selected. */
public final class NativeServerOwner {
    private NativeServerOwner() {}
    public static boolean isOwner(MinecraftServer server) { return server.isCallingFromMinecraftThread(); }
    public static void execute(MinecraftServer server, Runnable action) { server.addScheduledTask(action); }
    public static EntityPlayerMP player(MinecraftServer server, UUID actor) {
        return server.getPlayerList().getPlayerByUUID(actor);
    }
    public static UUID actor(EntityPlayerMP player) { return player.getUniqueID(); }
    public static MinecraftServer server(EntityPlayerMP player) { return player.mcServer; }
    public static Path worldDirectory(MinecraftServer server) {
        return server.getEntityWorld().getSaveHandler().getWorldDirectory().toPath();
    }
}
