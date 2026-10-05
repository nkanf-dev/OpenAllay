package dev.openallay.client.gui;

import java.util.Objects;
import net.minecraft.network.chat.Component;

/** Tooltip intent; the selected native widget binds rendering and narration. */
public record GuideTooltip(Component text) {
    public GuideTooltip { Objects.requireNonNull(text, "text"); }
    public static GuideTooltip create(Component text) { return new GuideTooltip(text); }
}
