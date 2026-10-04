package dev.openallay.neoforge.network;

import dev.openallay.OpenAllayConstants;
import java.util.Objects;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

/** Native registration before NeoForge introduced the client payload event. */
final class NeoForgeNativePayloadRegistration {
    // Set during client initialization, before the mod registration event fires.
    // The shared registration class does not reference any client-only game type.
    private static volatile IPayloadHandler<NeoForgeBridgePayloads.Packet> clientReceiver;

    private NeoForgeNativePayloadRegistration() {}

    static void registerClient(IPayloadHandler<NeoForgeBridgePayloads.Packet> receiver) {
        clientReceiver = Objects.requireNonNull(receiver, "receiver");
    }

    static void register(IEventBus modBus, IPayloadHandler<NeoForgeBridgePayloads.Packet> server) {
        // NeoForge requires a nonempty label, not an internal protocol version.
        modBus.addListener((RegisterPayloadHandlersEvent event) ->
                event.registrar(OpenAllayConstants.MOD_ID).optional().playBidirectional(
                        NeoForgeBridgePayloads.Packet.TYPE, NeoForgeBridgePayloads.Packet.CODEC,
                        new DirectionalPayloadHandler<>((packet, context) -> {
                            IPayloadHandler<NeoForgeBridgePayloads.Packet> receiver = clientReceiver;
                            if (receiver == null) {
                                throw new IllegalStateException("Client bridge receiver is not initialized");
                            }
                            receiver.handle(packet, context);
                        }, server)));
    }
}
