package dev.openallay.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.network.chat.Component;

/** Small safe formatter for the supported chat-like Markdown subset. */
final class GuideMarkup {
    private GuideMarkup() {}

    static List<Component> paragraphs(String value) {
        if (value == null || value.isEmpty()) return List.of(MinecraftComponents.empty());
        List<Component> output = new ArrayList<>();
        for (String raw : value.split("\\R", -1)) {
            String line = raw;
            Component component = MinecraftComponents.empty();
            if (line.startsWith("- ") || line.startsWith("* ")) {
                MinecraftComponents.append(component, MinecraftComponents.style(MinecraftComponents.literal("• "), ChatFormatting.AQUA));
                line = line.substring(2);
            }
            appendInline(component, line);
            output.add(component);
        }
        return List.copyOf(output);
    }

    private static void appendInline(Component target, String text) {
        int index = 0;
        while (index < text.length()) {
            if (text.startsWith("**", index)) {
                int end = text.indexOf("**", index + 2);
                if (end >= 0) {
                    MinecraftComponents.append(target, MinecraftComponents.style(MinecraftComponents.literal(text.substring(index + 2, end)), ChatFormatting.BOLD));
                    index = end + 2;
                    continue;
                }
            }
            char marker = text.charAt(index);
            if (marker == '`' || marker == '*') {
                int end = text.indexOf(marker, index + 1);
                if (end >= 0) {
                    var styled = MinecraftComponents.literal(text.substring(index + 1, end));
                    MinecraftComponents.append(target, marker == '`'
                            ? MinecraftComponents.style(styled, ChatFormatting.GRAY)
                            : MinecraftComponents.style(styled, ChatFormatting.ITALIC));
                    index = end + 1;
                    continue;
                }
            }
            int next = index + 1;
            while (next < text.length()
                    && text.charAt(next) != '`'
                    && text.charAt(next) != '*') next++;
            MinecraftComponents.append(target, MinecraftComponents.literal(text.substring(index, next)));
            index = next;
        }
    }
}
