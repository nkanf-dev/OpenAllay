package dev.openallay.neoforge;

import dev.openallay.OpenAllayBootstrap;
import dev.openallay.OpenAllayConstants;
import dev.openallay.OpenAllayRuntime;
import dev.openallay.extension.OpenAllayExtension;
import dev.openallay.neoforge.network.NeoForgeBridgePayloads;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Actual early NeoForge Forge-namespaced entrypoint. */
@Mod(OpenAllayConstants.MOD_ID)
public final class OpenAllayNeoForge {
    public static void registerExtension(OpenAllayExtension extension) {
        OpenAllayBootstrap.registerExtension(extension);
    }
    public OpenAllayNeoForge() {
        NeoForgeNativeModBus.install(FMLJavaModLoadingContext.get().getModEventBus());
        OpenAllayRuntime runtime = OpenAllayBootstrap.initialize();
        NeoForgeBridgePayloads.register(runtime);
        NeoForgeDevelopmentCommands.register(runtime);
        NeoForgeNativeModBus.get().addListener((net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) ->
                event.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.safeRunWhenOn(
                        net.minecraftforge.api.distmarker.Dist.CLIENT, () -> NeoForgeNativeClientBootstrap::initialize)));
    }
}
