package dev.openallay.script.command;

import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.openallay.model.CancellationSignal;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.host.RhinoHostAdapter;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;

/** Builds the closed commands.list/describe/run object without exposing Java methods. */
public final class JavascriptCommandBridge {
    private final CommandCapabilityRuntime.RequestCapability capability;
    private final CancellationSignal cancellation;
    private final java.util.function.BiFunction<String, Instant, EvidenceMetadata> evidence;
    private final java.util.function.Consumer<EvidenceMetadata> recordEvidence;

    JavascriptCommandBridge(
            CommandCapabilityRuntime.RequestCapability capability,
            CancellationSignal cancellation) {
        this(capability, cancellation, (kind, capturedAt) -> null, ignored -> {});
    }

    JavascriptCommandBridge(
            CommandCapabilityRuntime.RequestCapability capability,
            CancellationSignal cancellation,
            java.util.function.BiFunction<String, Instant, EvidenceMetadata> evidence,
            java.util.function.Consumer<EvidenceMetadata> recordEvidence) {
        this.capability = Objects.requireNonNull(capability, "capability");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.recordEvidence = Objects.requireNonNull(recordEvidence, "recordEvidence");
    }

    public Scriptable bind(
            Context context, ScriptableObject scope, RhinoHostAdapter adapter) {
        Scriptable commands = context.newObject(scope);
        define(context, scope, commands, "list", 0,
                ignored -> observed(capability.catalog(), "catalog", capability.catalog().capturedAt()), adapter);
        define(
                context,
                scope,
                commands,
                "describe",
                1,
                arguments -> observed(
                        capability.catalog().describe(string(arguments[0], "commands.describe"))
                                .orElseThrow(() -> new JavascriptExecutionException(
                                        "command_path_unavailable",
                                        "Command path is unavailable in this request")),
                        "catalog", capability.catalog().capturedAt()),
                adapter);
        define(
                context,
                scope,
                commands,
                "run",
                1,
                arguments -> observed(
                        capability.submit(string(arguments[0], "commands.run"), cancellation),
                        "feedback", Instant.now()),
                adapter);
        final class $oaPattern0_Holder { dev.latvian.mods.rhino.Scriptable value; ScriptableObject bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = commands) instanceof dev.latvian.mods.rhino.ScriptableObject && (($oaPattern0_holder.bound = (ScriptableObject) $oaPattern0_holder.value) != null))) {
            $oaPattern0_holder.bound.preventExtensions();
        }
        return commands;
    }

    private Object observed(Object value, String kind, Instant capturedAt) {
        EvidenceMetadata metadata = evidence.apply(kind, capturedAt);
        if (metadata != null) recordEvidence.accept(metadata);
        return value;
    }

    private static void define(
            Context context,
            ScriptableObject scope,
            Scriptable target,
            String name,
            int arity,
            Function<Object[], Object> invocation,
            RhinoHostAdapter adapter) {
        BaseFunction function = new BaseFunction(
                scope, ScriptableObject.getFunctionPrototype(scope, context)) {
            @Override
            public String getFunctionName() {
                return name;
            }

            @Override
            public Object call(
                    Context callContext,
                    Scriptable callScope,
                    Scriptable thisObject,
                    Object[] arguments) {
                if (arguments.length != arity) {
                    throw new JavascriptExecutionException(
                            "command_invalid",
                            name + " requires " + arity + " argument(s)");
                }
                return adapter.adapt(invocation.apply(arguments));
            }

            @Override
            public Scriptable construct(
                    Context callContext, Scriptable callScope, Object[] arguments) {
                throw new JavascriptExecutionException(
                        "javascript_host_access_denied",
                        "Command functions are not constructors");
            }
        };
        ScriptableObject.defineProperty(
                target,
                name,
                function,
                ScriptableObject.READONLY | ScriptableObject.PERMANENT,
                context);
    }

    private static String string(Object value, String operation) {
        final class $oaPattern1_Holder { java.lang.Object value; CharSequence bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if (!((($oaPattern1_holder.value = value) instanceof java.lang.CharSequence && (($oaPattern1_holder.bound = (CharSequence) $oaPattern1_holder.value) != null)))) {
            throw new JavascriptExecutionException(
                    "command_invalid", operation + " requires one command string");
        }
        return $oaPattern1_holder.bound.toString();
    }
}
