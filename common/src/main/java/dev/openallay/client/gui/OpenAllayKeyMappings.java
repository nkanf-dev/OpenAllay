package dev.openallay.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Shared identity and default for loader-registered client key mappings. */
public final class OpenAllayKeyMappings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("openallay", "guide"));
    public static final KeyMapping OPEN_GUIDE = new KeyMapping(
            "key.openallay.open_guide", InputConstants.KEY_K, CATEGORY);

    // Native options loading keeps existing custom or explicitly unbound preferences.
    public static final KeyMapping TOGGLE_HUD = unbound("key.openallay.toggle_hud");
    public static final KeyMapping EDIT_HUD = unbound("key.openallay.edit_hud");
    public static final KeyMapping INTERACT_HUD = new KeyMapping(
            "key.openallay.interact_hud", InputConstants.KEY_F8, CATEGORY);
    public static final KeyMapping VOICE_PTT = unbound("key.openallay.voice_ptt");

    public static java.util.List<KeyMapping> all() {
        return java.util.List.of(OPEN_GUIDE, TOGGLE_HUD, EDIT_HUD, INTERACT_HUD, VOICE_PTT);
    }

    private static KeyMapping unbound(String name) {
        return new KeyMapping(name, GuideNativeInput.keyboardType(), InputConstants.UNKNOWN.getValue(), CATEGORY);
    }

    private OpenAllayKeyMappings() {}
}
