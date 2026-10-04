package dev.openallay.client.gui;

/** Native text payload. Text editing and IME ownership remain with native widgets. */
public record GuideInputCharacter(int codePoint, int modifiers) {}
