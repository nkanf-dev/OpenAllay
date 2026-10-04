package dev.openallay.client.gui;

/** Native key payload plus semantics frozen by the actual native input binding. */
public record GuideInputKey(int key, int scancode, int keycode, int modifiers,
        boolean isConfirmation, boolean hasShiftDown, boolean controlDown,
        boolean isPaste, boolean isCopy, boolean isCut, boolean isEscape) {}
