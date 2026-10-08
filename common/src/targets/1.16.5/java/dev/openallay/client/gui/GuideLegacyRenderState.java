package dev.openallay.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.MemoryStack;

/** Restore the real fixed-function GUI state through RenderSystem's cached setters. */
final class GuideLegacyRenderState implements AutoCloseable {
    private final int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
    private final int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int texture;
    private final boolean textureEnabled;
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
    private final boolean rescale = GL11.glIsEnabled(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
    private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int alphaFunction = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
    private final float alphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
    private final float[] color = new float[4];
    private boolean closed;
    private GuideLegacyRenderState() {
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        textureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        RenderSystem.activeTexture(activeTexture);
        RenderSystem.pushLightingAttributes();
        try (MemoryStack memory = MemoryStack.stackPush()) {
            var values = memory.mallocFloat(4);
            GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, values);
            values.get(color);
        }
    }
    static GuideLegacyRenderState save() { return new GuideLegacyRenderState(); }
    @Override public void close() {
        if (closed) throw new IllegalStateException("Native GUI state was restored twice");
        closed = true;
        RenderSystem.popAttributes();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        RenderSystem.bindTexture(texture);
        if (textureEnabled) RenderSystem.enableTexture(); else RenderSystem.disableTexture();
        RenderSystem.activeTexture(activeTexture);
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        RenderSystem.alphaFunc(alphaFunction, alphaReference);
        if (alpha) RenderSystem.enableAlphaTest(); else RenderSystem.disableAlphaTest();
        RenderSystem.depthMask(depthMask);
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        if (lighting) RenderSystem.enableLighting(); else RenderSystem.disableLighting();
        if (rescale) RenderSystem.enableRescaleNormal(); else RenderSystem.disableRescaleNormal();
        RenderSystem.color4f(color[0], color[1], color[2], color[3]);
        RenderSystem.matrixMode(matrixMode);
    }
}
