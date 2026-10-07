package dev.openallay.extension;

import java.util.HashSet;
import java.util.List;

/** A namespaced, immutable method whitelist exposed by require(id), not by Java reflection. */
@dev.openallay.value.ValueType(JavascriptHostBinding.ValueSchemaProvider.class)
public final class JavascriptHostBinding {
    private final String id;
    private final List<JavascriptHostMethod> methods;
    public JavascriptHostBinding(String id, List<JavascriptHostMethod> methods) {

        id = requireId(id);
        methods = List.copyOf(methods);
        java.util.HashSet<java.lang.String> names = new HashSet<String>();
        for (JavascriptHostMethod method : methods) {
            if (!names.add(method.name())) throw new IllegalArgumentException("Duplicate host method");
        }

        this.id = id;
        this.methods = methods;
    }
    public String id() { return id; }
    public List<JavascriptHostMethod> methods() { return methods; }
private static String requireId(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid host binding ID");
        }
        return id;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JavascriptHostBinding)) return false;
        JavascriptHostBinding that = (JavascriptHostBinding) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(methods, that.methods);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(methods);
        return hash;
    }
    @Override public String toString() { return "JavascriptHostBinding[id=" + id + ", methods=" + methods + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<JavascriptHostBinding> schema() {
            return new dev.openallay.value.ValueSchema<>(JavascriptHostBinding.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<JavascriptHostBinding>>asList(new dev.openallay.value.ValueSchema.Component<>(JavascriptHostBinding.class, "id", JavascriptHostBinding::id), new dev.openallay.value.ValueSchema.Component<>(JavascriptHostBinding.class, "methods", JavascriptHostBinding::methods)), arguments -> new JavascriptHostBinding((String) arguments[0], (List) arguments[1]));
        }
    }
}
