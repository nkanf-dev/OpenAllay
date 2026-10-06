package dev.openallay.client.gui;

import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/** Native button narration composition, independent of constructor callback names. */
@FunctionalInterface
public interface GuideButtonNarration {
    GuideButtonNarration DEFAULT = Supplier::get;
    Component create(Supplier<Component> defaultNarration);
}
