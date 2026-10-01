package dev.openallay.neoforge.network;

import dev.openallay.OpenAllayConstants;
import dev.openallay.OpenAllayRuntime;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NeoForgeBridgePayloads {
    private NeoForgeBridgePayloads() {}

    public static void register(IEventBus modBus, OpenAllayRuntime runtime) {
        NeoForgeServerBridge server = new NeoForgeServerBridge(runtime);
        // NeoForge requires a nonempty registration label; this is not an internal format version.
        modBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar(OpenAllayConstants.MOD_ID)
                .optional()
                .playBidirectional(Packet.TYPE, Packet.CODEC, server::receive));
        server.registerLifecycle();
    }

    public record Packet(String kind, String json) implements CustomPacketPayload {
        public static final Type<Packet> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("openallay", "bridge"));
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
