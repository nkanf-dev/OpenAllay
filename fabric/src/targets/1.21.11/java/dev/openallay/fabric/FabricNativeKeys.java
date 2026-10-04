package dev.openallay.fabric;

import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/** Fabric key-registration naming is a native source-family fact. */
final class FabricNativeKeys {
    private FabricNativeKeys() {}
    static void register(KeyMapping key) { KeyBindingHelper.registerKeyBinding(key); }
}
