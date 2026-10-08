package dev.openallay.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;

/** Actual fixed-function model-view item calls, with exact caller state restoration. */
final class GuidePoseItems {
    private GuidePoseItems() {}
    private static void paint(PoseStack pose, Runnable draw) {
        try (GuideLegacyRenderState saved = GuideLegacyRenderState.save()) {
            RenderSystem.activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            RenderSystem.enableTexture();
            RenderSystem.matrixMode(GL11.GL_MODELVIEW);
            RenderSystem.pushMatrix();
            try { RenderSystem.multMatrix(pose.last().pose()); draw.run(); }
            finally { RenderSystem.popMatrix(); }
        }
    }
    static void render(PoseStack pose, ItemStack stack, int x, int y) {
        if (!stack.isEmpty()) paint(pose, () -> Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(stack, x, y));
    }
    static void decorations(PoseStack pose, Font font, ItemStack stack, int x, int y, String count) {
        if (!stack.isEmpty()) paint(pose, () -> Minecraft.getInstance().getItemRenderer().renderGuiItemDecorations(font, stack, x, y, count));
    }
}
