package dev.openallay.neoforge.network;

import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/** Native event objects converted to typed player facts. */
final class NeoForgeNativeServerLifecycle {
    private NeoForgeNativeServerLifecycle() {}
    static void register(Consumer<MinecraftServer> started, Consumer<ServerPlayer> joined,
            Consumer<ServerPlayer> disconnected) {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> started.accept(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) joined.accept(player);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) disconnected.accept(player);
        });
    }
}
