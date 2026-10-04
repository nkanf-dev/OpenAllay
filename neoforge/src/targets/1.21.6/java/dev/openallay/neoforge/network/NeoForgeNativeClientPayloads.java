package dev.openallay.neoforge.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

/** Legacy native sending and directional registration; no bridge behavior fork. */
final class NeoForgeNativeClientPayloads {
    private NeoForgeNativeClientPayloads() {}

    static void register(IEventBus modBus, IPayloadHandler<NeoForgeBridgePayloads.Packet> receiver) {
        NeoForgeNativePayloadRegistration.registerClient(receiver);
    }

    static void send(NeoForgeBridgePayloads.Packet packet) {
        PacketDistributor.sendToServer(packet);
    }
}
