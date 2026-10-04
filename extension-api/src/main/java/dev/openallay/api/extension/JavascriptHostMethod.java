package dev.openallay.api.extension;

import java.util.List;
import java.util.Set;
import java.util.Objects;

/** One exact trusted host implementation. The core detaches and type-checks JSON in both directions. */
public final class JavascriptHostMethod {
    private final String name;
    private final List<JavascriptHostValueType> parameters;
    private final JavascriptHostValueType result;
    private final Set<String> requiredCapabilities;
    private final Invoker invoker;

    public JavascriptHostMethod(
            String name,
            List<JavascriptHostValueType> parameters,
            JavascriptHostValueType result,
            Set<String> requiredCapabilities,
            Invoker invoker) {
        this.name = requireName(name);
        this.parameters = ApiValidation.list(parameters, "parameters");
        this.result = Objects.requireNonNull(result, "result");
        this.requiredCapabilities = ApiValidation.ids(requiredCapabilities, "required capability ID", true);
        this.invoker = Objects.requireNonNull(invoker, "invoker");
    }

    public String name() { return name; }
    public List<JavascriptHostValueType> parameters() { return parameters; }
    public JavascriptHostValueType result() { return result; }
    public Set<String> requiredCapabilities() { return requiredCapabilities; }
    public Invoker invoker() { return invoker; }

    private static String requireName(String name) {
        if (name == null || !name.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")
                || name.equals("constructor") || name.equals("__proto__") || name.equals("prototype"))
            throw new IllegalArgumentException("Invalid controlled host method name: " + name);
        return name;
    }

    @FunctionalInterface
    public interface Invoker {
        /**
         * Runs on the script worker. Each argument and the return string encode exactly one
         * detached JSON value, not a Java wrapper, engine object, callback or stream of values.
         * The core validates arity, strict JSON and declared types before/after this call.
         * Native implementations recheck active scope, capability and session before owner work.
         */
        String invoke(ExtensionInvocation context, List<String> argumentJson) throws Exception;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof JavascriptHostMethod)) return false;
        JavascriptHostMethod that = (JavascriptHostMethod) other;
        return Objects.equals(name, that.name) &&
                Objects.equals(parameters, that.parameters) &&
                Objects.equals(result, that.result) &&
                Objects.equals(requiredCapabilities, that.requiredCapabilities) &&
                Objects.equals(invoker, that.invoker);
    }
    @Override public int hashCode() { return Objects.hash(name, parameters, result, requiredCapabilities, invoker); }
}
