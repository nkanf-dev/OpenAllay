package dev.openallay.fabric.network;

import dev.openallay.platform.minecraft.MinecraftResourceIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Raw play-channel boundary before typed payload registration. Wire fields remain kind then JSON. */
public final class FabricBridgePayloads {
    public static final ResourceLocation CHANNEL = MinecraftResourceIds.fromNamespaceAndPath("openallay", "bridge");
    private FabricBridgePayloads() {}
    // Classic channels are registered with their actual client/server receivers, not a type registry.
    public record Packet(String kind, String json) {
        public Packet {
            if (kind == null || kind.isBlank() || json == null || json.isBlank()) {
                throw new IllegalArgumentException("Bridge packet kind and JSON are required");
            }
        }
        static Packet read(FriendlyByteBuf buffer) { return new Packet(buffer.readUtf(), buffer.readUtf()); }
        void write(FriendlyByteBuf buffer) { buffer.writeUtf(kind); buffer.writeUtf(json); }
    }
}
