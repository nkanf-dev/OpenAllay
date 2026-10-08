package dev.openallay.neoforge.network;

import dev.openallay.OpenAllayConstants;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

/** Actual early NeoForge Forge SimpleChannel; no application protocol gate or format fallback. */
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
        CHANNEL.registerMessage(0, NeoForgeBridgePayloads.Packet.class,
                NeoForgeBridgePayloads.Packet::write, NeoForgeBridgePayloads.Packet::read,
                (packet, contextSupplier) -> {
                    var context = contextSupplier.get();
                    if (context.getDirection() == NetworkDirection.PLAY_TO_SERVER) {
                        ServerPlayer player = context.getSender();
                        if (player != null) context.enqueueWork(() -> server.accept(packet, player));
                    } else if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                        Runnable callback = java.util.Objects.requireNonNull(
                                clientReceiver, "Client receiver not initialized").apply(packet);
                        context.enqueueWork(callback);
                    }
                    context.setPacketHandled(true);
                });
    }
    static void send(ServerPlayer player, NeoForgeBridgePayloads.Packet packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    static void sendToServer(NeoForgeBridgePayloads.Packet packet) { CHANNEL.sendToServer(packet); }
}
