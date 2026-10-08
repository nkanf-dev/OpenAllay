package dev.openallay.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Small native key callback binding. The native EditBox still owns text and IME. */
public class GuideNativeEditBox extends EditBox {
    public GuideNativeEditBox(Font font, int x, int y, int width, int height, Component title) {
        super(font, x, y, width, height, title);
    }

    @Override public final boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return guideKeyPressed(GuideNativeInput.capture(event));
    }
    public boolean guideKeyPressed(GuideInputKey event) { return super.keyPressed(GuideNativeInput.nativeKey(event)); }
    protected final void formatGuideText(java.util.function.BiFunction<String, Integer, GuideTextLine> formatter) {
        addFormatter((text, offset) -> GuideNativeFont.nativeLine(formatter.apply(text, offset)));
    }
    protected net.minecraft.network.chat.Component guideNarrationMessage() { return super.createNarrationMessage(); }
    @Override protected final net.minecraft.network.chat.MutableComponent createNarrationMessage() {
        return (net.minecraft.network.chat.MutableComponent) guideNarrationMessage();
    }
}
