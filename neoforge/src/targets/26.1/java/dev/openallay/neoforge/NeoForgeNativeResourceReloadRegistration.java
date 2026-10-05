package dev.openallay.neoforge;

import dev.openallay.platform.minecraft.MinecraftResourceIds;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;

/** 26.1 native ownership: register before sorting freezes the listener list. */
final class NeoForgeNativeResourceReloadRegistration {
    private NeoForgeNativeResourceReloadRegistration() {}

    static Function<Runnable, Runnable> install() {
        AtomicReference<Runnable> callback = new AtomicReference<>();
        NeoForgeNativeModBus.get().addListener((AddClientReloadListenersEvent event) ->
                event.addListener(MinecraftResourceIds.fromNamespaceAndPath("openallay", "guide_hud_layout"),
                        (ResourceManagerReloadListener) resources -> {
                            Runnable current = callback.get();
                            if (current != null) current.run();
                        }));
        return invalidateLayout -> {
            Objects.requireNonNull(invalidateLayout, "invalidateLayout");
            if (!callback.compareAndSet(null, invalidateLayout)) {
                throw new IllegalStateException("Resource reload callback is already bound");
            }
            return () -> callback.compareAndSet(invalidateLayout, null);
        };
    }
}
