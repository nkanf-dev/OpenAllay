package dev.openallay.tool.builtin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.agent.tool.ToolDescription;
import dev.openallay.agent.tool.ToolOptional;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
import dev.openallay.context.EvidenceBearing;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.extension.JavascriptInvocationScope;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
import dev.openallay.script.JavascriptExecution;
import dev.openallay.script.JavascriptExecutionException;
import dev.openallay.script.RhinoJavascriptRuntime;
import dev.openallay.script.data.MinecraftAgentHostGraph;
import dev.openallay.script.command.CommandCapabilityRuntime;
import dev.openallay.script.result.JavascriptSemanticKind;
import dev.openallay.script.workspace.AgentResultWorkspace;
import dev.openallay.script.workspace.AgentResultWorkspaceRegistry;
import dev.openallay.script.workspace.JavascriptResultPresenter;
import dev.openallay.script.workspace.WorkspaceException;
import dev.openallay.tool.Tool;
import dev.openallay.tool.ToolAccess;
import dev.openallay.tool.ToolDescriptor;
import dev.openallay.tool.ToolResult;
import dev.openallay.tool.RequestScopeParticipant;
import dev.openallay.tool.ModelFacingToolOutput;
import dev.openallay.world.WorldObservationRuntime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public final class RunJavascriptTool
        implements Tool<RunJavascriptTool.Input, RunJavascriptTool.Output>, RequestScopeParticipant {
    public static final String ID = "openallay:run_javascript";

    @ToolDescription("A JavaScript program body. End with an explicit return statement.")
    public record Input(
            String source,
            @ToolDescription("Opaque result handles this script needs to reopen.")
                    @ToolOptional List<String> handles,
            @ToolDescription(
                            "Select Minecraft data by bare top-level names, not mc. access paths: roots [\"player\"] "
                                    + "selects mc.player (mc.player.position); roots [\"game\"] selects mc.game "
                                    + "(mc.game.player.player.position when captured). Optional world and commands "
                                    + "bindings also use bare names and are called directly. Omit only for schema discovery.")
                    @ToolOptional List<String> roots,
            @ToolDescription("Short title in the player's language describing the intended work. Include on every new call.")
                    @ToolOptional String title,
            @ToolDescription("Short description in the player's language of what this call intends to do, not a result or success claim. Include on every new call.")
                    @ToolOptional String description) {
        public Input(String source, List<String> handles) {
            this(source, handles, List.of());
        }

        public Input(String source, List<String> handles, List<String> roots) {
            this(source, handles, roots, null, null);
        }

        public Input {
            handles = handles == null ? List.of() : List.copyOf(handles);
            roots = roots == null ? List.of() : List.copyOf(roots);
        }
    }

    public record Output(
            String handle,
            String resultType,
            long cardinality,
            List<String> fields,
            JsonElement preview,
            String modelText,
            JavascriptSemanticKind viewKind,
            boolean complete,
            int omittedRows,
            int omittedFields,
            long elapsedMillis,
            List<String> modules,
            List<EvidenceMetadata> evidence)
            implements EvidenceBearing, ModelFacingToolOutput {
        public Output {
            fields = List.copyOf(fields);
            preview = preview.deepCopy();
            java.util.Objects.requireNonNull(viewKind, "viewKind");
            modules = List.copyOf(modules);
            evidence = List.copyOf(evidence);
        }

        @Override
        public JsonElement preview() {
            return preview.deepCopy();
        }
    }

    private static final ToolDescriptor<Input, Output> DESCRIPTOR = new ToolDescriptor<>(
            ID,
            "Run JavaScript over the selected detached Minecraft data and enabled Extension bindings. Every source must end with an explicit return. "
                    + "Include title and description on every new call to explain the intended work in the player's language. "
                    + "Use the core JavaScript contract in the system prompt and prefer one filter/map/reduce/sort/join "
                    + "program over repeated calls; do not rediscover documented roots. "
                    + "Load a Skill only when a matching domain-specific or optional workflow requires it. "
                    + "Large results stay in a request workspace "
                    + "and can be reopened by an opaque handle. Default mode uses detached read-only game data. "
                    + "An explicit player setting may enable unrestricted Java/JVM access for future client-local requests. "
                    + "A separate default-off setting may add the complete commands object through the current player's Minecraft route.",
            Input.class,
            Output.class,
            ToolAccess.EXPERIMENTAL_ACTION,
            Set.of(
                    ContextCapability.REGISTRIES,
                    ContextCapability.RECIPES,
                    ContextCapability.PLAYER,
                    ContextCapability.OBSERVABLE_GAME_STATE));
    /** Display intent cannot make identical execution arguments appear to be a new operation. */
    public static JsonObject executionArguments(JsonObject arguments) {
        JsonObject execution = arguments.deepCopy();
        for (String field : List.of("title", "description")) {
            JsonElement value = execution.get(field);
            if (value != null && !value.isJsonNull()
                    && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())) {
                // Keep malformed inputs distinct so correcting an input failure can recover.
                return execution;
            }
        }
        execution.remove("title");
        execution.remove("description");
        return execution;
    }

    private static final String COMMANDS_BINDING = "commands";
    private static final String WORLD_BINDING = "world";

    private final RhinoJavascriptRuntime runtime;
    private final Function<ToolInvocationContext, MinecraftAgentHostGraph> graphFactory;
    private final AgentResultWorkspaceRegistry workspaces;
    private final JavascriptResultPresenter presenter;
    private final CommandCapabilityRuntime commands;
    private final WorldObservationRuntime worldObservations;
    private final OpenAllayExtensionRegistry extensions;
    private final Object requestResources = new Object();
    private final ConcurrentMap<String, MinecraftAgentHostGraph> graphs =
            new ConcurrentHashMap<>();

    public RunJavascriptTool(
            RhinoJavascriptRuntime runtime,
            Function<ToolInvocationContext, MinecraftAgentHostGraph> graphFactory,
            AgentResultWorkspaceRegistry workspaces,
            JavascriptResultPresenter presenter) {
        this(
                runtime,
                graphFactory,
                workspaces,
                presenter,
                new CommandCapabilityRuntime(),
                new WorldObservationRuntime());
    }

    public RunJavascriptTool(
            RhinoJavascriptRuntime runtime,
            Function<ToolInvocationContext, MinecraftAgentHostGraph> graphFactory,
            AgentResultWorkspaceRegistry workspaces,
            JavascriptResultPresenter presenter,
            CommandCapabilityRuntime commands) {
        this(
                runtime,
                graphFactory,
                workspaces,
                presenter,
                commands,
                new WorldObservationRuntime());
    }

    public RunJavascriptTool(
            RhinoJavascriptRuntime runtime,
            Function<ToolInvocationContext, MinecraftAgentHostGraph> graphFactory,
            AgentResultWorkspaceRegistry workspaces,
            JavascriptResultPresenter presenter,
            CommandCapabilityRuntime commands,
            WorldObservationRuntime worldObservations) {
        this(runtime, graphFactory, workspaces, presenter, commands, worldObservations, null);
    }

    public RunJavascriptTool(
            RhinoJavascriptRuntime runtime,
            Function<ToolInvocationContext, MinecraftAgentHostGraph> graphFactory,
            AgentResultWorkspaceRegistry workspaces,
            JavascriptResultPresenter presenter,
            CommandCapabilityRuntime commands,
            WorldObservationRuntime worldObservations,
            OpenAllayExtensionRegistry extensions) {
        this.runtime = runtime;
        this.graphFactory = graphFactory;
        this.workspaces = workspaces;
        this.presenter = presenter;
        this.commands = java.util.Objects.requireNonNull(commands, "commands");
        this.worldObservations =
                java.util.Objects.requireNonNull(worldObservations, "worldObservations");
        this.extensions = extensions;
    }

    @Override
    public ToolDescriptor<Input, Output> descriptor() {
        return DESCRIPTOR;
    }

    public boolean freezeCommandCapability(String correlationId) {
        return commands.freezeRequest(correlationId);
    }

    @Override
    public ToolResult<Output> invoke(ToolInvocationContext context, Input input) {
        throw new UnsupportedOperationException("run_javascript is asynchronous");
    }

    /**
     * The caller must reuse the request's cancellation lifetime for queued invocations and
     * cancel it before closing the request. A terminated request must not be resurrected with
     * a fresh signal. Admission happens before launching the worker, so close can revoke it.
     */
    @Override
    public CompletableFuture<ToolResult<Output>> invokeAsync(
            ToolInvocationContext context, Input input, CancellationSignal cancellation) {
        if (input == null || input.source() == null || input.source().isBlank()) {
            return CompletableFuture.completedFuture(
                    new ToolResult.Failure<>("invalid_tool_arguments", "source must not be blank"));
        }
        JavascriptInvocationScope scope;
        try {
            cancellation.throwIfCancelled();
            scope = extensions == null ? null
                    : extensions.prepareJavascriptInvocation(context, cancellation);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        CompletableFuture<ToolResult<Output>> future = new CompletableFuture<>();
        try {
            Thread worker = Thread.ofVirtual()
                    .name("openallay-javascript-" + context.correlationId())
                    .start(() -> execute(context, input, cancellation, scope, future));
            java.lang.ref.WeakReference<Thread> reference = new java.lang.ref.WeakReference<>(worker);
            cancellation.onCancel(() -> {
                Thread current = reference.get();
                if (current != null) current.interrupt();
            });
        } catch (RuntimeException failure) {
            if (scope != null) scope.close();
            future.completeExceptionally(failure);
        }
        return future;
    }

    private void execute(
            ToolInvocationContext context,
            Input input,
            CancellationSignal cancellation,
            JavascriptInvocationScope scope,
            CompletableFuture<ToolResult<Output>> future) {
        try {
            ToolResult<Output> result;
            try (scope) {
                result = executeActive(context, input, cancellation, scope);
            }
            future.complete(result);
        } catch (ModelClientException cancelled) {
            future.completeExceptionally(cancelled);
        } catch (JavascriptExecutionException failure) {
            future.complete(new ToolResult.Failure<>(failure.code(), failure.getMessage()));
        } catch (WorkspaceException failure) {
            future.complete(new ToolResult.Failure<>(failure.code(), failure.getMessage()));
        } catch (RuntimeException failure) {
            String message = failure.getMessage();
            future.complete(new ToolResult.Failure<>(
                    "javascript_failure",
                    message == null || message.isBlank()
                            ? "JavaScript execution failed"
                            : message));
        } catch (Throwable failure) {
            // This worker owns a manually completed future. Even a native/host Error must
            // settle it after the try-with-resources cleanup, without exposing foreign text.
            future.complete(new ToolResult.Failure<>(
                    "javascript_failure", "JavaScript execution failed"));
        }
    }

    private ToolResult<Output> executeActive(
            ToolInvocationContext context,
            Input input,
            CancellationSignal requestCancellation,
            JavascriptInvocationScope scope) {
        requestCancellation.throwIfCancelled();
        if (scope != null) scope.requireActive();
        CancellationSignal cancellation = scope == null ? requestCancellation : scope.cancellation();
        MinecraftAgentHostGraph graph;
        AgentResultWorkspace workspace;
        synchronized (requestResources) {
            requestCancellation.throwIfCancelled();
            if (scope != null) scope.requireActive();
            graph = graphs.computeIfAbsent(
                    context.correlationId(), ignored -> graphFactory.apply(context));
            workspace = workspaces.open(context.correlationId());
        }
        boolean commandsRequested = input.roots().contains(COMMANDS_BINDING);
        boolean worldRequested = input.roots().contains(WORLD_BINDING);
        List<String> minecraftRoots = input.roots().stream()
                .filter(root -> !COMMANDS_BINDING.equals(root)
                        && !WORLD_BINDING.equals(root))
                .toList();
        var selectedRoots = graph.select(minecraftRoots, input.roots().isEmpty());
        var commandBridge = commands.bridge(
                context.correlationId(), cancellation,
                (kind, capturedAt) -> new EvidenceMetadata(
                        DataAuthority.CLIENT_VISIBLE,
                        DataCompleteness.PARTIAL,
                        capturedAt,
                        "catalog".equals(kind)
                                ? "minecraft:command_catalog"
                                : "minecraft:command_feedback",
                        "catalog".equals(kind)
                                ? "minecraft:active_command_tree"
                                : "minecraft:observed_command_feedback",
                        context.player().map(value -> value.evidence().gameVersion()).orElse("unknown"),
                        context.player().map(value -> value.evidence().loader()).orElse("unknown"),
                        Map.of("openallay:scope", kind)),
                selectedRoots::recordEvidence);
        var worldBridge = worldObservations.bridge(
                context.correlationId(), cancellation, selectedRoots::recordEvidence);
        if (commandsRequested && commandBridge.isEmpty()) {
            throw new JavascriptExecutionException(
                    "javascript_root_unavailable",
                    "Requested JavaScript binding is unavailable: commands");
        }
        if (worldRequested && worldBridge.isEmpty()) {
            throw new JavascriptExecutionException(
                    "javascript_root_unavailable",
                    "Requested JavaScript binding is unavailable: world");
        }
        if (scope != null) scope.open(selectedRoots::recordEvidence);
        JavascriptExecution execution = runtime.execute(
                input.source(),
                selectedRoots,
                workspace.select(input.handles(), context.unrestrictedJavascript()),
                workspace.selectShapes(input.handles()),
                workspace.selectEvidence(input.handles()),
                selectedRoots::recordEvidence,
                selectedRoots::recordSchemaAccess,
                cancellation,
                commandBridge.orElse(null),
                worldBridge.orElse(null),
                context.unrestrictedJavascript());
        if (scope != null) {
            scope.complete();
            scope.close();
        }
        JsonElement canonical = execution.value();
        List<EvidenceMetadata> evidence = selectedRoots.evidence();
        if (evidence.isEmpty()) {
            return new ToolResult.Failure<>(
                    "context_evidence_unavailable",
                    "The script did not access evidence-bearing Minecraft data");
        }
        String handle = workspace.store(
                canonical, execution.shape(), context.unrestrictedJavascript(), evidence);
        var presentation = context.unrestrictedJavascript()
                ? presenter.presentUnrestricted(handle, canonical, execution.shape(), evidenceSummary(evidence))
                : presenter.present(handle, canonical, execution.shape(), evidenceSummary(evidence));
        String modelText = presentation.modelText();
        return new ToolResult.Success<>(new Output(
                handle,
                presentation.type(),
                presentation.cardinality(),
                presentation.fields(),
                presentation.preview(),
                modelText,
                presentation.viewKind(),
                presentation.complete(),
                presentation.omittedRows(),
                presentation.omittedFields(),
                execution.elapsed().toMillis(),
                execution.modules(),
                evidence));
    }

    @Override
    public void closeRequestScope(String correlationId) {
        if (extensions != null) extensions.closeJavascriptRequest(correlationId);
        synchronized (requestResources) {
            graphs.remove(correlationId);
            workspaces.close(correlationId);
        }
        commands.closeRequest(correlationId);
        worldObservations.closeRequest(correlationId);
        // Request authority is immutable in ToolInvocationContext and scoped to this request.
    }

    private static String evidenceSummary(List<EvidenceMetadata> evidence) {
        StringBuilder result = new StringBuilder("\nevidence:");
        int count = Math.min(6, evidence.size());
        for (int index = 0; index < count; index++) {
            EvidenceMetadata item = evidence.get(index);
            result.append("\n- authority=")
                    .append(item.authority())
                    .append(" completeness=")
                    .append(item.completeness())
                    .append(" source=")
                    .append(clip(item.sourceId(), 96))
                    .append(" provenance=")
                    .append(clip(item.provenance(), 160));
        }
        if (evidence.size() > count) {
            result.append("\n- ").append(evidence.size() - count).append(" more evidence record(s)");
        }
        return result.toString();
    }

    private static String clip(String value, int maximum) {
        if (value == null) {
            return "";
        }
        return value.length() <= maximum ? value : value.substring(0, maximum) + "…";
    }
}
