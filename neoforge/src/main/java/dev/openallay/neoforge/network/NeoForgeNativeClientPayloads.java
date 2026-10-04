package dev.openallay.neoforge.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

/** Client-only native registration and sending; bridge behavior stays shared. */
final class NeoForgeNativeClientPayloads {
    private NeoForgeNativeClientPayloads() {}

    static void register(IEventBus modBus, IPayloadHandler<NeoForgeBridgePayloads.Packet> receiver) {
        modBus.addListener((RegisterClientPayloadHandlersEvent event) ->
                event.register(NeoForgeBridgePayloads.Packet.TYPE, receiver));
    }

    static void send(NeoForgeBridgePayloads.Packet packet) {
        ClientPacketDistributor.sendToServer(packet);
    }
}
