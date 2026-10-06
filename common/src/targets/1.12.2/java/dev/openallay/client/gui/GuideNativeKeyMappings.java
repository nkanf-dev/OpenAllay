package dev.openallay.client.gui;

import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Actual 1.12.2 string category and LWJGL 2 unbound value. */
public final class GuideNativeKeyMappings {
    private GuideNativeKeyMappings() {}
    public static String category() { return "key.categories.openallay"; }
    public static KeyBinding create(String name, int key) { return new KeyBinding(name, key, category()); }
    public static KeyBinding unbound(String name) { return new KeyBinding(name, Keyboard.KEY_NONE, category()); }
}
