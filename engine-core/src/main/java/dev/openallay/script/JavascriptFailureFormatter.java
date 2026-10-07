package dev.openallay.script;

import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.EcmaError;
import dev.latvian.mods.rhino.EvaluatorException;
import dev.latvian.mods.rhino.JavaScriptException;
import dev.latvian.mods.rhino.RhinoException;
import dev.latvian.mods.rhino.ScriptStackElement;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.latvian.mods.rhino.WrappedException;
import dev.openallay.model.ModelClientException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Actual exception messages and registered script frames without native stack dumps. */
public final class JavascriptFailureFormatter {
    static final String USER_SOURCE = "openallay-agent.js";
    static final int USER_PREFIX_LINES = 2;
    static final int MODULE_PREFIX_LINES = 6;
    private final Map<String, Source> sources = new LinkedHashMap<>();

    JavascriptFailureFormatter(String source) {
        sources.put(USER_SOURCE, new Source(USER_PREFIX_LINES, lineCount(source)));
    }

    /** Formats a tool-boundary failure without exposing a native stack. */
    public static String format(Throwable failure) {
        return nativeSummary(unwrap(failure));
    }

    void registerModule(String id, String source) {
        sources.put(moduleSourceName(id), new Source(MODULE_PREFIX_LINES, lineCount(source)));
    }

    static String moduleSourceName(String id) {
        return "openallay-module-" + id + ".js";
    }

    static void rethrowControlFailure(Throwable failure) {
        Throwable original = unwrap(failure);
        if (original instanceof JavascriptExecutionException hostFailure) throw hostFailure;
        if (original instanceof ModelClientException cancellationFailure) throw cancellationFailure;
    }

    String format(RuntimeException failure, Context context) {
        Throwable original = unwrap(failure);
        String summary = original instanceof RhinoException rhino
                ? scriptSummary(rhino, context) : nativeSummary(original);
        LinkedHashSet<String> frames = new LinkedHashSet<>();
        if (failure instanceof RhinoException rhino) {
            String origin = location(rhino.sourceName(), rhino.lineNumber(), true);
            if (origin != null) frames.add("at " + origin);
            for (ScriptStackElement frame : rhino.getScriptStack()) {
                String location = location(frame.fileName, frame.lineNumber, false);
                if (location == null) continue;
                String label = frame.functionName == null || frame.functionName.isBlank()
                        ? location : frame.functionName + " (" + location + ")";
                frames.add("at " + label);
            }
        }
        return frames.isEmpty() ? summary : summary + "\n" + String.join("\n", frames);
    }

    private static Throwable unwrap(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (seen.add(current)) {
            Throwable next;
            if (current instanceof WrappedException wrapped) next = wrapped.getWrappedException();
            else if (current instanceof JavaScriptException thrown) next = thrown.getCause();
            else break;
            if (next == null || seen.contains(next)) break;
            current = next;
        }
        return current;
    }

    private static String scriptSummary(RhinoException failure, Context context) {
        String type;
        String message;
        if (failure instanceof EcmaError error) {
            type = error.getName();
            message = error.getErrorMessage();
        } else if (failure instanceof JavaScriptException thrown) {
            Object value = thrown.getValue();
            if (value instanceof ScriptableObject error
                    && value.getClass().getName().equals("dev.latvian.mods.rhino.NativeError")) {
                String name = errorTextProperty(error, "name", context);
                type = name == null || name.isBlank() ? "Error" : name;
                message = errorTextProperty(error, "message", context);
            } else {
                type = "JavaScriptException";
                message = textValue(value);
            }
            if (message == null) message = "Thrown value has no plain-text message";
        } else {
            type = failure instanceof EvaluatorException ? "SyntaxError" : failure.getClass().getSimpleName();
            message = failure.details();
        }
        return summary(type, message);
    }

    /** Reads only NativeError data properties; reporting must not execute guest getters or toString. */
    private static String errorTextProperty(ScriptableObject error, String name, Context context) {
        Set<Scriptable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Scriptable current = error;
        while (current instanceof ScriptableObject object
                && current.getClass().getName().equals("dev.latvian.mods.rhino.NativeError")
                && seen.add(current)) {
            if (object.has(context, name, object)) {
                if (object.getGetterOrSetter(context, name, 0, object, false) != null
                        || object.getGetterOrSetter(context, name, 0, object, true) != null) return null;
                return textValue(object.get(context, name, object));
            }
            current = object.getPrototype(context);
        }
        return null;
    }

    private static String textValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return text;
        if (value instanceof CharSequence
                && value.getClass().getName().equals("dev.latvian.mods.rhino.ConsString")) return value.toString();
        if (value instanceof Boolean || value instanceof Double || value instanceof Integer) return value.toString();
        return null;
    }

    private static String nativeSummary(Throwable failure) {
        return summary(failure.getClass().getSimpleName(), failure.getMessage());
    }

    private static String summary(String type, String message) {
        return type + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private String location(String name, int line, boolean origin) {
        Source source = sources.get(name);
        if (source == null || line <= source.prefixLines()) return null;
        int relative = line - source.prefixLines();
        if (relative > source.lines()) {
            if (!origin) return null;
            relative = source.lines(); // Closing-wrapper parser errors refer to end-of-input.
        }
        return name + ":" + relative;
    }

    private static int lineCount(String source) {
        return source.split("\r\n|[\n\r\u2028\u2029]", -1).length;
    }

    @dev.openallay.value.ValueType(Source.ValueSchemaProvider.class)
private static final class Source {
    private final int prefixLines;
    private final int lines;
    private Source(int prefixLines, int lines) {
        this.prefixLines = prefixLines;
        this.lines = lines;
    }
    public int prefixLines() { return prefixLines; }
    public int lines() { return lines; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Source)) return false;
        Source that = (Source) other;
        return prefixLines == that.prefixLines && lines == that.lines;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(prefixLines);
        hash = 31 * hash + Integer.hashCode(lines);
        return hash;
    }
    @Override public String toString() { return "Source[prefixLines=" + prefixLines + ", lines=" + lines + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Source> schema() {
            return new dev.openallay.value.ValueSchema<>(Source.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Source>>asList(new dev.openallay.value.ValueSchema.Component<>(Source.class, "prefixLines", Source::prefixLines), new dev.openallay.value.ValueSchema.Component<>(Source.class, "lines", Source::lines)), arguments -> new Source((Integer) arguments[0], (Integer) arguments[1]));
        }
    }
}
}
