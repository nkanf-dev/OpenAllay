package dev.openallay.integration.jei;

import mezz.jei.api.runtime.IJeiRuntime;

/** JEI7 has only onRuntimeAvailable. Product shutdown retires its retained runtime. */
public abstract class MinecraftJeiPluginLifecycle extends MinecraftJeiPluginUid {
    protected MinecraftJeiPluginLifecycle() { OpenAllayJeiBridge.registerExtension(); }
    @Override public final void onRuntimeAvailable(IJeiRuntime runtime) { OpenAllayJeiBridge.runtimeAvailable(runtime); }
}
