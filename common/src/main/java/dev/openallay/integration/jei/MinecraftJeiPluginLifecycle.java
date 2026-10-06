package dev.openallay.integration.jei;

import mezz.jei.api.runtime.IJeiRuntime;

/** Actual modern discovery lifecycle; loaded exclusively by optional JEI plugin discovery. */
public abstract class MinecraftJeiPluginLifecycle extends MinecraftJeiPluginUid {
    protected MinecraftJeiPluginLifecycle() { OpenAllayJeiBridge.registerExtension(); }
    @Override public final void onRuntimeAvailable(IJeiRuntime runtime) { OpenAllayJeiBridge.runtimeAvailable(runtime); }
    @Override public final void onRuntimeUnavailable() { OpenAllayJeiBridge.runtimeUnavailable(); }
}
