package dev.openallay.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Binds shared command trees to the selected loader event API. */
final class NeoForgeNativeCommandRegistration {
    private NeoForgeNativeCommandRegistration() {}

    static void client(Consumer<CommandDispatcher<CommandSourceStack>> registration) {
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                registration.accept(event.getDispatcher()));
    }

    static void server(Consumer<CommandDispatcher<CommandSourceStack>> registration) {
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                registration.accept(event.getDispatcher()));
    }
}
