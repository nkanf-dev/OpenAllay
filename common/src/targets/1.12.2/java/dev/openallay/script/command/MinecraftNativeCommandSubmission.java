package dev.openallay.script.command;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;

/** Use the real client command registry once, then submit unhandled commands to the server. */
final class MinecraftNativeCommandSubmission {
    private MinecraftNativeCommandSubmission() {}
    static void send(Minecraft client, String command) {
        if (!client.isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Command submission requires native client owner");
        }
        net.minecraft.client.entity.EntityPlayerSP player = java.util.Objects.requireNonNull(client.player, "Player command sender unavailable");
        String line = "/" + command;
        if (ClientCommandHandler.instance.executeCommand(player, line) == 0) {
            player.sendChatMessage(line);
        }
    }
}
