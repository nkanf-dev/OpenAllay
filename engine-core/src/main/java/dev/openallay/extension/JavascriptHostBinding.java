package dev.openallay.extension;

import java.util.HashSet;
import java.util.List;

/** A namespaced, immutable method whitelist exposed by require(id), not by Java reflection. */
public record JavascriptHostBinding(String id, List<JavascriptHostMethod> methods) {
    public JavascriptHostBinding {
        id = ExtensionCapability.requireIdentity(id);
        methods = List.copyOf(methods);
        var names = new HashSet<String>();
        for (JavascriptHostMethod method : methods) {
            if (!names.add(method.name())) throw new IllegalArgumentException("Duplicate host method");
        }
    }
}
