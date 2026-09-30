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

    JavascriptWorldBridge(
            WorldObservationCoordinator coordinator,
            CancellationSignal cancellation,
            Consumer<EvidenceMetadata> evidence) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.evidence = Objects.requireNonNull(evidence, "evidence");
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
                arguments -> observed(await(coordinator.inspect(
                        blocksRequest(context, arguments), cancellation))),
                adapter);
        define(
                context,
                scope,
                world,
                "entities",
                1,
                2,
                arguments -> observed(await(coordinator.entities(
                        entitiesRequest(context, arguments), cancellation))),
                adapter);
        define(
                context,
                scope,
                world,
                "entity",
                1,
                1,
                arguments -> observed(await(coordinator.entity(
                        string(arguments[0], "world.entity"), cancellation))),
                adapter);
        if (world instanceof ScriptableObject object) {
            object.preventExtensions();
        }
        return world;
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
        if (!(value instanceof Scriptable scriptable)) {
            throw invalid(operation + " requires an object");
        }
        return scriptable;
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
        if (!(value instanceof CharSequence text) || text.toString().isBlank()) {
            throw invalid(operation + " requires a non-blank string");
        }
        return text.toString();
    }

    private Object observed(Object value) {
        if (value instanceof BlockObservation blocks) evidence.accept(blocks.evidence());
        else if (value instanceof EntityObservation entities) evidence.accept(entities.evidence());
        else if (value instanceof WorldEntitySnapshot entity) evidence.accept(entity.evidence());
        return value;
    }

    private Object await(CompletionStage<?> stage) {
        var future = stage.toCompletableFuture();
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
            if (cause instanceof JavascriptExecutionException rejected) {
                throw rejected;
            }
            if (cause instanceof ModelClientException cancelled) {
                throw cancelled;
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
