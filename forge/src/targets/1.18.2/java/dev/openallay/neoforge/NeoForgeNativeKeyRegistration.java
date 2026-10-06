package dev.openallay.neoforge;

import dev.openallay.client.gui.OpenAllayKeyMappings;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Native setup capabilities differ; key identities/defaults stay shared. */
final class NeoForgeNativeKeyRegistration {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private NeoForgeNativeKeyRegistration() {}
    static void register(FMLClientSetupEvent event) {
        if (!REGISTERED.compareAndSet(false, true)) return;
        event.enqueueWork(() -> OpenAllayKeyMappings.all().forEach(ClientRegistry::registerKeyBinding));
    }
}
