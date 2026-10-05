package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

/** Native callback names/types are bound once; Screen feature painting stays shared. */
public abstract class GuideNativeScreen extends GuideNativeScreenCallbacks {
    private GuideGraphics paintGraphics;
    private int paintMouseX;
    private int paintMouseY;
    private PendingTooltip pendingTooltip;
    private record PendingTooltip(List<FormattedCharSequence> lines, ClientTooltipPositioner positioner) {}

    protected GuideNativeScreen(Component title) { super(title); }
    @Override public final void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (paintGraphics != null) throw new IllegalStateException("Screen paint is already active");
        GuideGraphics guide = GuideGraphics.wrap(graphics);
        PendingTooltip pending = pendingTooltip;
        pendingTooltip = null;
        paintGraphics = guide;
        paintMouseX = mouseX;
        paintMouseY = mouseY;
        try {
            GuideLegacyCursor.beginFrame();
            guide.paint(() -> {
                if (pending != null) {
                    guide.setTooltipForNextFrame(font, pending.lines(), pending.positioner(), mouseX, mouseY, false);
                }
                paintGuideScreen(guide, mouseX, mouseY, delta);
            });
        } finally {
            paintGraphics = null;
            paintMouseX = 0;
            paintMouseY = 0;
            // Screen.renderWithTooltip is final. Its native slot must not draw a second tooltip.
            clearGuideTooltipForNextRenderPass();
        }
    }

    @Override protected final void clearGuideTooltipForNextRenderPass() {
        pendingTooltip = null;
        if (paintGraphics != null) paintGraphics.clearTooltipForNextFrame();
        clearNativeTooltipForNextRenderPass();
    }

    @Override public final void setTooltipForNextRenderPass(
            List<FormattedCharSequence> lines, ClientTooltipPositioner positioner, boolean replaceExisting) {
        if (lines.isEmpty()) return;
        if (paintGraphics != null) {
            paintGraphics.setTooltipForNextFrame(font, lines, positioner, paintMouseX, paintMouseY, replaceExisting);
        } else if (pendingTooltip == null || replaceExisting) {
            pendingTooltip = new PendingTooltip(List.copyOf(lines), positioner);
        }
    }
    protected abstract void paintGuideScreen(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    @Override protected final void paintNativeGuideBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        GuideGraphics guide = paintGraphics != null && paintGraphics.nativeGraphics() == graphics
                ? paintGraphics : GuideGraphics.wrap(graphics);
        guide.paint(() -> paintGuideBackground(guide, mouseX, mouseY, delta));
    }
    protected void paintGuideBackground(GuideGraphics graphics, int mouseX, int mouseY, float delta) {
        renderNativeGuideBackground(graphics.nativeGraphics(), mouseX, mouseY, delta);
    }
    protected final void renderGuideWidgets(GuideGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics.nativeGraphics(), mouseX, mouseY, delta);
    }
    @Override public final void resize(net.minecraft.client.Minecraft client, int width, int height) { resizeGuide(width, height); }
    protected void resizeGuide(int width, int height) { resizeGuideWidgets(width, height); }
    protected final void resizeGuideWidgets(int width, int height) { super.resize(minecraft, width, height); }

    @Override public final boolean keyPressed(int key, int scancode, int modifiers) {
        return guideKeyPressed(GuideNativeInput.capture(key, scancode, modifiers));
    }
    @Override public final boolean keyReleased(int key, int scancode, int modifiers) {
        return guideKeyReleased(GuideNativeInput.capture(key, scancode, modifiers));
    }
    @Override public final boolean charTyped(char character, int modifiers) {
        return guideCharTyped(GuideNativeInput.capture(character, modifiers));
    }
    @Override public final boolean mouseClicked(double x, double y, int button) {
        return guideMouseClicked(GuideNativeInput.capture(x, y, button), false);
    }
    @Override public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return guideMouseDragged(GuideNativeInput.capture(x, y, button), dx, dy);
    }
    @Override public final boolean mouseReleased(double x, double y, int button) {
        return guideMouseReleased(GuideNativeInput.capture(x, y, button));
    }
    public boolean guideKeyPressed(GuideInputKey event) { return super.keyPressed(event.key(), event.scancode(), event.modifiers()); }
    public boolean guideKeyReleased(GuideInputKey event) { return super.keyReleased(event.key(), event.scancode(), event.modifiers()); }
    public boolean guideCharTyped(GuideInputCharacter event) {
        boolean handled = false;
        for (char character : Character.toChars(event.codePoint())) handled |= super.charTyped(character, event.modifiers());
        return handled;
    }
    public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) { return super.mouseClicked(event.x(), event.y(), event.button()); }
    public boolean guideMouseDragged(GuideInputMouse event, double dx, double dy) { return super.mouseDragged(event.x(), event.y(), event.button(), dx, dy); }
    public boolean guideMouseReleased(GuideInputMouse event) { return super.mouseReleased(event.x(), event.y(), event.button()); }
    @Override public void removed() {
        pendingTooltip = null;
        clearGuideTooltipForNextRenderPass();
        try { GuideLegacyCursor.close(); } finally { super.removed(); }
    }
    /** Legacy native Screen has no in-game marker; mod screens keep their own explicit policy. */
    public boolean isInGameUi() { return false; }
}
