package dev.openallay.neoforge.network;

import dev.openallay.OpenAllayConstants;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.PlayNetworkDirection;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.simple.SimpleChannel;

/** Actual 20.2/20.3 SimpleChannel; no application protocol gate or format fallback. */
final class NeoForgeNativePayloadRegistration {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            NeoForgeBridgePayloads.Packet.ID, () -> OpenAllayConstants.MOD_ID,
            NetworkRegistry.acceptMissingOr(label -> true), NetworkRegistry.acceptMissingOr(label -> true));
    private static volatile Function<NeoForgeBridgePayloads.Packet, Runnable> clientReceiver;
    private NeoForgeNativePayloadRegistration() {}
    static void registerClient(Function<NeoForgeBridgePayloads.Packet, Runnable> receiver) {
        clientReceiver = java.util.Objects.requireNonNull(receiver, "receiver");
    }
    static void register(BiConsumer<NeoForgeBridgePayloads.Packet, ServerPlayer> server) {
        CHANNEL.messageBuilder(NeoForgeBridgePayloads.Packet.class, 0)
                .encoder(NeoForgeBridgePayloads.Packet::write).decoder(NeoForgeBridgePayloads.Packet::read)
                .consumerNetworkThread((packet, context) -> {
                    if (context.getDirection() == PlayNetworkDirection.PLAY_TO_SERVER) {
                        ServerPlayer player = context.getSender();
                        if (player != null) context.enqueueWork(() -> server.accept(packet, player));
                    } else if (context.getDirection() == PlayNetworkDirection.PLAY_TO_CLIENT) {
                        Runnable callback = java.util.Objects.requireNonNull(
                                clientReceiver, "Client receiver not initialized").apply(packet);
                        context.enqueueWork(callback);
                    }
                    context.setPacketHandled(true);
                }).add();
    }
    static void send(ServerPlayer player, NeoForgeBridgePayloads.Packet packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    static void sendToServer(NeoForgeBridgePayloads.Packet packet) { CHANNEL.sendToServer(packet); }
}
