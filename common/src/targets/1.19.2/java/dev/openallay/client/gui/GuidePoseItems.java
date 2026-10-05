package dev.openallay.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;

/** Old native item calls use RenderSystem model-view; preserve the caller's pose and seed. */
final class GuidePoseItems {
    private GuidePoseItems() {}
    private static void paint(PoseStack pose, Runnable draw) {
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.mulPoseMatrix(pose.last().pose());
            RenderSystem.applyModelViewMatrix();
            draw.run();
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }
    static void render(PoseStack pose, ItemStack stack, int x, int y, int seed) {
        if (stack.isEmpty()) return;
        paint(pose, () -> Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(stack, x, y, seed));
    }
    static void decorations(PoseStack pose, Font font, ItemStack stack, int x, int y, String count) {
        if (stack.isEmpty()) return;
        paint(pose, () -> Minecraft.getInstance().getItemRenderer().renderGuiItemDecorations(font, stack, x, y, count));
    }
}
