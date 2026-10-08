package dev.openallay.script.command;

import net.minecraft.client.Minecraft;
import dev.openallay.neoforge.command.ForgeClientGuideCommands;

/** Direct player.chat bypasses Forge's Screen event. Intercept only the owned local root. */
final class MinecraftNativeCommandSubmission {
    private MinecraftNativeCommandSubmission() {}
    static void send(Minecraft client, String command) {
        if (!ForgeClientGuideCommands.submit("/" + command, false)) client.player.chat("/" + command);
    }
}
