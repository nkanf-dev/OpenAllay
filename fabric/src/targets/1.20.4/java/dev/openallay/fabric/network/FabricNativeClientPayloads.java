package dev.openallay.fabric.network;

import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

/** Raw callback keeps immutable actor/connection admission in the shared engine session. */
final class FabricNativeClientPayloads {
    private FabricNativeClientPayloads() {}
    static void register(BiFunction<FabricBridgePayloads.Packet, BooleanSupplier, Runnable> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(FabricBridgePayloads.CHANNEL,
                (client, handler, buffer, sender) -> {
                    FabricBridgePayloads.Packet packet = FabricBridgePayloads.Packet.read(buffer);
                    Runnable callback = receiver.apply(packet, () -> client.getConnection() == handler);
                    client.execute(callback);
                });
    }
    static boolean canSend() { return ClientPlayNetworking.canSend(FabricBridgePayloads.CHANNEL); }
    static void send(FabricBridgePayloads.Packet packet) {
        var buffer = PacketByteBufs.create();
        packet.write(buffer);
        ClientPlayNetworking.send(FabricBridgePayloads.CHANNEL, buffer);
    }
}
