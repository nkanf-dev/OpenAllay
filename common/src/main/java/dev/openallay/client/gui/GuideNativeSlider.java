package dev.openallay.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Keep native slider value/input ownership while binding tooltip intent. */
public abstract class GuideNativeSlider extends AbstractSliderButton {
    protected GuideNativeSlider(int x, int y, int width, int height, Component text, double value) {
        super(x, y, width, height, text, value);
    }
    public final void setTooltip(GuideTooltip tooltip) {
        super.setTooltip(tooltip == null ? null : net.minecraft.client.gui.components.Tooltip.create(tooltip.text()));
    }
}
