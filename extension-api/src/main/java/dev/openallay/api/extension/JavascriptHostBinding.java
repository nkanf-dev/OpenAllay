package dev.openallay.api.extension;

import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.HashSet;

/** An immutable exact method whitelist, exposed by module ID rather than reflection. */
public final class JavascriptHostBinding {
    private final String id;
    private final List<JavascriptHostMethod> methods;

    public JavascriptHostBinding(String id, List<JavascriptHostMethod> methods) {
        this.id = ApiValidation.id(id, "host binding ID");
        this.methods = requireMethods(methods);
    }

    public String id() { return id; }
    public List<JavascriptHostMethod> methods() { return methods; }

    private static List<JavascriptHostMethod> requireMethods(List<JavascriptHostMethod> methods) {
        List<JavascriptHostMethod> copy = ApiValidation.list(methods, "methods");
        Set<String> names = new HashSet<String>();
        for (JavascriptHostMethod method : copy)
            if (!names.add(method.name())) throw new IllegalArgumentException("Duplicate host method: " + method.name());
        return copy;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JavascriptHostBinding)) return false;
        JavascriptHostBinding that = (JavascriptHostBinding) other;
        return Objects.equals(id, that.id) &&
                Objects.equals(methods, that.methods);
    }
    @Override public int hashCode() { return Objects.hash(id, methods); }
}
