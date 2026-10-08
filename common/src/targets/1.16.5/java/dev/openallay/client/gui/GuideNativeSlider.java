package dev.openallay.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Actual native slider rendering/value/input, with the shared tooltip intent. */
public abstract class GuideNativeSlider extends AbstractSliderButton implements GuideNativeTooltipAccess {
    private GuideTooltip guideTooltip;
    protected GuideNativeSlider(int x, int y, int width, int height, Component text, double value) {
        super(x, y, width, height, text, value);
    }
    public final int getX() { return x; }
    public final int getY() { return y; }
    public final void setX(int x) { this.x = x; }
    public final void setY(int y) { this.y = y; }
    public final void setTooltip(GuideTooltip tooltip) { guideTooltip = tooltip; }
    @Override public final void renderButton(PoseStack pose, int mouseX, int mouseY, float delta) {
        GuideGraphics guide = GuideGraphics.wrap(pose);
        guide.paint(() -> {
            super.renderButton(pose, mouseX, mouseY, delta);
            if (guideTooltip != null && isHovered) guide.setTooltipForNextFrame(guideTooltip.text(), mouseX, mouseY);
        });
    }
    @Override protected final net.minecraft.network.chat.MutableComponent createNarrationMessage() {
        var text = super.createNarrationMessage();
        return guideTooltip == null ? text : text.append(dev.openallay.platform.minecraft.MinecraftComponents.literal(", ")).append(guideTooltip.text());
    }
}
