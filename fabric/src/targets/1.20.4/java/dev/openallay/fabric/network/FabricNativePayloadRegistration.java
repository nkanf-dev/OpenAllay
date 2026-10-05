package dev.openallay.fabric.network;

import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.server.level.ServerPlayer;

/** Actual raw-channel callback family; read buffer before queueing immutable fields. */
final class FabricNativePayloadRegistration {
    private FabricNativePayloadRegistration() {}
    static void register(BiConsumer<FabricBridgePayloads.Packet, ServerPlayer> receiver) {
        ServerPlayNetworking.registerGlobalReceiver(FabricBridgePayloads.CHANNEL,
                (server, player, handler, buffer, sender) -> {
                    FabricBridgePayloads.Packet packet = FabricBridgePayloads.Packet.read(buffer);
                    server.execute(() -> receiver.accept(packet, player));
                });
    }
    static void sendInitial(PacketSender sender, FabricBridgePayloads.Packet packet) {
        var buffer = PacketByteBufs.create();
        packet.write(buffer);
        sender.sendPacket(FabricBridgePayloads.CHANNEL, buffer);
    }
    static boolean send(ServerPlayer player, FabricBridgePayloads.Packet packet) {
        if (!ServerPlayNetworking.canSend(player, FabricBridgePayloads.CHANNEL)) return false;
        var buffer = PacketByteBufs.create();
        packet.write(buffer);
        ServerPlayNetworking.send(player, FabricBridgePayloads.CHANNEL, buffer);
        return true;
    }
}
