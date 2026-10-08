package dev.openallay.neoforge.network;

import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Native event objects converted to typed player facts. */
final class NeoForgeNativeServerLifecycle {
    private NeoForgeNativeServerLifecycle() {}
    static void register(Consumer<MinecraftServer> started, Consumer<ServerPlayer> joined,
            Consumer<ServerPlayer> disconnected, Consumer<MinecraftServer> stopped) {
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> stopped.accept(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> started.accept(event.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) joined.accept(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) disconnected.accept(player);
        });
    }
}
