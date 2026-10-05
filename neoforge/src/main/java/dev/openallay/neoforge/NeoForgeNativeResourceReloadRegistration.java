package dev.openallay.neoforge;

import java.util.function.Function;

/** Unchanged native families keep the coordinator's existing registration path. */
final class NeoForgeNativeResourceReloadRegistration {
    private NeoForgeNativeResourceReloadRegistration() {}

    static Function<Runnable, Runnable> install() {
        return null;
    }
}
