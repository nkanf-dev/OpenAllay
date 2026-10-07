package dev.openallay.tool.builtin;

import dev.openallay.concurrent.NamedThreads;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.openallay.agent.tool.ToolDescription;
import dev.openallay.agent.tool.ToolOptional;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.DataAuthority;
import dev.openallay.context.DataCompleteness;
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
import dev.openallay.tool.WorkspaceModelFacingToolOutput;
import dev.openallay.world.WorldObservationRuntime;
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
@dev.openallay.value.ValueType(Input.ValueSchemaProvider.class)
public static final class Input {
    private final String source;
    @ToolDescription("Opaque result handles from this active request that the script needs to reopen.") @ToolOptional private final List<String> handles;
    @ToolDescription("Short title in the player's language describing the intended work. Include on every new call.") @ToolOptional private final String title;
    @ToolDescription("Short description in the player's language of what this call intends to do, not a result or success claim. Include on every new call.") @ToolOptional private final String description;
    public Input(String source, List<String> handles, String title, String description) {

            handles = handles == null ? List.of() : List.copyOf(handles);

        this.source = source;
        this.handles = handles;
        this.title = title;
        this.description = description;
    }
    public String source() { return source; }
    public List<String> handles() { return handles; }
    public String title() { return title; }
    public String description() { return description; }
public Input(String source, List<String> handles) {
            this(source, handles, null, null);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Input)) return false;
        Input that = (Input) other;
        return java.util.Objects.equals(source, that.source) && java.util.Objects.equals(handles, that.handles) && java.util.Objects.equals(title, that.title) && java.util.Objects.equals(description, that.description);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(handles);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        return hash;
    }
    @Override public String toString() { return "Input[source=" + source + ", handles=" + handles + ", title=" + title + ", description=" + description + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Input> schema() {
            return new dev.openallay.value.ValueSchema<>(Input.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Input>>asList(new dev.openallay.value.ValueSchema.Component<>(Input.class, "source", Input::source), new dev.openallay.value.ValueSchema.Component<>(Input.class, "handles", Input::handles), new dev.openallay.value.ValueSchema.Component<>(Input.class, "title", Input::title), new dev.openallay.value.ValueSchema.Component<>(Input.class, "description", Input::description)), arguments -> new Input((String) arguments[0], (List) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(Output.ValueSchemaProvider.class)
public static final class Output implements WorkspaceModelFacingToolOutput, dev.openallay.agent.tool.ModelImageToolOutput {
    private final String handle;
    private final String resultType;
    private final long cardinality;
    private final List<String> fields;
    private final JsonElement preview;
    private final String modelText;
    private final JavascriptSemanticKind viewKind;
    private final boolean complete;
    private final int omittedRows;
    private final int omittedFields;
    private final dev.openallay.tool.ModelResultView modelView;
    private final long elapsedMillis;
    private final List<String> modules;
    private final List<dev.openallay.context.SourceObservation> sources;
    private final List<dev.openallay.model.image.ImageReference> images;
    public Output(String handle, String resultType, long cardinality, List<String> fields, JsonElement preview, String modelText, JavascriptSemanticKind viewKind, boolean complete, int omittedRows, int omittedFields, dev.openallay.tool.ModelResultView modelView, long elapsedMillis, List<String> modules, List<dev.openallay.context.SourceObservation> sources, List<dev.openallay.model.image.ImageReference> images) {

            fields = List.copyOf(fields);
            preview = dev.openallay.json.JsonTrees.copy(preview);
            java.util.Objects.requireNonNull(viewKind, "viewKind");
            modules = List.copyOf(modules);
            sources = List.copyOf(sources);
            images = List.copyOf(images);

        this.handle = handle;
        this.resultType = resultType;
        this.cardinality = cardinality;
        this.fields = fields;
        this.preview = preview;
        this.modelText = modelText;
        this.viewKind = viewKind;
        this.complete = complete;
        this.omittedRows = omittedRows;
        this.omittedFields = omittedFields;
        this.modelView = modelView;
        this.elapsedMillis = elapsedMillis;
        this.modules = modules;
        this.sources = sources;
        this.images = images;
    }
    public String handle() { return handle; }
    public String resultType() { return resultType; }
    public long cardinality() { return cardinality; }
    public List<String> fields() { return fields; }
    public String modelText() { return modelText; }
    public JavascriptSemanticKind viewKind() { return viewKind; }
    public boolean complete() { return complete; }
    public int omittedRows() { return omittedRows; }
    public int omittedFields() { return omittedFields; }
    public dev.openallay.tool.ModelResultView modelView() { return modelView; }
    public long elapsedMillis() { return elapsedMillis; }
    public List<String> modules() { return modules; }
    public List<dev.openallay.context.SourceObservation> sources() { return sources; }
    public List<dev.openallay.model.image.ImageReference> images() { return images; }
public Output(String handle, String resultType, long cardinality, List<String> fields,
                JsonElement preview, String modelText, JavascriptSemanticKind viewKind, boolean complete,
                int omittedRows, int omittedFields, dev.openallay.tool.ModelResultView modelView,
                long elapsedMillis, List<String> modules, List<dev.openallay.context.SourceObservation> sources) {
            this(handle, resultType, cardinality, fields, preview, modelText, viewKind, complete,
                    omittedRows, omittedFields, modelView, elapsedMillis, modules, sources, List.of());
        }

        public JsonElement preview() {
            return dev.openallay.json.JsonTrees.copy(preview);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Output)) return false;
        Output that = (Output) other;
        return java.util.Objects.equals(handle, that.handle) && java.util.Objects.equals(resultType, that.resultType) && cardinality == that.cardinality && java.util.Objects.equals(fields, that.fields) && java.util.Objects.equals(preview, that.preview) && java.util.Objects.equals(modelText, that.modelText) && java.util.Objects.equals(viewKind, that.viewKind) && complete == that.complete && omittedRows == that.omittedRows && omittedFields == that.omittedFields && java.util.Objects.equals(modelView, that.modelView) && elapsedMillis == that.elapsedMillis && java.util.Objects.equals(modules, that.modules) && java.util.Objects.equals(sources, that.sources) && java.util.Objects.equals(images, that.images);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(handle);
        hash = 31 * hash + java.util.Objects.hashCode(resultType);
        hash = 31 * hash + Long.hashCode(cardinality);
        hash = 31 * hash + java.util.Objects.hashCode(fields);
        hash = 31 * hash + java.util.Objects.hashCode(preview);
        hash = 31 * hash + java.util.Objects.hashCode(modelText);
        hash = 31 * hash + java.util.Objects.hashCode(viewKind);
        hash = 31 * hash + Boolean.hashCode(complete);
        hash = 31 * hash + Integer.hashCode(omittedRows);
        hash = 31 * hash + Integer.hashCode(omittedFields);
        hash = 31 * hash + java.util.Objects.hashCode(modelView);
        hash = 31 * hash + Long.hashCode(elapsedMillis);
        hash = 31 * hash + java.util.Objects.hashCode(modules);
        hash = 31 * hash + java.util.Objects.hashCode(sources);
        hash = 31 * hash + java.util.Objects.hashCode(images);
        return hash;
    }
    @Override public String toString() { return "Output[handle=" + handle + ", resultType=" + resultType + ", cardinality=" + cardinality + ", fields=" + fields + ", preview=" + preview + ", modelText=" + modelText + ", viewKind=" + viewKind + ", complete=" + complete + ", omittedRows=" + omittedRows + ", omittedFields=" + omittedFields + ", modelView=" + modelView + ", elapsedMillis=" + elapsedMillis + ", modules=" + modules + ", sources=" + sources + ", images=" + images + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Output> schema() {
            return new dev.openallay.value.ValueSchema<>(Output.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Output>>asList(new dev.openallay.value.ValueSchema.Component<>(Output.class, "handle", Output::handle), new dev.openallay.value.ValueSchema.Component<>(Output.class, "resultType", Output::resultType), new dev.openallay.value.ValueSchema.Component<>(Output.class, "cardinality", Output::cardinality), new dev.openallay.value.ValueSchema.Component<>(Output.class, "fields", Output::fields), new dev.openallay.value.ValueSchema.Component<>(Output.class, "preview", Output::preview), new dev.openallay.value.ValueSchema.Component<>(Output.class, "modelText", Output::modelText), new dev.openallay.value.ValueSchema.Component<>(Output.class, "viewKind", Output::viewKind), new dev.openallay.value.ValueSchema.Component<>(Output.class, "complete", Output::complete), new dev.openallay.value.ValueSchema.Component<>(Output.class, "omittedRows", Output::omittedRows), new dev.openallay.value.ValueSchema.Component<>(Output.class, "omittedFields", Output::omittedFields), new dev.openallay.value.ValueSchema.Component<>(Output.class, "modelView", Output::modelView), new dev.openallay.value.ValueSchema.Component<>(Output.class, "elapsedMillis", Output::elapsedMillis), new dev.openallay.value.ValueSchema.Component<>(Output.class, "modules", Output::modules), new dev.openallay.value.ValueSchema.Component<>(Output.class, "sources", Output::sources), new dev.openallay.value.ValueSchema.Component<>(Output.class, "images", Output::images)), arguments -> new Output((String) arguments[0], (String) arguments[1], (Long) arguments[2], (List) arguments[3], (JsonElement) arguments[4], (String) arguments[5], (JavascriptSemanticKind) arguments[6], (Boolean) arguments[7], (Integer) arguments[8], (Integer) arguments[9], (dev.openallay.tool.ModelResultView) arguments[10], (Long) arguments[11], (List) arguments[12], (List) arguments[13], (List) arguments[14]));
        }
    }
}

    private static final ToolDescriptor<Input, Output> DESCRIPTOR = new ToolDescriptor<>(
            ID,
            "Run JavaScript computations with automatic access to captured Minecraft data and enabled Extension bindings. The source field is program text and must end with an explicit return. "
                    + "Include title and description on every new call to explain the intended work in the player's language. "
                    + "Use the core JavaScript contract in the system prompt and prefer one filter/map/reduce/sort/join "
                    + "program over repeated calls; read documented data directly. "
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
        JsonObject execution = dev.openallay.json.JsonTrees.copy(arguments);
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

    /** Matches the exact command bridge supplied to Rhino by this Tool instance. */
    public boolean commandCapabilityAvailable(String correlationId) {
        return commands.availableFor(correlationId);
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
        java.util.concurrent.atomic.AtomicBoolean settled = new java.util.concurrent.atomic.AtomicBoolean();
        try {
            Thread worker = NamedThreads.startDaemon(
                    "openallay-javascript-" + context.correlationId(),
                    () -> execute(context, input, cancellation, scope, future, settled));
            java.lang.ref.WeakReference<Thread> reference = new java.lang.ref.WeakReference<>(worker);
            cancellation.onCancel(() -> {
                // Serialize the final active interrupt with terminal publication. A dependent
                // future callback can cancel on this same worker after the Tool has settled.
                synchronized (settled) {
                    Thread current = reference.get();
                    if (!settled.get() && current != null) current.interrupt();
                }
            });
        } catch (RuntimeException failure) {
            if (scope != null) scope.close();
            markSettled(settled);
            future.completeExceptionally(failure);
        }
        return future;
    }

    private void execute(
            ToolInvocationContext context,
            Input input,
            CancellationSignal cancellation,
            JavascriptInvocationScope scope,
            CompletableFuture<ToolResult<Output>> future,
            java.util.concurrent.atomic.AtomicBoolean settled) {
        try {
            ToolResult<Output> result;
            try (scope) {
                result = executeActive(context, input, cancellation, scope);
            }
            markSettled(settled);
            future.complete(result);
        } catch (ModelClientException cancelled) {
            markSettled(settled);
            future.completeExceptionally(cancelled);
        } catch (JavascriptExecutionException failure) {
            markSettled(settled);
            future.complete(new ToolResult.Failure<>(failure.code(), failure.getMessage()));
        } catch (WorkspaceException failure) {
            markSettled(settled);
            future.complete(new ToolResult.Failure<>(failure.code(), failure.getMessage()));
        } catch (Throwable failure) {
            // Settle even native errors after scope cleanup. Never forward a native stack or
            // an unchecked exception's arbitrary message to the model.
            markSettled(settled);
            future.complete(new ToolResult.Failure<>(
                    "javascript_failure",
                    dev.openallay.script.JavascriptFailureFormatter.format(failure)));
        }
    }

    /** Shares the listener's monitor; completion callbacks run only after this lock is released. */
    private static void markSettled(java.util.concurrent.atomic.AtomicBoolean settled) {
        synchronized (settled) { settled.set(true); }
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
        dev.openallay.script.data.MinecraftAgentHostGraph.InvocationData data = graph.open();
        java.util.Optional<dev.openallay.script.command.JavascriptCommandBridge> commandBridge = commands.bridge(
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
                data::recordEvidence);
        List<dev.openallay.model.image.ImageReference> capturedImages = new java.util.ArrayList<>();
        java.util.Optional<dev.openallay.world.JavascriptWorldBridge> worldBridge = worldObservations.bridge(
                context.correlationId(), cancellation, data::recordEvidence, capturedImages::add);
        if (scope != null) scope.open(data::recordEvidence);
        JavascriptExecution execution = runtime.execute(
                input.source(),
                data,
                workspace.select(input.handles(), context.unrestrictedJavascript()),
                workspace.selectShapes(input.handles()),
                workspace.selectSources(input.handles()),
                data::recordSource,
                cancellation,
                commandBridge.orElse(null),
                worldBridge.orElse(null),
                context.unrestrictedJavascript(),
                scope);
        if (scope != null) {
            scope.complete();
            scope.close();
        }
        requestCancellation.throwIfCancelled();
        JsonElement canonical = execution.value();
        java.util.List<dev.openallay.context.SourceObservation> sources = data.sources();
        String handle = workspace.store(
                canonical, execution.shape(), context.unrestrictedJavascript(), sources);
        String coverage = inputCoverage(sources);
        // Canonical storage and execution authority stay mode-specific. Model transport does not.
        dev.openallay.tool.result.NaturalModelView.Choice choice = dev.openallay.tool.result.NaturalModelView.artifact(canonical);
        dev.openallay.tool.ModelResultView naturalView = new dev.openallay.tool.ModelResultView(handle,
                dev.openallay.tool.result.JsonResultProjection.type(canonical),
                dev.openallay.tool.result.JsonResultProjection.cardinality(canonical),
                dev.openallay.tool.result.JsonResultProjection.serializedBytes(canonical),
                choice.complete(), "current request only", coverage);
        dev.openallay.script.workspace.JavascriptResultPresenter.Presentation presentation = presenter.presentChosen(handle, choice.value(), canonical,
                naturalView, execution.shape(), coverage);
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
                new dev.openallay.tool.ModelResultView(handle, presentation.type(),
                        presentation.cardinality(), presentation.canonicalUtf8Bytes(),
                        presentation.complete(), "current request only", coverage),
                execution.elapsed().toMillis(),
                execution.modules(),
                sources,
                capturedImages));
    }

    @Override
    public java.util.Optional<dev.openallay.tool.ModelResultSource> modelResultSource(
            ToolInvocationContext context, Output output) {
        return workspaces.existing(context.correlationId()).map(workspace ->
                workspace.modelSource(output.modelView(), inputCoverage(output.sources())));
    }

    @Override
    public void closeRequestScope(String correlationId) {
        if (extensions != null) extensions.closeJavascriptRequest(correlationId);
        synchronized (requestResources) {
            graphs.remove(correlationId);
            workspaces.close(correlationId);
        }
        commands.closeRequest(correlationId);
        worldObservations.closeObservations(correlationId);
        // Request authority is immutable in ToolInvocationContext and scoped to this request.
    }

    private static String inputCoverage(List<dev.openallay.context.SourceObservation> sources) {
        boolean incomplete = sources.stream().anyMatch(source ->
                source.evidence().completeness() != DataCompleteness.COMPLETE);
        boolean mixedAuthority = sources.stream().map(source -> source.evidence().authority())
                .distinct().count() > 1;
        if (!incomplete && !mixedAuthority) return "";
        String completeness = sources.stream().map(source -> source.evidence().completeness().name())
                .distinct().sorted().collect(java.util.stream.Collectors.joining(", "));
        return "input completeness: " + completeness
                + "; mixed authority: " + mixedAuthority
                + "\ninput coverage: " + sources.stream().map(source ->
                        source.evidence().authority() + " " + source.evidence().completeness())
                .distinct().sorted().collect(java.util.stream.Collectors.joining("; "));
    }

}
