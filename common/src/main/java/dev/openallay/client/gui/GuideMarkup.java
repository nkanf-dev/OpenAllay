package dev.openallay.client.gui;

import java.util.ArrayList;
import java.util.List;

import dev.openallay.platform.minecraft.MinecraftComponents;


/** Small safe formatter for the supported chat-like Markdown subset. */
final class GuideMarkup {
    private GuideMarkup() {}

    static List<net.minecraft.network.chat.Component> paragraphs(String value) {
        if (value == null || value.isEmpty()) return dev.openallay.util.Java8Collections.listOf(MinecraftComponents.empty());
        List<net.minecraft.network.chat.Component> output = new ArrayList<>();
        for (String raw : value.split("\\R", -1)) {
            String line = raw;
            net.minecraft.network.chat.Component component = MinecraftComponents.empty();
            if (line.startsWith("- ") || line.startsWith("* ")) {
                MinecraftComponents.append(component, MinecraftComponents.style(MinecraftComponents.literal("• "), net.minecraft.ChatFormatting.AQUA));
                line = line.substring(2);
            }
            appendInline(component, line);
            output.add(component);
        }
        return dev.openallay.util.Java8Collections.listCopyOf(output);
    }

    private static void appendInline(net.minecraft.network.chat.Component target, String text) {
        int index = 0;
        while (index < text.length()) {
            if (text.startsWith("**", index)) {
                int end = text.indexOf("**", index + 2);
                if (end >= 0) {
                    MinecraftComponents.append(target, MinecraftComponents.style(MinecraftComponents.literal(text.substring(index + 2, end)), net.minecraft.ChatFormatting.BOLD));
                    index = end + 2;
                    continue;
                }
            }
            char marker = text.charAt(index);
            if (marker == '`' || marker == '*') {
                int end = text.indexOf(marker, index + 1);
                if (end >= 0) {
                    net.minecraft.network.chat.Component styled = MinecraftComponents.literal(text.substring(index + 1, end));
                    MinecraftComponents.append(target, marker == '`'
                            ? MinecraftComponents.style(styled, net.minecraft.ChatFormatting.GRAY)
                            : MinecraftComponents.style(styled, net.minecraft.ChatFormatting.ITALIC));
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
