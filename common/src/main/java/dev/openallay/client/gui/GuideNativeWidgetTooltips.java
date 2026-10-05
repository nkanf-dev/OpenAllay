package dev.openallay.client.gui;

import net.minecraft.client.gui.components.AbstractWidget;

/** Tooltip intent crosses once from native-typed shared widget references. */
public final class GuideNativeWidgetTooltips {
    private GuideNativeWidgetTooltips() {}
    public static void set(AbstractWidget widget, GuideTooltip tooltip) {
        widget.setTooltip(tooltip == null ? null : net.minecraft.client.gui.components.Tooltip.create(tooltip.text()));
    }
}
