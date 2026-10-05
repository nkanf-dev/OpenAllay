package dev.openallay.fabric.network;

import dev.openallay.bridge.client.ClientBridgeSession;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

/** Fabric packet/lifecycle binding. Request behavior is inherited from one engine session. */
public final class FabricClientBridge extends ClientBridgeSession {
    public FabricClientBridge() {
        super(new NativeHost() {
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
                return FabricNativeClientPayloads.canSend();
            }

            @Override
            public void send(String kind, String json) {
                FabricNativeClientPayloads.send(new FabricBridgePayloads.Packet(kind, json));
            }
        }, event -> Minecraft.getInstance().execute(event));
    }

    public void register() {
        FabricNativeClientPayloads.register((packet, current) ->
                inboundCallback(packet.kind(), packet.json(), current));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disconnected());
    }
}
