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
        final class $oaPattern0_Holder { java.lang.Throwable value; JavascriptExecutionException bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = original) instanceof dev.openallay.script.JavascriptExecutionException && (($oaPattern0_holder.bound = (JavascriptExecutionException) $oaPattern0_holder.value) != null))) throw $oaPattern0_holder.bound;
        final class $oaPattern1_Holder { java.lang.Throwable value; ModelClientException bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = original) instanceof dev.openallay.model.ModelClientException && (($oaPattern1_holder.bound = (ModelClientException) $oaPattern1_holder.value) != null))) throw $oaPattern1_holder.bound;
    }

    String format(RuntimeException failure, Context context) {
        Throwable original = unwrap(failure);
        final class $oaPattern2_Holder { java.lang.Throwable value; RhinoException bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
String summary = (($oaPattern2_holder.value = original) instanceof dev.latvian.mods.rhino.RhinoException && (($oaPattern2_holder.bound = (RhinoException) $oaPattern2_holder.value) != null))
                ? scriptSummary($oaPattern2_holder.bound, context) : nativeSummary(original);
        LinkedHashSet<String> frames = new LinkedHashSet<>();
        final class $oaPattern3_Holder { java.lang.RuntimeException value; RhinoException bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = failure) instanceof dev.latvian.mods.rhino.RhinoException && (($oaPattern3_holder.bound = (RhinoException) $oaPattern3_holder.value) != null))) {
            String origin = location($oaPattern3_holder.bound.sourceName(), $oaPattern3_holder.bound.lineNumber(), true);
            if (origin != null) frames.add("at " + origin);
            for (ScriptStackElement frame : $oaPattern3_holder.bound.getScriptStack()) {
                String location = location(frame.fileName, frame.lineNumber, false);
                if (location == null) continue;
                String label = frame.functionName == null || dev.openallay.util.Java8Strings.isBlank(frame.functionName)
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
            final class $oaPattern4_Holder { java.lang.Throwable value; WrappedException bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = current) instanceof dev.latvian.mods.rhino.WrappedException && (($oaPattern4_holder.bound = (WrappedException) $oaPattern4_holder.value) != null))) next = $oaPattern4_holder.bound.getWrappedException();
            else {
final class $oaPattern5_Holder { java.lang.Throwable value; JavaScriptException bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = current) instanceof dev.latvian.mods.rhino.JavaScriptException && (($oaPattern5_holder.bound = (JavaScriptException) $oaPattern5_holder.value) != null))) next = $oaPattern5_holder.bound.getCause();
            else break;
}
            if (next == null || seen.contains(next)) break;
            current = next;
        }
        return current;
    }

    private static String scriptSummary(RhinoException failure, Context context) {
        String type;
        String message;
        final class $oaPattern6_Holder { dev.latvian.mods.rhino.RhinoException value; EcmaError bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = failure) instanceof dev.latvian.mods.rhino.EcmaError && (($oaPattern6_holder.bound = (EcmaError) $oaPattern6_holder.value) != null))) {
            type = $oaPattern6_holder.bound.getName();
            message = $oaPattern6_holder.bound.getErrorMessage();
        } else {
final class $oaPattern7_Holder { dev.latvian.mods.rhino.RhinoException value; JavaScriptException bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = failure) instanceof dev.latvian.mods.rhino.JavaScriptException && (($oaPattern7_holder.bound = (JavaScriptException) $oaPattern7_holder.value) != null))) {
            Object value = $oaPattern7_holder.bound.getValue();
            final class $oaPattern8_Holder { java.lang.Object value; ScriptableObject bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = value) instanceof dev.latvian.mods.rhino.ScriptableObject && (($oaPattern8_holder.bound = (ScriptableObject) $oaPattern8_holder.value) != null))
                    && value.getClass().getName().equals("dev.latvian.mods.rhino.NativeError")) {
                String name = errorTextProperty($oaPattern8_holder.bound, "name", context);
                type = name == null || dev.openallay.util.Java8Strings.isBlank(name) ? "Error" : name;
                message = errorTextProperty($oaPattern8_holder.bound, "message", context);
            } else {
                type = "JavaScriptException";
                message = textValue(value);
            }
            if (message == null) message = "Thrown value has no plain-text message";
        } else {
            type = failure instanceof EvaluatorException ? "SyntaxError" : failure.getClass().getSimpleName();
            message = failure.details();
        }
}
        return summary(type, message);
    }

    /** Reads only NativeError data properties; reporting must not execute guest getters or toString. */
    private static String errorTextProperty(ScriptableObject error, String name, Context context) {
        Set<Scriptable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Scriptable current = error;
        final class $oaPattern9_Holder { dev.latvian.mods.rhino.Scriptable value; ScriptableObject bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
while ((($oaPattern9_holder.value = current) instanceof dev.latvian.mods.rhino.ScriptableObject && (($oaPattern9_holder.bound = (ScriptableObject) $oaPattern9_holder.value) != null))
                && current.getClass().getName().equals("dev.latvian.mods.rhino.NativeError")
                && seen.add(current)) {
            if ($oaPattern9_holder.bound.has(context, name, $oaPattern9_holder.bound)) {
                if ($oaPattern9_holder.bound.getGetterOrSetter(context, name, 0, $oaPattern9_holder.bound, false) != null
                        || $oaPattern9_holder.bound.getGetterOrSetter(context, name, 0, $oaPattern9_holder.bound, true) != null) return null;
                return textValue($oaPattern9_holder.bound.get(context, name, $oaPattern9_holder.bound));
            }
            current = $oaPattern9_holder.bound.getPrototype(context);
        }
        return null;
    }

    private static String textValue(Object value) {
        if (value == null) return "null";
        final class $oaPattern10_Holder { java.lang.Object value; String bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = value) instanceof java.lang.String && (($oaPattern10_holder.bound = (String) $oaPattern10_holder.value) != null))) return $oaPattern10_holder.bound;
        if (value instanceof CharSequence
                && value.getClass().getName().equals("dev.latvian.mods.rhino.ConsString")) return value.toString();
        if (value instanceof Boolean || value instanceof Double || value instanceof Integer) return value.toString();
        return null;
    }

    private static String nativeSummary(Throwable failure) {
        return summary(failure.getClass().getSimpleName(), failure.getMessage());
    }

    private static String summary(String type, String message) {
        return type + (message == null || dev.openallay.util.Java8Strings.isBlank(message) ? "" : ": " + message);
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
