package dev.openallay.client.gui;



/** Bridge mixed into the old native editor; no replacement editor is constructed. */
public interface GuideNativeHintAccess {
    void openallay$hint(net.minecraft.network.chat.Component hint);
}
