package dev.openallay.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;

/** Native widget draw/input/narration callbacks before renderWidget was introduced. */
public abstract class GuideNativeWidget extends AbstractWidget implements GuideNativeTooltipAccess {
    private GuideTooltip guideTooltip;
    protected GuideNativeWidget(int x, int y, int width, int height, Component title) { super(x, y, width, height, title); }
    public final int getX() { return x; }
    public final int getY() { return y; }
    public final void setX(int x) { this.x = x; }
    public final void setY(int y) { this.y = y; }
    public final boolean isHovered() { return isHovered; }
    public final void setTooltip(GuideTooltip tooltip) { guideTooltip = tooltip; }
    @Override public final void renderButton(PoseStack pose, int mouseX, int mouseY, float delta) {
        GuideGraphics guide = GuideGraphics.wrap(pose);
        guide.paint(() -> {
            paintGuideWidget(guide, mouseX, mouseY, delta);
            if (guideTooltip != null && isHovered) guide.setTooltipForNextFrame(guideTooltip.text(), mouseX, mouseY);
        });
    }
    protected abstract void paintGuideWidget(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    @Override public final void updateNarration(NarrationElementOutput output) {
        narrateGuideWidget(output);
        if (guideTooltip != null) output.add(NarratedElementType.HINT, guideTooltip.text());
    }
    protected abstract void narrateGuideWidget(NarrationElementOutput output);
}
