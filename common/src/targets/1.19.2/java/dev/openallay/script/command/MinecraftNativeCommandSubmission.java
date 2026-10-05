package dev.openallay.script.command;

import net.minecraft.client.Minecraft;

/** Uses the active player's native command signing and submission route. */
final class MinecraftNativeCommandSubmission {
    private MinecraftNativeCommandSubmission() {}
    static void send(Minecraft client, String command) { client.player.commandSigned(command, null); }
}
