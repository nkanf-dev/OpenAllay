package dev.openallay.script.command;

import net.minecraft.client.Minecraft;

/** Submit through the active player's native pre-signing command/chat route. */
final class MinecraftNativeCommandSubmission {
    private MinecraftNativeCommandSubmission() {}
    static void send(Minecraft client, String command) { client.player.chat("/" + command); }
}
