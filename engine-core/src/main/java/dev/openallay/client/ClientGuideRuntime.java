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
        final class $oaPattern0_Holder { dev.openallay.agent.tool.AgentToolExecutor value; RemoteToolExecutor bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
toolExecutor = extension == null
                ? local
                : (($oaPattern0_holder.value = extension) instanceof dev.openallay.bridge.client.RemoteToolExecutor && (($oaPattern0_holder.bound = (RemoteToolExecutor) $oaPattern0_holder.value) != null))
                        ? new ClientPlacedToolExecutor(local, $oaPattern0_holder.bound)
                        : new CompositeAgentToolExecutor(dev.openallay.util.Java8Collections.listOf(local, extension));
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
                                                    dev.openallay.util.Java8Collections.toList(java.util.stream.Stream.concat(
                                                            sessions.history(request.sessionKey()).stream(),
                                                            java.util.stream.Stream.of(request.userInput()))))));
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
                estimator.estimate(budgetSystemPrompt(systemPrompt()), dev.openallay.util.Java8Collections.listOf(), toolExecutor.definitions()),
                estimator.estimate(enabled.budgetSystemPrompt(enabled.systemPrompt(true, true)),
                        dev.openallay.util.Java8Collections.listOf(), enabled.toolExecutor.definitions()));
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
        dev.openallay.skill.RetainedSkillContext retained = new dev.openallay.skill.RetainedSkillContext();
        String correlation = "context-budget-" + UUID.randomUUID();
        captured.prepareSystem(system, retained);
        captured.prepareContext(correlation, dev.openallay.util.Java8Collections.listOf(), retained);
        try {
            String facts = captured.skillManifest(correlation);
            return dev.openallay.util.Java8Strings.isBlank(facts) ? system : system + "\n" + facts;
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
        final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<dev.openallay.agent.session.AgentSessionStore.ControlLease> value; ToolResult.Failure<AgentSessionStore.ControlLease> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = reservation) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern1_holder.bound = (ToolResult.Failure<AgentSessionStore.ControlLease>) $oaPattern1_holder.value) != null))) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>($oaPattern1_holder.bound.code(), $oaPattern1_holder.bound.message()));
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
            List<dev.openallay.model.ModelToolDefinition> definitions = dev.openallay.util.Java8Collections.listCopyOf(capturedTools.definitions());
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
                return dev.openallay.util.Java8Strings.isBlank(facts) ? system : system + "\n" + facts;
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
        final class $oaPattern2_Holder { dev.openallay.model.ModelContent value; ModelContent.Text bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = message.content().get(0)) instanceof dev.openallay.model.ModelContent.Text && (($oaPattern2_holder.bound = (ModelContent.Text) $oaPattern2_holder.value) != null))
                && $oaPattern2_holder.bound.text().startsWith("[OpenAllay derived conversation memory; NOT factual evidence]\n")) {
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
        final class $oaPattern3_Holder { java.lang.Throwable value; ModelClientException bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = failure) instanceof dev.openallay.model.ModelClientException && (($oaPattern3_holder.bound = (ModelClientException) $oaPattern3_holder.value) != null))) return new ToolResult.Failure<>(
                $oaPattern3_holder.bound.failure().code(), $oaPattern3_holder.bound.failure().message());
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
            final class $oaPattern4_Holder { dev.openallay.model.ModelEvent value; ModelEvent.UsageStarted bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = event) instanceof dev.openallay.model.ModelEvent.UsageStarted && (($oaPattern4_holder.bound = (ModelEvent.UsageStarted) $oaPattern4_holder.value) != null))) forwarded = new AgentEvent.ModelUsageStarted(
                    $oaPattern4_holder.bound.callId(), $oaPattern4_holder.bound.modelIdentifier());
            else {
final class $oaPattern5_Holder { dev.openallay.model.ModelEvent value; ModelEvent.UsageObserved bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = event) instanceof dev.openallay.model.ModelEvent.UsageObserved && (($oaPattern5_holder.bound = (ModelEvent.UsageObserved) $oaPattern5_holder.value) != null))) forwarded = new AgentEvent.ModelUsageObserved(
                    $oaPattern5_holder.bound.callId(), $oaPattern5_holder.bound.modelIdentifier(), $oaPattern5_holder.bound.usage());
            else return;
}
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
            this.projection = dev.openallay.util.Java8Collections.listCopyOf(projection);
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
        java.util.TreeSet<String> ids = new java.util.TreeSet<>(dev.openallay.util.Java8Collections.toList(sessions.sessions(actor).stream()
                .map(AgentSessionKey::sessionId)));
        ids.add(selectedSession(actor));
        return dev.openallay.util.Java8Collections.listCopyOf(ids);
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
        final class $oaPattern6_Holder { dev.openallay.tool.ToolResult<dev.openallay.capability.ClientCapabilitySnapshot> value; ToolResult.Success<ClientCapabilitySnapshot> bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = resolved) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern6_holder.bound = (ToolResult.Success<ClientCapabilitySnapshot>) $oaPattern6_holder.value) != null))) {
            return $oaPattern6_holder.bound.value();
        }
        ToolResult.Failure<ClientCapabilitySnapshot> failure =
                (ToolResult.Failure<ClientCapabilitySnapshot>) resolved;
        throw new IllegalStateException(failure.code() + ": " + failure.message());
    }

    /** Optional guidance modes from an actual admitted request, never from a new resource capture. */
    @dev.openallay.value.ValueType(PromptModes.ValueSchemaProvider.class)
private static final class PromptModes {
    private final boolean unrestrictedJavascript;
    private final boolean commandsAvailable;
    private PromptModes(boolean unrestrictedJavascript, boolean commandsAvailable) {
        this.unrestrictedJavascript = unrestrictedJavascript;
        this.commandsAvailable = commandsAvailable;
    }
    public boolean unrestrictedJavascript() { return unrestrictedJavascript; }
    public boolean commandsAvailable() { return commandsAvailable; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PromptModes)) return false;
        PromptModes that = (PromptModes) other;
        return unrestrictedJavascript == that.unrestrictedJavascript && commandsAvailable == that.commandsAvailable;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(unrestrictedJavascript);
        hash = 31 * hash + Boolean.hashCode(commandsAvailable);
        return hash;
    }
    @Override public String toString() { return "PromptModes[unrestrictedJavascript=" + unrestrictedJavascript + ", commandsAvailable=" + commandsAvailable + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PromptModes> schema() {
            return new dev.openallay.value.ValueSchema<>(PromptModes.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PromptModes>>asList(new dev.openallay.value.ValueSchema.Component<>(PromptModes.class, "unrestrictedJavascript", PromptModes::unrestrictedJavascript), new dev.openallay.value.ValueSchema.Component<>(PromptModes.class, "commandsAvailable", PromptModes::commandsAvailable)), arguments -> new PromptModes((Boolean) arguments[0], (Boolean) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(EndpointRuntime.ValueSchemaProvider.class)
private static final class EndpointRuntime {
    private final ModelRequestScheduler scheduler;
    private final ContextCompactor compactor;
    private final ContextTokenEstimator estimator;
    private final ContextBudget contextBudget;
    private final String modelIdentifier;
    private final Map<AgentSessionKey, dev.openallay.guide.GuideContextEstimate> estimates;
    private final Map<AgentSessionKey, PromptModes> promptModes;
    private EndpointRuntime(ModelRequestScheduler scheduler, ContextCompactor compactor, ContextTokenEstimator estimator, ContextBudget contextBudget, String modelIdentifier, Map<AgentSessionKey, dev.openallay.guide.GuideContextEstimate> estimates, Map<AgentSessionKey, PromptModes> promptModes) {
        this.scheduler = scheduler;
        this.compactor = compactor;
        this.estimator = estimator;
        this.contextBudget = contextBudget;
        this.modelIdentifier = modelIdentifier;
        this.estimates = estimates;
        this.promptModes = promptModes;
    }
    public ModelRequestScheduler scheduler() { return scheduler; }
    public ContextCompactor compactor() { return compactor; }
    public ContextTokenEstimator estimator() { return estimator; }
    public ContextBudget contextBudget() { return contextBudget; }
    public String modelIdentifier() { return modelIdentifier; }
    public Map<AgentSessionKey, dev.openallay.guide.GuideContextEstimate> estimates() { return estimates; }
    public Map<AgentSessionKey, PromptModes> promptModes() { return promptModes; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EndpointRuntime)) return false;
        EndpointRuntime that = (EndpointRuntime) other;
        return java.util.Objects.equals(scheduler, that.scheduler) && java.util.Objects.equals(compactor, that.compactor) && java.util.Objects.equals(estimator, that.estimator) && java.util.Objects.equals(contextBudget, that.contextBudget) && java.util.Objects.equals(modelIdentifier, that.modelIdentifier) && java.util.Objects.equals(estimates, that.estimates) && java.util.Objects.equals(promptModes, that.promptModes);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(scheduler);
        hash = 31 * hash + java.util.Objects.hashCode(compactor);
        hash = 31 * hash + java.util.Objects.hashCode(estimator);
        hash = 31 * hash + java.util.Objects.hashCode(contextBudget);
        hash = 31 * hash + java.util.Objects.hashCode(modelIdentifier);
        hash = 31 * hash + java.util.Objects.hashCode(estimates);
        hash = 31 * hash + java.util.Objects.hashCode(promptModes);
        return hash;
    }
    @Override public String toString() { return "EndpointRuntime[scheduler=" + scheduler + ", compactor=" + compactor + ", estimator=" + estimator + ", contextBudget=" + contextBudget + ", modelIdentifier=" + modelIdentifier + ", estimates=" + estimates + ", promptModes=" + promptModes + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<EndpointRuntime> schema() {
            return new dev.openallay.value.ValueSchema<>(EndpointRuntime.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<EndpointRuntime>>asList(new dev.openallay.value.ValueSchema.Component<>(EndpointRuntime.class, "scheduler", EndpointRuntime::scheduler), new dev.openallay.value.ValueSchema.Component<>(EndpointRuntime.class, "compactor", EndpointRuntime::compactor), new dev.openallay.value.ValueSchema.Component<>(EndpointRuntime.class, "estimator", EndpointRuntime::estimator), new dev.openallay.value.ValueSchema.Component<>(EndpointRuntime.class, "contextBudget", EndpointRuntime::contextBudget), new dev.openallay.value.ValueSchema.Component<>(EndpointRuntime.class, "modelIdentifier", EndpointRuntime::modelIdentifier), new dev.openallay.value.ValueSchema.Component<>(EndpointRuntime.class, "estimates", EndpointRuntime::estimates), new dev.openallay.value.ValueSchema.Component<>(EndpointRuntime.class, "promptModes", EndpointRuntime::promptModes)), arguments -> new EndpointRuntime((ModelRequestScheduler) arguments[0], (ContextCompactor) arguments[1], (ContextTokenEstimator) arguments[2], (ContextBudget) arguments[3], (String) arguments[4], (Map) arguments[5], (Map) arguments[6]));
        }
    }
}
}
