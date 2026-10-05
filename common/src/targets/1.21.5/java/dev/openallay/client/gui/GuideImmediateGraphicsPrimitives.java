package dev.openallay.client.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.openallay.platform.minecraft.MinecraftResourceIds;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** Exact native paint primitives; shared paint scopes and tooltip selection stay in GuideGraphics. */
final class GuideImmediateGraphicsPrimitives {
    private GuideImmediateGraphicsPrimitives() {}

    static void itemTooltip(GuiGraphics graphics, Font font, ItemStack stack, List<Component> lines,
            Optional<TooltipComponent> image, int x, int y) {
        graphics.renderTooltip(font, lines, image, x, y, stack.get(DataComponents.TOOLTIP_STYLE));
    }

    static void blit(GuiGraphics graphics, String texture, int x0, int y0, int x1, int y1,
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

    static void blit(GuiGraphics graphics, String texture, int x, int y, float u, float v,
            int width, int height, int sourceWidth, int sourceHeight, int textureWidth, int textureHeight) {
        graphics.blit(RenderType::guiTextured, MinecraftResourceIds.parse(texture), x, y, u, v, width, height,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
    }
}
