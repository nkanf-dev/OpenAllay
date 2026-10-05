package dev.openallay.neoforge;

import dev.openallay.OpenAllayBootstrap;
import dev.openallay.OpenAllayConstants;
import dev.openallay.OpenAllayRuntime;
import dev.openallay.extension.OpenAllayExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import dev.openallay.neoforge.network.NeoForgeBridgePayloads;

@Mod(OpenAllayConstants.MOD_ID)
public final class OpenAllayNeoForge {
    /** Called by ordinary NeoForge Extension entrypoints before or during loader initialization. */
    public static void registerExtension(OpenAllayExtension extension) {
        OpenAllayBootstrap.registerExtension(extension);
    }

    public OpenAllayNeoForge(IEventBus modBus) {
        NeoForgeNativeModBus.install(modBus);
        OpenAllayRuntime runtime = OpenAllayBootstrap.initialize();
        NeoForgeBridgePayloads.register(runtime);
        NeoForgeDevelopmentCommands.register(runtime);
        if (NeoForgeNativeEnvironment.isClient()) {
            OpenAllayNeoForgeClient.initialize(runtime);
        }
    }
}
