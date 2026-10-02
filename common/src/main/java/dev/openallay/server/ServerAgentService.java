package dev.openallay.server;

import com.google.gson.Gson;
import dev.openallay.agent.AgentEvent;
import dev.openallay.agent.AgentRequest;
import dev.openallay.agent.GameGuideAgent;
import dev.openallay.agent.session.AgentSessionKey;
import dev.openallay.agent.session.AgentSessionStore;
import dev.openallay.agent.tool.AgentToolExecutor;
import dev.openallay.bridge.protocol.ServerAgentEventCodec;
import dev.openallay.bridge.protocol.ServerAgentEventPayload;
import dev.openallay.bridge.protocol.ServerAgentHistoryMessage;
import dev.openallay.bridge.protocol.ServerAgentRequestPayload;
import dev.openallay.context.ContextCapability;
import dev.openallay.context.ToolInvocationContext;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.tool.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class ServerAgentService {
    @FunctionalInterface
    public interface ContextProvider {
        CompletableFuture<ToolInvocationContext> capture(
                UUID actorId,
                Set<ContextCapability> capabilities,
                String correlationId,
                dev.openallay.model.CancellationSignal cancellation);
    }

    @FunctionalInterface
    public interface RequestRuntimeFactory {
        ToolResult<RequestRuntime> create(UUID actorId, ServerAgentRequestPayload payload);
    }

    private final RequestRuntimeFactory runtimes;
    private final AgentSessionStore sessions;
    private final ContextProvider contexts;
    private final ServerGuideEvents events;
    private final ServerAgentEventCodec eventCodec;
    private final String systemPrompt;
    private final Function<dev.openallay.model.CancellationSignal, CompletableFuture<Void>> dispatchReady;
    private final Map<UUID, Owner> active = new ConcurrentHashMap<>();

    public ServerAgentService(
            GameGuideAgent agent,
            AgentToolExecutor tools,
            AgentSessionStore sessions,
            ContextProvider contexts,
            ServerGuideEvents events,
            Gson gson,
            String systemPrompt) {
        this(agent, tools, sessions, contexts, events, gson, systemPrompt,
                cancellation -> CompletableFuture.completedFuture(null));
    }

    public ServerAgentService(
            GameGuideAgent agent,
            AgentToolExecutor tools,
            AgentSessionStore sessions,
            ContextProvider contexts,
            ServerGuideEvents events,
            Gson gson,
            String systemPrompt,
            Function<dev.openallay.model.CancellationSignal, CompletableFuture<Void>> dispatchReady) {
        this(
                (actor, payload) -> new ToolResult.Success<>(
                        new RequestRuntime(agent, tools, () -> {})),
                sessions,
                contexts,
                events,
                gson,
                systemPrompt,
                dispatchReady);
    }

    public ServerAgentService(
            RequestRuntimeFactory runtimes,
            AgentSessionStore sessions,
            ContextProvider contexts,
            ServerGuideEvents events,
            Gson gson,
            String systemPrompt,
            Function<dev.openallay.model.CancellationSignal, CompletableFuture<Void>> dispatchReady) {
        this.runtimes = java.util.Objects.requireNonNull(runtimes, "runtimes");
        this.sessions = sessions;
        this.contexts = contexts;
        this.events = events;
        this.eventCodec = new ServerAgentEventCodec(gson);
        this.systemPrompt = systemPrompt;
        this.dispatchReady = dispatchReady;
    }

    public ToolResult<Accepted> ask(UUID sender, ServerAgentRequestPayload payload) {
        ToolResult<RequestRuntime> prepared = runtimes.create(sender, payload);
        if (prepared instanceof ToolResult.Failure<RequestRuntime> failure) {
            return new ToolResult.Failure<>(failure.code(), failure.message());
        }
        RequestRuntime runtime = ((ToolResult.Success<RequestRuntime>) prepared).value();
        Owner owner = new Owner(
                sender,
                payload.sessionId(),
                payload.question(),
                payload.history().stream().map(ServerAgentHistoryMessage::toModelMessage).toList(),
                new dev.openallay.model.CancellationSignal(),
                runtime);
        if (active.putIfAbsent(payload.requestId(), owner) != null) {
            runtime.close().run();
            return new ToolResult.Failure<>("duplicate_request", "Request ID is already active");
        }
        CompletableFuture<?> work;
        try {
            work = dispatchReady.apply(owner.cancellation())
                .thenCompose(ignored -> {
                    if (!owns(payload.requestId(), owner) || owner.cancellation().isCancelled()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return contexts.capture(
                            sender,
                            runtime.tools().requiredContext(),
                            sender + "/" + payload.requestId(),
                            owner.cancellation());
                })
                .thenCompose(context -> {
                    if (context == null
                            || !owns(payload.requestId(), owner)
                            || owner.cancellation().isCancelled()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    synchronized (owner) {
                        if (!owns(payload.requestId(), owner) || owner.cancellation().isCancelled()) {
                            return CompletableFuture.completedFuture(null);
                        }
                        owner.engineStarted = true;
                    }
                    AgentRequest request = new AgentRequest(
                            payload.requestId(),
                            sender,
                            payload.sessionId(),
                            payload.question(),
                            runtime.systemPrompt() == null
                                    ? systemPrompt
                                    : runtime.systemPrompt(),
                            context,
                            payload.stream());
                    List<ModelMessage> restored = payload.history().stream()
                            .map(ServerAgentHistoryMessage::toModelMessage)
                            .toList();
                    return sessions.hasContext(request.sessionKey())
                            ? runtime.agent().ask(request,
                                    event -> publish(payload.requestId(), owner, event))
                            : runtime.agent().askWithHistory(request, restored,
                                    event -> publish(payload.requestId(), owner, event));
                })
                ;
        } catch (RuntimeException failure) {
            work = CompletableFuture.failedFuture(failure);
        }
        work.whenComplete((result, failure) -> {
            try {
                if (failure != null) publishFailure(payload.requestId(), owner, failure);
            } finally {
                synchronized (owner) {
                    owner.engineFinished = true;
                    releaseIfFinished(payload.requestId(), owner);
                }
            }
        });
        return new ToolResult.Success<>(new Accepted(payload.requestId(), payload.sessionId()));
    }

    public boolean cancel(UUID sender, UUID requestId) {
        Owner owner = active.get(requestId);
        if (owner == null || !owner.actorId().equals(sender)) return false;
        synchronized (owner) {
            if (!owns(requestId, owner) || owner.terminal) return false;
            if (!owner.engineStarted) {
                List<ModelMessage> original = List.of(ModelMessage.userText(owner.question()),
                        new ModelMessage(ModelRole.ASSISTANT, List.of(new ModelContent.Text(
                                "[OpenAllay request ended: agent_cancelled] Agent request was cancelled"))));
                List<ModelMessage> projected = new java.util.ArrayList<>(owner.history());
                projected.addAll(original);
                publish(requestId, owner, new AgentEvent.ContextFinalized(projected, original));
                publish(requestId, owner, new AgentEvent.Failed("agent_cancelled", "Agent request was cancelled"));
                owner.cancellation().cancel();
                owner.engineFinished = true;
                releaseIfFinished(requestId, owner);
                return true;
            }
            boolean beforeCapture = owner.cancellation().cancel();
            boolean agent = sessions.cancel(new AgentSessionKey(sender, owner.sessionId()), requestId);
            return beforeCapture || agent;
        }
    }

    public int disconnect(UUID sender) {
        int count = 0;
        for (var entry : active.entrySet()) {
            Owner owner = entry.getValue();
            if (!owner.actorId().equals(sender)) continue;
            synchronized (owner) {
                if (!owns(entry.getKey(), owner)) continue;
                count++;
                owner.disconnected = true;
                owner.cancellation().cancel();
                if (!owner.engineStarted) owner.engineFinished = true;
                releaseIfFinished(entry.getKey(), owner);
            }
        }
        sessions.clearActor(sender);
        return count;
    }

    public int activeRequests() {
        return (int) active.values().stream().filter(owner -> !owner.disconnected).count();
    }

    private void publish(UUID requestId, Owner owner, AgentEvent event) {
        synchronized (owner) {
            if (!owns(requestId, owner) || owner.released) return;
            boolean numeric = event instanceof AgentEvent.ModelUsageStarted
                    || event instanceof AgentEvent.ModelUsageObserved;
            if (owner.terminal && !numeric) return;
            if (event instanceof AgentEvent.ModelUsageStarted started) owner.pendingCalls.add(started.callId());
            if (event instanceof AgentEvent.FinalText || event instanceof AgentEvent.Failed) owner.terminal = true;
            try {
                if (!owner.disconnected) events.send(owner.actorId(), eventCodec.encode(requestId, event));
            } finally {
                if (event instanceof AgentEvent.ModelUsageObserved observed) owner.pendingCalls.remove(observed.callId());
                releaseIfFinished(requestId, owner);
            }
        }
    }

    private void releaseIfFinished(UUID requestId, Owner owner) {
        if (owner.released || !owner.engineFinished || !owner.pendingCalls.isEmpty()) return;
        owner.released = true;
        active.remove(requestId, owner);
        try {
            owner.runtime().close().run();
        } finally {
            if (!owner.disconnected) events.send(owner.actorId(), eventCodec.encode(requestId,
                    new AgentEvent.RequestReleased()));
        }
    }

    private void publishFailure(UUID requestId, Owner owner, Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        publish(requestId, owner, new AgentEvent.Failed("server_agent_failure", message));
    }

    private boolean owns(UUID requestId, Owner owner) {
        return owner.equals(active.get(requestId));
    }

    public record Accepted(UUID requestId, String sessionId) {}

    public record RequestRuntime(
            GameGuideAgent agent,
            AgentToolExecutor tools,
            String systemPrompt,
            Runnable close) {
        public RequestRuntime {
            java.util.Objects.requireNonNull(agent, "agent");
            java.util.Objects.requireNonNull(tools, "tools");
            java.util.Objects.requireNonNull(close, "close");
        }

        public RequestRuntime(
                GameGuideAgent agent, AgentToolExecutor tools, Runnable close) {
            this(agent, tools, null, close);
        }
    }

    private static final class Owner {
        private final UUID actorId;
        private final String sessionId;
        private final String question;
        private final List<ModelMessage> history;
        private final dev.openallay.model.CancellationSignal cancellation;
        private final RequestRuntime runtime;
        private final Set<UUID> pendingCalls = new java.util.HashSet<>();
        private boolean terminal;
        private boolean engineStarted;
        private boolean engineFinished;
        private boolean released;
        private boolean disconnected;

        private Owner(UUID actorId, String sessionId, String question, List<ModelMessage> history,
                dev.openallay.model.CancellationSignal cancellation, RequestRuntime runtime) {
            this.actorId = actorId;
            this.sessionId = sessionId;
            this.question = question;
            this.history = dev.openallay.agent.context.ModelContextCodec.safe(history);
            this.cancellation = cancellation;
            this.runtime = runtime;
        }
        private UUID actorId() { return actorId; }
        private String sessionId() { return sessionId; }
        private String question() { return question; }
        private List<ModelMessage> history() { return history; }
        private dev.openallay.model.CancellationSignal cancellation() { return cancellation; }
        private RequestRuntime runtime() { return runtime; }
    }
}
