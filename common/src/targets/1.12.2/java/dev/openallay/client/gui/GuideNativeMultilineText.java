package dev.openallay.client.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.text.ITextComponent;

/** Actual primitive selection uses the one canonical multiline algorithm, not a target copy. */
public final class GuideNativeMultilineText {
    private GuideNativeMultilineText() {}
    public static GuideMultilineEditor find(GuideWidget widget) {
        final class $oaPattern0_Holder { dev.openallay.client.gui.GuideWidget value; GuidePrimitiveMultilineEditor bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
return (($oaPattern0_holder.value = widget) instanceof dev.openallay.client.gui.GuidePrimitiveMultilineEditor && (($oaPattern0_holder.bound = (GuidePrimitiveMultilineEditor) $oaPattern0_holder.value) != null)) ? $oaPattern0_holder.bound : null;
    }

    public static GuideMultilineEditor create(FontRenderer font, int x, int y, int width, int height,
            ITextComponent placeholder, ITextComponent narration) {
        return new GuidePrimitiveMultilineEditor(font, x, y, width, height, placeholder, narration);
    }
    public static GuideMultilineEditor find(GuideWidgetInput widget) {
        final class $oaPattern1_Holder { dev.openallay.client.gui.GuideWidgetInput value; GuidePrimitiveMultilineEditor bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
return (($oaPattern1_holder.value = widget) instanceof dev.openallay.client.gui.GuidePrimitiveMultilineEditor && (($oaPattern1_holder.bound = (GuidePrimitiveMultilineEditor) $oaPattern1_holder.value) != null)) ? $oaPattern1_holder.bound : null;
    }
    public static void setValue(GuideMultilineEditor editor, String value, boolean bypassLineLimit) {
        editor.setValue(value, bypassLineLimit);
    }
    public static void tick(GuideWidgetInput widget) {
        final class $oaPattern2_Holder { dev.openallay.client.gui.GuideWidgetInput value; GuidePrimitiveMultilineEditor bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = widget) instanceof dev.openallay.client.gui.GuidePrimitiveMultilineEditor && (($oaPattern2_holder.bound = (GuidePrimitiveMultilineEditor) $oaPattern2_holder.value) != null))) $oaPattern2_holder.bound.tick();
    }
    public static int defaultTotalPadding() { return 8; }
}
