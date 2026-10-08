package dev.openallay.client.gui;




/** Shared identity and default for loader-registered client key mappings. */
public final class OpenAllayKeyMappings {
    public static final net.minecraft.client.KeyMapping OPEN_GUIDE = GuideNativeKeyMappings.create("key.openallay.open_guide", dev.openallay.client.gui.GuideInputCodes.KEY_K);

    // Native options loading keeps existing custom or explicitly unbound preferences.
    public static final net.minecraft.client.KeyMapping TOGGLE_HUD = unbound("key.openallay.toggle_hud");
    public static final net.minecraft.client.KeyMapping EDIT_HUD = unbound("key.openallay.edit_hud");
    public static final net.minecraft.client.KeyMapping INTERACT_HUD = GuideNativeKeyMappings.create("key.openallay.interact_hud", dev.openallay.client.gui.GuideInputCodes.KEY_F8);
    public static final net.minecraft.client.KeyMapping VOICE_PTT = unbound("key.openallay.voice_ptt");

    public static java.util.List<net.minecraft.client.KeyMapping> all() {
        return dev.openallay.util.Java8Collections.listOf(OPEN_GUIDE, TOGGLE_HUD, EDIT_HUD, INTERACT_HUD, VOICE_PTT);
    }

    private static net.minecraft.client.KeyMapping unbound(String name) {
        return GuideNativeKeyMappings.unbound(name);
    }

    private OpenAllayKeyMappings() {}
}
