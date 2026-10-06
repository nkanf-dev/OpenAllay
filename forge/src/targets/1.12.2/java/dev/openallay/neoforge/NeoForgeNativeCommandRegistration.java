package dev.openallay.neoforge;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.command.ICommand;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

/** Typed native registries, not a synthetic Brigadier dispatcher. */
final class NeoForgeNativeCommandRegistration {
    private static final List<ICommand> server = new ArrayList<>();
    private NeoForgeNativeCommandRegistration() {}
    static void client(ICommand command) { ClientCommandHandler.instance.registerCommand(command); }
    static void server(ICommand command) { server.add(java.util.Objects.requireNonNull(command)); }
    static void serverStarting(FMLServerStartingEvent event) { server.forEach(event::registerServerCommand); }
}
