package dev.openallay.client.gui;

import dev.openallay.platform.minecraft.MinecraftResourceIds;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * OpenAllay's concrete paint operations for the PoseStack/immediate graphics family.
 * A complete paint scope defers tooltips until shared pose/scissor cleanup has returned.
 * This client binding is not part of the native-neutral engine or Extension API.
 */
public final class GuideGraphics {
    private static final ThreadLocal<PaintScope> CURRENT_PAINT = new ThreadLocal<>();
    private final GuiGraphics graphics;

    private static final class PaintScope {
        private final GuiGraphics graphics;
        private Runnable tooltip;
        private PaintScope(GuiGraphics graphics) { this.graphics = graphics; }
    }

    public GuideGraphics(GuiGraphics graphics) {
        this.graphics = Objects.requireNonNull(graphics, "graphics");
    }

    /**
     * Run one complete paint and draw its selected tooltip only after a successful return.
     * Nested wrappers for the same native canvas share the outer scope and never drain early.
     */
    public void paint(Runnable paint) {
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
        return new GuideGraphics(graphics);
    }

    /** Direct native canvas for optional integrations compiled for this graphics family. */
    public GuiGraphics nativeGraphics() {
        return graphics;
    }

    public int guiWidth() { return graphics.guiWidth(); }
    public int guiHeight() { return graphics.guiHeight(); }
    public void pushPose() { graphics.pose().pushPose(); }
    public void popPose() { graphics.pose().popPose(); }
    public void translatePose(float x, float y) { graphics.pose().translate(x, y, 0.0F); }
    public void scalePose(float x, float y) { graphics.pose().scale(x, y, 1.0F); }
    public void requestResizeCursor() { GuideLegacyCursor.requestResize(); }

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
        setTooltipForNextFrame(Minecraft.getInstance().font, text, x, y);
    }

    public void setTooltipForNextFrame(List<FormattedCharSequence> lines, int x, int y) {
        setTooltipForNextFrame(Minecraft.getInstance().font, lines, x, y);
    }

    public void setTooltipForNextFrame(Font font, Component text, int x, int y) {
        setTooltipForNextFrame(font, List.of(text.getVisualOrderText()), x, y);
    }

    public void setTooltipForNextFrame(Font font, ItemStack stack, int x, int y) {
        List<Component> lines = List.copyOf(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
        var image = stack.getTooltipImage();
        var style = stack.get(DataComponents.TOOLTIP_STYLE);
        if (!lines.isEmpty() || image.isPresent()) {
            deferTooltip(() -> graphics.renderTooltip(font, lines, image, x, y, style), false);
        }
    }

    public void setTooltipForNextFrame(Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        List<FormattedCharSequence> captured = List.copyOf(lines);
        if (!captured.isEmpty()) {
            deferTooltip(() -> graphics.renderTooltip(font, captured, x, y), false);
        }
    }

    public void setTooltipForNextFrame(
            Font font, List<FormattedCharSequence> lines, ClientTooltipPositioner positioner,
            int x, int y, boolean replaceExisting) {
        List<FormattedCharSequence> captured = List.copyOf(lines);
        if (!captured.isEmpty()) {
            deferTooltip(() -> graphics.renderTooltip(font, captured, positioner, x, y), replaceExisting);
        }
    }

    /** Normalized texture coordinates; x1 and y1 are destination corners, not sizes. */
    public void blitTexture(
            String texture, int x0, int y0, int x1, int y1,
            float u0, float u1, float v0, float v1) {
        var id = MinecraftResourceIds.parse(texture);
        Matrix4f pose = graphics.pose().last().pose();
        graphics.drawSpecial(buffers -> {
            VertexConsumer vertices = buffers.getBuffer(RenderType.guiTextured(id));
            vertices.addVertex(pose, x0, y0, 0.0F).setUv(u0, v0).setColor(-1);
            vertices.addVertex(pose, x0, y1, 0.0F).setUv(u0, v1).setColor(-1);
            vertices.addVertex(pose, x1, y1, 0.0F).setUv(u1, v1).setColor(-1);
            vertices.addVertex(pose, x1, y0, 0.0F).setUv(u1, v0).setColor(-1);
        });
    }

    /** GUI texture with explicit pixel source rectangle and destination size. */
    public void blitTexture(
            String texture, int x, int y, float u, float v, int width, int height,
            int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        graphics.blit(RenderType::guiTextured, MinecraftResourceIds.parse(texture), x, y, u, v, width, height,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
    }
}
