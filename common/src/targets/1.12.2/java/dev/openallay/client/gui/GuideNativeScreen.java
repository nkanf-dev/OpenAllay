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
        final class $oaPattern0_Holder { dev.openallay.client.gui.GuideWidget value; GuideNativeButton bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = widget) instanceof dev.openallay.client.gui.GuideNativeButton && (($oaPattern0_holder.bound = (GuideNativeButton) $oaPattern0_holder.value) != null))) addGuideWidget($oaPattern0_holder.bound);
        else {
final class $oaPattern1_Holder { dev.openallay.client.gui.GuideWidget value; GuideNativeEditBox bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = widget) instanceof dev.openallay.client.gui.GuideNativeEditBox && (($oaPattern1_holder.bound = (GuideNativeEditBox) $oaPattern1_holder.value) != null))) addGuideWidget($oaPattern1_holder.bound);
        else {
final class $oaPattern2_Holder { dev.openallay.client.gui.GuideWidget value; GuideNativeWidget bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = widget) instanceof dev.openallay.client.gui.GuideNativeWidget && (($oaPattern2_holder.bound = (GuideNativeWidget) $oaPattern2_holder.value) != null))) super.addButton($oaPattern2_holder.bound);
        else throw new IllegalArgumentException("Widget has no selected native registration leaf");
}
}
        return widget;
    }
    public final GuideWidgetInput getFocused() { return getGuideFocused(); }
    public final java.util.List<GuideWidget> children() { return guideWidgetChildren(); }
    public final void setDragging(boolean dragging) {
        if (!dragging && getGuideFocused() != null) getGuideFocused().guideMouseReleased(GuideNativeInput.mouseEvent(0, 0, 0, 0));
    }
    public final void setFocused(GuideWidget widget) {
        final class $oaPattern3_Holder { dev.openallay.client.gui.GuideWidget value; GuideWidgetInput bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if (!((($oaPattern3_holder.value = widget) instanceof dev.openallay.client.gui.GuideWidgetInput && (($oaPattern3_holder.bound = (GuideWidgetInput) $oaPattern3_holder.value) != null)))) throw new IllegalArgumentException("Widget has no native input owner");
        setGuideFocused($oaPattern3_holder.bound);
    }
    protected final void setInitialFocus(GuideWidget widget) { setFocused(widget); }
    public final boolean guideWidgetFocused(GuideWidget widget) { return getGuideFocused() == widget; }
    public final GuideWidget getGuideWidgetFocused() {
        final class $oaPattern4_Holder { dev.openallay.client.gui.GuideWidgetInput value; GuideWidget bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
return (($oaPattern4_holder.value = getGuideFocused()) instanceof dev.openallay.client.gui.GuideWidget && (($oaPattern4_holder.bound = (GuideWidget) $oaPattern4_holder.value) != null)) ? $oaPattern4_holder.bound : null;
    }
    public final java.util.List<GuideWidget> guideWidgetChildren() {
        java.util.List<GuideWidget> children = new java.util.ArrayList<>();
        for (GuiButton button : buttonList) {
final class $oaPattern5_Holder { net.minecraft.client.gui.GuiButton value; GuideWidget bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = button) instanceof dev.openallay.client.gui.GuideWidget && (($oaPattern5_holder.bound = (GuideWidget) $oaPattern5_holder.value) != null))) children.add($oaPattern5_holder.bound);
}
        children.addAll(textFields);
        return dev.openallay.util.Java8Collections.listCopyOf(children);
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
            final class $oaPattern6_Holder { net.minecraft.client.gui.GuiButton value; GuideNativeWidget bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = button) instanceof dev.openallay.client.gui.GuideNativeWidget && (($oaPattern6_holder.bound = (GuideNativeWidget) $oaPattern6_holder.value) != null)) && $oaPattern6_holder.bound.guideMouseClicked(event, doubleClick)) {
                setGuideFocused($oaPattern6_holder.bound);
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
            final class $oaPattern7_Holder { net.minecraft.client.gui.GuiButton value; GuideNativeWidget bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = button) instanceof dev.openallay.client.gui.GuideNativeWidget && (($oaPattern7_holder.bound = (GuideNativeWidget) $oaPattern7_holder.value) != null)) && $oaPattern7_holder.bound.guideMouseScrolled(x, y, vertical)) return true;
        }
        return false;
    }
    @Override protected void actionPerformed(GuiButton button) {
        final class $oaPattern8_Holder { net.minecraft.client.gui.GuiButton value; GuideNativeButton bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = button) instanceof dev.openallay.client.gui.GuideNativeButton && (($oaPattern8_holder.bound = (GuideNativeButton) $oaPattern8_holder.value) != null))) $oaPattern8_holder.bound.onPress();
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
        for (GuiButton button : buttonList) {
final class $oaPattern9_Holder { net.minecraft.client.gui.GuiButton value; GuidePrimitiveMultilineEditor bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = button) instanceof dev.openallay.client.gui.GuidePrimitiveMultilineEditor && (($oaPattern9_holder.bound = (GuidePrimitiveMultilineEditor) $oaPattern9_holder.value) != null))) $oaPattern9_holder.bound.tick();
}
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
