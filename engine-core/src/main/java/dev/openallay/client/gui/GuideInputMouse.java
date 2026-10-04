package dev.openallay.client.gui;

/** Native pointer payload. Button semantics belong to the native input binding. */
public record GuideInputMouse(double x, double y, int button, int modifiers, boolean leftClick) {}
