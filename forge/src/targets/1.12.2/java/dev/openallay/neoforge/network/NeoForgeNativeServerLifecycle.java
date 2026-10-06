package dev.openallay.neoforge.network;

import java.util.function.Consumer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** FML lifecycle events are entrypoint methods; retain their original server across shutdown. */
public final class NeoForgeNativeServerLifecycle {
    private static Consumer<MinecraftServer> starting;
    private static Consumer<MinecraftServer> stopping;
    private static MinecraftServer originalServer;
    private NeoForgeNativeServerLifecycle() {}
    static void register(Consumer<MinecraftServer> started, Consumer<EntityPlayerMP> joined,
            Consumer<EntityPlayerMP> disconnected, Consumer<MinecraftServer> stopped) {
        if (starting != null) throw new IllegalStateException("Server lifecycle already registered");
        starting = java.util.Objects.requireNonNull(started);
        stopping = java.util.Objects.requireNonNull(stopped);
        MinecraftForge.EVENT_BUS.register(new PlayerListener(joined, disconnected));
    }
    public static final class PlayerListener {
        private final Consumer<EntityPlayerMP> joined;
        private final Consumer<EntityPlayerMP> disconnected;
        PlayerListener(Consumer<EntityPlayerMP> joined, Consumer<EntityPlayerMP> disconnected) {
            this.joined = joined; this.disconnected = disconnected;
        }
        @SubscribeEvent public void joined(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.player instanceof EntityPlayerMP player) joined.accept(player);
        }
        @SubscribeEvent public void left(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.player instanceof EntityPlayerMP player) disconnected.accept(player);
        }
    }
    public static void started() {
        MinecraftServer server = java.util.Objects.requireNonNull(
                FMLCommonHandler.instance().getMinecraftServerInstance(), "FML server missing");
        if (originalServer != null) throw new IllegalStateException("Previous server retained");
        originalServer = server;
        starting.accept(server);
    }
    public static void stopped() {
        MinecraftServer server = originalServer;
        originalServer = null;
        if (server != null) stopping.accept(server);
    }
}
