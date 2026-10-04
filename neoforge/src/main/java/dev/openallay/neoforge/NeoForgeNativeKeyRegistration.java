package dev.openallay.neoforge;

import dev.openallay.client.gui.OpenAllayKeyMappings;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Native event capabilities differ; key identities/defaults stay shared. */
final class NeoForgeNativeKeyRegistration {
    private NeoForgeNativeKeyRegistration() {}
    static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(dev.openallay.client.gui.GuideNativeKeyMappings.category());
        OpenAllayKeyMappings.all().forEach(event::register);
    }
}
