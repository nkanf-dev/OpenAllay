package dev.openallay.neoforge.network;

import dev.openallay.bridge.client.ClientBridgeSession;
import java.util.Optional;
import net.minecraft.client.Minecraft;

/** NeoForge packet/lifecycle binding. Request behavior is inherited from one engine session. */
public final class NeoForgeClientBridge extends ClientBridgeSession {
    public NeoForgeClientBridge() {
        super(new ClientBridgeSession.NativeHost() {
            @Override
            public Optional<Connection> captureConnection() {
                Minecraft client = Minecraft.getInstance();
                var connection = client.getConnection();
                if (client.player == null || connection == null) return Optional.empty();
                return Optional.of(new Connection(client.player.getUUID(),
                        () -> Minecraft.getInstance().getConnection() == connection));
            }

            @Override
            public boolean canSend() {
                // Existing NeoForge sends are unguarded; do not add a Fabric channel policy here.
                return true;
            }

            @Override
            public void send(String kind, String json) {
                NeoForgeNativeClientPayloads.send(new NeoForgeBridgePayloads.Packet(kind, json));
            }
        }, event -> Minecraft.getInstance().execute(event));
    }

    public void register() {
        NeoForgeNativeClientPayloads.register(packet -> {
            var connection = Minecraft.getInstance().getConnection();
            return inboundCallback(packet.kind(), packet.json(),
                    () -> Minecraft.getInstance().getConnection() == connection);
        });
        NeoForgeNativeClientPayloads.onDisconnected(this::disconnected);
    }
}
