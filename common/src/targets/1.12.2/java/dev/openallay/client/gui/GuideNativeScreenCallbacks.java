package dev.openallay.client.gui;

import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Actual GuiScreen callbacks. Layout, action policy and screen state belong to the one feature screen. */
public abstract class GuideNativeScreenCallbacks extends GuiScreen {
    private boolean guideAttached;
    private int dragX;
    private int dragY;
    private GuideWidgetInput guideFocused;

    @Override public final void initGui() {
        if (!guideAttached) {
            guideAttached = true;
            try { guideAdded(); }
            catch (RuntimeException | Error failure) {
                guideAttached = false;
                throw failure;
            }
        }
        initGuideScreen();
    }
    protected void guideAdded() {}
    protected void guideRemoved() {}
    protected void initGuideScreen() { super.initGui(); }
    protected abstract void paintNativeGuideScreen(int mouseX, int mouseY, float partialTicks);
    @Override public final void drawScreen(int mouseX, int mouseY, float partialTicks) {
        paintNativeGuideScreen(mouseX, mouseY, partialTicks);
    }
    protected final void renderNativeGuideWidgets(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    protected final void renderNativeGuideBackground() { super.drawDefaultBackground(); }
    @Override public void onResize(Minecraft client, int width, int height) {
        // Native setWorldAndResolution owns Forge InitGui events and field rebinding.
        super.onResize(client, width, height);
    }
    @Override public final void updateScreen() { tickGuideScreen(); }
    protected void tickGuideScreen() { super.updateScreen(); }

    @Override protected final void keyTyped(char character, int key) throws IOException {
        int modifiers = GuideNativeInput.modifiers();
        boolean handled = false;
        if (key != Keyboard.KEY_NONE) handled = guideKeyPressed(GuideNativeInput.capture(key, 0, modifiers));
        // A handled shortcut must not also insert its character. LWJGL 2 reports both together.
        if (!handled && character >= ' ' && character != 127) {
            handled = guideCharTyped(GuideNativeInput.capture(character, modifiers));
        }
        if (handled) keyHandled = true;
        else super.keyTyped(character, key);
    }
    @Override public final void handleKeyboardInput() throws IOException {
        // Keep native dispatchKeypresses and its physical key/character admission rule.
        super.handleKeyboardInput();
        int key = Keyboard.getEventKey();
        if (!Keyboard.getEventKeyState() && key != Keyboard.KEY_NONE
                && guideKeyReleased(GuideNativeInput.capture(key, 0, GuideNativeInput.modifiers()))) {
            keyHandled = true;
        }
    }
    @Override protected final void mouseClicked(int x, int y, int button) throws IOException {
        dragX = x;
        dragY = y;
        if (guideMouseClicked(GuideNativeInput.capture(x, y, button), false)) mouseHandled = true;
        else super.mouseClicked(x, y, button);
    }
    @Override protected final void mouseReleased(int x, int y, int button) {
        boolean handled = guideMouseReleased(GuideNativeInput.capture(x, y, button));
        // Always clear native selectedButton, even if a feature consumed release.
        super.mouseReleased(x, y, button);
        if (handled) mouseHandled = true;
    }
    @Override protected final void mouseClickMove(int x, int y, int button, long heldMillis) {
        int dx = x - dragX;
        int dy = y - dragY;
        dragX = x;
        dragY = y;
        if (guideMouseDragged(GuideNativeInput.capture(x, y, button), dx, dy)) mouseHandled = true;
        else super.mouseClickMove(x, y, button, heldMillis);
    }
    @Override public final void handleMouseInput() throws IOException {
        // Preserve native touchscreen counters, button ownership and drag admission.
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || mc.currentScreen != this) return;
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        // LWJGL 2 exposes one wheel axis. No fabricated horizontal scroll.
        if (guideMouseScrolled(x, y, 0.0, Math.signum(wheel))) mouseHandled = true;
    }
    public boolean guideKeyPressed(GuideInputKey event) {
        return guideFocused != null && guideFocused.guideKeyPressed(event);
    }
    public boolean guideKeyReleased(GuideInputKey event) {
        return guideFocused != null && guideFocused.guideKeyReleased(event);
    }
    public boolean guideCharTyped(GuideInputCharacter event) {
        return guideFocused != null && guideFocused.guideCharTyped(event);
    }
    public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) { return false; }
    public boolean guideMouseDragged(GuideInputMouse event, double dx, double dy) { return false; }
    public boolean guideMouseReleased(GuideInputMouse event) { return false; }
    public boolean guideMouseScrolled(double x, double y, double horizontal, double vertical) { return false; }
    public final GuideWidgetInput getGuideFocused() { return guideFocused; }
    public final void setGuideFocused(GuideWidgetInput child) {
        if (child == guideFocused) return;
        if (guideFocused != null) guideFocused.guideSetFocused(false);
        guideFocused = child;
        if (child != null) child.guideSetFocused(true);
    }
    public final void clearGuideFocus() { setGuideFocused(null); }
    @Override public final void onGuiClosed() {
        try { guideRemoved(); }
        finally {
            guideAttached = false;
            try { clearGuideFocus(); }
            finally { super.onGuiClosed(); }
        }
    }
}
