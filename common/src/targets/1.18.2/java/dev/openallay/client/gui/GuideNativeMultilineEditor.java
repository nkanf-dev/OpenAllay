package dev.openallay.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Selected constructor only. One canonical primitive owns editing, scrolling and painting. */
public final class GuideNativeMultilineEditor extends GuidePrimitiveMultilineEditor {
    public GuideNativeMultilineEditor(Font font, int x, int y, int width, int height, Component placeholder, Component narration) {
        super(font, x, y, width, height, placeholder, narration);
    }
}
