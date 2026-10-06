package dev.openallay.client.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;

/** Typed constructor compatibility seam over the one canonical primitive. */
public final class GuideNativeMultilineEditor extends GuidePrimitiveMultilineEditor {
    public GuideNativeMultilineEditor(FontRenderer font, int x, int y, int width, int height,
            ITextComponent placeholder, ITextComponent narration) {
        super(font, x, y, width, height, placeholder, narration);
    }
}
