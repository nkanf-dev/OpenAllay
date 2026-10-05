package dev.openallay.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.openallay.client.gui.mixin.GuiComponentTextureAccess;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Exact native textured quad and native tooltip components before GuiGraphics exists. */
final class GuideImmediateGraphicsPrimitives {
    private GuideImmediateGraphicsPrimitives() {}
    static void itemTooltip(PoseStack pose, Font font, ItemStack stack, List<Component> lines,
            Optional<TooltipComponent> image, int x, int y) {
        GuidePoseTooltip.item(pose, font, stack, lines, image, x, y);
    }
    static void blit(PoseStack pose, String texture, int x0, int y0, int x1, int y1,
            float u0, float u1, float v0, float v1) {
        var shader = RenderSystem.getShader();
        int oldTexture = RenderSystem.getShaderTexture(0);
        float[] color = RenderSystem.getShaderColor().clone();
        boolean blend = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        int srcRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB);
        int dstRgb = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB);
        int srcAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        try {
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, MinecraftResourceIds.parse(texture));
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            GuiComponentTextureAccess.openallay$blit(pose.last().pose(), x0, x1, y0, y1, 0, u0, u1, v0, v1);
        } finally {
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (!blend) RenderSystem.disableBlend();
            RenderSystem.setShaderTexture(0, oldTexture);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.setShader(() -> shader);
        }
    }
    static void blit(PoseStack pose, String texture, int x, int y, float u, float v,
            int width, int height, int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        blit(pose, texture, x, y, x + width, y + height,
                u / textureWidth, (u + sourceWidth) / textureWidth,
                v / textureHeight, (v + sourceHeight) / textureHeight);
    }
}
