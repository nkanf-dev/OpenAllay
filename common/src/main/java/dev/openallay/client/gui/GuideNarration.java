package dev.openallay.client.gui;


import dev.openallay.platform.minecraft.MinecraftComponents;

/** Product narration projection; selected native widgets own speech scheduling. */
@FunctionalInterface
public interface GuideNarration {
    enum Part { TITLE, USAGE, HINT }
    void add(Part part, net.minecraft.network.chat.Component text);
    default void add(Part part, String text) { add(part, MinecraftComponents.literal(text)); }
}
