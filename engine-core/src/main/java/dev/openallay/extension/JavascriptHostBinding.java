package dev.openallay.extension;

import java.util.HashSet;
import java.util.List;

/** A namespaced, immutable method whitelist exposed by require(id), not by Java reflection. */
public record JavascriptHostBinding(String id, List<JavascriptHostMethod> methods) {
    public JavascriptHostBinding {
        id = requireId(id);
        methods = List.copyOf(methods);
        java.util.HashSet<java.lang.String> names = new HashSet<String>();
        for (JavascriptHostMethod method : methods) {
            if (!names.add(method.name())) throw new IllegalArgumentException("Duplicate host method");
        }
    }

    private static String requireId(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid host binding ID");
        }
        return id;
    }
}
