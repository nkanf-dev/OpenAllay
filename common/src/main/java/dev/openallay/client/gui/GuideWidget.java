package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;

/** Product widget geometry and state. Actual native owners remain in selected typed adapters. */
public interface GuideWidget {
    int getX();
    int getY();
    int getWidth();
    int getHeight();
    void setX(int x);
    void setY(int y);
    boolean guideActive();
    void guideActive(boolean active);
    boolean guideVisible();
    void guideVisible(boolean visible);
    Component getMessage();
    Class<?> guideNativeType();
    default boolean isMouseOver(double x, double y) {
        return guideVisible() && x >= getX() && y >= getY() && x < getX() + getWidth() && y < getY() + getHeight();
    }
}
