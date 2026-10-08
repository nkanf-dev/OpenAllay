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
    @dev.openallay.value.ValueType(SavedClip.ValueSchemaProvider.class)
private static final class SavedClip {
    private final boolean enabled;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private SavedClip(boolean enabled, int x, int y, int width, int height) {
        this.enabled = enabled;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    public boolean enabled() { return enabled; }
    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SavedClip)) return false;
        SavedClip that = (SavedClip) other;
        return enabled == that.enabled && x == that.x && y == that.y && width == that.width && height == that.height;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + Integer.hashCode(x);
        hash = 31 * hash + Integer.hashCode(y);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        return hash;
    }
    @Override public String toString() { return "SavedClip[enabled=" + enabled + ", x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SavedClip> schema() {
            return new dev.openallay.value.ValueSchema<>(SavedClip.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SavedClip>>asList(new dev.openallay.value.ValueSchema.Component<>(SavedClip.class, "enabled", SavedClip::enabled), new dev.openallay.value.ValueSchema.Component<>(SavedClip.class, "x", SavedClip::x), new dev.openallay.value.ValueSchema.Component<>(SavedClip.class, "y", SavedClip::y), new dev.openallay.value.ValueSchema.Component<>(SavedClip.class, "width", SavedClip::width), new dev.openallay.value.ValueSchema.Component<>(SavedClip.class, "height", SavedClip::height)), arguments -> new SavedClip((Boolean) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (Integer) arguments[4]));
        }
    }
}
    private static final ThreadLocal<java.util.ArrayDeque<SavedClip>> CLIPS = ThreadLocal.withInitial(java.util.ArrayDeque::new);
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
    /** Read actual native model-view transform and intersect the caller's real GL clip. */
    public static void enableScissor(int x0, int y0, int x1, int y1) {
        Minecraft client = Minecraft.getMinecraft();
        int scale = new ScaledResolution(client).getScaleFactor();
        java.nio.FloatBuffer transform = java.nio.ByteBuffer.allocateDirect(16 * 4)
                .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, transform);
        float left = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            float x = (i & 1) == 0 ? x0 : x1;
            float y = (i & 2) == 0 ? y0 : y1;
            float tx = transform.get(0) * x + transform.get(4) * y + transform.get(12);
            float ty = transform.get(1) * x + transform.get(5) * y + transform.get(13);
            left = Math.min(left, tx); right = Math.max(right, tx);
            top = Math.min(top, ty); bottom = Math.max(bottom, ty);
        }
        if (x1 <= x0 || y1 <= y0) { right = left; bottom = top; }
        // LWJGL 2 validates glGetInteger buffers for the maximum 16-int native result,
        // even though GL_SCISSOR_BOX writes only four values.
        java.nio.IntBuffer box = java.nio.ByteBuffer.allocateDirect(16 * 4)
                .order(java.nio.ByteOrder.nativeOrder()).asIntBuffer();
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, box);
        SavedClip saved = new SavedClip(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), box.get(0), box.get(1), box.get(2), box.get(3));
        CLIPS.get().push(saved);
        int l = (int) Math.floor(left * scale), r = (int) Math.ceil(right * scale);
        int b = (int) Math.floor(client.displayHeight - bottom * scale);
        int t = (int) Math.ceil(client.displayHeight - top * scale);
        if (saved.enabled()) {
            l = Math.max(l, saved.x()); b = Math.max(b, saved.y());
            r = Math.min(r, saved.x() + saved.width()); t = Math.min(t, saved.y() + saved.height());
        }
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(l, b, Math.max(0, r - l), Math.max(0, t - b));
    }
    public static void disableScissor() {
        java.util.ArrayDeque<dev.openallay.client.gui.GuideImmediateGraphicsPrimitives.SavedClip> clips = CLIPS.get();
        if (clips.isEmpty()) throw new IllegalStateException("Unbalanced native Guide scissor");
        SavedClip saved = clips.pop();
        GL11.glScissor(saved.x(), saved.y(), saved.width(), saved.height());
        if (saved.enabled()) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (clips.isEmpty()) CLIPS.remove();
    }
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
