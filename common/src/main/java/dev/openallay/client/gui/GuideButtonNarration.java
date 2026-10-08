package dev.openallay.client.gui;

import java.util.function.Supplier;


/** Native button narration composition, independent of constructor callback names. */
@FunctionalInterface
public interface GuideButtonNarration {
    GuideButtonNarration DEFAULT = Supplier::get;
    net.minecraft.network.chat.Component create(Supplier<? extends net.minecraft.network.chat.Component> defaultNarration);
}
