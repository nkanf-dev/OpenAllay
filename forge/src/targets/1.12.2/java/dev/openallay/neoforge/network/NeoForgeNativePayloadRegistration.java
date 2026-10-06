package dev.openallay.neoforge.network;

import java.util.function.BiConsumer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Consume actual FML14 context and transfer the original native listener to its owner. */
final class NeoForgeNativePayloadRegistration {
    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("openallay:bridge");
    private static BiConsumer<NeoForgeBridgePayloads.Packet, EntityPlayerMP> server;
    private NeoForgeNativePayloadRegistration() {}
    static void register(BiConsumer<NeoForgeBridgePayloads.Packet, EntityPlayerMP> receiver) {
        if (server != null) throw new IllegalStateException("Bridge already registered");
        server = java.util.Objects.requireNonNull(receiver);
        CHANNEL.registerMessage(ServerHandler.class, NeoForgeBridgePayloads.Packet.class, 0, Side.SERVER);
        CHANNEL.registerMessage(ClientHandler.class, NeoForgeBridgePayloads.Packet.class, 0, Side.CLIENT);
    }
    public static final class ServerHandler implements IMessageHandler<NeoForgeBridgePayloads.Packet, IMessage> {
        @Override public IMessage onMessage(NeoForgeBridgePayloads.Packet packet, MessageContext context) {
            NetHandlerPlayServer original = context.getServerHandler();
            EntityPlayerMP player = original.player;
            MinecraftServer owner = player.mcServer;
            owner.addScheduledTask(() -> {
                if (player.connection == original && original.player == player
                        && original.netManager.isChannelOpen()
                        && owner.getPlayerList().getPlayerByUUID(player.getUniqueID()) == player) {
                    server.accept(packet, player);
                }
            });
            return null;
        }
    }
    public static final class ClientHandler implements IMessageHandler<NeoForgeBridgePayloads.Packet, IMessage> {
        @Override public IMessage onMessage(NeoForgeBridgePayloads.Packet packet, MessageContext context) {
            NeoForgeNativeClientPayloads.receive(packet, context);
            return null;
        }
    }
    static void send(EntityPlayerMP player, NeoForgeBridgePayloads.Packet message) {
        if (!player.mcServer.isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Bridge send requires native server owner");
        }
        Packet<?> packet = CHANNEL.getPacketFrom(message);
        if (!(packet instanceof FMLProxyPacket proxy)) {
            throw new IllegalStateException("FML bridge did not produce its native proxy packet");
        }
        player.connection.sendPacket(proxy);
    }
    static void sendToServer(NeoForgeBridgePayloads.Packet packet) { CHANNEL.sendToServer(packet); }
}
