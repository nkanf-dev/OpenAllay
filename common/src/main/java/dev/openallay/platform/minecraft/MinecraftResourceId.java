package dev.openallay.platform.minecraft;

import java.util.Objects;

/** Detached canonical native resource ID; never retains a Minecraft object. */
public record MinecraftResourceId(String namespace, String path) {
    public MinecraftResourceId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (namespace.isEmpty() || path.isEmpty()) {
            throw new IllegalArgumentException("Resource ID requires a namespace and path");
        }
    }

    /** Detach the canonical namespace:path form returned by a native resource ID. */
    public static MinecraftResourceId from(String canonicalId) {
        Objects.requireNonNull(canonicalId, "canonicalId");
        int separator = canonicalId.indexOf(':');
        if (separator <= 0 || separator == canonicalId.length() - 1) {
            throw new IllegalArgumentException("Expected canonical resource ID: " + canonicalId);
        }
        return new MinecraftResourceId(
                canonicalId.substring(0, separator), canonicalId.substring(separator + 1));
    }

    @Override public String toString() { return namespace + ":" + path; }
}
