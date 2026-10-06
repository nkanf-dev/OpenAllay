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
    protected GuideNativeScreen(ITextComponent title) { this.title = Objects.requireNonNull(title, "title"); }
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
    }
    protected final void guideRebuildWidgets() {
        clearGuideFocus();
        buttonList.clear();
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
    protected final void tickGuideWidgets() {}
    @Override protected final void tickGuideScreen() { tick(); }
    public void tick() { super.tickGuideScreen(); }
    public void onClose() { mc.displayGuiScreen(null); }
    public boolean isPauseScreen() { return true; }
    @Override public final boolean doesGuiPauseGame() { return isPauseScreen(); }
    public boolean isInGameUi() { return false; }
}
