package dev.openallay.neoforge;

import dev.openallay.client.gui.OpenAllayKeyMappings;
import net.minecraftforge.fml.client.registry.ClientRegistry;

/** Native key registration keeps all shared key identities and defaults. */
final class NeoForgeNativeKeyRegistration {
    private NeoForgeNativeKeyRegistration() {}
    static void register() { OpenAllayKeyMappings.all().forEach(ClientRegistry::registerKeyBinding); }
}
