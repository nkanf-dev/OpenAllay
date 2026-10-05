package dev.openallay.platform.minecraft;

import net.minecraft.resources.ResourceLocation;

/** Pre-1.21 native ID construction, inherited through 1.20.1. Callers infer the raw type. */
public final class MinecraftResourceIds {
    private MinecraftResourceIds() {}

    public static ResourceLocation parse(String value) { return new ResourceLocation(value); }
    public static ResourceLocation tryParse(String value) { return ResourceLocation.tryParse(value); }
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }
    public static String keyId(net.minecraft.resources.ResourceKey<?> key) { return key.location().toString(); }
}
