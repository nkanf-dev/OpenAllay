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

    private static final String HELPERS = "const __openallayArrayView = value =>\n  Array.isArray(value)\n  || (value !== null\n    && typeof value === \"object\"\n    && Object.getPrototypeOf(value) === Array.prototype\n    && Number.isSafeInteger(Number(value.length)));\nconst helpers = Object.freeze({\n  groupBy(values, key) {\n    return values.reduce((groups, value) => {\n      const group = String(key(value));\n      (groups[group] ??= []).push(value);\n      return groups;\n    }, {});\n  },\n  sum(values, select = value => value) {\n    return values.reduce((total, value) => total + Number(select(value)), 0);\n  },\n  minBy(values, select) {\n    return values.reduce((best, value) =>\n      best === undefined || select(value) < select(best) ? value : best, undefined);\n  },\n  maxBy(values, select) {\n    return values.reduce((best, value) =>\n      best === undefined || select(value) > select(best) ? value : best, undefined);\n  },\n  schema(value, depth) {\n    // Rhino's interpreter reuses lexical bindings in nested/repeated callbacks.\n    // Keep recursive traversal callback-free, with loop state in this call frame.\n    function visit(current, remaining) {\n      if (current === null) return \"null\";\n      if (__openallayArrayView(current)) {\n        if (remaining <= 0 || current.length === 0) return [];\n        const selected = current.slice(0, 8);\n        const samples = [];\n        const signatures = [];\n        let sample;\n        let signature;\n        for (let index = 0; index < selected.length; index++) {\n          if (!(index in selected)) continue;\n          sample = visit(selected[index], remaining - 1);\n          signature = JSON.stringify(sample);\n          if (!signatures.includes(signature)) {\n            samples.push(sample);\n            signatures.push(signature);\n          }\n        }\n        return samples;\n      }\n      if (typeof current !== \"object\") return typeof current;\n      if (remaining <= 0) return \"object\";\n      const keys = Object.keys(current).sort();\n      const entries = [];\n      for (let index = 0; index < keys.length; index++) {\n        entries.push([keys[index], visit(current[keys[index]], remaining - 1)]);\n      }\n      return Object.fromEntries(entries);\n    }\n    return visit(value, depth === undefined ? 3 : Math.max(0, Number(depth) || 0));\n  }\n});\n";

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
                dev.openallay.util.Java8Collections.mapOf(),
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
                dev.openallay.util.Java8Collections.mapOf(),
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
        return execute(source, minecraftRoots, workspaceValues, workspaceShapes, dev.openallay.util.Java8Collections.mapOf(),
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
        if (source == null || dev.openallay.util.Java8Strings.isBlank(source)) {
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
            final class $oaPattern0_Holder { java.util.Map<java.lang.String, java.lang.Object> value; DeclaredHostRoots bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
defineGlobal(
                    context,
                    scope,
                    "schema",
                    schemaApi(
                            context,
                            scope,
                            adapter,
                            (($oaPattern0_holder.value = minecraftRoots) instanceof dev.openallay.script.schema.DeclaredHostRoots && (($oaPattern0_holder.bound = (DeclaredHostRoots) $oaPattern0_holder.value) != null))
                                    ? $oaPattern0_holder.bound.schemaCatalog()
                                    : new HostSchemaCatalog(dev.openallay.util.Java8Collections.listOf())));
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
                    dev.openallay.util.Java8Collections.listCopyOf(usedModules));
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

    /** Runs only trusted native work outside the interpreter budget, never Agent callbacks. */
    public static <T> T callNative(Context context, java.util.concurrent.Callable<T> action) throws Exception {
        return ((OpenAllayRhinoContext) context).callNative(action);
    }

    private static void installJavaBridge(Context context, ScriptableObject scope) {
        defineGlobal(context, scope, "Java", UnrestrictedJavaAccess.bind(context, scope));
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
                final class $oaPattern1_Holder { java.lang.Object value; CharSequence bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if (arguments.length != 1 || !((($oaPattern1_holder.value = arguments[0]) instanceof java.lang.CharSequence && (($oaPattern1_holder.bound = (CharSequence) $oaPattern1_holder.value) != null)))) {
                    throw new JavascriptExecutionException(
                            "workspace_handle_unavailable",
                            "workspace.open requires one selected result handle");
                }
                JsonElement value = values.get($oaPattern1_holder.bound.toString());
                if (value == null) {
                    throw new JavascriptExecutionException(
                            "workspace_handle_unavailable",
                            "Result handle is unavailable in this execution");
                }
                if (opened.add($oaPattern1_holder.bound.toString())) {
                    sources.getOrDefault($oaPattern1_holder.bound.toString(), dev.openallay.util.Java8Collections.listOf()).forEach(sourceRecorder);
                }
                JavascriptResultShape shape = shapes.get($oaPattern1_holder.bound.toString());
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
        final class $oaPattern2_Holder { dev.latvian.mods.rhino.Scriptable value; ScriptableObject bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = workspace) instanceof dev.latvian.mods.rhino.ScriptableObject && (($oaPattern2_holder.bound = (ScriptableObject) $oaPattern2_holder.value) != null))) {
            $oaPattern2_holder.bound.preventExtensions();
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
                final class $oaPattern3_Holder { java.lang.Object value; CharSequence bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if (arguments.length != 1 || !((($oaPattern3_holder.value = arguments[0]) instanceof java.lang.CharSequence && (($oaPattern3_holder.bound = (CharSequence) $oaPattern3_holder.value) != null)))) {
                    throw new JavascriptExecutionException(
                            "javascript_schema_invalid",
                            "schema.describe requires one exact declared path");
                }
                Object described = catalog.describe($oaPattern3_holder.bound.toString())
                        .orElseThrow(() -> new JavascriptExecutionException(
                                "javascript_schema_unavailable",
                                "Declared JavaScript schema path is unavailable: " + $oaPattern3_holder.bound));
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
        final class $oaPattern4_Holder { dev.latvian.mods.rhino.Scriptable value; ScriptableObject bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = api) instanceof dev.latvian.mods.rhino.ScriptableObject && (($oaPattern4_holder.bound = (ScriptableObject) $oaPattern4_holder.value) != null))) {
            $oaPattern4_holder.bound.preventExtensions();
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
                final class $oaPattern5_Holder { java.lang.Object value; CharSequence bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if (arguments.length != 1 || !((($oaPattern5_holder.value = arguments[0]) instanceof java.lang.CharSequence && (($oaPattern5_holder.bound = (CharSequence) $oaPattern5_holder.value) != null)))) {
                    throw new JavascriptExecutionException(
                            "javascript_module_unavailable",
                            "require needs one exact bundled module id");
                }
                String id = $oaPattern5_holder.bound.toString();
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
                    String program = "(function() {\n  \"use strict\";\n  const module = {exports: {}};\n  const exports = module.exports;\n  (function(module, exports, require) {\n    \"use strict\";\n    %s\n  })(module, exports, require);\n  return module.exports;\n})()\n".formatted(moduleSource);
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
