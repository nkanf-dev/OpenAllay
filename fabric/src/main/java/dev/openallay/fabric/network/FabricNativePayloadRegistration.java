package dev.openallay.fabric.network;

import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.level.ServerPlayer;

/** Actual typed-payload family; callbacks are admitted on the owning server thread. */
final class FabricNativePayloadRegistration {
    private FabricNativePayloadRegistration() {}
    static void register(BiConsumer<FabricBridgePayloads.Packet, ServerPlayer> receiver) {
        PayloadTypeRegistry.serverboundPlay().register(FabricBridgePayloads.Packet.TYPE, FabricBridgePayloads.Packet.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FabricBridgePayloads.Packet.TYPE, FabricBridgePayloads.Packet.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FabricBridgePayloads.Packet.TYPE,
                (packet, context) -> context.server().execute(() -> receiver.accept(packet, context.player())));
    }
    static void sendInitial(PacketSender sender, FabricBridgePayloads.Packet packet) { sender.sendPacket(packet); }
    static boolean send(ServerPlayer player, FabricBridgePayloads.Packet packet) {
        if (!ServerPlayNetworking.canSend(player, FabricBridgePayloads.Packet.TYPE)) return false;
        ServerPlayNetworking.send(player, packet);
        return true;
    }
}
