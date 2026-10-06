package dev.openallay.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Real native Button input and press ownership with typed old draw/narration callbacks. */
public abstract class GuideNativeButton extends Button implements GuideNativeTooltipAccess {
    private final GuideButtonNarration guideNarration;
    private GuideTooltip guideTooltip;
    protected GuideNativeButton(int x, int y, int width, int height, Component title, java.util.function.Consumer<OpenAllayButton> press, GuideButtonNarration narration) {
        super(x, y, width, height, title, button -> press.accept((OpenAllayButton) button));
        guideNarration = java.util.Objects.requireNonNull(narration, "narration");
    }
    public final int getX() { return x; }
    public final int getY() { return y; }
    public final void setX(int x) { this.x = x; }
    public final void setY(int y) { this.y = y; }
    public final boolean isGuideHovered() { return isHovered; }
    public final void setTooltip(GuideTooltip tooltip) { guideTooltip = tooltip; }
    @Override protected final MutableComponent createNarrationMessage() {
        MutableComponent text = guideNarration.create(() -> super.createNarrationMessage());
        return guideTooltip == null ? text : text.append(dev.openallay.platform.minecraft.MinecraftComponents.literal(", ")).append(guideTooltip.text());
    }
    @Override public final void renderButton(PoseStack pose, int mouseX, int mouseY, float delta) {
        GuideGraphics guide = GuideGraphics.wrap(pose);
        guide.paint(() -> {
            paintGuideButton(guide, mouseX, mouseY, delta);
            if (guideTooltip != null && isHovered) guide.setTooltipForNextFrame(guideTooltip.text(), mouseX, mouseY);
        });
    }
    protected abstract void paintGuideButton(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    protected final void paintGuideButtonLabel(GuideGraphics graphics, Component label, int padding) {
        var font = Minecraft.getInstance().font;
        int textWidth = font.width(label), available = width - padding * 2;
        int color = label.getStyle().getColor() == null ? 0xFFFFFFFF : 0xFF000000 | label.getStyle().getColor().getValue();
        int baseline = y + (height - 9) / 2;
        if (textWidth <= available) {
            graphics.text(font, label, x + (width - textWidth) / 2, baseline, color);
        } else {
            int overflow = textWidth - available;
            double seconds = net.minecraft.Util.getMillis() / 1000.0;
            double period = Math.max(overflow * 0.5, 3.0);
            double fraction = Math.sin((Math.PI / 2) * Math.cos((Math.PI * 2) * seconds / period)) / 2 + 0.5;
            graphics.enableScissor(x + padding, y, x + width - padding, y + height);
            try { graphics.text(font, label, x + padding - (int) (fraction * overflow), baseline, color); }
            finally { graphics.disableScissor(); }
        }
    }
}
