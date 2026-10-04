package dev.openallay.platform.minecraft;

import net.minecraft.resources.ResourceLocation;

/** Native ID construction for this Minecraft naming family. Callers infer the raw type. */
public final class MinecraftResourceIds {
    private MinecraftResourceIds() {}

    public static ResourceLocation parse(String value) { return ResourceLocation.parse(value); }
    public static ResourceLocation tryParse(String value) { return ResourceLocation.tryParse(value); }
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
    public static String keyId(net.minecraft.resources.ResourceKey<?> key) { return key.location().toString(); }
}
