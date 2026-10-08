package dev.openallay.neoforge;

import dev.openallay.client.gui.OpenAllayKeyMappings;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraftforge.fml.client.registry.ClientRegistry;

/** Called only from real FMLClientSetupEvent.enqueueWork on the physical client. */
final class NeoForgeNativeKeyRegistration {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private NeoForgeNativeKeyRegistration() {}
    static void register() {
        if (REGISTERED.compareAndSet(false, true)) OpenAllayKeyMappings.all().forEach(ClientRegistry::registerKeyBinding);
    }
}
