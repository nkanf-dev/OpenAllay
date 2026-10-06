package dev.openallay.platform.minecraft;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;

/** Typed native text factories; layout, translation keys and styles stay at shared callers. */
public final class MinecraftComponents {
    private MinecraftComponents() {}
    public static MutableComponent literal(String text) { return Component.literal(text); }
    public static MutableComponent translatable(String key) { return Component.translatable(key); }
    public static MutableComponent translatable(String key, Object... arguments) { return Component.translatable(key, arguments); }
    public static MutableComponent empty() { return Component.empty(); }
    public static Component append(Component target, String text) { return ((MutableComponent) target).append(text); }
    public static Component append(Component target, Component child) { return ((MutableComponent) target).append(child); }
    public static Component copy(Component target) { return target.copy(); }
    public static Component style(Component target, ChatFormatting formatting) { return ((MutableComponent) target).withStyle(formatting); }
    public static Component style(Component target, Style style) { return ((MutableComponent) target).withStyle(style); }
    public static Component style(Component target, java.util.function.UnaryOperator<Style> operator) { return ((MutableComponent) target).withStyle(operator); }
    public static String getString(Component target) { return target.getString(); }
}
