package dev.openallay.neoforge.network;

import dev.openallay.OpenAllayRuntime;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** Real FML14 message frame: kind then JSON; shared bridge protocol remains unchanged. */
public final class NeoForgeBridgePayloads {
    private NeoForgeBridgePayloads() {}
    public static void register(OpenAllayRuntime runtime) {
        NeoForgeServerBridge server = new NeoForgeServerBridge(runtime);
        NeoForgeNativePayloadRegistration.register(server::receive);
        server.registerLifecycle();
    }
    public static final class Packet implements IMessage {
        private String kind;
        private String json;
        public Packet() {}
        public Packet(String kind, String json) { install(kind, json); }
        private void install(String kind, String json) {
            if (kind == null || kind.isBlank() || json == null || json.isBlank()) {
                throw new IllegalArgumentException("Bridge packet kind and JSON are required");
            }
            this.kind = kind;
            this.json = json;
        }
        public String kind() { return kind; }
        public String json() { return json; }
        @Override public void fromBytes(ByteBuf bytes) {
            PacketBuffer buffer = new PacketBuffer(bytes);
            install(buffer.readString(32767), buffer.readString(32767));
        }
        @Override public void toBytes(ByteBuf bytes) {
            PacketBuffer buffer = new PacketBuffer(bytes);
            buffer.writeString(kind);
            buffer.writeString(json);
        }
    }
}
