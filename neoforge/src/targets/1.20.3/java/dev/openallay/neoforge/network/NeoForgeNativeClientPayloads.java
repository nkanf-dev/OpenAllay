package dev.openallay.neoforge.network;

import java.util.function.Function;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Actual modern client registration; receiving thread is the loader's main-thread default. */
final class NeoForgeNativeClientPayloads {
    private NeoForgeNativeClientPayloads() {}
    static void register(Function<NeoForgeBridgePayloads.Packet, Runnable> receiver) {
        NeoForgeNativePayloadRegistration.registerClient(receiver);
    }
    static void onDisconnected(Runnable disconnected) {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> disconnected.run());
    }
    static void send(NeoForgeBridgePayloads.Packet packet) { NeoForgeNativePayloadRegistration.sendToServer(packet); }
}
