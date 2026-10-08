package dev.openallay.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;

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

    @Override public final void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        GuideLegacyCursor.beginFrame();
        paintGuideScreen(GuideGraphics.wrap(graphics), mouseX, mouseY, delta);
    }
    protected abstract void paintGuideScreen(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    @Override public final void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        paintGuideBackground(GuideGraphics.wrap(graphics), mouseX, mouseY, delta);
    }
    protected void paintGuideBackground(GuideGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderBackground(graphics.nativeGraphics(), mouseX, mouseY, delta);
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
    @Override public final void removed() {
        try { guideRemoved(); }
        finally { GuideLegacyCursor.close(); super.removed();         }
    }
    /** Legacy native Screen has no in-game marker; mod screens keep their own explicit policy. */
    public boolean isInGameUi() { return false; }
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
