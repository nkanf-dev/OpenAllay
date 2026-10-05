package dev.openallay.neoforge.network;

import dev.openallay.OpenAllayConstants;
import dev.openallay.neoforge.NeoForgeNativeModBus;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;

/** Actual 20.4 native ID/FriendlyByteBuf reader and network-thread context. */
final class NeoForgeNativePayloadRegistration {
    private static volatile Function<NeoForgeBridgePayloads.Packet, Runnable> clientReceiver;
    private NeoForgeNativePayloadRegistration() {}
    static void registerClient(Function<NeoForgeBridgePayloads.Packet, Runnable> receiver) {
        clientReceiver = java.util.Objects.requireNonNull(receiver, "receiver");
    }
    static void register(BiConsumer<NeoForgeBridgePayloads.Packet, ServerPlayer> server) {
        NeoForgeNativeModBus.get().addListener((RegisterPayloadHandlerEvent event) ->
                event.registrar(OpenAllayConstants.MOD_ID).optional().play(
                        NeoForgeBridgePayloads.Packet.ID, NeoForgeBridgePayloads.Packet::read,
                        handlers -> handlers.client((packet, context) -> {
                            Runnable callback = java.util.Objects.requireNonNull(
                                    clientReceiver, "Client receiver not initialized").apply(packet);
                            context.workHandler().execute(callback);
                        }).server((packet, context) -> {
                            if (context.player().orElse(null) instanceof ServerPlayer player) {
                                context.workHandler().execute(() -> server.accept(packet, player));
                            }
                        })));
    }
    static void send(ServerPlayer player, NeoForgeBridgePayloads.Packet packet) {
        PacketDistributor.PLAYER.with(player).send(packet);
    }
}
