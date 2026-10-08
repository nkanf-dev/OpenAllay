package dev.openallay.extension;

import java.util.regex.Pattern;

/** Reviewed CommonJS module source contributed by one registered Extension. */
@dev.openallay.value.ValueType(JavascriptModuleSource.ValueSchemaProvider.class)
public final class JavascriptModuleSource {
    private final String id;
    private final String source;
    public JavascriptModuleSource(String id, String source) {

        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid JavaScript module ID: " + id);
        }
        if (source == null || dev.openallay.util.Java8Strings.isBlank(source)) {
            throw new IllegalArgumentException("JavaScript module source must not be blank");
        }

        this.id = id;
        this.source = source;
    }
    public String id() { return id; }
    public String source() { return source; }
private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JavascriptModuleSource)) return false;
        JavascriptModuleSource that = (JavascriptModuleSource) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(source, that.source);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        return hash;
    }
    @Override public String toString() { return "JavascriptModuleSource[id=" + id + ", source=" + source + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<JavascriptModuleSource> schema() {
            return new dev.openallay.value.ValueSchema<>(JavascriptModuleSource.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<JavascriptModuleSource>>asList(new dev.openallay.value.ValueSchema.Component<>(JavascriptModuleSource.class, "id", JavascriptModuleSource::id), new dev.openallay.value.ValueSchema.Component<>(JavascriptModuleSource.class, "source", JavascriptModuleSource::source)), arguments -> new JavascriptModuleSource((String) arguments[0], (String) arguments[1]));
        }
    }
}
