package dev.openallay.neoforge.network;

import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Captures the original client listener before the Netty-to-client thread handoff. */
public final class NeoForgeNativeClientPayloads {
    private static Function<NeoForgeBridgePayloads.Packet, Runnable> receiver;
    private NeoForgeNativeClientPayloads() {}
    static void register(Function<NeoForgeBridgePayloads.Packet, Runnable> callback) {
        if (receiver != null) throw new IllegalStateException("Client bridge already registered");
        receiver = java.util.Objects.requireNonNull(callback);
    }
    static void receive(NeoForgeBridgePayloads.Packet packet, MessageContext context) {
        NetHandlerPlayClient original = context.getClientHandler();
        Minecraft client = Minecraft.getMinecraft();
        client.addScheduledTask(() -> {
            if (receiver != null && client.getConnection() == original) receiver.apply(packet).run();
        });
    }
    static void onDisconnected(Runnable disconnected) {
        MinecraftForge.EVENT_BUS.register(new DisconnectListener(disconnected));
    }
    public static final class DisconnectListener {
        private final Runnable disconnected;
        DisconnectListener(Runnable disconnected) { this.disconnected = disconnected; }
        @SubscribeEvent public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            Minecraft client = Minecraft.getMinecraft();
            client.addScheduledTask(() -> {
                var current = client.getConnection();
                if (current == null || current.getNetworkManager() == event.getManager()) disconnected.run();
            });
        }
    }
    static void send(NeoForgeBridgePayloads.Packet packet) { NeoForgeNativePayloadRegistration.sendToServer(packet); }
}
