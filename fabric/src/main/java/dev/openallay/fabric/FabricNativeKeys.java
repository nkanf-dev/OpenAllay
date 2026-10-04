package dev.openallay.fabric;

import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/** Fabric key-registration naming is a native source-family fact. */
final class FabricNativeKeys {
    private FabricNativeKeys() {}
    static void register(KeyMapping key) { KeyMappingHelper.registerKeyMapping(key); }
}
