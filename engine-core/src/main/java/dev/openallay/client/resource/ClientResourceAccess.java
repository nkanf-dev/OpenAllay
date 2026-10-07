package dev.openallay.client.resource;

import java.util.List;

public interface ClientResourceAccess {
    List<ClientResource> list(String pathPrefix);

    default List<ClientResource> selected(String pathPrefix) {
        return dev.openallay.util.Java8Collections.toList(list(pathPrefix).stream().filter(ClientResource::selected));
    }

    static String validatePrefix(String prefix) {
        if (prefix == null
                || dev.openallay.util.Java8Strings.isBlank(prefix)
                || prefix.startsWith("/")
                || prefix.contains("\\")
                || prefix.contains(":")
                || prefix.equals("..")
                || prefix.startsWith("../")
                || prefix.contains("/../")) {
            throw new IllegalArgumentException("Invalid asset path prefix: " + prefix);
        }
        String normalized = java.nio.file.Paths.get(prefix).normalize().toString()
                .replace(java.io.File.separatorChar, '/');
        if (!normalized.equals(prefix) && !normalized.equals(prefix.replaceAll("/+$", ""))) {
            throw new IllegalArgumentException("Asset prefix must be normalized: " + prefix);
        }
        return normalized;
    }
}
