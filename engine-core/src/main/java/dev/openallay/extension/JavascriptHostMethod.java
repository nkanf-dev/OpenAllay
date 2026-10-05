package dev.openallay.extension;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One exact trusted implementation. The implementation owns native scheduling and validation. */
public record JavascriptHostMethod(
        String name,
        List<JavascriptHostValueType> parameters,
        JavascriptHostValueType result,
        Invoker invoker) {
    public JavascriptHostMethod {
        if (name == null || !name.matches("[a-zA-Z_$][a-zA-Z0-9_$]*")
                || Set.of("constructor", "__proto__", "prototype").contains(name)) {
            throw new IllegalArgumentException("Invalid controlled host method name");
        }
        parameters = List.copyOf(parameters);
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(invoker, "invoker");
    }

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
}
