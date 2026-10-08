package dev.openallay.neoforge.network;

import dev.openallay.OpenAllayRuntime;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Actual FriendlyByteBuf frame; exact current fields are kind then JSON. */
public final class NeoForgeBridgePayloads {
    private NeoForgeBridgePayloads() {}
    public static void register(OpenAllayRuntime runtime) {
        NeoForgeServerBridge server = new NeoForgeServerBridge(runtime);
        NeoForgeNativePayloadRegistration.register(server::receive);
        server.registerLifecycle();
    }
    public record Packet(String kind, String json) {
        public static final ResourceLocation ID = MinecraftResourceIds.fromNamespaceAndPath("openallay", "bridge");
        public Packet {
            if (kind == null || kind.isBlank() || json == null || json.isBlank()) {
                throw new IllegalArgumentException("Bridge packet kind and JSON are required");
            }
        }
        public static Packet read(FriendlyByteBuf buffer) { return new Packet(buffer.readUtf(), buffer.readUtf()); }
        public void write(FriendlyByteBuf buffer) { buffer.writeUtf(kind); buffer.writeUtf(json); }
    }
}
