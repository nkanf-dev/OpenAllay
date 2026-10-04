package dev.openallay.fabric.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Register the same typed packet on the actual loader API family. */
final class FabricNativePayloadRegistration {
    private FabricNativePayloadRegistration() {}
    static void register() {
        PayloadTypeRegistry.playC2S().register(FabricBridgePayloads.Packet.TYPE, FabricBridgePayloads.Packet.CODEC);
        PayloadTypeRegistry.playS2C().register(FabricBridgePayloads.Packet.TYPE, FabricBridgePayloads.Packet.CODEC);
    }
}
