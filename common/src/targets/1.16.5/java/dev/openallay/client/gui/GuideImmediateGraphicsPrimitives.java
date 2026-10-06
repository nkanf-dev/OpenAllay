package dev.openallay.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tesselator;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Public actual fixed-function quad ABI; no shader or private invoker bridge. */
final class GuideImmediateGraphicsPrimitives {
    private GuideImmediateGraphicsPrimitives() {}
    static void blit(PoseStack pose, String texture, int x0, int y0, int x1, int y1,
            float u0, float u1, float v0, float v1) {
        try (GuideLegacyRenderState saved = GuideLegacyRenderState.save()) {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            Minecraft.getInstance().getTextureManager().bind(MinecraftResourceIds.parse(texture));
            RenderSystem.enableTexture();
            RenderSystem.color4f(1, 1, 1, 1);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableAlphaTest();
            Tesselator tessellator = Tesselator.getInstance();
            BufferBuilder buffer = tessellator.getBuilder();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX);
            var matrix = pose.last().pose();
            buffer.vertex(matrix, x0, y1, 0).uv(u0, v1).endVertex();
            buffer.vertex(matrix, x1, y1, 0).uv(u1, v1).endVertex();
            buffer.vertex(matrix, x1, y0, 0).uv(u1, v0).endVertex();
            buffer.vertex(matrix, x0, y0, 0).uv(u0, v0).endVertex();
            tessellator.end();
        }
    }
    static void blit(PoseStack pose, String texture, int x, int y, float u, float v,
            int width, int height, int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        blit(pose, texture, x, y, x + width, y + height,
                u / textureWidth, (u + sourceWidth) / textureWidth,
                v / textureHeight, (v + sourceHeight) / textureHeight);
    }
}
