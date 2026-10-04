package dev.openallay.neoforge;

import dev.openallay.integration.jei.MinecraftJeiPluginUid;

import dev.openallay.integration.jei.OpenAllayJeiBridge;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;

/** NeoForge-root JEI discovery adapter for the common integration. */
@JeiPlugin
public final class OpenAllayNeoForgeJeiPlugin extends MinecraftJeiPluginUid {
    public OpenAllayNeoForgeJeiPlugin() {
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
