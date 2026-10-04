package dev.openallay.fabric;

import dev.openallay.integration.jei.MinecraftJeiPluginUid;

import dev.openallay.integration.jei.OpenAllayJeiBridge;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;

/** Fabric-root JEI discovery adapter for the common integration. */
@JeiPlugin
public final class OpenAllayFabricJeiPlugin extends MinecraftJeiPluginUid {
    public OpenAllayFabricJeiPlugin() {
        OpenAllayJeiBridge.registerExtension();
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        OpenAllayJeiBridge.runtimeAvailable(runtime);
    }

    @Override
    public void onRuntimeUnavailable() {
        OpenAllayJeiBridge.runtimeUnavailable();
    }
}
