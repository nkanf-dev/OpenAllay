package dev.openallay.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Typed LWJGL 2 immediate primitives; no screen, layout or provider algorithm lives here. */
public final class GuideImmediateGraphicsPrimitives {
    private GuideImmediateGraphicsPrimitives() {}
    public static int guiWidth() { return new ScaledResolution(Minecraft.getMinecraft()).getScaledWidth(); }
    public static int guiHeight() { return new ScaledResolution(Minecraft.getMinecraft()).getScaledHeight(); }
    public static void pushPose() { GlStateManager.pushMatrix(); }
    public static void popPose() { GlStateManager.popMatrix(); }
    public static void translatePose(float x, float y) { GlStateManager.translate(x, y, 0.0F); }
    public static void scalePose(float x, float y) { GlStateManager.scale(x, y, 1.0F); }
    public static void text(FontRenderer font, String text, int x, int y, int color, boolean shadow) {
        font.drawString(text, x, y, color, shadow);
    }
    public static void fill(int x0, int y0, int x1, int y1, int argb) {
        int a = argb >>> 24;
        int r = argb >> 16 & 255;
        int g = argb >> 8 & 255;
        int b = argb & 255;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        try {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(x0, y1, 0).color(r, g, b, a).endVertex();
            buffer.pos(x1, y1, 0).color(r, g, b, a).endVertex();
            buffer.pos(x1, y0, 0).color(r, g, b, a).endVertex();
            buffer.pos(x0, y0, 0).color(r, g, b, a).endVertex();
            tessellator.draw();
        } finally {
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
        }
    }
    /** Caller supplies already transformed GUI bounds; shared scissor composition remains canonical. */
    public static void enableScissor(int x0, int y0, int x1, int y1) {
        Minecraft client = Minecraft.getMinecraft();
        ScaledResolution scaled = new ScaledResolution(client);
        int scale = scaled.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x0 * scale, client.displayHeight - y1 * scale,
                Math.max(0, x1 - x0) * scale, Math.max(0, y1 - y0) * scale);
    }
    public static void disableScissor() { GL11.glDisable(GL11.GL_SCISSOR_TEST); }
    public static void blit(ResourceLocation texture, int x0, int y0, int x1, int y1,
            float u0, float u1, float v0, float v1) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1, 1, 1, 1);
        try {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
            buffer.pos(x0, y1, 0).tex(u0, v1).color(255, 255, 255, 255).endVertex();
            buffer.pos(x1, y1, 0).tex(u1, v1).color(255, 255, 255, 255).endVertex();
            buffer.pos(x1, y0, 0).tex(u1, v0).color(255, 255, 255, 255).endVertex();
            buffer.pos(x0, y0, 0).tex(u0, v0).color(255, 255, 255, 255).endVertex();
            tessellator.draw();
        } finally {
            GlStateManager.disableBlend();
            GlStateManager.color(1, 1, 1, 1);
        }
    }
}
