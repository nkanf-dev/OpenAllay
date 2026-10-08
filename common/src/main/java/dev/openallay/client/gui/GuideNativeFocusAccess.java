package dev.openallay.client.gui;

/** Native widget transition owner, implemented in a legitimate native binding scope. */
public interface GuideNativeFocusAccess {
    void openallay$guideFocus(boolean focused);
}
