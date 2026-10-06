package dev.openallay.client.gui;

import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Actual 1.12.2 string category and LWJGL 2 unbound value. */
public final class GuideNativeKeyMappings {
    private GuideNativeKeyMappings() {}
    public static boolean consume(net.minecraft.client.settings.KeyBinding mapping) { return mapping.isPressed(); }
    public static boolean down(net.minecraft.client.settings.KeyBinding mapping) { return mapping.isKeyDown(); }
    public static boolean unbound(net.minecraft.client.settings.KeyBinding mapping) { return mapping.getKeyCode() == org.lwjgl.input.Keyboard.KEY_NONE; }
    public static net.minecraft.util.text.ITextComponent display(net.minecraft.client.settings.KeyBinding mapping) { return new net.minecraft.util.text.TextComponentString(mapping.getDisplayName()); }

    public static String category() { return "key.categories.openallay"; }
    public static KeyBinding create(String name, int key) { return new KeyBinding(name, key, category()); }
    public static KeyBinding unbound(String name) { return new KeyBinding(name, Keyboard.KEY_NONE, category()); }
}
