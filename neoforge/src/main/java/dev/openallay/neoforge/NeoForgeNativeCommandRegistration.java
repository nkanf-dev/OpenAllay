package dev.openallay.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Binds shared command trees to the selected loader event API. */
final class NeoForgeNativeCommandRegistration {
    private NeoForgeNativeCommandRegistration() {}

    static void client(Consumer<CommandDispatcher<CommandSourceStack>> registration) {
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) ->
                registration.accept(event.getDispatcher()));
    }

    static void server(Consumer<CommandDispatcher<CommandSourceStack>> registration) {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                registration.accept(event.getDispatcher()));
    }
}
