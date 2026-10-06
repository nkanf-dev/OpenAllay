package dev.openallay.neoforge;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;

/** Native resources notify the existing shared coordinator; retirement detaches the callback. */
final class NeoForgeNativeResourceReloadRegistration {
    private NeoForgeNativeResourceReloadRegistration() {}
    static Function<Runnable, Runnable> install() {
        IReloadableResourceManager resources = (IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager();
        return callback -> {
            AtomicReference<Runnable> active = new AtomicReference<>(callback);
            resources.registerReloadListener(manager -> {
                Runnable current = active.get();
                if (current != null) current.run();
            });
            return () -> active.set(null);
        };
    }
}
