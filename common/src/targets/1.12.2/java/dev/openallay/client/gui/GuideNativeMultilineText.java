package dev.openallay.client.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;

/** Actual primitive selection uses the one canonical multiline algorithm, not a target copy. */
public final class GuideNativeMultilineText {
    private GuideNativeMultilineText() {}
    public static GuideMultilineEditor find(GuideWidget widget) {
        return widget instanceof GuidePrimitiveMultilineEditor editor ? editor : null;
    }

    public static GuideMultilineEditor create(FontRenderer font, int x, int y, int width, int height,
            ITextComponent placeholder, ITextComponent narration) {
        return new GuidePrimitiveMultilineEditor(font, x, y, width, height, placeholder, narration);
    }
    public static GuideMultilineEditor find(GuideWidgetInput widget) {
        return widget instanceof GuidePrimitiveMultilineEditor editor ? editor : null;
    }
    public static void setValue(GuideMultilineEditor editor, String value, boolean bypassLineLimit) {
        editor.setValue(value, bypassLineLimit);
    }
    public static void tick(GuideWidgetInput widget) {
        if (widget instanceof GuidePrimitiveMultilineEditor editor) editor.tick();
    }
    public static int defaultTotalPadding() { return 8; }
}
