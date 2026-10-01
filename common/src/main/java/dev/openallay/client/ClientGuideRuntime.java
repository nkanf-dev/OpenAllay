package dev.openallay.client;

import com.google.gson.Gson;
import dev.openallay.OpenAllayRuntime;
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

    public ClientGuideRuntime(
            OpenAllayRuntime runtime,
            ModelClient model,
            AgentSessionStore sessions,
            Gson gson,
            ClientEventDispatcher dispatcher) {
        this(runtime, model, sessions, gson, dispatcher, null, new LiveTraceStore(null), null, null);
    }

    public ClientGuideRuntime(
            OpenAllayRuntime runtime,
            ModelClient model,
            AgentSessionStore sessions,
            Gson gson,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension) {
        this(runtime, model, sessions, gson, dispatcher, extension, new LiveTraceStore(null), null, null);
    }

    ClientGuideRuntime(
            OpenAllayRuntime runtime,
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
                new ConcurrentHashMap<>());
    }

    private ClientGuideRuntime(
            EndpointRuntime endpoint,
            AgentSessionStore sessions,
            Gson gson,
            ClientEventDispatcher dispatcher,
            AgentToolExecutor extension,
            LiveTraceStore traces,
            ClientCapabilitySnapshot capabilities,
            Map<UUID, String> selectedSessions) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.traces = Objects.requireNonNull(traces, "traces");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.gson = Objects.requireNonNull(gson, "gson");
        this.extension = extension;
        this.selectedSessions = Objects.requireNonNull(selectedSessions, "selectedSessions");
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
                            endpoint.estimates().put(request.sessionKey(),
                                    new dev.openallay.guide.GuideContextEstimate(request.requestId(), tokens));
                        }
                    }
                });
    }

    ClientGuideRuntime withCapabilities(ClientCapabilitySnapshot replacement) {
        return new ClientGuideRuntime(
                endpoint,
                sessions,
                gson,
                dispatcher,
                extension,
                traces,
                replacement,
                selectedSessions);
    }

    @Override
    public Optional<dev.openallay.guide.GuideContextEstimate> contextEstimate(
            String profileId, UUID actor, String sessionId) {
        return Optional.ofNullable(endpoint.estimates().get(new AgentSessionKey(actor, sessionId)));
    }

    void clearContextEstimate(UUID actor, String sessionId) {
        synchronized (sessions) {
            endpoint.estimates().remove(new AgentSessionKey(actor, sessionId));
        }
    }

    void clearContextEstimates(UUID actor) {
        synchronized (sessions) {
            endpoint.estimates().keySet().removeIf(key -> key.actorId().equals(actor));
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
        ClientCapabilitySnapshot requestCapabilities = capabilities.forRequest(context);
        ClientGuideRuntime requestRuntime = withCapabilities(requestCapabilities);
        AgentRequest request = new AgentRequest(
                requestId,
                actor,
                session,
                question,
                requestRuntime.systemPrompt(context.unrestrictedJavascript(),
                        requestCapabilities.commandCapabilityAvailable(context.correlationId())),
                context,
                true);
        return requestRuntime.agent.ask(request, event ->
                    dispatcher.execute(() -> events.accept(event)))
                .thenApply(result -> {
                    if (result.trace() != null) {
                        traces.record(result.trace());
                    }
                    return result;
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
        ModelRequestScheduler scheduler = new ModelRequestScheduler(model);
        ContextCompactor compactor = contextBudget == null ? null : new ContextCompactor(
                scheduler,
                gson,
                estimator,
                contextBudget,
                modelIdentifier,
                Clock.systemUTC());
        return new EndpointRuntime(scheduler, compactor, estimator, contextBudget, modelIdentifier,
                new ConcurrentHashMap<>());
    }

    private static ClientCapabilitySnapshot defaultCapabilities(OpenAllayRuntime runtime) {
        ToolResult<ClientCapabilitySnapshot> resolved = new ClientCapabilityResolver().resolve(
                CapabilityPolicy.defaults(), runtime.tools().registrations(), runtime.skills());
        if (resolved instanceof ToolResult.Success<ClientCapabilitySnapshot> success) {
            return success.value();
        }
        ToolResult.Failure<ClientCapabilitySnapshot> failure =
                (ToolResult.Failure<ClientCapabilitySnapshot>) resolved;
        throw new IllegalStateException(failure.code() + ": " + failure.message());
    }

    private record EndpointRuntime(
            ModelRequestScheduler scheduler,
            ContextCompactor compactor,
            ContextTokenEstimator estimator,
            ContextBudget contextBudget,
            String modelIdentifier,
            Map<AgentSessionKey, dev.openallay.guide.GuideContextEstimate> estimates) {}
}
