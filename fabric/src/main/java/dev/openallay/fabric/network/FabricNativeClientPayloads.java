package dev.openallay.fabric.network;

import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Capture native connection identity and engine scope before queueing the client callback. */
final class FabricNativeClientPayloads {
    private FabricNativeClientPayloads() {}
    static void register(BiFunction<FabricBridgePayloads.Packet, BooleanSupplier, Runnable> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(FabricBridgePayloads.Packet.TYPE, (packet, context) -> {
            var connection = context.client().getConnection();
            Runnable callback = receiver.apply(packet, () -> context.client().getConnection() == connection);
            context.client().execute(callback);
        });
    }
    static boolean canSend() { return ClientPlayNetworking.canSend(FabricBridgePayloads.Packet.TYPE); }
    static void send(FabricBridgePayloads.Packet packet) { ClientPlayNetworking.send(packet); }
}
