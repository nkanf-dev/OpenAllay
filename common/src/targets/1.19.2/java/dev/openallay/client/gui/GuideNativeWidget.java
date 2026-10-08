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
    public final boolean isGuideHovered() { return isHovered; }
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
        narrateGuideWidget((part, text) -> output.add(NarratedElementType.valueOf(part.name()), text));
        if (guideTooltip != null) output.add(NarratedElementType.HINT, guideTooltip.text());
    }
    protected abstract void narrateGuideWidget(GuideNarration output);

    public boolean guideKeyPressed(GuideInputKey event) { return false; }
    public boolean guideKeyReleased(GuideInputKey event) { return false; }
    public boolean guideCharTyped(GuideInputCharacter event) { return false; }
    public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) { return false; }
    public boolean guideMouseDragged(GuideInputMouse event, double dx, double dy) { return false; }
    public boolean guideMouseReleased(GuideInputMouse event) { return false; }
    public boolean guideMouseScrolled(double x, double y, double amount) { return false; }

    @Override public boolean keyPressed(int key, int scancode, int modifiers) { return guideKeyPressed(GuideNativeInput.capture(key, scancode, modifiers)); }
    @Override public boolean keyReleased(int key, int scancode, int modifiers) { return guideKeyReleased(GuideNativeInput.capture(key, scancode, modifiers)); }
    @Override public boolean charTyped(char character, int modifiers) { return guideCharTyped(GuideNativeInput.capture(character, modifiers)); }
    @Override public boolean mouseClicked(double x, double y, int button) { return guideMouseClicked(GuideNativeInput.capture(x, y, button), false); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return guideMouseDragged(GuideNativeInput.capture(x, y, button), dx, dy); }
    @Override public boolean mouseReleased(double x, double y, int button) { return guideMouseReleased(GuideNativeInput.capture(x, y, button)); }
    @Override public boolean mouseScrolled(double x, double y, double vertical) { return guideMouseScrolled(x, y, vertical); }
    protected final void guideSetBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
}
