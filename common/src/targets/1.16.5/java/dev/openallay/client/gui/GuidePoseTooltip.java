package dev.openallay.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/** Actual Screen tooltip overloads preserve Forge item font and pre/post hooks. */
final class GuidePoseTooltip extends Screen {
    private GuidePoseTooltip(Font font) {
        super(MinecraftComponents.empty());
        minecraft = Minecraft.getInstance();
        this.font = font;
        itemRenderer = minecraft.getItemRenderer();
        width = minecraft.getWindow().getGuiScaledWidth();
        height = minecraft.getWindow().getGuiScaledHeight();
    }
    static List<Component> itemLines(ItemStack stack) { return List.copyOf(new GuidePoseTooltip(Minecraft.getInstance().font).getTooltipFromItem(stack)); }
    static void lines(PoseStack pose, Font font, List<FormattedCharSequence> lines, int x, int y) {
        new GuidePoseTooltip(font).renderToolTip(pose, lines, x, y, font);
    }
    static void item(PoseStack pose, Font font, ItemStack stack, int x, int y) {
        new GuidePoseTooltip(font).renderTooltip(pose, stack, x, y);
    }
}
