package dev.openallay.script;

import com.google.gson.JsonElement;
import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.openallay.model.CancellationSignal;
import dev.openallay.extension.JavascriptInvocationScope;
import dev.openallay.context.SourceObservation;
import dev.openallay.model.ModelClientException;
import dev.openallay.script.host.RhinoHostAdapter;
import dev.openallay.script.schema.DeclaredHostRoots;
import dev.openallay.script.schema.HostSchemaCatalog;
import dev.openallay.script.command.JavascriptCommandBridge;
import dev.openallay.world.JavascriptWorldBridge;
import dev.openallay.script.result.JavascriptResultShape;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.Set;

public final class RhinoJavascriptRuntime {
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    private static final String HELPERS = """
            const __openallayArrayView = value =>
              Array.isArray(value)
              || (value !== null
                && typeof value === "object"
                && Object.getPrototypeOf(value) === Array.prototype
                && Number.isSafeInteger(Number(value.length)));
            const helpers = Object.freeze({
              groupBy(values, key) {
                return values.reduce((groups, value) => {
                  const group = String(key(value));
                  (groups[group] ??= []).push(value);
                  return groups;
                }, {});
              },
              sum(values, select = value => value) {
                return values.reduce((total, value) => total + Number(select(value)), 0);
              },
              minBy(values, select) {
                return values.reduce((best, value) =>
                  best === undefined || select(value) < select(best) ? value : best, undefined);
              },
              maxBy(values, select) {
                return values.reduce((best, value) =>
                  best === undefined || select(value) > select(best) ? value : best, undefined);
              },
              schema(value, depth) {
                // Rhino's interpreter reuses lexical bindings in nested/repeated callbacks.
                // Keep recursive traversal callback-free, with loop state in this call frame.
                function visit(current, remaining) {
                  if (current === null) return "null";
                  if (__openallayArrayView(current)) {
                    if (remaining <= 0 || current.length === 0) return [];
                    const selected = current.slice(0, 8);
                    const samples = [];
                    const signatures = [];
                    let sample;
                    let signature;
                    for (let index = 0; index < selected.length; index++) {
                      if (!(index in selected)) continue;
                      sample = visit(selected[index], remaining - 1);
                      signature = JSON.stringify(sample);
                      if (!signatures.includes(signature)) {
                        samples.push(sample);
                        signatures.push(signature);
                      }
                    }
                    return samples;
                  }
                  if (typeof current !== "object") return typeof current;
                  if (remaining <= 0) return "object";
                  const keys = Object.keys(current).sort();
                  const entries = [];
                  for (let index = 0; index < keys.length; index++) {
                    entries.push([keys[index], visit(current[keys[index]], remaining - 1)]);
                  }
                  return Object.fromEntries(entries);
                }
                return visit(value, depth === undefined ? 3 : Math.max(0, Number(depth) || 0));
              }
            });
            """;

    private final Duration timeout;
    private final JavascriptRuntimeLimits limits;
    private final RhinoJsonNormalizer normalizer;
    private final JavascriptModuleCatalog modules;

    public RhinoJavascriptRuntime() {
        this(DEFAULT_TIMEOUT, JavascriptRuntimeLimits.DEFAULT, JavascriptModuleCatalog.bundled());
    }

    public RhinoJavascriptRuntime(Duration timeout) {
        this(timeout, JavascriptRuntimeLimits.DEFAULT, JavascriptModuleCatalog.bundled());
    }

    public RhinoJavascriptRuntime(Duration timeout, JavascriptRuntimeLimits limits) {
        this(timeout, limits, JavascriptModuleCatalog.bundled());
    }

    public RhinoJavascriptRuntime(
            Duration timeout,
            JavascriptRuntimeLimits limits,
            JavascriptModuleCatalog modules) {
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.modules = Objects.requireNonNull(modules, "modules");
        this.normalizer = new RhinoJsonNormalizer(limits);
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public JavascriptExecution execute(
            String source,
            Map<String, Object> minecraftRoots,
            Map<String, JsonElement> workspaceValues,
            CancellationSignal cancellation) {
        return execute(
                source,
                minecraftRoots,
                workspaceValues,
                Map.of(),
                cancellation,
                null,
                null);
    }

    public JavascriptExecution execute(
            String source,
            Map<String, Object> minecraftRoots,
            Map<String, JsonElement> workspaceValues,
            CancellationSignal cancellation,
            JavascriptCommandBridge commands) {
        return execute(
                source,
                minecraftRoots,
                workspaceValues,
                Map.of(),
                cancellation,
                commands,
                null);
    }

    public JavascriptExecution execute(
            String source,
            Map<String, Object> minecraftRoots,
            Map<String, JsonElement> workspaceValues,
            Map<String, JavascriptResultShape> workspaceShapes,
            CancellationSignal cancellation,
            JavascriptCommandBridge commands) {
        return execute(
                source,
                minecraftRoots,
                workspaceValues,
                workspaceShapes,
                cancellation,
                commands,
                null);
    }

    public JavascriptExecution execute(
            String source,
            Map<String, Object> minecraftRoots,
            Map<String, JsonElement> workspaceValues,
            Map<String, JavascriptResultShape> workspaceShapes,
            CancellationSignal cancellation,
            JavascriptCommandBridge commands,
            JavascriptWorldBridge world) {
        return execute(source, minecraftRoots, workspaceValues, workspaceShapes, cancellation, commands, world, false);
    }

    public JavascriptExecution execute(
            String source, Map<String, Object> minecraftRoots, Map<String, JsonElement> workspaceValues,
            Map<String, JavascriptResultShape> workspaceShapes, CancellationSignal cancellation,
            JavascriptCommandBridge commands, JavascriptWorldBridge world, boolean unrestricted) {
        return execute(source, minecraftRoots, workspaceValues, workspaceShapes, Map.of(),
                ignored -> {}, cancellation, commands, world, unrestricted);
    }

    public JavascriptExecution execute(
            String source,
            Map<String, Object> minecraftRoots,
            Map<String, JsonElement> workspaceValues,
            Map<String, JavascriptResultShape> workspaceShapes,
            Map<String, List<SourceObservation>> workspaceSources,
            Consumer<SourceObservation> workspaceSourceRecorder,
            CancellationSignal cancellation,
            JavascriptCommandBridge commands,
            JavascriptWorldBridge world,
            boolean unrestricted) {
        return execute(source, minecraftRoots, workspaceValues, workspaceShapes, workspaceSources,
                workspaceSourceRecorder, cancellation, commands, world, unrestricted, null);
    }

    public JavascriptExecution execute(
            String source,
            Map<String, Object> minecraftRoots,
            Map<String, JsonElement> workspaceValues,
            Map<String, JavascriptResultShape> workspaceShapes,
            Map<String, List<SourceObservation>> workspaceSources,
            Consumer<SourceObservation> workspaceSourceRecorder,
            CancellationSignal cancellation,
            JavascriptCommandBridge commands,
            JavascriptWorldBridge world,
            boolean unrestricted,
            JavascriptInvocationScope extensionScope) {
        if (source == null || source.isBlank()) {
            throw new JavascriptExecutionException(
                    "javascript_invalid", "JavaScript source must not be blank");
        }
        if (!unrestricted && source.length() > limits.maxSourceCharacters()) {
            throw new JavascriptExecutionException(
                    "javascript_source_too_large",
                    "JavaScript source exceeds the execution budget");
        }
        Objects.requireNonNull(minecraftRoots, "minecraftRoots");
        Objects.requireNonNull(workspaceValues, "workspaceValues");
        Objects.requireNonNull(workspaceShapes, "workspaceShapes");
        Objects.requireNonNull(workspaceSources, "workspaceSources");
        Objects.requireNonNull(workspaceSourceRecorder, "workspaceSourceRecorder");
        Objects.requireNonNull(cancellation, "cancellation").throwIfCancelled();

        long started = System.nanoTime();
        OpenAllayRhinoContextFactory factory =
                new OpenAllayRhinoContextFactory(cancellation, timeout, unrestricted);
        Context context = factory.enter();
        JavascriptFailureFormatter failures = new JavascriptFailureFormatter(source);
        try {
            ScriptableObject scope = unrestricted
                    ? context.initStandardObjects(null, false)
                    : context.initSafeStandardObjects(null, false);
            RhinoBuiltinBindings.install(context, scope, limits, unrestricted);
            if (unrestricted) installJavaBridge(context, scope);
            RhinoHostAdapter adapter = new RhinoHostAdapter(context, scope);
            defineGlobal(context, scope, "mc", adapter.adapt(minecraftRoots));
            defineGlobal(
                    context,
                    scope,
                    "schema",
                    schemaApi(
                            context,
                            scope,
                            adapter,
                            minecraftRoots instanceof DeclaredHostRoots declared
                                    ? declared.schemaCatalog()
                                    : new HostSchemaCatalog(List.of())));
            defineGlobal(
                    context,
                    scope,
                    "workspace",
                    workspace(context, scope, adapter, workspaceValues, workspaceShapes,
                            workspaceSources, workspaceSourceRecorder));
            if (commands != null) {
                defineGlobal(context, scope, "commands", commands.bind(context, scope, adapter));
            }
            if (world != null) {
                defineGlobal(context, scope, "world", world.bind(context, scope, adapter));
            }
            Map<String, Scriptable> extensionBindings = RhinoExtensionBindings.bind(
                    context, scope, adapter, normalizer, extensionScope);
            LinkedHashSet<String> usedModules = new LinkedHashSet<>();
            defineGlobal(
                    context,
                    scope,
                    "require",
                    moduleLoader(context, scope, usedModules, failures, extensionBindings));
            installHelpers(context, scope);
            String program = buildProgram(source);
            Object value = context.evaluateString(
                    scope, program, JavascriptFailureFormatter.USER_SOURCE, 1, null);
            ((OpenAllayRhinoContext) context).checkBudget();
            RhinoJsonNormalizer.Result normalized = unrestricted
                    ? normalizer.normalizeUnrestricted(value, context)
                    : normalizer.normalize(value, context);
            return new JavascriptExecution(
                    normalized.value(),
                    normalized.shape(),
                    Duration.ofNanos(System.nanoTime() - started),
                    List.copyOf(usedModules));
        } catch (JavascriptExecutionException failure) {
            throw failure;
        } catch (ModelClientException cancellationFailure) {
            throw cancellationFailure;
        } catch (RuntimeException failure) {
            JavascriptFailureFormatter.rethrowControlFailure(failure);
            throw new JavascriptExecutionException(
                    "javascript_error", failures.format(failure, context), failure);
        }
    }

    private static void installJavaBridge(Context context, ScriptableObject scope) {
        BaseFunction type = new BaseFunction(scope, ScriptableObject.getFunctionPrototype(scope, context)) {
            @Override public Object call(Context cx, Scriptable callScope, Scriptable thisObject, Object[] args) {
                if (args.length != 1) throw new JavascriptExecutionException("javascript_class_invalid", "Java.type requires one class name");
                String name = cx.toString(args[0]);
                try { return context.wrapJavaClass(scope, Class.forName(name, true, cx.getApplicationClassLoader())); }
                catch (ClassNotFoundException e) { throw new JavascriptExecutionException("javascript_class_unavailable", "Java class is unavailable: " + name); }
            }
            @Override public String getFunctionName() { return "type"; }
        };
        ScriptableObject java = (ScriptableObject) context.newObject(scope);
        ScriptableObject.defineProperty(java, "type", type, ScriptableObject.READONLY | ScriptableObject.PERMANENT, context);
        defineGlobal(context, scope, "Java", java);
    }

    private static void installHelpers(Context context, ScriptableObject scope) {
        Object value = context.evaluateString(
                scope,
                "(function() {\n\"use strict\";\n" + HELPERS + "\nreturn helpers;\n})()",
                "openallay-runtime.js",
                1,
                null);
        defineGlobal(context, scope, "helpers", value);
    }

    private static String buildProgram(String source) {
        // Keep the two wrapper lines in sync with JavascriptFailureFormatter.USER_PREFIX_LINES.
        return "(function() {\n\"use strict\";\n" + source + "\n})()";
    }

    private static void defineGlobal(
            Context context, ScriptableObject scope, String name, Object value) {
        ScriptableObject.defineProperty(
                scope,
                name,
                value,
                ScriptableObject.READONLY
                        | ScriptableObject.PERMANENT
                        | ScriptableObject.DONTENUM,
                context);
    }

    private static Scriptable workspace(
            Context context,
            ScriptableObject scope,
            RhinoHostAdapter adapter,
            Map<String, JsonElement> values,
            Map<String, JavascriptResultShape> shapes,
            Map<String, List<SourceObservation>> sources,
            Consumer<SourceObservation> sourceRecorder) {
        Scriptable workspace = context.newObject(scope);
        Set<String> opened = new java.util.HashSet<>();
        BaseFunction open = new BaseFunction(
                scope, ScriptableObject.getFunctionPrototype(scope, context)) {
            @Override
            public String getFunctionName() {
                return "open";
            }

            @Override
            public Object call(
                    Context callContext,
                    Scriptable callScope,
                    Scriptable thisObject,
                    Object[] arguments) {
                if (arguments.length != 1 || !(arguments[0] instanceof CharSequence handle)) {
                    throw new JavascriptExecutionException(
                            "workspace_handle_unavailable",
                            "workspace.open requires one selected result handle");
                }
                JsonElement value = values.get(handle.toString());
                if (value == null) {
                    throw new JavascriptExecutionException(
                            "workspace_handle_unavailable",
                            "Result handle is unavailable in this execution");
                }
                if (opened.add(handle.toString())) {
                    sources.getOrDefault(handle.toString(), List.of()).forEach(sourceRecorder);
                }
                JavascriptResultShape shape = shapes.get(handle.toString());
                return shape == null
                        ? adapter.adapt(value)
                        : adapter.adaptWorkspace(value, shape);
            }

            @Override
            public Scriptable construct(
                    Context callContext, Scriptable callScope, Object[] arguments) {
                throw new JavascriptExecutionException(
                        "javascript_host_access_denied",
                        "Host functions are not constructors");
            }
        };
        ScriptableObject.defineProperty(
                workspace,
                "open",
                open,
                ScriptableObject.READONLY | ScriptableObject.PERMANENT,
                context);
        if (workspace instanceof ScriptableObject object) {
            object.preventExtensions();
        }
        return workspace;
    }

    private static Scriptable schemaApi(
            Context context,
            ScriptableObject scope,
            RhinoHostAdapter adapter,
            HostSchemaCatalog catalog) {
        Scriptable api = context.newObject(scope);
        BaseFunction list = new BaseFunction(
                scope, ScriptableObject.getFunctionPrototype(scope, context)) {
            @Override
            public String getFunctionName() {
                return "list";
            }

            @Override
            public Object call(
                    Context callContext,
                    Scriptable callScope,
                    Scriptable thisObject,
                    Object[] arguments) {
                if (arguments.length != 0) {
                    throw new JavascriptExecutionException(
                            "javascript_schema_invalid",
                            "schema.list does not accept arguments");
                }
                return adapter.adapt(catalog.list());
            }
        };
        BaseFunction describe = new BaseFunction(
                scope, ScriptableObject.getFunctionPrototype(scope, context)) {
            @Override
            public String getFunctionName() {
                return "describe";
            }

            @Override
            public Object call(
                    Context callContext,
                    Scriptable callScope,
                    Scriptable thisObject,
                    Object[] arguments) {
                if (arguments.length != 1 || !(arguments[0] instanceof CharSequence path)) {
                    throw new JavascriptExecutionException(
                            "javascript_schema_invalid",
                            "schema.describe requires one exact declared path");
                }
                Object described = catalog.describe(path.toString())
                        .orElseThrow(() -> new JavascriptExecutionException(
                                "javascript_schema_unavailable",
                                "Declared JavaScript schema path is unavailable: " + path));
                return adapter.adapt(described);
            }
        };
        ScriptableObject.defineProperty(
                api,
                "list",
                list,
                ScriptableObject.READONLY | ScriptableObject.PERMANENT,
                context);
        ScriptableObject.defineProperty(
                api,
                "describe",
                describe,
                ScriptableObject.READONLY | ScriptableObject.PERMANENT,
                context);
        if (api instanceof ScriptableObject object) {
            object.preventExtensions();
        }
        return api;
    }

    private BaseFunction moduleLoader(
            Context context,
            ScriptableObject scope,
            LinkedHashSet<String> usedModules,
            JavascriptFailureFormatter failures,
            Map<String, Scriptable> extensionBindings) {
        LinkedHashMap<String, Object> cache = new LinkedHashMap<>();
        Set<String> loading = new java.util.HashSet<>();
        return new BaseFunction(
                scope, ScriptableObject.getFunctionPrototype(scope, context)) {
            @Override
            public String getFunctionName() {
                return "require";
            }

            @Override
            public Object call(
                    Context callContext,
                    Scriptable callScope,
                    Scriptable thisObject,
                    Object[] arguments) {
                if (arguments.length != 1 || !(arguments[0] instanceof CharSequence idValue)) {
                    throw new JavascriptExecutionException(
                            "javascript_module_unavailable",
                            "require needs one exact bundled module id");
                }
                String id = idValue.toString();
                Scriptable binding = extensionBindings.get(id);
                if (binding != null) {
                    usedModules.add(id);
                    return binding;
                }
                if (cache.containsKey(id)) {
                    usedModules.add(id);
                    return cache.get(id);
                }
                if (!loading.add(id)) {
                    throw new JavascriptExecutionException(
                            "javascript_module_error",
                            "Cyclic JavaScript module dependency: " + id);
                }
                try {
                    String moduleSource = modules.source(id);
                    failures.registerModule(id, moduleSource);
                    // Six wrapper lines precede module source; the formatter maps them out.
                    String program = """
                            (function() {
                              "use strict";
                              const module = {exports: {}};
                              const exports = module.exports;
                              (function(module, exports, require) {
                                "use strict";
                                %s
                              })(module, exports, require);
                              return module.exports;
                            })()
                            """.formatted(moduleSource);
                    Object exports = callContext.evaluateString(
                            scope, program, JavascriptFailureFormatter.moduleSourceName(id), 1, null);
                    cache.put(id, exports);
                    usedModules.add(id);
                    return exports;
                } catch (JavascriptExecutionException failure) {
                    throw failure;
                } catch (ModelClientException cancellationFailure) {
                    throw cancellationFailure;
                } catch (RuntimeException failure) {
                    JavascriptFailureFormatter.rethrowControlFailure(failure);
                    throw new JavascriptExecutionException(
                            "javascript_module_error",
                            failures.format(failure, callContext),
                            failure);
                } finally {
                    loading.remove(id);
                }
            }

            @Override
            public Scriptable construct(
                    Context callContext, Scriptable callScope, Object[] arguments) {
                throw new JavascriptExecutionException(
                        "javascript_host_access_denied",
                        "require is not a constructor");
            }
        };
    }
}
