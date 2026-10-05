package dev.openallay.client.gui;

import java.util.function.Supplier;
import net.minecraft.network.chat.MutableComponent;

/** Native button narration composition, independent of constructor callback names. */
@FunctionalInterface
public interface GuideButtonNarration {
    GuideButtonNarration DEFAULT = Supplier::get;
    MutableComponent create(Supplier<MutableComponent> defaultNarration);
}
