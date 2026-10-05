package dev.openallay.platform.minecraft;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/** Native text constructors before Component gained its static factories. */
public final class MinecraftComponents {
    private MinecraftComponents() {}
    public static MutableComponent literal(String text) { return new TextComponent(text); }
    public static MutableComponent translatable(String key) { return new TranslatableComponent(key); }
    public static MutableComponent translatable(String key, Object... arguments) { return new TranslatableComponent(key, arguments); }
    public static MutableComponent empty() { return new TextComponent(""); }
}
