package dev.openallay.client.gui;

/** Native-neutral widget callback seam. Native widgets still own text and clipboard edits. */
public interface GuideWidgetInput {
    default boolean guideKeyPressed(GuideInputKey event) { return false; }
    default boolean guideKeyReleased(GuideInputKey event) { return false; }
    default boolean guideCharTyped(GuideInputCharacter event) { return false; }
    default boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) { return false; }
    default boolean guideMouseDragged(GuideInputMouse event, double dx, double dy) { return false; }
    default boolean guideMouseReleased(GuideInputMouse event) { return false; }
    void guideSetFocused(boolean focused);
    boolean guideIsFocused();
}
