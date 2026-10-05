package dev.openallay.neoforge.network;

import dev.openallay.OpenAllayConstants;
import dev.openallay.neoforge.NeoForgeNativeModBus;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Actual modern server registration; context is consumed here, not by the engine. */
final class NeoForgeNativePayloadRegistration {
    private NeoForgeNativePayloadRegistration() {}
    static void register(BiConsumer<NeoForgeBridgePayloads.Packet, ServerPlayer> server) {
        NeoForgeNativeModBus.get().addListener((RegisterPayloadHandlersEvent event) ->
                event.registrar(OpenAllayConstants.MOD_ID).optional().playBidirectional(
                        NeoForgeBridgePayloads.Packet.TYPE, NeoForgeBridgePayloads.Packet.CODEC,
                        (packet, context) -> {
                            if (context.player() instanceof ServerPlayer player) server.accept(packet, player);
                        }));
    }
    static void send(ServerPlayer player, NeoForgeBridgePayloads.Packet packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
