package dev.openallay.neoforge.network;

import dev.openallay.neoforge.NeoForgeNativeModBus;
import java.util.function.Function;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

/** Actual modern client registration; receiving thread is the loader's main-thread default. */
final class NeoForgeNativeClientPayloads {
    private NeoForgeNativeClientPayloads() {}
    static void register(Function<NeoForgeBridgePayloads.Packet, Runnable> receiver) {
        NeoForgeNativeModBus.get().addListener((RegisterClientPayloadHandlersEvent event) ->
                event.register(NeoForgeBridgePayloads.Packet.TYPE, (packet, context) -> receiver.apply(packet).run()));
    }
    static void onDisconnected(Runnable disconnected) {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> disconnected.run());
    }
    static void send(NeoForgeBridgePayloads.Packet packet) { ClientPacketDistributor.sendToServer(packet); }
}
