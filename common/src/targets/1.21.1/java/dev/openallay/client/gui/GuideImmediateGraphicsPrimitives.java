package dev.openallay.client.gui;

import dev.openallay.client.gui.mixin.GuiGraphicsTextureAccess;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** 1.21/1.21.1 texture and tooltip contracts before RenderType-based GUI blits/style components. */
final class GuideImmediateGraphicsPrimitives {
    private GuideImmediateGraphicsPrimitives() {}

    static void itemTooltip(GuiGraphics graphics, Font font, ItemStack stack, List<Component> lines,
            Optional<TooltipComponent> image, int x, int y) {
        graphics.renderTooltip(font, lines, image, x, y);
    }

    static void blit(GuiGraphics graphics, String texture, int x0, int y0, int x1, int y1,
            float u0, float u1, float v0, float v1) {
        // Native innerBlit owns its current PoseStack, texture/shader binding and blend cleanup.
        graphics.flush();
        ((GuiGraphicsTextureAccess) graphics).openallay$blitTexture(MinecraftResourceIds.parse(texture),
                x0, x1, y0, y1, 0, u0, u1, v0, v1, 1.0F, 1.0F, 1.0F, 1.0F);
    }

    static void blit(GuiGraphics graphics, String texture, int x, int y, float u, float v,
            int width, int height, int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        blit(graphics, texture, x, y, x + width, y + height,
                u / textureWidth, (u + sourceWidth) / textureWidth,
                v / textureHeight, (v + sourceHeight) / textureHeight);
    }
}
