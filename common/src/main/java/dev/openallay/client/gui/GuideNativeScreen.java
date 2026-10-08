package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Native callback names/types are bound once; Screen feature painting stays shared. */
public abstract class GuideNativeScreen extends Screen implements GuideWidgetInput {
    protected final void tickGuideWidgets() {} // Native widgets blink from elapsed time.
    protected GuideNativeScreen(Component title) { super(title); }
    /** Register/focus the actual owner carried by the product widget adapter. */
    protected final GuideWidget addGuideWidgetHandle(GuideWidget widget) {
        addGuideWidget(GuideNativeWidgets.nativeWidget(widget));
        return widget;
    }
    public final void setFocused(GuideWidget widget) { setFocused(GuideNativeWidgets.nativeWidget(widget)); }
    protected final void setInitialFocus(GuideWidget widget) { setInitialFocus(GuideNativeWidgets.nativeWidget(widget)); }
    public final GuideWidget getGuideWidgetFocused() {
        return getFocused() instanceof net.minecraft.client.gui.components.AbstractWidget widget
                ? GuideNativeWidgets.wrap(widget) : null;
    }
    public final java.util.List<GuideWidget> guideWidgetChildren() {
        return children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast).map(GuideNativeWidgets::wrap).toList();
    }
    public final boolean guideWidgetFocused(GuideWidget widget) {
        return getFocused() == GuideNativeWidgets.nativeWidget(widget);
    }
    /** Register the actual native widget for both input and rendering. */
    protected final <T extends net.minecraft.client.gui.components.AbstractWidget> T addGuideWidget(T widget) {
        return super.addRenderableWidget(widget);
    }

    /** Rebuild native children without changing the shared screen attachment. */
    protected final void guideRebuildWidgets() { super.rebuildWidgets(); }
    @Override protected final void setInitialFocus() { guideInitialFocus(); }
    protected void guideInitialFocus() { super.setInitialFocus(); }
    @Override public final boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return guideMouseScrolled(x, y, horizontal, vertical);
    }
    public boolean guideMouseScrolled(double x, double y, double horizontal, double vertical) {
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        GuideGraphics guide = GuideGraphics.wrap(graphics);
        guide.paint(() -> paintGuideScreen(guide, mouseX, mouseY, delta));
    }
    protected abstract void paintGuideScreen(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    @Override public final void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        GuideGraphics guide = GuideGraphics.wrap(graphics);
        guide.paint(() -> paintGuideBackground(guide, mouseX, mouseY, delta));
    }
    protected void paintGuideBackground(GuideGraphics graphics, int mouseX, int mouseY, float delta) {
        super.extractBackground(graphics.nativeGraphics(), mouseX, mouseY, delta);
    }
    protected final void renderGuideWidgets(GuideGraphics graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics.nativeGraphics(), mouseX, mouseY, delta);
    }
    @Override public final void resize(int width, int height) { resizeGuide(width, height); }
    protected void resizeGuide(int width, int height) { resizeGuideWidgets(width, height); }
    protected final void resizeGuideWidgets(int width, int height) { super.resize(width, height); }

    @Override public final boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return guideKeyPressed(GuideNativeInput.capture(event));
    }
    @Override public final boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
        return guideKeyReleased(GuideNativeInput.capture(event));
    }
    @Override public final boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        return guideCharTyped(GuideNativeInput.capture(event));
    }
    @Override public final boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return guideMouseClicked(GuideNativeInput.capture(event), doubleClick);
    }
    @Override public final boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        return guideMouseDragged(GuideNativeInput.capture(event), dx, dy);
    }
    @Override public final boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        return guideMouseReleased(GuideNativeInput.capture(event));
    }
    public boolean guideKeyPressed(GuideInputKey event) { return super.keyPressed(GuideNativeInput.nativeKey(event)); }
    public boolean guideKeyReleased(GuideInputKey event) { return super.keyReleased(GuideNativeInput.nativeKey(event)); }
    public boolean guideCharTyped(GuideInputCharacter event) { return super.charTyped(GuideNativeInput.nativeCharacter(event)); }
    public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) { return super.mouseClicked(GuideNativeInput.nativeMouse(event), doubleClick); }
    public boolean guideMouseDragged(GuideInputMouse event, double dx, double dy) { return super.mouseDragged(GuideNativeInput.nativeMouse(event), dx, dy); }
    public boolean guideMouseReleased(GuideInputMouse event) { return super.mouseReleased(GuideNativeInput.nativeMouse(event)); }
    @Override public final void removed() { try { guideRemoved(); } finally { super.removed(); } }
    @Override protected final void init() { initGuideScreen(); }
    protected void initGuideScreen() { super.init(); }
    @Override public final void added() { guideAdded(); }
    protected void guideAdded() { super.added(); }
    protected void guideRemoved() { }
    @Override protected final void repositionElements() { repositionGuideElements(); }
    protected void repositionGuideElements() { super.repositionElements(); }
    public final void clearGuideWidgetFocus() { GuideNativeFocus.clear(this); }
    protected final void guideDragging(boolean dragging) { setDragging(dragging); }
    @Override public final void guideSetFocused(boolean focused) {
        if (focused) throw new UnsupportedOperationException("Acquire a screen child through its native focus path");
        GuideNativeFocus.clear(this);
    }
    @Override public final boolean guideIsFocused() { return getFocused() != null; }
    public final boolean guideWidgetRegistered(GuideWidget widget) { return children().contains(GuideNativeWidgets.nativeWidget(widget)); }
}
