package dev.openallay.platform.minecraft;

import java.util.Objects;

/** Detached canonical native resource ID; never retains a Minecraft object. */
@dev.openallay.value.ValueType(MinecraftResourceId.ValueSchemaProvider.class)
public final class MinecraftResourceId {
    private final String namespace;
    private final String path;
    public MinecraftResourceId(String namespace, String path) {

        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (namespace.isEmpty() || path.isEmpty()) {
            throw new IllegalArgumentException("Resource ID requires a namespace and path");
        }

        this.namespace = namespace;
        this.path = path;
    }
    public String namespace() { return namespace; }
    public String path() { return path; }
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MinecraftResourceId)) return false;
        MinecraftResourceId that = (MinecraftResourceId) other;
        return java.util.Objects.equals(namespace, that.namespace) && java.util.Objects.equals(path, that.path);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(namespace);
        hash = 31 * hash + java.util.Objects.hashCode(path);
        return hash;
    }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<MinecraftResourceId> schema() {
            return new dev.openallay.value.ValueSchema<>(MinecraftResourceId.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<MinecraftResourceId>>asList(new dev.openallay.value.ValueSchema.Component<>(MinecraftResourceId.class, "namespace", MinecraftResourceId::namespace), new dev.openallay.value.ValueSchema.Component<>(MinecraftResourceId.class, "path", MinecraftResourceId::path)), arguments -> new MinecraftResourceId((String) arguments[0], (String) arguments[1]));
        }
    }
}
