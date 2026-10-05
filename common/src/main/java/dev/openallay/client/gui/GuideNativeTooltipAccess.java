package dev.openallay.client.gui;

/** Implemented by owned native widget bindings, not by native feature code. */
public interface GuideNativeTooltipAccess {
    void setTooltip(GuideTooltip tooltip);
}
