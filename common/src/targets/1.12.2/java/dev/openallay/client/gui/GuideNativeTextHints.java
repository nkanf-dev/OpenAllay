package dev.openallay.client.gui;

import net.minecraft.util.text.ITextComponent;

/** Actual legacy editor hint intent. Rendering remains the selected text widget's responsibility. */
public final class GuideNativeTextHints {
    private GuideNativeTextHints() {}
    public static void setHint(GuideNativeEditBox editor, ITextComponent hint) { editor.setHint(hint); }
}
