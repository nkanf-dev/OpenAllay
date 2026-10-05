package dev.openallay.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Native Screen tooltip renderer without changing the displayed screen or UI ownership. */
final class GuidePoseTooltip extends Screen {
    private GuidePoseTooltip(Font font) {
        super(Component.empty());
        minecraft = Minecraft.getInstance();
        this.font = font;
        itemRenderer = minecraft.getItemRenderer();
        width = minecraft.getWindow().getGuiScaledWidth();
        height = minecraft.getWindow().getGuiScaledHeight();
    }
    static List<Component> itemLines(ItemStack stack) {
        return List.copyOf(new GuidePoseTooltip(Minecraft.getInstance().font).getTooltipFromItem(stack));
    }
    static void lines(PoseStack pose, Font font, List<FormattedCharSequence> lines, int x, int y) {
        new GuidePoseTooltip(font).renderTooltip(pose, lines, x, y);
    }
    static void item(PoseStack pose, Font font, ItemStack stack, List<Component> lines,
            Optional<TooltipComponent> image, int x, int y) {
        // The real item overload preserves Forge tooltip image/text hooks and the native stack context.
        new GuidePoseTooltip(font).renderTooltip(pose, stack, x, y);
    }
}
