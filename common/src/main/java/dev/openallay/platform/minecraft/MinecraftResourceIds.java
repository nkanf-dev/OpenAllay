package dev.openallay.platform.minecraft;

import net.minecraft.resources.Identifier;

/** Native ID construction for this Minecraft naming family. Callers infer the raw type. */
public final class MinecraftResourceIds {
    private MinecraftResourceIds() {}

    public static Identifier parse(String value) { return Identifier.parse(value); }
    public static Identifier tryParse(String value) { return Identifier.tryParse(value); }
    public static Identifier fromNamespaceAndPath(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
    public static String keyId(net.minecraft.resources.ResourceKey<?> key) { return key.identifier().toString(); }
}
