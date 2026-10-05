package dev.openallay.client.gui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Hint-only native operation; editor text, cursor, focus and formatter stay native. */
public final class GuideNativeTextHints {
    private GuideNativeTextHints() {}
    public static void setHint(EditBox editor, Component hint) { editor.setHint(hint); }
}
