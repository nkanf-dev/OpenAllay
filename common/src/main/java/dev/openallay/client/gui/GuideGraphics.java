package dev.openallay.client.gui;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * One feature-facing paint API for every selected Minecraft graphics family.
 * Composite operations belong here; the selected base binds actual native canvas calls.
 * Native hooks use the inherited typed wrap factory, not native-typed constructors here.
 */
public final class GuideGraphics extends GuideNativeGraphics {
    GuideGraphics(GuideNativeGraphics binding) { super(binding); }

    public void paint(Runnable paint) { nativePaint(paint); }
    public int guiWidth() { return nativeGuiWidth(); }
    public int guiHeight() { return nativeGuiHeight(); }
    public void pushPose() { nativePushPose(); }
    public void popPose() { nativePopPose(); }
    public void translatePose(float x, float y) { nativeTranslatePose(x, y); }
    public void scalePose(float x, float y) { nativeScalePose(x, y); }
    public void requestResizeCursor() { nativeRequestResizeCursor(); }

    public void enableScissor(int x0, int y0, int x1, int y1) { nativeEnableScissor(x0, y0, x1, y1); }
    public void disableScissor() { nativeDisableScissor(); }
    public void fill(int x0, int y0, int x1, int y1, int color) { nativeFill(x0, y0, x1, y1, color); }

    /** A feature composition adds no native operation or target override. */
    public void outline(int x, int y, int width, int height, int color) {
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y + 1, x + 1, y + height - 1, color);
        fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public void text(Font font, String text, int x, int y, int color) {
        nativeText(font, text, x, y, color);
    }
    public void text(Font font, String text, int x, int y, int color, boolean shadow) {
        nativeText(font, text, x, y, color, shadow);
    }
    public void text(Font font, Component text, int x, int y, int color) {
        nativeText(font, text, x, y, color);
    }
    public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
        nativeText(font, text, x, y, color, shadow);
    }
    public void text(Font font, FormattedCharSequence text, int x, int y, int color) {
        nativeText(font, text, x, y, color);
    }
    public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        nativeText(font, text, x, y, color, shadow);
    }
    public void item(ItemStack stack, int x, int y) { nativeItem(stack, x, y); }
    public void item(ItemStack stack, int x, int y, int seed) { nativeItem(stack, x, y, seed); }
    public void itemDecorations(Font font, ItemStack stack, int x, int y) {
        nativeItemDecorations(font, stack, x, y);
    }
    public void itemDecorations(Font font, ItemStack stack, int x, int y, String count) {
        nativeItemDecorations(font, stack, x, y, count);
    }

    public void setTooltipForNextFrame(Component text, int x, int y) { nativeTooltip(text, x, y); }
    public void setTooltipForNextFrame(List<FormattedCharSequence> lines, int x, int y) {
        nativeTooltip(lines, x, y);
    }
    public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
        nativeTooltip(font, text, x, y);
    }
    public void setTooltipForNextFrame(Font font, ItemStack stack, int x, int y) {
        nativeTooltip(font, stack, x, y);
    }
    public void setTooltipForNextFrame(Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        nativeTooltip(font, lines, x, y);
    }
    public void setTooltipForNextFrame(
            Font font, List<FormattedCharSequence> lines, GuideTooltipPlacement positioner,
            int x, int y, boolean replaceExisting) {
        nativeTooltip(font, lines, positioner, x, y, replaceExisting);
    }

    /** Normalized texture coordinates; x1 and y1 are destination corners, not sizes. */
    public void blitTexture(
            String texture, int x0, int y0, int x1, int y1,
            float u0, float u1, float v0, float v1) {
        nativeBlit(texture, x0, y0, x1, y1, u0, u1, v0, v1);
    }
    /** GUI texture with explicit pixel source rectangle and destination size. */
    public void blitTexture(
            String texture, int x, int y, float u, float v, int width, int height,
            int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        nativeBlit(texture, x, y, u, v, width, height,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
    }
}
