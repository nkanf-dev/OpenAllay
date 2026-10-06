package dev.openallay.client.gui;


import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Typed PoseStack/immediate-canvas routes; feature operations live in GuideGraphics.
 * A complete paint scope defers tooltips until shared pose/scissor cleanup has returned.
 * This client binding is not part of the native-neutral engine or Extension API.
 */
public class GuideNativeGraphics {
    private static final ThreadLocal<PaintScope> CURRENT_PAINT = new ThreadLocal<>();
    private final GuiGraphics graphics;

    private static final class PaintScope {
        private final GuiGraphics graphics;
        private Runnable tooltip;
        private PaintScope(GuiGraphics graphics) { this.graphics = graphics; }
    }

    private GuideNativeGraphics(GuiGraphics graphics) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
    }

    protected GuideNativeGraphics(GuideNativeGraphics binding) {
        this.graphics = Objects.requireNonNull(binding, "binding").graphics;
    }

    /**
     * Run one complete paint and draw its selected tooltip only after a successful return.
     * Nested wrappers for the same native canvas share the outer scope and never drain early.
     */
    protected final void nativePaint(Runnable paint) {
        Objects.requireNonNull(paint, "paint");
        PaintScope previous = CURRENT_PAINT.get();
        if (previous != null && previous.graphics == graphics) {
            paint.run();
            return;
        }
        PaintScope scope = new PaintScope(graphics);
        CURRENT_PAINT.set(scope);
        try {
            paint.run();
            if (scope.tooltip != null) {
                graphics.flush();
                scope.tooltip.run();
                graphics.flush();
            }
        } finally {
            scope.tooltip = null;
            if (previous == null) CURRENT_PAINT.remove();
            else CURRENT_PAINT.set(previous);
        }
    }

    private void deferTooltip(Runnable tooltip, boolean replaceExisting) {
        PaintScope scope = CURRENT_PAINT.get();
        if (scope == null || scope.graphics != graphics) {
            throw new IllegalStateException("Tooltips require a complete GuideGraphics.paint scope");
        }
        if (scope.tooltip == null || replaceExisting) scope.tooltip = tooltip;
    }

    void clearTooltipForNextFrame() {
        PaintScope scope = CURRENT_PAINT.get();
        if (scope != null && scope.graphics == graphics) scope.tooltip = null;
    }

    public static GuideGraphics wrap(GuiGraphics graphics) {
        return new GuideGraphics(new GuideNativeGraphics(graphics));
    }

    /** Direct native canvas for optional integrations compiled for this graphics family. */
    public final GuiGraphics nativeGraphics() {
        return graphics;
    }

    protected final int nativeGuiWidth() { return graphics.guiWidth(); }
    protected final int nativeGuiHeight() { return graphics.guiHeight(); }
    protected final void nativePushPose() { graphics.pose().pushPose(); }
    protected final void nativePopPose() { graphics.pose().popPose(); }
    protected final void nativeTranslatePose(float x, float y) { graphics.pose().translate(x, y, 0.0F); }
    protected final void nativeScalePose(float x, float y) { graphics.pose().scale(x, y, 1.0F); }
    protected final void nativeRequestResizeCursor() { GuideLegacyCursor.requestResize(); }

    protected final void nativeEnableScissor(int x0, int y0, int x1, int y1) {
        graphics.enableScissor(x0, y0, x1, y1);
    }

    protected final void nativeDisableScissor() { graphics.disableScissor(); }

    protected final void nativeFill(int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y1, color);
    }

    protected final void nativeText(Font font, String text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color);
    }

    protected final void nativeText(Font font, String text, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, text, x, y, color, shadow);
    }

    protected final void nativeText(Font font, Component text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color);
    }

    protected final void nativeText(Font font, Component text, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, text, x, y, color, shadow);
    }

    protected final void nativeText(Font font, GuideTextLine text, int x, int y, int color) {
        graphics.drawString(font, GuideNativeFont.nativeLine(text), x, y, color);
    }

    protected final void nativeText(Font font, GuideTextLine text, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, GuideNativeFont.nativeLine(text), x, y, color, shadow);
    }

    protected final void nativeItem(ItemStack stack, int x, int y) {
        graphics.renderItem(stack, x, y);
    }


    protected final void nativeItemDecorations(Font font, ItemStack stack, int x, int y) {
        graphics.renderItemDecorations(font, stack, x, y);
    }

    protected final void nativeItemDecorations(Font font, ItemStack stack, int x, int y, String count) {
        graphics.renderItemDecorations(font, stack, x, y, count);
    }

    protected final void nativeTooltip(Component text, int x, int y) {
        nativeTooltip(Minecraft.getInstance().font, text, x, y);
    }

    protected final void nativeTooltip(List<GuideTextLine> lines, int x, int y) {
        nativeTooltip(Minecraft.getInstance().font, lines, x, y);
    }

    protected final void nativeTooltip(Font font, Component text, int x, int y) {
        nativeTooltip(font, List.of(GuideNativeFont.visual(text)), x, y);
    }

    protected final void nativeTooltip(Font font, ItemStack stack, int x, int y) {
        List<Component> lines = List.copyOf(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
        var image = stack.getTooltipImage();
        if (!lines.isEmpty() || image.isPresent()) {
            deferTooltip(() -> GuideImmediateGraphicsPrimitives.itemTooltip(graphics, font, stack, lines, image, x, y), false);
        }
    }

    protected final void nativeTooltip(Font font, List<? extends GuideTextLine> lines, int x, int y) {
        List<FormattedCharSequence> captured = GuideNativeFont.nativeLines(lines);
        if (!captured.isEmpty()) {
            deferTooltip(() -> graphics.renderTooltip(font, captured, x, y), false);
        }
    }

    protected final void nativeTooltip(
            Font font, List<GuideTextLine> lines, GuideTooltipPlacement positioner,
            int x, int y, boolean replaceExisting) {
        List<FormattedCharSequence> captured = GuideNativeFont.nativeLines(lines);
        if (!captured.isEmpty()) {
            deferTooltip(() -> graphics.renderTooltip(font, captured, net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE, x, y), replaceExisting);
        }
    }

    /** Normalized texture coordinates; x1 and y1 are destination corners, not sizes. */
    protected final void nativeBlit(
            String texture, int x0, int y0, int x1, int y1,
            float u0, float u1, float v0, float v1) {
        GuideImmediateGraphicsPrimitives.blit(graphics, texture, x0, y0, x1, y1, u0, u1, v0, v1);
    }

    /** GUI texture with explicit pixel source rectangle and destination size. */
    protected final void nativeBlit(
            String texture, int x, int y, float u, float v, int width, int height,
            int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        GuideImmediateGraphicsPrimitives.blit(graphics, texture, x, y, u, v, width, height,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
    }
    final void nativeTooltipPositioned(Font font, List<FormattedCharSequence> lines,
            net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner positioner,
            int x, int y, boolean replaceExisting) {
        List<FormattedCharSequence> captured = List.copyOf(lines);
        if (!captured.isEmpty()) {
            deferTooltip(() -> graphics.renderTooltip(font, captured, positioner, x, y), replaceExisting);
        }
    }
}
