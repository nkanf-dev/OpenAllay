package dev.openallay.neoforge.network;

import dev.openallay.bridge.client.ClientBridgeSession;
import java.util.Optional;
import net.minecraft.client.Minecraft;

/** Native-only FML14 host facts; all request/session behavior stays in ClientBridgeSession. */
public final class NeoForgeClientBridge extends ClientBridgeSession {
    public NeoForgeClientBridge() {
        super(new ClientBridgeSession.NativeHost() {
            @Override public Optional<Connection> captureConnection() {
                Minecraft client = Minecraft.getMinecraft();
                net.minecraft.client.network.NetHandlerPlayClient original = client.getConnection();
                if (client.player == null || original == null) return Optional.empty();
                return Optional.of(new Connection(client.player.getUniqueID(),
                        () -> Minecraft.getMinecraft().getConnection() == original));
            }
            @Override public boolean canSend() { return true; }
            @Override public void send(String kind, String json) {
                NeoForgeNativeClientPayloads.send(new NeoForgeBridgePayloads.Packet(kind, json));
            }
        }, event -> Minecraft.getMinecraft().addScheduledTask(event));
    }
    public void register() {
        NeoForgeNativeClientPayloads.register(packet -> {
            net.minecraft.client.network.NetHandlerPlayClient original = Minecraft.getMinecraft().getConnection();
            return inboundCallback(packet.kind(), packet.json(),
                    () -> Minecraft.getMinecraft().getConnection() == original);
        });
        NeoForgeNativeClientPayloads.onDisconnected(this::disconnected);
    }
}
