package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;
import dev.openallay.platform.minecraft.MinecraftComponents;

/** Product narration projection; selected native widgets own speech scheduling. */
@FunctionalInterface
public interface GuideNarration {
    enum Part { TITLE, USAGE, HINT }
    void add(Part part, Component text);
    default void add(Part part, String text) { add(part, MinecraftComponents.literal(text)); }
}
