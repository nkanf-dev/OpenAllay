package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.resources.ResourceLocation;

/** Minecraft 1.20.6/1.20.5 resource IDs; all world behavior stays in shared families. */
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
        return new ResourceLocation(namespace, path);
    }
    static String keyId(net.minecraft.resources.ResourceKey<?> key) { return key.location().toString(); }
}
