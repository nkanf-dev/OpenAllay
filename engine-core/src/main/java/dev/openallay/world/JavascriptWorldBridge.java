package dev.openallay.world;

import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.latvian.mods.rhino.Undefined;
import dev.openallay.model.CancellationSignal;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.model.ModelClientException;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.host.RhinoHostAdapter;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Consumer;

/** Closed Rhino surface for request-scoped, owning-thread world observations. */
public final class JavascriptWorldBridge {
    private final WorldObservationCoordinator coordinator;
    private final CancellationSignal cancellation;
    private final Consumer<EvidenceMetadata> evidence;
    private final Consumer<dev.openallay.model.image.ImageReference> images;

    JavascriptWorldBridge(
            WorldObservationCoordinator coordinator,
            CancellationSignal cancellation,
            Consumer<EvidenceMetadata> evidence,
            Consumer<dev.openallay.model.image.ImageReference> images) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.images = Objects.requireNonNull(images, "images");
    }

    public Scriptable bind(Context context, ScriptableObject scope, RhinoHostAdapter adapter) {
        Scriptable world = context.newObject(scope);
        define(
                context,
                scope,
                world,
                "inspect",
                1,
                2,
                arguments -> observed(await(context, coordinator.inspect(
                        blocksRequest(context, arguments), cancellation))),
                adapter);
        define(
                context,
                scope,
                world,
                "entities",
                1,
                2,
                arguments -> observed(await(context, coordinator.entities(
                        entitiesRequest(context, arguments), cancellation))),
                adapter);
        define(
                context,
                scope,
                world,
                "entity",
                1,
                1,
                arguments -> observed(await(context, coordinator.entity(
                        string(arguments[0], "world.entity"), cancellation))),
                adapter);
        define(context, scope, world, "focus", 0, 0,
                arguments -> observed(await(context, coordinator.focus(cancellation))), adapter);
        define(context, scope, world, "capture", 0, 1,
                arguments -> observed(await(context, coordinator.capture(viewRequest(context, arguments), cancellation))), adapter);
        final class $oaPattern0_Holder { dev.latvian.mods.rhino.Scriptable value; ScriptableObject bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = world) instanceof dev.latvian.mods.rhino.ScriptableObject && (($oaPattern0_holder.bound = (ScriptableObject) $oaPattern0_holder.value) != null))) {
            $oaPattern0_holder.bound.preventExtensions();
        }
        return world;
    }

    private static WorldViewRequest viewRequest(Context context, Object[] arguments) {
        if (arguments.length == 0) return WorldViewRequest.defaults();
        Scriptable options = scriptable(arguments[0], "world.capture options");
        for (Object id : options.getIds(context)) {
            final class $oaPattern1_Holder { java.lang.Object value; String bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if (!((($oaPattern1_holder.value = id) instanceof java.lang.String && (($oaPattern1_holder.bound = (String) $oaPattern1_holder.value) != null))) || !$oaPattern1_holder.bound.equals("target")) {
                throw invalid("world.capture has an unknown option");
            }
        }
        Object selected = optionalProperty(context, options, "target");
        WorldViewRequest.Target target = WorldViewRequest.Target.WORLD;
        if (selected != Undefined.INSTANCE && selected != null) {
            try { target = WorldViewRequest.Target.valueOf(string(selected, "world.capture target")); }
            catch (IllegalArgumentException malformed) {
                throw invalid("world.capture target must be WORLD, GAME_UI, or ASSOCIATED_UI");
            }
        }
        return new WorldViewRequest(target);
    }

    private static WorldObservationRequest blocksRequest(Context context, Object[] arguments) {
        WorldBounds bounds = bounds(context, arguments[0], "world.inspect");
        Object options = arguments.length > 1 ? arguments[1] : Undefined.INSTANCE;
        return WorldObservationRequest.blocks(
                bounds, booleanOption(context, options, "includeAir", false));
    }

    private static WorldObservationRequest entitiesRequest(Context context, Object[] arguments) {
        WorldBounds bounds = bounds(context, arguments[0], "world.entities");
        Object options = arguments.length > 1 ? arguments[1] : Undefined.INSTANCE;
        return WorldObservationRequest.entities(
                bounds, stringOption(context, options, "type"));
    }

    private static WorldBounds bounds(Context context, Object value, String operation) {
        Scriptable object = scriptable(value, operation + " bounds");
        return new WorldBounds(
                position(
                        context,
                        property(context, object, "from", operation),
                        operation + ".from"),
                position(
                        context,
                        property(context, object, "to", operation),
                        operation + ".to"));
    }

    private static WorldPosition position(Context context, Object value, String operation) {
        Scriptable position = scriptable(value, operation);
        return new WorldPosition(
                integer(context, property(context, position, "x", operation), operation + ".x"),
                integer(context, property(context, position, "y", operation), operation + ".y"),
                integer(context, property(context, position, "z", operation), operation + ".z"));
    }

    private static boolean booleanOption(
            Context context, Object value, String name, boolean fallback) {
        if (value == null || value == Undefined.INSTANCE) {
            return fallback;
        }
        Object option = optionalProperty(
                context, scriptable(value, "world options"), name);
        return option == Undefined.INSTANCE ? fallback : context.toBoolean(option);
    }

    private static String stringOption(Context context, Object value, String name) {
        if (value == null || value == Undefined.INSTANCE) {
            return "";
        }
        Object option = optionalProperty(
                context, scriptable(value, "world options"), name);
        return option == Undefined.INSTANCE || option == null
                ? ""
                : string(option, "world.entities type");
    }

    private static Scriptable scriptable(Object value, String operation) {
        final class $oaPattern2_Holder { java.lang.Object value; Scriptable bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if (!((($oaPattern2_holder.value = value) instanceof dev.latvian.mods.rhino.Scriptable && (($oaPattern2_holder.bound = (Scriptable) $oaPattern2_holder.value) != null)))) {
            throw invalid(operation + " requires an object");
        }
        return $oaPattern2_holder.bound;
    }

    private static Object property(
            Context context, Scriptable value, String name, String operation) {
        Object property = ScriptableObject.getProperty(value, name, context);
        if (property == Scriptable.NOT_FOUND) {
            throw invalid(operation + " is missing " + name);
        }
        return property;
    }

    private static Object optionalProperty(Context context, Scriptable value, String name) {
        Object property = ScriptableObject.getProperty(value, name, context);
        return property == Scriptable.NOT_FOUND ? Undefined.INSTANCE : property;
    }

    private static int integer(Context context, Object value, String operation) {
        double number = context.toNumber(value);
        if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw invalid(operation + " requires an integer");
        }
        return (int) number;
    }

    private static String string(Object value, String operation) {
        final class $oaPattern3_Holder { java.lang.Object value; CharSequence bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if (!((($oaPattern3_holder.value = value) instanceof java.lang.CharSequence && (($oaPattern3_holder.bound = (CharSequence) $oaPattern3_holder.value) != null))) || dev.openallay.util.Java8Strings.isBlank($oaPattern3_holder.bound.toString())) {
            throw invalid(operation + " requires a non-blank string");
        }
        return $oaPattern3_holder.bound.toString();
    }

    private Object observed(Object value) {
        final class $oaPattern4_Holder { java.lang.Object value; BlockObservation bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = value) instanceof dev.openallay.world.BlockObservation && (($oaPattern4_holder.bound = (BlockObservation) $oaPattern4_holder.value) != null))) evidence.accept($oaPattern4_holder.bound.evidence());
        else {
final class $oaPattern5_Holder { java.lang.Object value; EntityObservation bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = value) instanceof dev.openallay.world.EntityObservation && (($oaPattern5_holder.bound = (EntityObservation) $oaPattern5_holder.value) != null))) evidence.accept($oaPattern5_holder.bound.evidence());
        else {
final class $oaPattern6_Holder { java.lang.Object value; WorldEntitySnapshot bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = value) instanceof dev.openallay.world.WorldEntitySnapshot && (($oaPattern6_holder.bound = (WorldEntitySnapshot) $oaPattern6_holder.value) != null))) evidence.accept($oaPattern6_holder.bound.evidence());
        else {
final class $oaPattern7_Holder { java.lang.Object value; WorldFocusObservation bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = value) instanceof dev.openallay.world.WorldFocusObservation && (($oaPattern7_holder.bound = (WorldFocusObservation) $oaPattern7_holder.value) != null))) evidence.accept($oaPattern7_holder.bound.evidence());
        else {
final class $oaPattern8_Holder { java.lang.Object value; WorldViewCapture bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = value) instanceof dev.openallay.world.WorldViewCapture && (($oaPattern8_holder.bound = (WorldViewCapture) $oaPattern8_holder.value) != null))) {
            evidence.accept($oaPattern8_holder.bound.evidence());
            images.accept($oaPattern8_holder.bound.image());
        }
}
}
}
}
        return value;
    }

    private Object await(Context context, CompletionStage<?> stage) {
        try {
            // Requests are decoded and submitted before this boundary. Only the owning-thread
            // completion wait is excluded; evidence and detached result adaptation stay budgeted.
            return RhinoJavascriptRuntime.callNative(context, () -> await(stage));
        } catch (JavascriptExecutionException | ModelClientException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new JavascriptExecutionException(
                    "world_observation_failed", "World observation failed", failure);
        }
    }

    private Object await(CompletionStage<?> stage) {
        java.util.concurrent.CompletableFuture<?> future = stage.toCompletableFuture();
        try {
            while (!future.isDone()) {
                cancellation.throwIfCancelled();
                try {
                    return future.get(50, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ignored) {
                    // The Rhino worker waits; the Minecraft owning thread never does.
                }
            }
            return future.join();
        } catch (ModelClientException failure) {
            throw failure;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new JavascriptExecutionException(
                    "world_observation_cancelled",
                    "World observation was interrupted",
                    interrupted);
        } catch (ExecutionException | CompletionException failure) {
            Throwable cause = failure.getCause();
            final class $oaPattern9_Holder { java.lang.Throwable value; JavascriptExecutionException bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = cause) instanceof dev.openallay.script.JavascriptExecutionException && (($oaPattern9_holder.bound = (JavascriptExecutionException) $oaPattern9_holder.value) != null))) {
                throw $oaPattern9_holder.bound;
            }
            final class $oaPattern10_Holder { java.lang.Throwable value; ModelClientException bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = cause) instanceof dev.openallay.model.ModelClientException && (($oaPattern10_holder.bound = (ModelClientException) $oaPattern10_holder.value) != null))) {
                throw $oaPattern10_holder.bound;
            }
            throw new JavascriptExecutionException(
                    "world_observation_failed",
                    "World observation failed",
                    cause == null ? failure : cause);
        }
    }

    private static void define(
            Context context,
            ScriptableObject scope,
            Scriptable target,
            String name,
            int minimumArity,
            int maximumArity,
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
                if (arguments.length < minimumArity || arguments.length > maximumArity) {
                    throw invalid(name + " requires " + minimumArity
                            + (minimumArity == maximumArity ? "" : ".." + maximumArity)
                            + " argument(s)");
                }
                return adapter.adapt(invocation.apply(arguments));
            }

            @Override
            public Scriptable construct(
                    Context callContext, Scriptable callScope, Object[] arguments) {
                throw new JavascriptExecutionException(
                        "javascript_host_access_denied",
                        "World observation functions are not constructors");
            }
        };
        ScriptableObject.defineProperty(
                target,
                name,
                function,
                ScriptableObject.READONLY | ScriptableObject.PERMANENT,
                context);
    }

    private static JavascriptExecutionException invalid(String message) {
        return new JavascriptExecutionException("world_observation_invalid", message);
    }
}
