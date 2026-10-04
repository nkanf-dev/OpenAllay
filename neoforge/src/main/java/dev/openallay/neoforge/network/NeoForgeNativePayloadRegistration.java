package dev.openallay.neoforge.network;

import dev.openallay.OpenAllayConstants;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

/** Native registration for the family with a separate client payload event. */
final class NeoForgeNativePayloadRegistration {
    private NeoForgeNativePayloadRegistration() {}

    static void register(IEventBus modBus, IPayloadHandler<NeoForgeBridgePayloads.Packet> server) {
        // NeoForge requires a nonempty label, not an internal protocol version.
        modBus.addListener((RegisterPayloadHandlersEvent event) ->
                event.registrar(OpenAllayConstants.MOD_ID).optional().playBidirectional(
                        NeoForgeBridgePayloads.Packet.TYPE, NeoForgeBridgePayloads.Packet.CODEC, server));
    }
}
