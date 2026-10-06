package dev.openallay.neoforge;

import dev.openallay.OpenAllayRuntime;

/** Real physical-client referent; native keys and local commands register in shared startup order. */
final class NeoForgeNativeClientBootstrap {
    private NeoForgeNativeClientBootstrap() {}
    static void initialize(OpenAllayRuntime runtime) {
        OpenAllayNeoForgeClient.initialize(runtime);
    }
}
