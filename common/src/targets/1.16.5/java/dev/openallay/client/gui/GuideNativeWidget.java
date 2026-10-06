package dev.openallay.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import dev.openallay.platform.minecraft.MinecraftComponents;

/** Actual Widget paint and native narration scheduling; feature narration is one projection. */
public abstract class GuideNativeWidget extends AbstractWidget implements GuideNativeTooltipAccess {
    private GuideTooltip guideTooltip;
    protected GuideNativeWidget(int x, int y, int width, int height, Component title) { super(x, y, width, height, title); }
    public final int getX() { return x; }
    public final int getY() { return y; }
    public final void setX(int x) { this.x = x; }
    public final void setY(int y) { this.y = y; }
    public final boolean isGuideHovered() { return isHovered; }
    public final void setTooltip(GuideTooltip tooltip) { guideTooltip = tooltip; }
    @Override public final void renderButton(PoseStack pose, int mouseX, int mouseY, float delta) {
        GuideGraphics guide = GuideGraphics.wrap(pose);
        guide.paint(() -> {
            paintGuideWidget(guide, mouseX, mouseY, delta);
            if (guideTooltip != null && isHovered) guide.setTooltipForNextFrame(guideTooltip.text(), mouseX, mouseY);
        });
    }
    /** Shared editor calls setFocused directly; dispatch its real transition hook once. */
    @Override public void setFocused(boolean focused) {
        if (isFocused() == focused) return;
        super.setFocused(focused);
        onFocusedChanged(focused);
    }
    @Override protected final MutableComponent createNarrationMessage() {
        MutableComponent result = MinecraftComponents.empty();
        narrateGuideWidget((part, text) -> {
            if (!result.getString().isEmpty()) result.append(MinecraftComponents.literal(", "));
            result.append(text);
        });
        if (guideTooltip != null) result.append(MinecraftComponents.literal(", ")).append(guideTooltip.text());
        return result;
    }
    protected abstract void paintGuideWidget(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    protected abstract void narrateGuideWidget(GuideNarration output);
}
