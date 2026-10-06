package dev.openallay.client.gui;

import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.ITextComponent;

/** Actual GuiScreen paint/lifecycle seam. Canonical feature screens remain the only layout owners. */
public abstract class GuideNativeScreen extends GuideNativeScreenCallbacks {
    protected Minecraft minecraft;
    protected FontRenderer font;
    private final ITextComponent title;
    private GuideGraphics paintGraphics;
    private final java.util.List<GuideNativeEditBox> textFields = new java.util.ArrayList<>();
    protected GuideNativeScreen(ITextComponent title) { this.title = Objects.requireNonNull(title, "title"); }
    protected final GuideWidget addGuideWidgetHandle(GuideWidget widget) {
        if (widget instanceof GuideNativeButton button) addGuideWidget(button);
        else if (widget instanceof GuideNativeEditBox field) addGuideWidget(field);
        else if (widget instanceof GuideNativeWidget primitive) super.addButton(primitive);
        else throw new IllegalArgumentException("Widget has no selected native registration leaf");
        return widget;
    }
    public final GuideWidgetInput getFocused() { return getGuideFocused(); }
    public final java.util.List<GuideWidget> children() { return guideWidgetChildren(); }
    public final void setDragging(boolean dragging) {
        if (!dragging && getGuideFocused() != null) getGuideFocused().guideMouseReleased(GuideNativeInput.mouseEvent(0, 0, 0, 0));
    }
    public final void setFocused(GuideWidget widget) {
        if (!(widget instanceof GuideWidgetInput input)) throw new IllegalArgumentException("Widget has no native input owner");
        setGuideFocused(input);
    }
    protected final void setInitialFocus(GuideWidget widget) { setFocused(widget); }
    public final boolean guideWidgetFocused(GuideWidget widget) { return getGuideFocused() == widget; }
    public final GuideWidget getGuideWidgetFocused() {
        return getGuideFocused() instanceof GuideWidget widget ? widget : null;
    }
    public final java.util.List<GuideWidget> guideWidgetChildren() {
        java.util.List<GuideWidget> children = new java.util.ArrayList<>();
        for (GuiButton button : buttonList) if (button instanceof GuideWidget widget) children.add(widget);
        children.addAll(textFields);
        return java.util.List.copyOf(children);
    }
    public final ITextComponent getTitle() { return title; }
    @Override protected void initGuideScreen() {
        minecraft = mc;
        font = fontRenderer;
        super.initGuideScreen();
    }
    @Override public void setWorldAndResolution(Minecraft client, int width, int height) {
        // Bind canonical field names before native initGui dispatch reaches feature init hooks.
        minecraft = client;
        font = client.fontRenderer;
        super.setWorldAndResolution(client, width, height);
    }
    protected final <T extends GuiButton> T addGuideWidget(T widget) { return super.addButton(widget); }
    protected final <T extends GuideNativeEditBox> T addGuideWidget(T widget) {
        textFields.add(widget);
        return widget;
    }
    @Override public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) {
        for (GuideNativeEditBox field : textFields) {
            if (field.guideMouseClicked(event, doubleClick)) {
                setGuideFocused(field);
                return true;
            }
        }
        for (GuiButton button : buttonList) {
            if (button instanceof GuideNativeWidget primitive && primitive.guideMouseClicked(event, doubleClick)) {
                setGuideFocused(primitive);
                return true;
            }
        }
        clearGuideFocus();
        return super.guideMouseClicked(event, doubleClick);
    }
    @Override public boolean guideMouseDragged(GuideInputMouse event, double dx, double dy) {
        return getGuideFocused() != null && getGuideFocused().guideMouseDragged(event, dx, dy);
    }
    @Override public boolean guideMouseReleased(GuideInputMouse event) {
        boolean focused = getGuideFocused() != null && getGuideFocused().guideMouseReleased(event);
        return super.guideMouseReleased(event) || focused;
    }
    @Override public boolean guideMouseScrolled(double x, double y, double horizontal, double vertical) {
        for (GuiButton button : buttonList) {
            if (button instanceof GuideNativeWidget primitive && primitive.guideMouseScrolled(x, y, vertical)) return true;
        }
        return false;
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button instanceof GuideNativeButton guideButton) guideButton.onPress();
    }
    @Override protected final void paintNativeGuideScreen(int mouseX, int mouseY, float partialTicks) {
        if (paintGraphics != null) throw new IllegalStateException("Screen paint is already active");
        GuideGraphics graphics = GuideGraphics.wrap();
        paintGraphics = graphics;
        try { graphics.paint(() -> paintGuideScreen(graphics, mouseX, mouseY, partialTicks)); }
        finally { paintGraphics = null; }
    }
    protected abstract void paintGuideScreen(GuideGraphics graphics, int mouseX, int mouseY, float delta);
    protected void paintGuideBackground(GuideGraphics graphics, int mouseX, int mouseY, float delta) {
        renderNativeGuideBackground();
    }
    protected final void renderGuideWidgets(GuideGraphics graphics, int mouseX, int mouseY, float delta) {
        renderNativeGuideWidgets(mouseX, mouseY, delta);
        for (GuideNativeEditBox field : textFields) field.render(mouseX, mouseY);
    }
    protected final void guideRebuildWidgets() {
        clearGuideFocus();
        buttonList.clear();
        textFields.clear();
        initGuideScreen();
    }
    protected void repositionGuideElements() { guideRebuildWidgets(); }
    @Override public final void onResize(Minecraft client, int width, int height) {
        minecraft = client;
        font = client.fontRenderer;
        resizeGuide(width, height);
    }
    protected void resizeGuide(int width, int height) { resizeGuideWidgets(width, height); }
    protected final void resizeGuideWidgets(int width, int height) {
        setGuiSize(width, height);
        repositionGuideElements();
    }
    protected void guideInitialFocus() {}
    protected final void tickGuideWidgets() {
        for (GuideNativeEditBox field : textFields) field.tick();
        for (GuiButton button : buttonList) if (button instanceof GuidePrimitiveMultilineEditor primitive) primitive.tick();
    }
    @Override protected final void tickGuideScreen() { tick(); }
    public void tick() { super.tickGuideScreen(); }
    public void onClose() { mc.displayGuiScreen(null); }
    public boolean isPauseScreen() { return true; }
    @Override public final boolean doesGuiPauseGame() { return isPauseScreen(); }
    public boolean isInGameUi() { return false; }
    public final void clearGuideWidgetFocus() { clearGuideFocus(); }
    protected final void guideDragging(boolean dragging) { setDragging(dragging); }
    public final boolean guideWidgetRegistered(GuideWidget widget) { return guideWidgetChildren().contains(widget); }
}
