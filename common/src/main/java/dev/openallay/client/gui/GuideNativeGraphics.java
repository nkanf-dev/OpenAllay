package dev.openallay.client.gui;

import dev.openallay.platform.minecraft.MinecraftResourceIds;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Typed extractor-canvas routes. Feature API and composite operations live in GuideGraphics.
 * The public native canvas return type is retained for optional viewer integrations.
 * This client binding is not part of the native-neutral engine or Extension API.
 */
public class GuideNativeGraphics {
    private static final ViewportPaint<GuiGraphicsExtractor> VIEWPORT_PAINT = new ViewportPaint<>();
    private final GuiGraphicsExtractor graphics;

    private GuideNativeGraphics(GuiGraphicsExtractor graphics) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
    }

    protected GuideNativeGraphics(GuideNativeGraphics binding) {
        this.graphics = Objects.requireNonNull(binding, "binding").graphics;
    }

    /**
     * Give native scrolling text a viewport parent, including native widget/viewer extraction.
     * Install it in screen coordinates, then restore the caller's exact pose before painting.
     * Empty intersections remain native no-draw; never widen or remove a caller's clip.
     * Callbacks must balance their own pose/scissor pushes, including on exceptional return.
     */
    protected final void nativePaint(Runnable paint) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        VIEWPORT_PAINT.paint(graphics, width, height, () -> {
            graphics.pose().pushMatrix();
            try {
                graphics.pose().identity();
                graphics.enableScissor(0, 0, width, height);
            } finally {
                graphics.pose().popMatrix();
            }
        }, graphics::disableScissor, paint);
    }

    /** Canvas identity and balanced scope lifetime only; geometry stays in native scissors. */
    static final class ViewportPaint<T> {
        private final ThreadLocal<T> current = new ThreadLocal<>();

        void paint(T canvas, int width, int height, Runnable enter, Runnable exit, Runnable paint) {
            Objects.requireNonNull(paint, "paint");
            if (width <= 0 || height <= 0) return;
            T previous = current.get();
            if (previous == canvas) {
                paint.run();
                return;
            }
            enter.run();
            current.set(canvas);
            try {
                paint.run();
            } finally {
                try {
                    exit.run();
                } finally {
                    if (previous == null) current.remove();
                    else current.set(previous);
                }
            }
        }
    }

    public static GuideGraphics wrap(GuiGraphicsExtractor graphics) {
        return new GuideGraphics(new GuideNativeGraphics(graphics));
    }

    /** Direct native canvas for optional integrations compiled for this graphics family. */
    public final GuiGraphicsExtractor nativeGraphics() {
        return graphics;
    }

    protected final int nativeGuiWidth() { return graphics.guiWidth(); }
    protected final int nativeGuiHeight() { return graphics.guiHeight(); }
    protected final void nativePushPose() { graphics.pose().pushMatrix(); }
    protected final void nativePopPose() { graphics.pose().popMatrix(); }
    protected final void nativeTranslatePose(float x, float y) { graphics.pose().translate(x, y); }
    protected final void nativeScalePose(float x, float y) { graphics.pose().scale(x, y); }
    protected final boolean nativeResizeCursorAvailable() { return true; }
    protected final void nativeRequestResizeCursor() { graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL); }

    protected final void nativeEnableScissor(int x0, int y0, int x1, int y1) {
        graphics.enableScissor(x0, y0, x1, y1);
    }

    protected final void nativeDisableScissor() { graphics.disableScissor(); }

    protected final void nativeFill(int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y1, color);
    }

    protected final void nativeText(Font font, String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color);
    }

    protected final void nativeText(Font font, String text, int x, int y, int color, boolean shadow) {
        graphics.text(font, text, x, y, color, shadow);
    }

    protected final void nativeText(Font font, Component text, int x, int y, int color) {
        graphics.text(font, text, x, y, color);
    }

    protected final void nativeText(Font font, Component text, int x, int y, int color, boolean shadow) {
        graphics.text(font, text, x, y, color, shadow);
    }

    protected final void nativeText(Font font, GuideTextLine text, int x, int y, int color) {
        graphics.text(font, GuideNativeFont.nativeLine(text), x, y, color);
    }

    protected final void nativeText(Font font, GuideTextLine text, int x, int y, int color, boolean shadow) {
        graphics.text(font, GuideNativeFont.nativeLine(text), x, y, color, shadow);
    }

    protected final void nativeItem(ItemStack stack, int x, int y) {
        graphics.item(stack, x, y);
    }


    protected final void nativeItemDecorations(Font font, ItemStack stack, int x, int y) {
        graphics.itemDecorations(font, stack, x, y);
    }

    protected final void nativeItemDecorations(Font font, ItemStack stack, int x, int y, String count) {
        graphics.itemDecorations(font, stack, x, y, count);
    }

    protected final void nativeTooltip(Component text, int x, int y) {
        graphics.setTooltipForNextFrame(text, x, y);
    }

    protected final void nativeTooltip(List<GuideTextLine> lines, int x, int y) {
        graphics.setTooltipForNextFrame(GuideNativeFont.nativeLines(lines), x, y);
    }

    protected final void nativeTooltip(Font font, Component text, int x, int y) {
        graphics.setTooltipForNextFrame(font, text, x, y);
    }

    protected final void nativeTooltip(Font font, ItemStack stack, int x, int y) {
        graphics.setTooltipForNextFrame(font, stack, x, y);
    }

    protected final void nativeTooltip(Font font, List<? extends GuideTextLine> lines, int x, int y) {
        graphics.setTooltipForNextFrame(font, GuideNativeFont.nativeLines(lines), x, y);
    }

    protected final void nativeTooltip(
            Font font, List<GuideTextLine> lines, GuideTooltipPlacement positioner,
            int x, int y, boolean replaceExisting) {
        graphics.setTooltipForNextFrame(font, GuideNativeFont.nativeLines(lines), net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner.INSTANCE, x, y, replaceExisting);
    }

    /** Normalized texture coordinates; x1 and y1 are destination corners, not sizes. */
    protected final void nativeBlit(
            String texture, int x0, int y0, int x1, int y1,
            float u0, float u1, float v0, float v1) {
        graphics.blit(MinecraftResourceIds.parse(texture), x0, y0, x1, y1, u0, u1, v0, v1);
    }

    /** GUI texture with explicit pixel source rectangle and destination size. */
    protected final void nativeBlit(
            String texture, int x, int y, float u, float v, int width, int height,
            int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, MinecraftResourceIds.parse(texture), x, y, u, v, width, height,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
    }
    final void nativeTooltipPositioned(Font font, List<FormattedCharSequence> lines,
            net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner positioner,
            int x, int y, boolean replaceExisting) {
        graphics.setTooltipForNextFrame(font, lines, positioner, x, y, replaceExisting);
    }
}
