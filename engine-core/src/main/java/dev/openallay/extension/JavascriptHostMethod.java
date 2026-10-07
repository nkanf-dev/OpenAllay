package dev.openallay.extension;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One exact trusted implementation. The implementation owns native scheduling and validation. */
@dev.openallay.value.ValueType(JavascriptHostMethod.ValueSchemaProvider.class)
public final class JavascriptHostMethod {
    private final String name;
    private final List<JavascriptHostValueType> parameters;
    private final JavascriptHostValueType result;
    private final Invoker invoker;
    public JavascriptHostMethod(String name, List<JavascriptHostValueType> parameters, JavascriptHostValueType result, Invoker invoker) {

        if (name == null || !name.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")
                || dev.openallay.util.Java8Collections.setOf("constructor", "__proto__", "prototype").contains(name)) {
            throw new IllegalArgumentException("Invalid controlled host method name");
        }
        parameters = dev.openallay.util.Java8Collections.listCopyOf(parameters);
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(invoker, "invoker");

        this.name = name;
        this.parameters = parameters;
        this.result = result;
        this.invoker = invoker;
    }
    public String name() { return name; }
    public List<JavascriptHostValueType> parameters() { return parameters; }
    public JavascriptHostValueType result() { return result; }
    public Invoker invoker() { return invoker; }
@FunctionalInterface
    public interface Invoker {
        /**
         * Called on the JavaScript worker, never a game owner thread. Arguments are detached
         * JSON copies. Return detached JSON only. Before each queued native action, recheck
         * context activity and the exact backend/session identity.
         * Never retain or forward Rhino values, or evaluate Agent callbacks on an owner thread.
         */
        JsonElement invoke(JavascriptInvocationContext context, List<JsonElement> arguments)
                throws Exception;
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JavascriptHostMethod)) return false;
        JavascriptHostMethod that = (JavascriptHostMethod) other;
        return java.util.Objects.equals(name, that.name) && java.util.Objects.equals(parameters, that.parameters) && java.util.Objects.equals(result, that.result) && java.util.Objects.equals(invoker, that.invoker);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(name);
        hash = 31 * hash + java.util.Objects.hashCode(parameters);
        hash = 31 * hash + java.util.Objects.hashCode(result);
        hash = 31 * hash + java.util.Objects.hashCode(invoker);
        return hash;
    }
    @Override public String toString() { return "JavascriptHostMethod[name=" + name + ", parameters=" + parameters + ", result=" + result + ", invoker=" + invoker + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<JavascriptHostMethod> schema() {
            return new dev.openallay.value.ValueSchema<>(JavascriptHostMethod.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<JavascriptHostMethod>>asList(new dev.openallay.value.ValueSchema.Component<>(JavascriptHostMethod.class, "name", JavascriptHostMethod::name), new dev.openallay.value.ValueSchema.Component<>(JavascriptHostMethod.class, "parameters", JavascriptHostMethod::parameters), new dev.openallay.value.ValueSchema.Component<>(JavascriptHostMethod.class, "result", JavascriptHostMethod::result), new dev.openallay.value.ValueSchema.Component<>(JavascriptHostMethod.class, "invoker", JavascriptHostMethod::invoker)), arguments -> new JavascriptHostMethod((String) arguments[0], (List) arguments[1], (JavascriptHostValueType) arguments[2], (Invoker) arguments[3]));
        }
    }
}
