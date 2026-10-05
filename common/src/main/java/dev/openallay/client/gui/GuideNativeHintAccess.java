package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;

/** Bridge mixed into the old native editor; no replacement editor is constructed. */
public interface GuideNativeHintAccess {
    void openallay$hint(Component hint);
}
