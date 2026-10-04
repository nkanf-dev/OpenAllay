package dev.openallay.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Small native key callback binding. The native EditBox still owns text and IME. */
public abstract class GuideNativeEditBox extends EditBox {
    protected GuideNativeEditBox(Font font, int x, int y, int width, int height, Component title) {
        super(font, x, y, width, height, title);
    }

    @Override public final boolean keyPressed(int key, int scancode, int modifiers) {
        return guideKeyPressed(GuideNativeInput.capture(key, scancode, modifiers));
    }
    public boolean guideKeyPressed(GuideInputKey event) { return super.keyPressed(event.key(), event.scancode(), event.modifiers()); }
    protected final void formatGuideText(java.util.function.BiFunction<String, Integer, net.minecraft.util.FormattedCharSequence> formatter) {
        setFormatter((text, offset) -> formatter.apply(text, offset));
    }
}
