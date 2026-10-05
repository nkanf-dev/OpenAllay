package dev.openallay.client.gui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Hint-only mutation on the actual native editor instance. */
public final class GuideNativeTextHints {
    private GuideNativeTextHints() {}
    public static void setHint(EditBox editor, Component hint) {
        ((GuideNativeHintAccess) editor).openallay$hint(hint);
    }
}
