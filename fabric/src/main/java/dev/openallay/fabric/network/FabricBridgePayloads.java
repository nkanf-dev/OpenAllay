package dev.openallay.fabric.network;

import dev.openallay.platform.minecraft.MinecraftResourceIds;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class FabricBridgePayloads {
    private FabricBridgePayloads() {}

    public record Packet(String kind, String json) implements CustomPacketPayload {
        public static final Type<Packet> TYPE = new Type<>(
                MinecraftResourceIds.fromNamespaceAndPath("openallay", "bridge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Packet> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                Packet::kind,
                ByteBufCodecs.STRING_UTF8,
                Packet::json,
                Packet::new);

        public Packet {
            if (kind == null || kind.isBlank() || json == null || json.isBlank()) {
                throw new IllegalArgumentException("Bridge packet kind and JSON are required");
            }
        }

        @Override
        public Type<Packet> type() {
            return TYPE;
        }
    }
}
