package dev.openallay.neoforge.network;

import java.util.function.Function;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Actual 20.4 client transport; registration queues exactly once in the shared native registrar. */
final class NeoForgeNativeClientPayloads {
    private NeoForgeNativeClientPayloads() {}
    static void register(Function<NeoForgeBridgePayloads.Packet, Runnable> receiver) {
        NeoForgeNativePayloadRegistration.registerClient(receiver);
    }
    static void onDisconnected(Runnable disconnected) {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> disconnected.run());
    }
    static void send(NeoForgeBridgePayloads.Packet packet) { PacketDistributor.SERVER.noArg().send(packet); }
}
