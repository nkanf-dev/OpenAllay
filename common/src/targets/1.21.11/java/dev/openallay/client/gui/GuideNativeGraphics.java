package dev.openallay.client.gui;

import dev.openallay.platform.minecraft.MinecraftResourceIds;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Typed callback-canvas routes. Feature API and composite operations live in GuideGraphics.
 * Cursor ABI differences have their own small selected binding.
 * This client binding is not part of the native-neutral engine or Extension API.
 */
public class GuideNativeGraphics {
    private final GuiGraphics graphics;

    private GuideNativeGraphics(GuiGraphics graphics) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
    }

    protected GuideNativeGraphics(GuideNativeGraphics binding) {
        this.graphics = Objects.requireNonNull(binding, "binding").graphics;
    }

    /** Run one complete paint; the native canvas owns deferred tooltip rendering. */
    protected final void nativePaint(Runnable paint) {
        Objects.requireNonNull(paint, "paint").run();
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
    protected final void nativePushPose() { graphics.pose().pushMatrix(); }
    protected final void nativePopPose() { graphics.pose().popMatrix(); }
    protected final void nativeTranslatePose(float x, float y) { graphics.pose().translate(x, y); }
    protected final void nativeScalePose(float x, float y) { graphics.pose().scale(x, y); }
    protected final void nativeRequestResizeCursor() { GuideNativeCursor.requestResize(graphics); }

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
