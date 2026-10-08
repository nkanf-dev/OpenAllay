package dev.openallay.neoforge;

import dev.openallay.OpenAllayBootstrap;
import dev.openallay.neoforge.command.ForgeClientGuideCommands;

/** Separate real client Dist referent; normal server bootstrap never resolves client bodies. */
final class NeoForgeNativeClientBootstrap {
    private NeoForgeNativeClientBootstrap() {}
    static void initialize() {
        ForgeClientGuideCommands.install();
        NeoForgeNativeKeyRegistration.register();
        OpenAllayNeoForgeClient.initialize(OpenAllayBootstrap.initialize());
    }
}
