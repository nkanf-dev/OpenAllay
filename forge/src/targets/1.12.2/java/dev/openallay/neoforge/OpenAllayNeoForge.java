package dev.openallay.neoforge;

import dev.openallay.OpenAllayBootstrap;
import dev.openallay.OpenAllayConstants;
import dev.openallay.OpenAllayRuntime;
import dev.openallay.extension.OpenAllayExtension;
import dev.openallay.neoforge.network.NeoForgeBridgePayloads;
import dev.openallay.neoforge.network.NeoForgeNativeServerLifecycle;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;

/** FML14 lifecycle entrypoint; feature startup remains in the canonical bootstrap. */
@Mod(modid = OpenAllayConstants.MOD_ID, name = "OpenAllay", useMetadata = true,
        acceptedMinecraftVersions = "[1.12.2]", acceptableRemoteVersions = "*")
public final class OpenAllayNeoForge {
    public static void registerExtension(OpenAllayExtension extension) {
        OpenAllayBootstrap.registerExtension(extension);
    }
    @Mod.EventHandler
    public void preInitialize(FMLPreInitializationEvent event) {
        NeoForgeNativeLoaderFacts.install(event.getModConfigurationDirectory().toPath());
        NeoForgeNativeModBus.install(MinecraftForge.EVENT_BUS);
        OpenAllayRuntime runtime = OpenAllayBootstrap.initialize();
        NeoForgeBridgePayloads.register(runtime);
        NeoForgeDevelopmentCommands.register(runtime);
        if (NeoForgeNativeEnvironment.isClient()) NeoForgeNativeClientBootstrap.initialize(runtime);
    }
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        NeoForgeNativeCommandRegistration.serverStarting(event);
    }
    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        NeoForgeNativeServerLifecycle.started();
    }
    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        NeoForgeNativeServerLifecycle.stopped();
    }
}
