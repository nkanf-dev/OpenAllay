package dev.openallay.platform.minecraft;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Typed native text factories; layout, translation keys and styles stay at shared callers. */
public final class MinecraftComponents {
    private MinecraftComponents() {}
    public static MutableComponent literal(String text) { return Component.literal(text); }
    public static MutableComponent translatable(String key) { return Component.translatable(key); }
    public static MutableComponent translatable(String key, Object... arguments) { return Component.translatable(key, arguments); }
    public static MutableComponent empty() { return Component.empty(); }
}
