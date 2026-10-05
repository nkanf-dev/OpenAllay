package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;

/** Native callback/scrolling label binding for the pre-text-collector widget family. */
public abstract class GuideNativeButton extends Button {
    protected GuideNativeButton(int x, int y, int width, int height, Component title, OnPress press, GuideButtonNarration narration) {
        super(x, y, width, height, title, press, narration::create);
    }
    @Override protected final void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        paintGuideButton(GuideGraphics.wrap(graphics), mouseX, mouseY, delta);
    }
    protected abstract void paintGuideButton(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    protected final void paintGuideButtonLabel(GuideGraphics graphics, Component label, int padding) {
        renderScrollingString(graphics.nativeGraphics(), Minecraft.getInstance().font, label,
                getX() + padding, getY(), getX() + getWidth() - padding, getY() + getHeight(),
                label.getStyle().getColor() == null ? 0xFFFFFFFF : 0xFF000000 | label.getStyle().getColor().getValue());
    }
    public final void setTooltip(GuideTooltip tooltip) {
        super.setTooltip(tooltip == null ? null : net.minecraft.client.gui.components.Tooltip.create(tooltip.text()));
    }
}
