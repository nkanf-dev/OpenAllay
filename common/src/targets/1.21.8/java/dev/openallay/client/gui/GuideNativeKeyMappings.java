package dev.openallay.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** Actual native category type is owned at the registration boundary. */
public final class GuideNativeKeyMappings {
    private static final String CATEGORY = "key.category.openallay.guide";
    private GuideNativeKeyMappings() {}
    public static boolean consume(net.minecraft.client.KeyMapping mapping) { return mapping.consumeClick(); }
    public static boolean down(net.minecraft.client.KeyMapping mapping) { return mapping.isDown(); }
    public static boolean unbound(net.minecraft.client.KeyMapping mapping) { return mapping.isUnbound(); }
    public static net.minecraft.network.chat.Component display(net.minecraft.client.KeyMapping mapping) { return mapping.getTranslatedKeyMessage(); }

    public static String category() { return CATEGORY; }
    public static KeyMapping create(String name, int key) { return new KeyMapping(name, key, CATEGORY); }
    public static KeyMapping unbound(String name) {
        return new KeyMapping(name, GuideNativeInput.keyboardType(), InputConstants.UNKNOWN.getValue(), CATEGORY);
    }
}
