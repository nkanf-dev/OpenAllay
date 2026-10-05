package dev.openallay.client;

import com.google.gson.Gson;
import dev.openallay.FeatureServices;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentRequest;
import dev.openallay.agent.AgentResult;
import dev.openallay.agent.GameGuideAgent;
import dev.openallay.agent.context.ContextBudget;
import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.agent.context.ContextCompactor;
import dev.openallay.agent.context.ContextTokenEstimator;
import dev.openallay.model.tokenizer.ModelContextTokenEstimator;
import dev.openallay.agent.session.AgentSessionKey;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.trace.LiveTraceStore;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.agent.tool.CompositeAgentToolExecutor;
import dev.openallay.agent.tool.LocalAgentToolExecutor;
import dev.openallay.bridge.client.ClientPlacedToolExecutor;
import dev.openallay.bridge.client.RemoteToolExecutor;
import dev.openallay.capability.CapabilityPolicy;
import dev.openallay.capability.ClientCapabilityResolver;
import dev.openallay.capability.ClientCapabilitySnapshot;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.guide.GuideLocalEndpoint;
import dev.openallay.guide.GuideContextSpec;
import dev.openallay.guide.GuideCompactResult;
import dev.openallay.guide.GuidePreparedCompaction;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelRole;
import dev.openallay.model.image.ImagePayloadResolver;
import dev.openallay.skill.RetainedSkillContext;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.scheduling.ModelRequestScheduler;
import dev.openallay.tool.ToolResult;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ClientGuideRuntime implements GuideLocalEndpoint {
    private final EndpointRuntime endpoint;
    private final AgentSessionStore sessions;
    private final GameGuideAgent agent;
    private final AgentToolExecutor toolExecutor;
    private final ClientEventDispatcher dispatcher;
    private final LiveTraceStore traces;
    private final ClientCapabilitySnapshot capabilities;
    private final Gson gson;
    private final AgentToolExecutor extension;
    private final Map<UUID, String> selectedSessions;
    private final PromptModes promptModes;

    public ClientGuideRuntime(
            FeatureServices runtime,
            ModelClient model,
            AgentSessionStore sessions,
            Gson gson,
            ClientEventDispatcher dispatcher) {
        this(runtime, model, sessions, gson, dispatcher, null, new LiveTraceStore(null), null, null);
    }

    public ClientGuideRuntime(
            FeatureServices runtime,
            ModelClient model,
            AgentSessionStore sessions,
            Gson gson,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension) {
        this(runtime, model, sessions, gson, dispatcher, extension, new LiveTraceStore(null), null, null);
    }

    ClientGuideRuntime(
            FeatureServices runtime,
            ModelClient model,
            AgentSessionStore sessions,
            Gson gson,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension,
            LiveTraceStore traces,
            ContextBudget contextBudget,
            String modelIdentifier) {
        this(
                model,
                sessions,
                gson,
                dispatcher,
                extension,
                traces,
                contextBudget,
                modelIdentifier,
                defaultCapabilities(runtime));
    }

    ClientGuideRuntime(
            ModelClient model,
            AgentSessionStore sessions,
            Gson gson,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension,
            LiveTraceStore traces,
            ContextBudget contextBudget,
            String modelIdentifier,
            ClientCapabilitySnapshot capabilities) {
        this(model, sessions, gson, dispatcher, extension, traces, contextBudget, modelIdentifier,
                capabilities, ModelContextTokenEstimator.conservative());
    }

    ClientGuideRuntime(
            ModelClient model,
            AgentSessionStore sessions,
            Gson gson,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension,
            LiveTraceStore traces,
            ContextBudget contextBudget,
            String modelIdentifier,
            ClientCapabilitySnapshot capabilities,
            ContextTokenEstimator estimator) {
        this(
                endpoint(model, gson, contextBudget, modelIdentifier, estimator),
                sessions,
                gson,
                dispatcher,
                extension,
                traces,
                capabilities,
                new ConcurrentHashMap<>(),
                null);
    }

    private ClientGuideRuntime(
            EndpointRuntime endpoint,
            AgentSessionStore sessions,
            Gson gson,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension,
            LiveTraceStore traces,
            ClientCapabilitySnapshot capabilities,
            Map<UUID, String> selectedSessions,
            PromptModes promptModes) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.traces = Objects.requireNonNull(traces, "traces");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.gson = Objects.requireNonNull(gson, "gson");
        this.extension = extension;
        this.selectedSessions = Objects.requireNonNull(selectedSessions, "selectedSessions");
        this.promptModes = promptModes;
        LocalAgentToolExecutor local = new LocalAgentToolExecutor(capabilities.localTools(), gson);
        toolExecutor = extension == null
                ? local
                : extension instanceof RemoteToolExecutor remote
                        ? new ClientPlacedToolExecutor(local, remote)
                        : new CompositeAgentToolExecutor(List.of(local, extension));
        agent = new GameGuideAgent(
                endpoint.scheduler(), toolExecutor, sessions, gson, endpoint.compactor(),
                (request, tokens) -> {
                    synchronized (sessions) {
                        AgentSessionStore.Status status = sessions.status(request.sessionKey());
                        if (status.active() && request.requestId().equals(status.requestId())) {
                            if (promptModes != null) endpoint.promptModes().put(request.sessionKey(), promptModes);
                            endpoint.estimates().put(request.sessionKey(),
                                    new dev.openallay.guide.GuideContextEstimate(
                                            request.requestId(), tokens,
                                            endpoint.contextBudget(), endpoint.contextBudget() == null
                                                    ? null : endpoint.modelIdentifier(),
                                            endpoint.estimator().imageAccounting(
                                                    java.util.stream.Stream.concat(
                                                            sessions.history(request.sessionKey()).stream(),
                                                            java.util.stream.Stream.of(request.userInput()))
                                                            .toList())));
                        }
                    }
                });
    }

    ClientGuideRuntime withCapabilities(ClientCapabilitySnapshot replacement) {
        return withCapabilities(replacement, promptModes);
    }

    private ClientGuideRuntime withCapabilities(ClientCapabilitySnapshot replacement, PromptModes modes) {
        return new ClientGuideRuntime(
                endpoint,
                sessions,
                gson,
                dispatcher,
                extension,
                traces,
                replacement,
                selectedSessions,
                modes);
    }

    @Override
    public Optional<dev.openallay.guide.GuideContextEstimate> contextEstimate(
            String profileId, UUID actor, String sessionId) {
        return Optional.ofNullable(endpoint.estimates().get(new AgentSessionKey(actor, sessionId)));
    }

    void clearContextEstimate(UUID actor, String sessionId) {
        synchronized (sessions) {
            AgentSessionKey key = new AgentSessionKey(actor, sessionId);
            endpoint.estimates().remove(key);
            endpoint.promptModes().remove(key);
        }
    }

    void clearContextEstimates(UUID actor) {
        synchronized (sessions) {
            endpoint.estimates().keySet().removeIf(key -> key.actorId().equals(actor));
            endpoint.promptModes().keySet().removeIf(key -> key.actorId().equals(actor));
        }
    }

    Object endpointIdentity() {
        return endpoint.scheduler();
    }

    public Set<dev.openallay.context.ContextCapability> requiredContext() {
        return toolExecutor.requiredContext();
    }

    @Override
    public Optional<GuideContextSpec> contextSpec(String profileId) {
        if (endpoint.contextBudget() == null || endpoint.modelIdentifier() == null) {
            return Optional.empty();
        }
        // Reserve enough room for either captured mode without advertising the enabled view.
        ClientGuideRuntime enabled = withCapabilities(capabilities.forRequest(true, true));
        ContextTokenEstimator estimator = endpoint.estimator();
        int promptAndTools = Math.max(
                estimator.estimate(budgetSystemPrompt(systemPrompt()), List.of(), toolExecutor.definitions()),
                estimator.estimate(enabled.budgetSystemPrompt(enabled.systemPrompt(true, true)),
                        List.of(), enabled.toolExecutor.definitions()));
        if (promptAndTools >= endpoint.contextBudget().inputTokens()) {
            return Optional.empty();
        }
        return Optional.of(new GuideContextSpec(
                endpoint.contextBudget(), promptAndTools, endpoint.modelIdentifier(), endpoint.estimator()));
    }

    private String budgetSystemPrompt(String prompt) {
        // Match the Agent's initial actual system delivery, including inline Skill range facts.
        AgentToolExecutor captured = toolExecutor;
        String system = captured.skillSystemPrompt(prompt);
        var retained = new dev.openallay.skill.RetainedSkillContext();
        String correlation = "context-budget-" + UUID.randomUUID();
        captured.prepareSystem(system, retained);
        captured.prepareContext(correlation, List.of(), retained);
        try {
            String facts = captured.skillManifest(correlation);
            return facts.isBlank() ? system : system + "\n" + facts;
        } finally {
            captured.closeSkillContext(correlation);
        }
    }

    @Override
    public boolean compactAvailable(String profileId) {
        return defaultProfileId().equals(profileId) && endpoint.compactor() != null;
    }

    @Override
    public Object compactIdentity(String profileId) {
        return compactAvailable(profileId) ? this : null;
    }

    @Override
    public CompletableFuture<ToolResult<GuidePreparedCompaction>> prepareCompaction(
            String profileId,
            UUID actor,
            String sessionId,
            UUID controlId,
            List<ModelMessage> durableSeed,
            CancellationSignal cancellation,
            ImagePayloadResolver images,
            Consumer<AgentEvent> usage) {
        return prepareCompaction(profileId, actor, sessionId, controlId, durableSeed, cancellation,
                images, usage, null);
    }

    /** The registry supplies the selected profile's exact externally resolved image capability. */
    CompletableFuture<ToolResult<GuidePreparedCompaction>> prepareCompaction(
            String profileId,
            UUID actor,
            String sessionId,
            UUID controlId,
            List<ModelMessage> durableSeed,
            CancellationSignal cancellation,
            ImagePayloadResolver images,
            Consumer<AgentEvent> usage,
            dev.openallay.model.image.ImageInputCapability imageCapability) {
        if (!compactAvailable(profileId)) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "compact_unavailable", "Manual compaction requires a local model with a known context budget"));
        }
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(images, "images");
        Objects.requireNonNull(usage, "usage");
        if (cancellation.isCancelled()) return CompletableFuture.completedFuture(
                new ToolResult.Failure<>("compact_cancelled", "Manual compaction was cancelled"));
        ToolResult<AgentSessionStore.ControlLease> reservation = sessions.reserveControl(
                new AgentSessionKey(actor, sessionId), controlId, durableSeed);
        if (reservation instanceof ToolResult.Failure<AgentSessionStore.ControlLease> failure) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(failure.code(), failure.message()));
        }
        AgentSessionStore.ControlLease lease =
                ((ToolResult.Success<AgentSessionStore.ControlLease>) reservation).value();
        PromptModes modes = endpoint.promptModes().getOrDefault(lease.key(), new PromptModes(false, false));
        ClientGuideRuntime captured = withCapabilities(capabilities.forRequest(
                modes.unrestrictedJavascript(), modes.commandsAvailable()), modes);
        AgentToolExecutor capturedTools = captured.toolExecutor;
        ManualCompactionScope scope = new ManualCompactionScope(lease, cancellation, usage, capturedTools);
        try {
            lease.cancellation().throwIfCancelled();
            if (imageCapability != null
                    && imageCapability != dev.openallay.model.image.ImageInputCapability.SUPPORTED
                    && dev.openallay.model.image.ModelImages.hasImages(lease.history())) {
                scope.finishPreparation();
                scope.close();
                return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                        imageCapability == dev.openallay.model.image.ImageInputCapability.UNKNOWN
                                ? "image_input_unknown" : "image_input_unsupported",
                        "The selected model has no confirmed image input support"));
            }
            ContextCompactor compactor = endpoint.compactor();
            List<dev.openallay.model.ModelToolDefinition> definitions = List.copyOf(capturedTools.definitions());
            // This index is private to preparation. It never touches the session's retained facts.
            RetainedSkillContext retained = new RetainedSkillContext();
            String system = capturedTools.skillSystemPrompt(captured.systemPrompt(
                    modes.unrestrictedJavascript(), modes.commandsAvailable()));
            capturedTools.prepareSystem(system, retained);
            List<ModelMessage> source = dev.openallay.agent.context.ModelContextCodec.safe(
                    capturedTools.refreshContext(lease.history(), retained));
            java.util.function.Function<List<ModelMessage>, String> prompt = candidate -> {
                lease.cancellation().throwIfCancelled();
                capturedTools.prepareContext(scope.correlationId, candidate, retained);
                String facts = capturedTools.skillManifest(scope.correlationId);
                return facts.isBlank() ? system : system + "\n" + facts;
            };
            int before = compactor.estimateTokens(prompt.apply(source), source, definitions);
            if (source.isEmpty()) {
                scope.finishPreparation();
                return CompletableFuture.completedFuture(new ToolResult.Success<>(new PreparedManualCompaction(
                        scope, new GuideCompactResult(GuideCompactResult.Status.NOT_NEEDED,
                                before, before, compactor.inputTokenBudget(), null), lease.history())));
            }
            // Do not run prepareModelView/fitResults over the retained recent turn. The manual
            // compactor bounds only its older summary input, and retains this suffix as real data.
            return compactor.compactManually(prompt, source, protectedTurnStart(source),
                            definitions, lease.key().schedulingKey(), lease.cancellation(),
                            images, scope::observeUsage)
                    .handle((result, failure) -> {
                        try {
                            if (failure != null) return ClientGuideRuntime.<GuidePreparedCompaction>compactFailure(
                                    failure, lease.cancellation());
                            if (!sessions.ownsControl(lease)) return new ToolResult.Failure<GuidePreparedCompaction>(
                                    "compact_cancelled", "Manual compaction was cancelled");
                            if (!result.successful()) return new ToolResult.Failure<GuidePreparedCompaction>(
                                    result.failureCode(), result.failureMessage());
                            boolean compacted = result.checkpoint() != null;
                            List<ModelMessage> projection = compacted
                                    ? result.projection().messages() : lease.history();
                            int after = compacted ? compactor.estimateTokens(
                                    prompt.apply(projection), projection, definitions) : before;
                            GuideCompactResult outcome = new GuideCompactResult(compacted
                                    ? GuideCompactResult.Status.COMPACTED : GuideCompactResult.Status.NOT_NEEDED,
                                    before, after, compactor.inputTokenBudget(), result.checkpoint());
                            return new ToolResult.Success<GuidePreparedCompaction>(
                                    new PreparedManualCompaction(scope, outcome, projection));
                        } catch (Throwable preparationFailure) {
                            return ClientGuideRuntime.<GuidePreparedCompaction>compactFailure(
                                    preparationFailure, lease.cancellation());
                        } finally {
                            scope.finishPreparation();
                        }
                    }).thenApply(result -> {
                        if (result instanceof ToolResult.Failure<GuidePreparedCompaction>) scope.close();
                        return result;
                    });
        } catch (Throwable failure) {
            scope.finishPreparation();
            scope.close();
            return CompletableFuture.completedFuture(compactFailure(failure, lease.cancellation()));
        }
    }

    /** Keep the latest actual question and at least the newest completed question/reply turn. */
    private static int protectedTurnStart(List<ModelMessage> messages) {
        int latestQuestion = -1;
        int latestCompleted = -1;
        for (int index = 0; index < messages.size(); index++) {
            ModelMessage message = messages.get(index);
            if (realQuestion(message)) latestQuestion = index;
            if (latestQuestion >= 0 && message.role() == ModelRole.ASSISTANT
                    && message.content().stream().noneMatch(ModelContent.ToolUse.class::isInstance)
                    && message.content().stream().anyMatch(ModelContent.Text.class::isInstance)) {
                latestCompleted = latestQuestion;
            }
        }
        if (latestQuestion < 0) return 0;
        return latestCompleted < 0 ? latestQuestion : Math.min(latestQuestion, latestCompleted);
    }

    private static boolean realQuestion(ModelMessage message) {
        if (message.role() != ModelRole.USER || message.content().stream().anyMatch(
                ModelContent.ToolResult.class::isInstance)) return false;
        if (message.content().get(0) instanceof ModelContent.Text text
                && text.text().startsWith("[OpenAllay derived conversation memory; NOT factual evidence]\n")) {
            return false;
        }
        return message.content().stream().anyMatch(item -> item instanceof ModelContent.Text
                || item instanceof ModelContent.Image);
    }

    private static <T> ToolResult<T> compactFailure(Throwable failure, CancellationSignal cancellation) {
        while ((failure instanceof java.util.concurrent.CompletionException
                || failure instanceof java.util.concurrent.ExecutionException) && failure.getCause() != null) {
            failure = failure.getCause();
        }
        if (cancellation.isCancelled()) return new ToolResult.Failure<>(
                "compact_cancelled", "Manual compaction was cancelled");
        if (failure instanceof ModelClientException model) return new ToolResult.Failure<>(
                model.failure().code(), model.failure().message());
        return new ToolResult.Failure<>("compact_failed", "Manual compaction could not prepare a valid summary");
    }

    /** Only Skill bindings are scoped here. No Tool execution or request resource capture occurs. */
    private final class ManualCompactionScope implements AutoCloseable {
        private final AgentSessionStore.ControlLease lease;
        private final Consumer<AgentEvent> usage;
        private final AgentToolExecutor capturedTools;
        private final String correlationId;
        private boolean finishedPreparation;
        private boolean closed;

        private ManualCompactionScope(AgentSessionStore.ControlLease lease,
                CancellationSignal cancellation, Consumer<AgentEvent> usage, AgentToolExecutor capturedTools) {
            this.lease = lease;
            this.usage = usage;
            this.capturedTools = capturedTools;
            correlationId = "manual-compact-" + lease.controlId();
            lease.cancellation().onCancel(this::close);
            cancellation.onCancel(lease.cancellation()::cancel);
        }

        private void observeUsage(ModelEvent event) {
            AgentEvent forwarded;
            if (event instanceof ModelEvent.UsageStarted started) forwarded = new AgentEvent.ModelUsageStarted(
                    started.callId(), started.modelIdentifier());
            else if (event instanceof ModelEvent.UsageObserved observed) forwarded = new AgentEvent.ModelUsageObserved(
                    observed.callId(), observed.modelIdentifier(), observed.usage());
            else return;
            usage.accept(forwarded);
        }

        private synchronized void finishPreparation() {
            if (!finishedPreparation) {
                finishedPreparation = true;
                try {
                    capturedTools.closeSkillContext(correlationId);
                } catch (RuntimeException ignored) {
                    // Cleanup must not strand an otherwise released control reservation.
                }
            }
        }

        @Override
        public void close() {
            synchronized (sessions) {
                if (closed) return;
                closed = true;
                sessions.releaseControl(lease);
            }
        }
    }

    private final class PreparedManualCompaction implements GuidePreparedCompaction {
        private final ManualCompactionScope scope;
        private final GuideCompactResult outcome;
        private final List<ModelMessage> projection;

        private PreparedManualCompaction(ManualCompactionScope scope,
                GuideCompactResult outcome, List<ModelMessage> projection) {
            this.scope = scope;
            this.outcome = outcome;
            this.projection = List.copyOf(projection);
        }

        @Override public GuideCompactResult outcome() { return outcome; }
        @Override public List<ModelMessage> source() { return scope.lease.history(); }
        @Override public List<ModelMessage> projection() { return projection; }

        @Override
        public boolean current() {
            synchronized (sessions) {
                return !scope.closed && sessions.ownsControl(scope.lease);
            }
        }

        @Override
        public boolean publish() {
            synchronized (sessions) {
                if (!current()) return false;
                if (outcome.status() == GuideCompactResult.Status.COMPACTED) {
                    if (!sessions.publishControl(scope.lease, projection)) return false;
                    endpoint.estimates().computeIfPresent(scope.lease.key(), (key, previous) ->
                            new dev.openallay.guide.GuideContextEstimate(previous.requestId(),
                                    outcome.afterTokens(), endpoint.contextBudget(), endpoint.modelIdentifier(),
                                    endpoint.estimator().imageAccounting(projection)));
                } else sessions.releaseControl(scope.lease);
                scope.closed = true;
                return true;
            }
        }

        @Override public void close() { scope.close(); }
    }

    @Override
    public boolean hasContext(UUID actor, String sessionId) {
        return sessions.hasContext(new AgentSessionKey(actor, sessionId));
    }

    @Override
    public void hydrateContext(
            UUID actor,
            String sessionId,
            List<ModelMessage> messages,
            List<ContextCheckpoint> checkpoints) {
        sessions.hydrate(new AgentSessionKey(actor, sessionId), messages, checkpoints);
    }

    public CompletableFuture<AgentResult> ask(
            UUID actor,
            String question,
            ToolInvocationContext context,
            Consumer<AgentEvent> events) {
        return ask(
                actor,
                selectedSession(actor),
                UUID.randomUUID(),
                question,
                context,
                events);
    }

    @Override
    public CompletableFuture<AgentResult> ask(
            UUID actor,
            String session,
            UUID requestId,
            String question,
            ToolInvocationContext context,
            Consumer<AgentEvent> events) {
        return ask(actor, session, requestId, ModelMessage.userText(question),
                AgentRequest.unavailableImages(), context, events);
    }

    @Override
    public CompletableFuture<AgentResult> ask(
            UUID actor,
            String session,
            UUID requestId,
            ModelMessage userInput,
            dev.openallay.model.image.ImagePayloadResolver images,
            ToolInvocationContext context,
            Consumer<AgentEvent> events) {
        ClientCapabilitySnapshot requestCapabilities = capabilities.forRequest(context);
        // Command permission was frozen before capture; expose only the route Rhino will bind.
        PromptModes modes = new PromptModes(context.unrestrictedJavascript(),
                requestCapabilities.commandCapabilityAvailable(context.correlationId()));
        ClientGuideRuntime requestRuntime = withCapabilities(requestCapabilities, modes);
        AgentRequest request = new AgentRequest(
                requestId,
                actor,
                session,
                userInput,
                requestRuntime.systemPrompt(modes.unrestrictedJavascript(), modes.commandsAvailable()),
                context,
                true,
                images);
        dev.openallay.agent.ModelCallReceipts receipts = new dev.openallay.agent.ModelCallReceipts();
        return receipts.after(requestRuntime.agent.ask(request, event ->
                    receipts.accept(event, received -> dispatcher.execute(() -> events.accept(received)))))
                .thenCompose(result -> {
                    if (result.trace() != null) {
                        traces.record(result.trace());
                    }
                    // Model events enqueue a second hop in GuideService. Complete on the same
                    // dispatcher after their first hop so numeric application precedes cleanup.
                    CompletableFuture<AgentResult> handedOff = new CompletableFuture<>();
                    dispatcher.execute(() -> handedOff.complete(result));
                    return handedOff;
                });
    }

    public String selectedSession(UUID actor) {
        return selectedSessions.computeIfAbsent(actor, ignored -> "main");
    }

    public void selectSession(UUID actor, String sessionId) {
        new AgentSessionKey(actor, sessionId);
        selectedSessions.put(actor, sessionId);
    }



    public List<String> sessions(UUID actor) {
        java.util.TreeSet<String> ids = new java.util.TreeSet<>(sessions.sessions(actor).stream()
                .map(AgentSessionKey::sessionId).toList());
        ids.add(selectedSession(actor));
        return List.copyOf(ids);
    }

    public boolean closeSession(UUID actor, String sessionId) {
        boolean existed = sessions.sessions(actor).stream()
                .anyMatch(key -> key.sessionId().equals(sessionId));
        sessions.clear(new AgentSessionKey(actor, sessionId));
        clearContextEstimate(actor, sessionId);
        if (selectedSession(actor).equals(sessionId)) {
            selectedSessions.put(actor, "main");
        }
        return existed;
    }

    public boolean cancel(UUID actor) {
        return cancel(actor, selectedSession(actor));
    }

    @Override
    public boolean cancel(UUID actor, String sessionId) {
        return sessions.cancel(new AgentSessionKey(actor, sessionId));
    }

    @Override
    public ToolResult<Boolean> steer(
            UUID actor, String sessionId, UUID requestId, UUID messageId, ModelMessage message) {
        return sessions.steer(new AgentSessionKey(actor, sessionId), requestId, messageId, message);
    }

    @Override
    public boolean cancelSteer(UUID actor, String sessionId, UUID requestId, UUID messageId) {
        return sessions.cancelSteer(new AgentSessionKey(actor, sessionId), requestId, messageId);
    }

    @Override
    public boolean cancel(UUID actor, String sessionId, UUID expectedRequestId) {
        return sessions.cancel(new AgentSessionKey(actor, sessionId), expectedRequestId);
    }

    @Override
    public void clearSession(UUID actor, String sessionId) {
        sessions.clear(new AgentSessionKey(actor, sessionId));
        clearContextEstimate(actor, sessionId);
    }

    public void clearActor(UUID actor) {
        sessions.clearActor(actor);
        clearContextEstimates(actor);
        selectedSessions.remove(actor);
    }

    public LiveTraceStore traces() {
        return traces;
    }

    private String systemPrompt() {
        return systemPrompt(false);
    }

    private String systemPrompt(boolean unrestrictedJavascript) {
        return systemPrompt(unrestrictedJavascript, false);
    }

    private String systemPrompt(boolean unrestrictedJavascript, boolean commandsAvailable) {
        return dev.openallay.agent.AgentSystemPrompt.compose(
                capabilities.skills().metadataPrompt(),
                dev.openallay.script.schema.CoreJavascriptContract.render(
                        dev.openallay.script.data.MinecraftAgentHostGraph.declaredOnlyCatalog()),
                unrestrictedJavascript,
                capabilities.skills().find(dev.openallay.skill.SkillCatalogSnapshot.UNRESTRICTED_JAVASCRIPT)
                        .map(dev.openallay.skill.SkillDocument::instructions).orElse(""),
                commandsAvailable);
    }

    private static EndpointRuntime endpoint(
            ModelClient model,
            Gson gson,
            ContextBudget contextBudget,
            String modelIdentifier, ContextTokenEstimator estimator) {
        Objects.requireNonNull(estimator, "estimator");
        ModelRequestScheduler scheduler = new ModelRequestScheduler(
                dev.openallay.model.ObservingModelClient.observe(model, modelIdentifier == null ? "" : modelIdentifier));
        ContextCompactor compactor = contextBudget == null ? null : new ContextCompactor(
                scheduler,
                gson,
                estimator,
                contextBudget,
                modelIdentifier,
                Clock.systemUTC());
        return new EndpointRuntime(scheduler, compactor, estimator, contextBudget, modelIdentifier,
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    private static ClientCapabilitySnapshot defaultCapabilities(FeatureServices runtime) {
        ToolResult<ClientCapabilitySnapshot> resolved = new ClientCapabilityResolver().resolve(
                CapabilityPolicy.defaults(), runtime.tools().registrations(), runtime.skills());
        if (resolved instanceof ToolResult.Success<ClientCapabilitySnapshot> success) {
            return success.value();
        }
        ToolResult.Failure<ClientCapabilitySnapshot> failure =
                (ToolResult.Failure<ClientCapabilitySnapshot>) resolved;
        throw new IllegalStateException(failure.code() + ": " + failure.message());
    }

    /** Optional guidance modes from an actual admitted request, never from a new resource capture. */
    private record PromptModes(boolean unrestrictedJavascript, boolean commandsAvailable) {}

    private record EndpointRuntime(
            ModelRequestScheduler scheduler,
            ContextCompactor compactor,
            ContextTokenEstimator estimator,
            ContextBudget contextBudget,
            String modelIdentifier,
            Map<AgentSessionKey, dev.openallay.guide.GuideContextEstimate> estimates,
            Map<AgentSessionKey, PromptModes> promptModes) {}
}
