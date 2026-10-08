package dev.openallay.client.gui;

import net.minecraft.client.gui.GuiScreen;

/** Actual text owner retirement without stopping a replacement screen's input. */
public final class GuideTextInputFocus {
    private GuideTextInputFocus() {}
    public static boolean isTextFocused(GuiScreen screen) {
        final class $oaPattern0_Holder { net.minecraft.client.gui.GuiScreen value; GuideNativeScreenCallbacks bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if (!((($oaPattern0_holder.value = screen) instanceof dev.openallay.client.gui.GuideNativeScreenCallbacks && (($oaPattern0_holder.bound = (GuideNativeScreenCallbacks) $oaPattern0_holder.value) != null)))) return false;
        GuideWidgetInput focused = $oaPattern0_holder.bound.getGuideFocused();
        return focused instanceof GuideNativeEditBox || focused instanceof GuidePrimitiveMultilineEditor;
    }
    public static void release(GuiScreen screen) {
        final class $oaPattern1_Holder { net.minecraft.client.gui.GuiScreen value; GuideNativeScreenCallbacks bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = screen) instanceof dev.openallay.client.gui.GuideNativeScreenCallbacks && (($oaPattern1_holder.bound = (GuideNativeScreenCallbacks) $oaPattern1_holder.value) != null))) $oaPattern1_holder.bound.clearGuideFocus();
    }
}
