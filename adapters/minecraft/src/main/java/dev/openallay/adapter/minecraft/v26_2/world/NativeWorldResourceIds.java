package dev.openallay.adapter.minecraft.v26_2.world;

import net.minecraft.resources.Identifier;

/** Native ID boundary owned by the world adapter, with no common-client dependency. */
final class NativeWorldResourceIds {
    private NativeWorldResourceIds() {}

    static Identifier tryParse(String value) { return Identifier.tryParse(value); }

    static Identifier parse(String value, String field) {
        var id = tryParse(value);
        if (id == null || value.isBlank() || id.getPath().isEmpty()) {
            throw new IllegalArgumentException("Invalid " + field + ": " + value);
        }
        return id;
    }

    static Identifier fromNamespaceAndPath(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
    static String keyId(net.minecraft.resources.ResourceKey<?> key) { return key.identifier().toString(); }
}
