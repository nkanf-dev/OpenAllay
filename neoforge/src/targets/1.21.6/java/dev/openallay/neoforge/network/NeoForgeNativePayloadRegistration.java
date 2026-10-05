package dev.openallay.neoforge.network;

import dev.openallay.OpenAllayConstants;
import dev.openallay.neoforge.NeoForgeNativeModBus;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;

/** Native registration before the separate client registration event. */
final class NeoForgeNativePayloadRegistration {
    private static volatile Function<NeoForgeBridgePayloads.Packet, Runnable> clientReceiver;
    private NeoForgeNativePayloadRegistration() {}
    static void registerClient(Function<NeoForgeBridgePayloads.Packet, Runnable> receiver) {
        clientReceiver = java.util.Objects.requireNonNull(receiver, "receiver");
    }
    static Runnable clientCallback(NeoForgeBridgePayloads.Packet packet) {
        return java.util.Objects.requireNonNull(clientReceiver, "Client receiver not initialized").apply(packet);
    }
    static void register(BiConsumer<NeoForgeBridgePayloads.Packet, ServerPlayer> server) {
        NeoForgeNativeModBus.get().addListener((RegisterPayloadHandlersEvent event) ->
                event.registrar(OpenAllayConstants.MOD_ID).optional().playBidirectional(
                        NeoForgeBridgePayloads.Packet.TYPE, NeoForgeBridgePayloads.Packet.CODEC,
                        new DirectionalPayloadHandler<>((packet, context) -> clientCallback(packet).run(),
                                (packet, context) -> {
                                    if (context.player() instanceof ServerPlayer player) server.accept(packet, player);
                                })));
    }
    static void send(ServerPlayer player, NeoForgeBridgePayloads.Packet packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
