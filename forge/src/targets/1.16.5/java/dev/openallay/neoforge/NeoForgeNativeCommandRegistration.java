package dev.openallay.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Real Forge36 server registration. Development authorization remains in shared commands. */
final class NeoForgeNativeCommandRegistration {
    private NeoForgeNativeCommandRegistration() {}
    static void server(Consumer<CommandDispatcher<CommandSourceStack>> registration) {
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> registration.accept(event.getDispatcher()));
    }
}
