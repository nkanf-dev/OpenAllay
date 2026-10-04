package dev.openallay.client.gui;

import dev.openallay.platform.minecraft.MinecraftResourceIds;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * OpenAllay's concrete paint operations for the callback graphics family.
 * Native hooks wrap their canvas once; shared paint code uses this mod-owned type.
 * This client binding is not part of the native-neutral engine or Extension API.
 */
public final class GuideGraphics {
    private final GuiGraphics graphics;

    public GuideGraphics(GuiGraphics graphics) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
    }

    public static GuideGraphics wrap(GuiGraphics graphics) {
        return new GuideGraphics(graphics);
    }

    /** Direct native canvas for optional integrations compiled for this graphics family. */
    public GuiGraphics nativeGraphics() {
        return graphics;
    }

    public int guiWidth() { return graphics.guiWidth(); }
    public int guiHeight() { return graphics.guiHeight(); }
    public void pushPose() { graphics.pose().pushMatrix(); }
    public void popPose() { graphics.pose().popMatrix(); }
    public void translatePose(float x, float y) { graphics.pose().translate(x, y); }
    public void scalePose(float x, float y) { graphics.pose().scale(x, y); }
    public void requestResizeCursor() { graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL); }

    public void enableScissor(int x0, int y0, int x1, int y1) {
        graphics.enableScissor(x0, y0, x1, y1);
    }

    public void disableScissor() { graphics.disableScissor(); }

    public void fill(int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y1, color);
    }

    public void outline(int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public void text(Font font, String text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color);
    }

    public void text(Font font, String text, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, text, x, y, color, shadow);
    }

    public void text(Font font, Component text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color);
    }

    public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, text, x, y, color, shadow);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        graphics.drawString(font, text, x, y, color, shadow);
    }

    public void item(ItemStack stack, int x, int y) {
        graphics.renderItem(stack, x, y);
    }

    public void item(ItemStack stack, int x, int y, int seed) {
        graphics.renderItem(stack, x, y, seed);
    }

    public void itemDecorations(Font font, ItemStack stack, int x, int y) {
        graphics.renderItemDecorations(font, stack, x, y);
    }

    public void itemDecorations(Font font, ItemStack stack, int x, int y, String count) {
        graphics.renderItemDecorations(font, stack, x, y, count);
    }

    public void setTooltipForNextFrame(Component text, int x, int y) {
        graphics.setTooltipForNextFrame(text, x, y);
    }

    public void setTooltipForNextFrame(List<FormattedCharSequence> lines, int x, int y) {
        graphics.setTooltipForNextFrame(lines, x, y);
    }

    public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
        graphics.setTooltipForNextFrame(font, text, x, y);
    }

    public void setTooltipForNextFrame(Font font, ItemStack stack, int x, int y) {
        graphics.setTooltipForNextFrame(font, stack, x, y);
    }

    public void setTooltipForNextFrame(Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        graphics.setTooltipForNextFrame(font, lines, x, y);
    }

    public void setTooltipForNextFrame(
            Font font, List<FormattedCharSequence> lines, ClientTooltipPositioner positioner,
            int x, int y, boolean replaceExisting) {
        graphics.setTooltipForNextFrame(font, lines, positioner, x, y, replaceExisting);
    }

    /** Normalized texture coordinates; x1 and y1 are destination corners, not sizes. */
    public void blitTexture(
            String texture, int x0, int y0, int x1, int y1,
            float u0, float u1, float v0, float v1) {
        graphics.blit(MinecraftResourceIds.parse(texture), x0, y0, x1, y1, u0, u1, v0, v1);
    }

    /** GUI texture with explicit pixel source rectangle and destination size. */
    public void blitTexture(
            String texture, int x, int y, float u, float v, int width, int height,
            int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, MinecraftResourceIds.parse(texture), x, y, u, v, width, height,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
    }
}
