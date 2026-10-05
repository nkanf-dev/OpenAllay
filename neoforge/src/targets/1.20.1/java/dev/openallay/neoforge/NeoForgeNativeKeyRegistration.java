package dev.openallay.neoforge;

import dev.openallay.client.gui.OpenAllayKeyMappings;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

/** Native event capabilities differ; key identities/defaults stay shared. */
final class NeoForgeNativeKeyRegistration {
    private NeoForgeNativeKeyRegistration() {}
    static void register(RegisterKeyMappingsEvent event) {
        OpenAllayKeyMappings.all().forEach(event::register);
    }
}
