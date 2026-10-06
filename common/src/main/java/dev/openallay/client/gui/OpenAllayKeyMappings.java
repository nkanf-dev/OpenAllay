package dev.openallay.client.gui;


import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Shared identity and default for loader-registered client key mappings. */
public final class OpenAllayKeyMappings {
    public static final KeyMapping OPEN_GUIDE = GuideNativeKeyMappings.create("key.openallay.open_guide", dev.openallay.client.gui.GuideInputCodes.KEY_K);

    // Native options loading keeps existing custom or explicitly unbound preferences.
    public static final KeyMapping TOGGLE_HUD = unbound("key.openallay.toggle_hud");
    public static final KeyMapping EDIT_HUD = unbound("key.openallay.edit_hud");
    public static final KeyMapping INTERACT_HUD = GuideNativeKeyMappings.create("key.openallay.interact_hud", dev.openallay.client.gui.GuideInputCodes.KEY_F8);
    public static final KeyMapping VOICE_PTT = unbound("key.openallay.voice_ptt");

    public static java.util.List<KeyMapping> all() {
        return java.util.List.of(OPEN_GUIDE, TOGGLE_HUD, EDIT_HUD, INTERACT_HUD, VOICE_PTT);
    }

    private static KeyMapping unbound(String name) {
        return GuideNativeKeyMappings.unbound(name);
    }

    private OpenAllayKeyMappings() {}
}
