package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.resources.ResourceLocation;

/** Native ID boundary owned by the world adapter, with no common-client dependency. */
final class NativeWorldResourceIds {
    private NativeWorldResourceIds() {}

    static ResourceLocation tryParse(String value) { return ResourceLocation.tryParse(value); }

    static ResourceLocation parse(String value, String field) {
        var id = tryParse(value);
        if (id == null || value.isBlank() || id.getPath().isEmpty()) {
            throw new IllegalArgumentException("Invalid " + field + ": " + value);
        }
        return id;
    }

    static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
    static String keyId(net.minecraft.resources.ResourceKey<?> key) { return key.location().toString(); }
}
