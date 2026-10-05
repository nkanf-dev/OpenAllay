package dev.openallay.fabric;

import dev.openallay.OpenAllayBootstrap;
import dev.openallay.OpenAllayRuntime;
import dev.openallay.extension.OpenAllayExtension;
import net.fabricmc.api.ModInitializer;
import dev.openallay.fabric.network.FabricServerBridge;

public final class OpenAllayFabric implements ModInitializer {
    /** Called by ordinary Fabric Extension entrypoints before or during loader initialization. */
    public static void registerExtension(OpenAllayExtension extension) {
        OpenAllayBootstrap.registerExtension(extension);
    }

    @Override
    public void onInitialize() {
        OpenAllayRuntime runtime = OpenAllayBootstrap.initialize();
        FabricServerBridge.register(runtime);
        FabricDevelopmentCommands.register(runtime);
    }
}
