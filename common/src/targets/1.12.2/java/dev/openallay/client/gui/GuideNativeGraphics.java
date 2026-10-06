package dev.openallay.client.gui;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

/** Actual 1.12.2 immediate canvas binding. Feature paint operations remain in GuideGraphics. */
public class GuideNativeGraphics {
    private static final ThreadLocal<PaintScope> CURRENT_PAINT = new ThreadLocal<>();
    private static final class PaintScope { private Runnable tooltip; }
    private GuideNativeGraphics() {}
    protected GuideNativeGraphics(GuideNativeGraphics binding) { Objects.requireNonNull(binding, "binding"); }
    /** No fabricated PoseStack or window object: native rendering uses the current GL context. */
    public static GuideGraphics wrap() { return new GuideGraphics(new GuideNativeGraphics()); }
    public static boolean isGuidePaintActive() { return CURRENT_PAINT.get() != null; }
    protected final void nativePaint(Runnable paint) {
        Objects.requireNonNull(paint, "paint");
        if (CURRENT_PAINT.get() != null) { paint.run(); return; }
        PaintScope scope = new PaintScope();
        CURRENT_PAINT.set(scope);
        try {
            paint.run();
            if (scope.tooltip != null) scope.tooltip.run();
        } finally {
            scope.tooltip = null;
            CURRENT_PAINT.remove();
        }
    }
    private void deferTooltip(Runnable tooltip, boolean replaceExisting) {
        PaintScope scope = CURRENT_PAINT.get();
        if (scope == null) throw new IllegalStateException("Tooltips require a complete GuideGraphics.paint scope");
        if (scope.tooltip == null || replaceExisting) scope.tooltip = tooltip;
    }
    void clearTooltipForNextFrame() {
        PaintScope scope = CURRENT_PAINT.get();
        if (scope != null) scope.tooltip = null;
    }
    protected final int nativeGuiWidth() { return GuideImmediateGraphicsPrimitives.guiWidth(); }
    protected final int nativeGuiHeight() { return GuideImmediateGraphicsPrimitives.guiHeight(); }
    protected final void nativePushPose() { GuideImmediateGraphicsPrimitives.pushPose(); }
    protected final void nativePopPose() { GuideImmediateGraphicsPrimitives.popPose(); }
    protected final void nativeTranslatePose(float x, float y) { GuideImmediateGraphicsPrimitives.translatePose(x, y); }
    protected final void nativeScalePose(float x, float y) { GuideImmediateGraphicsPrimitives.scalePose(x, y); }
    protected final boolean nativeResizeCursorAvailable() { return false; }
    protected final void nativeRequestResizeCursor() {
        throw new UnsupportedOperationException("LWJGL 2 has no stock resize-all cursor ABI");
    }
    protected final void nativeEnableScissor(int x0, int y0, int x1, int y1) { GuideImmediateGraphicsPrimitives.enableScissor(x0, y0, x1, y1); }
    protected final void nativeDisableScissor() { GuideImmediateGraphicsPrimitives.disableScissor(); }
    protected final void nativeFill(int x0, int y0, int x1, int y1, int color) { GuideImmediateGraphicsPrimitives.fill(x0, y0, x1, y1, color); }
    protected final void nativeText(FontRenderer font, String text, int x, int y, int color) { nativeText(font, text, x, y, color, true); }
    protected final void nativeText(FontRenderer font, String text, int x, int y, int color, boolean shadow) {
        GuideImmediateGraphicsPrimitives.text(font, text, x, y, color, shadow);
    }
    protected final void nativeText(FontRenderer font, ITextComponent text, int x, int y, int color) { nativeText(font, text, x, y, color, true); }
    protected final void nativeText(FontRenderer font, ITextComponent text, int x, int y, int color, boolean shadow) {
        nativeText(font, text.getFormattedText(), x, y, color, shadow);
    }
    protected final void nativeText(FontRenderer font, GuideTextLine text, int x, int y, int color) { nativeText(font, text, x, y, color, true); }
    protected final void nativeText(FontRenderer font, GuideTextLine text, int x, int y, int color, boolean shadow) {
        nativeText(font, GuideNativeFont.nativeLine(text), x, y, color, shadow);
    }
    protected final void nativeItem(ItemStack stack, int x, int y) {
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
    }
    protected final void nativeItemDecorations(FontRenderer font, ItemStack stack, int x, int y) { nativeItemDecorations(font, stack, x, y, null); }
    protected final void nativeItemDecorations(FontRenderer font, ItemStack stack, int x, int y, String count) {
        Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(font, stack, x, y, count);
    }
    protected final void nativeTooltip(ITextComponent text, int x, int y) { nativeTooltip(Minecraft.getMinecraft().fontRenderer, text, x, y); }
    protected final void nativeTooltip(List<GuideTextLine> lines, int x, int y) { nativeTooltip(Minecraft.getMinecraft().fontRenderer, lines, x, y); }
    protected final void nativeTooltip(FontRenderer font, ITextComponent text, int x, int y) {
        nativeTooltip(font, List.of(GuideNativeFont.visual(text)), x, y);
    }
    protected final void nativeTooltip(FontRenderer font, ItemStack stack, int x, int y) {
        // Native protected renderToolTip preserves item font selection and Forge pre/post hooks.
        TooltipScreen screen = tooltipScreen();
        deferTooltip(() -> screen.item(stack, x, y), false);
    }
    protected final void nativeTooltip(FontRenderer font, List<? extends GuideTextLine> lines, int x, int y) {
        nativeTooltip(font, List.copyOf(lines), GuideTooltipPlacement.DEFAULT, x, y, false);
    }
    protected final void nativeTooltip(FontRenderer font, List<GuideTextLine> lines,
            GuideTooltipPlacement positioner, int x, int y, boolean replaceExisting) {
        Objects.requireNonNull(positioner, "positioner");
        List<String> captured = GuideNativeFont.nativeLines(lines);
        if (!captured.isEmpty()) {
            TooltipScreen screen = tooltipScreen();
            deferTooltip(() -> screen.lines(font, captured, x, y), replaceExisting);
        }
    }
    private static TooltipScreen tooltipScreen() {
        // Do not call setWorldAndResolution: tooltip projection must not emit InitGui events or open a screen.
        TooltipScreen screen = new TooltipScreen();
        screen.bind(Minecraft.getMinecraft(), GuideImmediateGraphicsPrimitives.guiWidth(), GuideImmediateGraphicsPrimitives.guiHeight());
        return screen;
    }
    /** Protected native tooltip access only; all tooltip layout/render algorithms remain in GuiScreen. */
    private static final class TooltipScreen extends GuiScreen {
        private void bind(Minecraft client, int width, int height) {
            this.mc = client;
            this.width = width;
            this.height = height;
            this.fontRenderer = client.fontRenderer;
            this.itemRender = client.getRenderItem();
        }
        private void item(ItemStack stack, int x, int y) { super.renderToolTip(stack, x, y); }
        private void lines(FontRenderer font, List<String> lines, int x, int y) { super.drawHoveringText(lines, x, y, font); }
    }
    protected final void nativeBlit(String texture, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1) {
        GuideImmediateGraphicsPrimitives.blit(new ResourceLocation(texture), x0, y0, x1, y1, u0, u1, v0, v1);
    }
    protected final void nativeBlit(String texture, int x, int y, float u, float v, int width, int height,
            int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        if (textureWidth <= 0 || textureHeight <= 0) throw new IllegalArgumentException("Texture dimensions must be positive");
        nativeBlit(texture, x, y, x + width, y + height, u / textureWidth, (u + sourceWidth) / textureWidth,
                v / textureHeight, (v + sourceHeight) / textureHeight);
    }
}
