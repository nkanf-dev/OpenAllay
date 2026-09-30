package dev.openallay.tool.builtin;

import com.google.gson.JsonElement;
import dev.openallay.agent.tool.ToolDescription;
import dev.openallay.agent.tool.ToolOptional;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.EvidenceBearing;
import dev.openallay.context.EvidenceMetadata;
import dev.openallay.context.ToolInvocationContext;
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
import java.util.List;
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
                            "Top-level bindings required by this program, for example items, recipes, or the "
                                    + "enabled experimental commands binding. Omit only for schema discovery.")
                    @ToolOptional List<String> roots) {
        public Input(String source, List<String> handles) {
            this(source, handles, List.of());
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
            "Analyze the current detached Minecraft data with isolated JavaScript. Every source must end with an explicit return. "
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
    private static final String COMMANDS_BINDING = "commands";
    private static final String WORLD_BINDING = "world";

    private final RhinoJavascriptRuntime runtime;
    private final Function<ToolInvocationContext, MinecraftAgentHostGraph> graphFactory;
    private final AgentResultWorkspaceRegistry workspaces;
    private final JavascriptResultPresenter presenter;
    private final CommandCapabilityRuntime commands;
    private final WorldObservationRuntime worldObservations;
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
        this.runtime = runtime;
        this.graphFactory = graphFactory;
        this.workspaces = workspaces;
        this.presenter = presenter;
        this.commands = java.util.Objects.requireNonNull(commands, "commands");
        this.worldObservations =
                java.util.Objects.requireNonNull(worldObservations, "worldObservations");
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

    @Override
    public CompletableFuture<ToolResult<Output>> invokeAsync(
            ToolInvocationContext context, Input input, CancellationSignal cancellation) {
        if (input == null || input.source() == null || input.source().isBlank()) {
            return CompletableFuture.completedFuture(
                    new ToolResult.Failure<>("invalid_tool_arguments", "source must not be blank"));
        }
        CompletableFuture<ToolResult<Output>> future = new CompletableFuture<>();
        Thread worker = Thread.ofVirtual()
                .name("openallay-javascript-" + context.correlationId())
                .start(() -> execute(context, input, cancellation, future));
        cancellation.onCancel(worker::interrupt);
        return future;
    }

    private void execute(
            ToolInvocationContext context,
            Input input,
            CancellationSignal cancellation,
            CompletableFuture<ToolResult<Output>> future) {
        try {
            cancellation.throwIfCancelled();
            MinecraftAgentHostGraph graph = graphs.computeIfAbsent(
                    context.correlationId(), ignored -> graphFactory.apply(context));
            var commandBridge = commands.bridge(context.correlationId(), cancellation);
            var worldBridge =
                    worldObservations.bridge(context.correlationId(), cancellation);
            boolean commandsRequested = input.roots().contains(COMMANDS_BINDING);
            if (commandsRequested && commandBridge.isEmpty()) {
                throw new JavascriptExecutionException(
                        "javascript_root_unavailable",
                        "Requested JavaScript binding is unavailable: commands");
            }
            boolean worldRequested = input.roots().contains(WORLD_BINDING);
            if (worldRequested && worldBridge.isEmpty()) {
                throw new JavascriptExecutionException(
                        "javascript_root_unavailable",
                        "Requested JavaScript binding is unavailable: world");
            }
            List<String> minecraftRoots = input.roots().stream()
                    .filter(root -> !COMMANDS_BINDING.equals(root)
                            && !WORLD_BINDING.equals(root))
                    .toList();
            var selectedRoots = graph.select(minecraftRoots);
            if (graph.evidence().isEmpty()) {
                future.complete(new ToolResult.Failure<>(
                        "context_evidence_unavailable",
                        "No evidence-bearing Minecraft data was captured for this request"));
                return;
            }
            AgentResultWorkspace workspace = workspaces.open(context.correlationId());
            JavascriptExecution execution = runtime.execute(
                    input.source(),
                    selectedRoots,
                    workspace.select(input.handles(), context.unrestrictedJavascript()),
                    workspace.selectShapes(input.handles()),
                    cancellation,
                    commandBridge.orElse(null),
                    worldBridge.orElse(null),
                    context.unrestrictedJavascript());
            JsonElement canonical = execution.value();
            String handle = workspace.store(canonical, execution.shape(), context.unrestrictedJavascript());
            List<EvidenceMetadata> evidence = graph.evidence();
            var presentation = context.unrestrictedJavascript()
                    ? presenter.presentUnrestricted(handle, canonical, execution.shape(), evidenceSummary(evidence))
                    : presenter.present(handle, canonical, execution.shape(), evidenceSummary(evidence));
            String modelText = presentation.modelText();
            future.complete(new ToolResult.Success<>(new Output(
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
                    evidence)));
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
        }
    }

    @Override
    public void closeRequestScope(String correlationId) {
        graphs.remove(correlationId);
        workspaces.close(correlationId);
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
