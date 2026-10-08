package dev.openallay.platform.minecraft;

import net.minecraft.util.ResourceLocation;

/** Actual 1.12 native resource identity. This family has no ResourceKey object. */
public final class MinecraftResourceIds {
    private MinecraftResourceIds() {}
    public static ResourceLocation parse(String value) { return new ResourceLocation(value); }
    public static ResourceLocation tryParse(String value) {
        if (value == null) return null;
        try { return new ResourceLocation(value); }
        catch (IllegalArgumentException invalid) { return null; }
    }
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }
    public static String keyId(ResourceLocation id) { return id.toString(); }
}
