package dev.openallay.fabric.network;

import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.level.ServerPlayer;

/** Fabric 4.0.8 invokes typed play receivers on the owning server thread. */
final class FabricNativePayloadRegistration {
    private FabricNativePayloadRegistration() {}
    static void register(BiConsumer<FabricBridgePayloads.Packet, ServerPlayer> receiver) {
        PayloadTypeRegistry.playC2S().register(FabricBridgePayloads.Packet.TYPE, FabricBridgePayloads.Packet.CODEC);
        PayloadTypeRegistry.playS2C().register(FabricBridgePayloads.Packet.TYPE, FabricBridgePayloads.Packet.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FabricBridgePayloads.Packet.TYPE,
                (packet, context) -> receiver.accept(packet, context.player()));
    }
    static void sendInitial(PacketSender sender, FabricBridgePayloads.Packet packet) { sender.sendPacket(packet); }
    static boolean send(ServerPlayer player, FabricBridgePayloads.Packet packet) {
        if (!ServerPlayNetworking.canSend(player, FabricBridgePayloads.Packet.TYPE)) return false;
        ServerPlayNetworking.send(player, packet);
        return true;
    }
}
