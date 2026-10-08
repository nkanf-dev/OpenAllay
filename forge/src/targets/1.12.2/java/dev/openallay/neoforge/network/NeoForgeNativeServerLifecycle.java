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
            final class $oaPattern0_Holder { net.minecraft.entity.player.EntityPlayer value; EntityPlayerMP bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = event.player) instanceof net.minecraft.entity.player.EntityPlayerMP && (($oaPattern0_holder.bound = (EntityPlayerMP) $oaPattern0_holder.value) != null))) joined.accept($oaPattern0_holder.bound);
        }
        @SubscribeEvent public void left(PlayerEvent.PlayerLoggedOutEvent event) {
            final class $oaPattern1_Holder { net.minecraft.entity.player.EntityPlayer value; EntityPlayerMP bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = event.player) instanceof net.minecraft.entity.player.EntityPlayerMP && (($oaPattern1_holder.bound = (EntityPlayerMP) $oaPattern1_holder.value) != null))) disconnected.accept($oaPattern1_holder.bound);
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
