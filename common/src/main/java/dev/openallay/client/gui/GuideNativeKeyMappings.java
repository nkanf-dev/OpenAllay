package dev.openallay.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Actual native category type is owned at the registration boundary. */
public final class GuideNativeKeyMappings {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(dev.openallay.platform.minecraft.MinecraftResourceIds.fromNamespaceAndPath("openallay", "guide"));
    private GuideNativeKeyMappings() {}
    public static KeyMapping.Category category() { return CATEGORY; }
    public static KeyMapping create(String name, int key) { return new KeyMapping(name, key, CATEGORY); }
    public static KeyMapping unbound(String name) {
        return new KeyMapping(name, GuideNativeInput.keyboardType(), InputConstants.UNKNOWN.getValue(), CATEGORY);
    }
}
