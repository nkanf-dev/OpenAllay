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
import dev.openallay.bridge.protocol.ServerAgentSteerPayload;
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
        default ContextProvider bind(UUID actor) { return this; }
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
    private static final java.util.concurrent.Executor IMAGE_WORKER =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "openallay-server-image-input");
                thread.setDaemon(true);
                return thread;
            });
    private final Object requestAdmissionLock = new Object();
    private final Map<UUID, Owner> active = new ConcurrentHashMap<>();
    private final Map<UUID, Owner> pendingRelease = new ConcurrentHashMap<>();
    private final Map<AgentSessionKey, UUID> sessionAdmissions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> imageScopes = new ConcurrentHashMap<>();
    private final dev.openallay.model.image.ImageAttachmentStore images;
    private final dev.openallay.model.image.ImageInputCapability imageCapability;

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
        this(runtimes, sessions, contexts, events, gson, systemPrompt, dispatchReady,
                null, dev.openallay.model.image.ImageInputCapability.UNKNOWN);
    }

    public ServerAgentService(
            RequestRuntimeFactory runtimes,
            AgentSessionStore sessions,
            ContextProvider contexts,
            ServerGuideEvents events,
            Gson gson,
            String systemPrompt,
            Function<dev.openallay.model.CancellationSignal, CompletableFuture<Void>> dispatchReady,
            dev.openallay.model.image.ImageAttachmentStore images,
            dev.openallay.model.image.ImageInputCapability imageCapability) {
        this.images = images;
        this.imageCapability = java.util.Objects.requireNonNull(imageCapability, "imageCapability");
        this.runtimes = java.util.Objects.requireNonNull(runtimes, "runtimes");
        this.sessions = sessions;
        this.contexts = contexts;
        this.events = events;
        this.eventCodec = new ServerAgentEventCodec(gson);
        this.systemPrompt = systemPrompt;
        this.dispatchReady = dispatchReady;
    }

    public ToolResult<Accepted> ask(UUID sender, ServerAgentRequestPayload payload) {
        java.util.Objects.requireNonNull(sender, "sender");
        java.util.Objects.requireNonNull(payload, "payload");
        ServerGuideEvents boundEvents = events.bind(sender);
        ContextProvider boundContexts = contexts.bind(sender);
        List<ModelMessage> restored = payload.history().stream()
                .map(ServerAgentHistoryMessage::toModelMessage).toList();
        ModelMessage userInput = payload.userInput().toModelMessage();
        AgentSessionKey key = new AgentSessionKey(sender, payload.sessionId());
        Owner owner;
        RequestRuntime[] created = new RequestRuntime[1];
        Owner[] admitted = new Owner[1];
        try {
        synchronized (requestAdmissionLock) {
            if (active.containsKey(payload.requestId()) || pendingRelease.containsKey(payload.requestId())) {
                return new ToolResult.Failure<>("duplicate_request", "Request ID is active or awaiting release");
            }
            if (sessionAdmissions.containsKey(key)) {
                return new ToolResult.Failure<>("agent_busy", "An Agent request is active or awaiting release in this session");
            }
            List<dev.openallay.model.image.ImageReference> required = imageReferences(
                    java.util.stream.Stream.of(restored, sessions.history(key), List.of(userInput))
                            .flatMap(List::stream).toList());
            if (!required.isEmpty()
                    && imageCapability != dev.openallay.model.image.ImageInputCapability.SUPPORTED) {
                return new ToolResult.Failure<>(
                        imageCapability == dev.openallay.model.image.ImageInputCapability.UNKNOWN
                                ? "image_input_unknown" : "image_input_unsupported",
                        "The server model has no confirmed image input support. Select an image-capable model or remove images from this conversation.");
            }
            if (!required.isEmpty() && images == null) {
                return new ToolResult.Failure<>("image_unavailable", "The server image store is unavailable");
            }
            ToolResult<RequestRuntime> prepared = runtimes.create(sender, payload);
            if (prepared instanceof ToolResult.Failure<RequestRuntime> failure) {
                return new ToolResult.Failure<>(failure.code(), failure.message());
            }
            RequestRuntime runtime = ((ToolResult.Success<RequestRuntime>) prepared).value();
            created[0] = runtime;
            UUID scope = imageScopes.computeIfAbsent(sender, ignored -> UUID.randomUUID());
            owner = new Owner(sender, payload.sessionId(), userInput, restored,
                    new dev.openallay.model.CancellationSignal(), runtime, scope, required, boundEvents, boundContexts);
            admitted[0] = owner;
            active.put(payload.requestId(), owner);
            sessionAdmissions.put(key, payload.requestId());
        }
        } catch (RuntimeException | Error failure) {
            synchronized (requestAdmissionLock) {
                if (admitted[0] != null) active.remove(payload.requestId(), admitted[0]);
                sessionAdmissions.remove(key, payload.requestId());
            }
            if (created[0] != null) {
                try { created[0].close().run(); }
                catch (RuntimeException | Error cleanupFailure) {
                    if (cleanupFailure instanceof Error && !(failure instanceof Error)) { if (cleanupFailure != failure) cleanupFailure.addSuppressed(failure); throw cleanupFailure; }
                    if (failure != cleanupFailure) failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
        RequestRuntime runtime = owner.runtime();
        CompletableFuture<?> work;
        Error synchronousFatal = null;
        try {
            CompletableFuture<Void> preparedImages = owner.requiredImages.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : imageOperation(payload.requestId(), owner, () -> prepareImages(payload, owner));
            work = preparedImages.thenCompose(ignored -> {
                    if (!owns(payload.requestId(), owner) || owner.cancellation().isCancelled()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return dispatchReady.apply(owner.cancellation());
                })
                .thenCompose(ignored -> {
                    if (!owns(payload.requestId(), owner) || owner.cancellation().isCancelled()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return owner.contexts.capture(
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
                            owner.userInput(),
                            runtime.systemPrompt() == null
                                    ? systemPrompt
                                    : runtime.systemPrompt(),
                            context,
                            payload.stream(),
                            reference -> readImage(payload.requestId(), owner, reference));
                    return sessions.hasContext(request.sessionKey())
                            ? runtime.agent().ask(request,
                                    event -> publish(payload.requestId(), owner, event))
                            : runtime.agent().askWithHistory(request, restored,
                                    event -> publish(payload.requestId(), owner, event));
                })
                ;
        } catch (RuntimeException failure) {
            work = CompletableFuture.failedFuture(failure);
        } catch (Error failure) {
            synchronousFatal = failure;
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
        if (synchronousFatal != null) throw synchronousFatal;
        return new ToolResult.Success<>(new Accepted(payload.requestId(), payload.sessionId()));
    }

    /** Applies only to the exact live actor/request. PUT replaces an unconsumed inbox entry. */
    public boolean steer(UUID sender, ServerAgentSteerPayload payload) {
        Owner owner = active.get(payload.requestId());
        if (owner != null && owner.actorId().equals(sender)) {
            synchronized (owner) {
                if (ownsRequest(sender, payload.requestId())) {
                    if (payload.operation() == ServerAgentSteerPayload.Operation.REMOVE) {
                        boolean importing = owner.imports.remove(payload.messageId()) != null;
                        boolean removed = owner.reserved
                                ? sessions.cancelSteer(owner.key(), payload.requestId(), payload.messageId())
                                : owner.inbox.remove(payload.messageId()) != null;
                        return importing || removed;
                    }
                    Object operation = new Object();
                    owner.imports.put(payload.messageId(), operation);
                    // Text and image instructions share the existing serial preparation worker.
                    // A later text PUT cannot pass an earlier image import. The operation count
                    // also keeps cleanup behind revoked work until its actual preparation settles.
                    imageOperation(payload.requestId(), owner,
                            () -> prepareSteer(payload, owner, operation));
                    return true;
                }
            }
        }
        rejectSteer(sender, payload);
        return false;
    }

    private boolean prepareSteer(ServerAgentSteerPayload payload, Owner owner, Object operation) {
        synchronized (owner) {
            if (!currentImport(payload, owner, operation)) return false;
        }
        ModelMessage message;
        try {
            message = owner.runtime().prepareSteer().apply(payload);
            ServerAgentSteerPayload.validateMessage(ServerAgentHistoryMessage.from(message));
            List<dev.openallay.model.image.ImageReference> required = imageReferences(List.of(message));
            if (!required.isEmpty()) {
                if (images == null) throw new IllegalArgumentException("Server image store is unavailable");
                java.util.Set<dev.openallay.model.image.ImageReference> retained;
                synchronized (owner) {
                    if (!currentImport(payload, owner, operation)) return false;
                    retained = new java.util.LinkedHashSet<>(owner.allowedImages);
                    retained.addAll(required);
                }
                // Keep store work outside the owner lock. Stop revokes the token immediately;
                // the tracked image operation keeps all pins alive only until actual cleanup.
                images.retain(owner.actorId(), requestImageOwner(owner.imageScope, payload.requestId()),
                        List.copyOf(retained));
                synchronized (owner) {
                    if (!currentImport(payload, owner, operation)) return false;
                    owner.allowedImages.addAll(required);
                }
            }
        } catch (java.io.IOException | RuntimeException invalid) {
            synchronized (owner) {
                if (!currentImport(payload, owner, operation)) return false;
                owner.imports.remove(payload.messageId());
                rejectSteer(owner.events, owner.actorId(), payload);
                return false;
            }
        }
        synchronized (owner) {
            if (!currentImport(payload, owner, operation)) return false;
            owner.imports.remove(payload.messageId());
            boolean accepted;
            if (!owner.reserved) {
                owner.inbox.put(payload.messageId(), message);
                accepted = true;
            } else {
                ToolResult<Boolean> result = sessions.steer(
                        owner.key(), payload.requestId(), payload.messageId(), message);
                accepted = result instanceof ToolResult.Success<Boolean> success && success.value();
            }
            if (!accepted) rejectSteer(owner.events, owner.actorId(), payload);
            return accepted;
        }
    }

    private boolean currentImport(ServerAgentSteerPayload payload, Owner owner, Object operation) {
        return ownsRequest(owner.actorId(), payload.requestId())
                && owner.imports.get(payload.messageId()) == operation;
    }

    private void rejectSteer(UUID sender, ServerAgentSteerPayload payload) {
        rejectSteer(events.bind(sender), sender, payload);
    }
    private void rejectSteer(ServerGuideEvents bound, UUID sender, ServerAgentSteerPayload payload) {
        if (payload.operation() == ServerAgentSteerPayload.Operation.PUT) {
            bound.send(sender, eventCodec.encode(
                    payload.requestId(), new AgentEvent.SteerRejected(payload.messageId())));
        }
    }

    /** Admission check before a loader retains partial steer bytes. It grants no new scope. */
    public boolean ownsRequest(UUID sender, UUID requestId) {
        Owner owner = active.get(requestId);
        if (owner == null || !owner.actorId().equals(sender)) return false;
        synchronized (owner) {
            return owns(requestId, owner) && !owner.released && !owner.cleanupStarted && !owner.terminal
                    && currentScope(owner) && !owner.cancellation().isCancelled();
        }
    }

    /** Result bytes are imported on the tracked image worker before a pending Tool completes. */
    public CompletableFuture<Void> prepareClientToolImages(
            UUID actor, UUID requestId, String sessionId,
            List<dev.openallay.model.image.ImageReference> references,
            List<dev.openallay.bridge.protocol.ServerAgentImageAttachment> attachments,
            java.util.function.BooleanSupplier invocationCurrent) {
        Owner owner = active.get(requestId);
        if (owner == null || !owner.actorId().equals(actor) || !owner.sessionId().equals(sessionId)
                || !ownsRequest(actor, requestId) || !invocationCurrent.getAsBoolean()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client Tool request scope closed"));
        }
        List<dev.openallay.model.image.ImageReference> required;
        try {
            required = dev.openallay.model.image.ModelImages.unique(List.copyOf(references));
            List<dev.openallay.model.image.ImageReference> supplied = attachments.stream()
                    .map(dev.openallay.bridge.protocol.ServerAgentImageAttachment::reference).toList();
            if (dev.openallay.model.image.ModelImages.unique(supplied).size() != supplied.size()
                    || !new java.util.HashSet<>(required).equals(new java.util.HashSet<>(supplied))) {
                throw new IllegalArgumentException("Client Tool image attachments do not match typed references");
            }
            if (required.isEmpty()) return CompletableFuture.completedFuture(null);
            if (images == null || imageCapability != dev.openallay.model.image.ImageInputCapability.SUPPORTED) {
                throw new IllegalArgumentException("Server model image input is unavailable");
            }
        } catch (RuntimeException invalid) {
            return CompletableFuture.failedFuture(invalid);
        }
        List<dev.openallay.bridge.protocol.ServerAgentImageAttachment> captured = List.copyOf(attachments);
        return imageOperation(requestId, owner, () -> {
            try {
                requireClientToolImport(requestId, owner, invocationCurrent);
                for (dev.openallay.bridge.protocol.ServerAgentImageAttachment attachment : captured) {
                    requireClientToolImport(requestId, owner, invocationCurrent);
                    dev.openallay.model.image.ImageReference imported = images.importImage(actor,
                            requestImageOwner(owner.imageScope, requestId), attachment.bytes());
                    if (!imported.equals(attachment.reference())) {
                        throw new java.io.IOException("Client Tool image metadata differs from actual image");
                    }
                }
                java.util.LinkedHashSet<dev.openallay.model.image.ImageReference> retained;
                synchronized (owner) {
                    requireClientToolImport(requestId, owner, invocationCurrent);
                    retained = new java.util.LinkedHashSet<>(owner.allowedImages);
                    retained.addAll(required);
                }
                images.retain(actor, requestImageOwner(owner.imageScope, requestId), List.copyOf(retained));
                synchronized (owner) {
                    requireClientToolImport(requestId, owner, invocationCurrent);
                    owner.allowedImages.addAll(required);
                }
            } catch (java.io.IOException invalid) {
                throw new java.io.UncheckedIOException(invalid);
            }
        }).thenRun(() -> requireClientToolImport(requestId, owner, invocationCurrent));
    }

    private void requireClientToolImport(UUID requestId, Owner owner,
            java.util.function.BooleanSupplier invocationCurrent) {
        if (!ownsRequest(owner.actorId(), requestId) || active.get(requestId) != owner
                || !invocationCurrent.getAsBoolean()) {
            throw new IllegalStateException("Client Tool image request scope closed");
        }
    }

    public boolean cancel(UUID sender, UUID requestId) {
        Owner owner = active.get(requestId);
        if (owner == null || !owner.actorId().equals(sender)) return false;
        synchronized (owner) {
            if (!owns(requestId, owner) || owner.terminal) return false;
            owner.inbox.clear();
            owner.imports.clear();
            if (!owner.engineStarted) {
                List<ModelMessage> original = List.of(owner.userInput(),
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
        return disconnectWork(sender).count();
    }

    /** Shutdown can wait for image imports, event retention and cleanup off the owner thread. */
    public CompletableFuture<Integer> disconnectAsync(UUID sender) {
        Disconnect work = disconnectWork(sender);
        return work.cleanup().thenApply(ignored -> work.count());
    }

    private Disconnect disconnectWork(UUID sender) {
        UUID scope;
        Map<UUID, Owner> retained;
        synchronized (requestAdmissionLock) {
            scope = imageScopes.remove(sender);
            retained = new java.util.HashMap<>(pendingRelease);
            retained.putAll(active);
        }
        List<AgentSessionKey> detached = sessions.sessions(sender);
        java.util.List<CompletableFuture<Void>> cleanup = new java.util.ArrayList<>();
        int count = 0;
        Set<Owner> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (java.util.Map.Entry<java.util.UUID, dev.openallay.server.ServerAgentService.Owner> entry : retained.entrySet()) {
            Owner owner = entry.getValue();
            if (!seen.add(owner) || !owner.actorId().equals(sender)
                    || (scope != null && !owner.imageScope.equals(scope))) continue;
            synchronized (owner) {
                if (active.get(entry.getKey()) != owner && pendingRelease.get(entry.getKey()) != owner) continue;
                count++;
                owner.disconnected = true;
                owner.inbox.clear();
                owner.imports.clear();
                owner.cancellation().cancel();
                if (!owner.engineStarted) owner.engineFinished = true;
                cleanup.add(owner.releaseCompletion);
                releaseIfFinished(entry.getKey(), owner);
            }
        }
        sessions.clearActor(sender);
        CompletableFuture<Void> finished = CompletableFuture.allOf(cleanup.toArray(CompletableFuture[]::new));
        if (images != null && scope != null) {
            finished = finished.thenRunAsync(() -> {
                for (AgentSessionKey key : detached) {
                    try { images.release(sender, sessionImageOwner(scope, key.sessionId())); }
                    catch (java.io.IOException ignored) { /* Keep bytes on cleanup failure. */ }
                }
                try { images.collect(sender); }
                catch (java.io.IOException ignored) { /* Keep bytes on cleanup failure. */ }
            }, IMAGE_WORKER);
        }
        return new Disconnect(count, finished);
    }

    @dev.openallay.value.ValueType(Disconnect.ValueSchemaProvider.class)
private static final class Disconnect {
    private final int count;
    private final CompletableFuture<Void> cleanup;
    private Disconnect(int count, CompletableFuture<Void> cleanup) {
        this.count = count;
        this.cleanup = cleanup;
    }
    public int count() { return count; }
    public CompletableFuture<Void> cleanup() { return cleanup; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Disconnect)) return false;
        Disconnect that = (Disconnect) other;
        return count == that.count && java.util.Objects.equals(cleanup, that.cleanup);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(count);
        hash = 31 * hash + java.util.Objects.hashCode(cleanup);
        return hash;
    }
    @Override public String toString() { return "Disconnect[count=" + count + ", cleanup=" + cleanup + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Disconnect> schema() {
            return new dev.openallay.value.ValueSchema<>(Disconnect.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Disconnect>>asList(new dev.openallay.value.ValueSchema.Component<>(Disconnect.class, "count", Disconnect::count), new dev.openallay.value.ValueSchema.Component<>(Disconnect.class, "cleanup", Disconnect::cleanup)), arguments -> new Disconnect((Integer) arguments[0], (CompletableFuture) arguments[1]));
        }
    }
}

    /** Correlation checks never grant another actor authority over this request. */
    public boolean hasRequest(UUID actor, UUID requestId) {
        Owner owner = active.get(requestId);
        if (owner == null) owner = pendingRelease.get(requestId);
        if (owner == null) return false;
        synchronized (owner) {
            return !owner.released && owner.actorId().equals(actor);
        }
    }

    public int activeRequests() {
        return (int) active.values().stream().filter(owner -> !owner.disconnected).count();
    }

    private void publish(UUID requestId, Owner owner, AgentEvent event) {
        synchronized (owner) {
            if (!owner.reserved && event instanceof AgentEvent.StateChanged state
                    && state.state() == dev.openallay.agent.AgentState.PREPARING) {
                owner.reserved = true;
                if (!owns(requestId, owner) || owner.cancellation().isCancelled() || owner.disconnected) {
                    owner.inbox.clear();
                    owner.imports.clear();
                    sessions.cancel(owner.key(), requestId);
                }
                // Reserve already exists. Flush synchronously, before any model dispatch or
                // off-thread S2b event/image retention work can consume this first boundary.
                for (java.util.Map.Entry<java.util.UUID, dev.openallay.model.ModelMessage> pending : owner.inbox.entrySet()) {
                    ToolResult<Boolean> result = sessions.steer(
                            owner.key(), requestId, pending.getKey(), pending.getValue());
                    if (!(result instanceof ToolResult.Success<Boolean> success && success.value())) {
                        owner.events.send(owner.actorId(), eventCodec.encode(requestId,
                                new AgentEvent.SteerRejected(pending.getKey())));
                    }
                }
                owner.inbox.clear();
            }
        }
        if (images == null) {
            publishPrepared(requestId, owner, event);
        } else {
            imageOperation(requestId, owner, () -> publishPrepared(requestId, owner, event))
                    .exceptionally(failure -> {
                        publishFailure(requestId, owner, failure);
                        return null;
                    });
        }
    }

    private void publishPrepared(UUID requestId, Owner owner, AgentEvent event) {
        synchronized (owner) {
            if (!owns(requestId, owner) || owner.released) return;
            boolean numeric = event instanceof AgentEvent.ModelUsageStarted
                    || event instanceof AgentEvent.ModelUsageObserved;
            if (owner.terminal && !numeric) return;
            if (event instanceof AgentEvent.ModelUsageStarted started) owner.pendingCalls.add(started.callId());
            if (event instanceof AgentEvent.FinalText || event instanceof AgentEvent.Failed) {
                owner.terminal = true;
                owner.inbox.clear();
                owner.imports.clear();
            }
            try {
                if (images != null && currentScope(owner)
                        && (event instanceof AgentEvent.ContextUpdated || event instanceof AgentEvent.ContextFinalized)) {
                    try {
                        images.retain(owner.actorId(), sessionImageOwner(owner.imageScope, owner.sessionId()),
                                imageReferences(sessions.history(new AgentSessionKey(owner.actorId(), owner.sessionId()))));
                    } catch (java.io.IOException invalid) {
                        throw new IllegalArgumentException("Cannot retain server context images", invalid);
                    }
                }
                if (!owner.disconnected) owner.events.send(owner.actorId(), eventCodec.encode(requestId, event));
            } finally {
                if (event instanceof AgentEvent.ModelUsageObserved observed) owner.pendingCalls.remove(observed.callId());
                releaseIfFinished(requestId, owner);
            }
        }
    }

    private void releaseIfFinished(UUID requestId, Owner owner) {
        if (owner.released || owner.cleanupStarted || !owner.engineFinished
                || !owner.pendingCalls.isEmpty() || owner.imageOperations != 0) return;
        owner.cleanupStarted = true;
        owner.inbox.clear();
        owner.imports.clear();
        synchronized (requestAdmissionLock) { pendingRelease.put(requestId, owner); }
        Runnable cleanup = () -> {
            RuntimeException runtimeFailure = null;
            Error fatalFailure = null;
            try { owner.runtime().close().run(); }
            catch (RuntimeException failure) { runtimeFailure = failure; }
            catch (Error failure) { fatalFailure = failure; }
            try {
                if (images != null) {
                    images.release(owner.actorId(), requestImageOwner(owner.imageScope, requestId));
                    images.collect(owner.actorId());
                }
            } catch (java.io.IOException failure) {
                // Preserve bytes on IO cleanup failure; never issue a successful custody receipt.
                dev.openallay.OpenAllayConstants.LOGGER.warn("Server request image cleanup failed", failure);
            } catch (RuntimeException | Error failure) {
                if (fatalFailure != null) { if (fatalFailure != failure) fatalFailure.addSuppressed(failure); }
                else if (failure instanceof Error fatal) {
                    if (runtimeFailure != null && (Throwable) fatal != runtimeFailure) fatal.addSuppressed(runtimeFailure);
                    fatalFailure = fatal;
                } else if (runtimeFailure != null) { if (runtimeFailure != failure) runtimeFailure.addSuppressed(failure); }
                else runtimeFailure = (RuntimeException) failure;
            }
            synchronized (requestAdmissionLock) { active.remove(requestId, owner); }
            Runnable retired = () -> retireRelease(requestId, owner);
            try {
                if (owner.disconnected) retired.run();
                else owner.events.send(owner.actorId(), eventCodec.encode(requestId,
                        new AgentEvent.RequestReleased()), retired);
            } catch (RuntimeException | Error failure) {
                Throwable eventFailure = failure;
                try { retired.run(); } // Setup/encoding failure before dispatch must release custody.
                catch (RuntimeException | Error retirementFailure) {
                    if (retirementFailure instanceof Error && !(eventFailure instanceof Error)) {
                        if (retirementFailure != eventFailure) retirementFailure.addSuppressed(eventFailure); eventFailure = retirementFailure;
                    } else if (eventFailure != retirementFailure) eventFailure.addSuppressed(retirementFailure);
                }
                if (fatalFailure != null) { if (fatalFailure != eventFailure) fatalFailure.addSuppressed(eventFailure); }
                else if (eventFailure instanceof Error fatal) {
                    if (runtimeFailure != null && (Throwable) fatal != runtimeFailure) fatal.addSuppressed(runtimeFailure);
                    fatalFailure = fatal;
                } else if (runtimeFailure != null) { if (runtimeFailure != failure) runtimeFailure.addSuppressed(failure); }
                else runtimeFailure = (RuntimeException) failure;
            }
            if (fatalFailure != null) throw fatalFailure;
            if (runtimeFailure != null) throw runtimeFailure;
        };
        if (images == null) cleanup.run();
        else {
            try { IMAGE_WORKER.execute(cleanup); }
            catch (RuntimeException | Error failure) {
                // Rejected cleanup has not run. Execute it once now, without waiting for an owner.
                try { cleanup.run(); }
                catch (RuntimeException | Error secondary) {
                    if (secondary instanceof Error && !(failure instanceof Error)) { if (secondary != failure) secondary.addSuppressed(failure); throw secondary; }
                    if (failure != secondary) failure.addSuppressed(secondary);
                }
                throw failure;
            }
        }
    }

    private void retireRelease(UUID requestId, Owner owner) {
        synchronized (owner) {
            if (owner.released) return;
            synchronized (requestAdmissionLock) {
                active.remove(requestId, owner);
                pendingRelease.remove(requestId, owner);
                sessionAdmissions.remove(new AgentSessionKey(owner.actorId(), owner.sessionId()), requestId);
            }
            owner.released = true;
        }
        // Mapping state is terminal before dependents or successor admission can run.
        owner.releaseCompletion.complete(null);
    }

    /** Every byte read/import/retention and its event handoff finish before the release fence. */
    private CompletableFuture<Void> imageOperation(UUID requestId, Owner owner, Runnable operation) {
        synchronized (owner) {
            if (owner.released || owner.cleanupStarted) return CompletableFuture.completedFuture(null);
            owner.imageOperations++;
        }
        CompletableFuture<Void> completed = new CompletableFuture<>();
        java.util.concurrent.atomic.AtomicBoolean claimed = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable work = () -> {
            if (!claimed.compareAndSet(false, true)) return;
            RuntimeException runtimeFailure = null;
            Error fatalFailure = null;
            try { operation.run(); completed.complete(null); }
            catch (RuntimeException failure) { completed.completeExceptionally(failure); runtimeFailure = failure; }
            catch (Error failure) { completed.completeExceptionally(failure); fatalFailure = failure; }
            try {
                synchronized (owner) { owner.imageOperations--; releaseIfFinished(requestId, owner); }
            } catch (RuntimeException | Error cleanupFailure) {
                if (fatalFailure != null) { if (fatalFailure != cleanupFailure) fatalFailure.addSuppressed(cleanupFailure); }
                else if (cleanupFailure instanceof Error fatal) {
                    if (runtimeFailure != null && (Throwable) fatal != runtimeFailure) fatal.addSuppressed(runtimeFailure);
                    fatalFailure = fatal;
                } else if (runtimeFailure != null) { if (runtimeFailure != cleanupFailure) runtimeFailure.addSuppressed(cleanupFailure); }
                else runtimeFailure = (RuntimeException) cleanupFailure;
            }
            if (fatalFailure != null) throw fatalFailure;
            if (runtimeFailure != null && !completed.isCompletedExceptionally()) throw runtimeFailure;
        };
        try { IMAGE_WORKER.execute(work); }
        catch (RuntimeException | Error failure) {
            if (claimed.compareAndSet(false, true)) {
                completed.completeExceptionally(failure);
                try { synchronized (owner) { owner.imageOperations--; releaseIfFinished(requestId, owner); } }
                catch (RuntimeException | Error secondary) {
                    if (secondary instanceof Error && !(failure instanceof Error)) { if (secondary != failure) secondary.addSuppressed(failure); throw secondary; }
                    if (failure != secondary) failure.addSuppressed(secondary);
                }
            }
            if (failure instanceof Error fatal) throw fatal;
        }
        return completed;
    }

    private void prepareImages(ServerAgentRequestPayload payload, Owner owner) {
        try {
            for (dev.openallay.bridge.protocol.ServerAgentImageAttachment attachment : payload.imageAttachments()) {
                if (!currentImagePreparation(payload.requestId(), owner)) return;
                dev.openallay.model.image.ImageReference imported = images.importImage(owner.actorId(),
                        requestImageOwner(owner.imageScope, payload.requestId()), attachment.bytes());
                if (!imported.equals(attachment.reference())) {
                    throw new java.io.IOException("Uploaded metadata does not match the actual image");
                }
            }
            for (dev.openallay.model.image.ImageReference reference : owner.requiredImages) {
                if (!currentImagePreparation(payload.requestId(), owner)) return;
                images.read(owner.actorId(), reference);
            }
            if (currentImagePreparation(payload.requestId(), owner)) {
                images.retain(owner.actorId(), requestImageOwner(owner.imageScope, payload.requestId()), owner.requiredImages);
            }
        } catch (java.io.IOException | IllegalArgumentException invalid) {
            throw new java.util.concurrent.CompletionException(new ImageAttachmentFailure(invalid));
        }
    }

    private byte[] readImage(UUID requestId, Owner owner, dev.openallay.model.image.ImageReference reference)
            throws java.io.IOException {
        if (images == null || !currentImagePreparation(requestId, owner)
                || !allowedImage(owner, reference)) {
            throw new java.io.IOException("Image is outside this active request's actor and scope");
        }
        byte[] bytes = images.read(owner.actorId(), reference);
        if (!currentImagePreparation(requestId, owner)) throw new java.io.IOException("Image request scope closed");
        return bytes;
    }

    private boolean allowedImage(Owner owner, dev.openallay.model.image.ImageReference reference) {
        synchronized (owner) { return owner.allowedImages.contains(reference); }
    }

    private boolean currentImagePreparation(UUID requestId, Owner owner) {
        return owns(requestId, owner) && currentScope(owner) && !owner.cancellation().isCancelled();
    }

    private boolean currentScope(Owner owner) {
        return !owner.disconnected && owner.imageScope.equals(imageScopes.get(owner.actorId()));
    }

    private static String requestImageOwner(UUID scope, UUID requestId) {
        return "server-request:" + scope + ":" + requestId;
    }

    private static String sessionImageOwner(UUID scope, String sessionId) {
        return "server-session:" + scope + ":" + sessionId;
    }

    private static List<dev.openallay.model.image.ImageReference> imageReferences(List<ModelMessage> messages) {
        return dev.openallay.model.image.ModelImages.uniqueReferences(messages);
    }

    private static final class ImageAttachmentFailure extends RuntimeException {
        private ImageAttachmentFailure(Throwable cause) {
            super("A required image attachment is invalid or unavailable", cause);
        }
    }

    private void publishFailure(UUID requestId, Owner owner, Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        publish(requestId, owner, new AgentEvent.Failed(
                cause instanceof ImageAttachmentFailure ? "image_attachment_failed" : "server_agent_failure", message));
    }

    private boolean owns(UUID requestId, Owner owner) {
        return owner.equals(active.get(requestId));
    }

    @dev.openallay.value.ValueType(Accepted.ValueSchemaProvider.class)
public static final class Accepted {
    private final UUID requestId;
    private final String sessionId;
    public Accepted(UUID requestId, String sessionId) {
        this.requestId = requestId;
        this.sessionId = sessionId;
    }
    public UUID requestId() { return requestId; }
    public String sessionId() { return sessionId; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Accepted)) return false;
        Accepted that = (Accepted) other;
        return java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(sessionId, that.sessionId);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        return hash;
    }
    @Override public String toString() { return "Accepted[requestId=" + requestId + ", sessionId=" + sessionId + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Accepted> schema() {
            return new dev.openallay.value.ValueSchema<>(Accepted.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Accepted>>asList(new dev.openallay.value.ValueSchema.Component<>(Accepted.class, "requestId", Accepted::requestId), new dev.openallay.value.ValueSchema.Component<>(Accepted.class, "sessionId", Accepted::sessionId)), arguments -> new Accepted((UUID) arguments[0], (String) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(RequestRuntime.ValueSchemaProvider.class)
public static final class RequestRuntime {
    private final GameGuideAgent agent;
    private final AgentToolExecutor tools;
    private final String systemPrompt;
    private final Function<ServerAgentSteerPayload, ModelMessage> prepareSteer;
    private final Runnable close;
    public RequestRuntime(GameGuideAgent agent, AgentToolExecutor tools, String systemPrompt, Function<ServerAgentSteerPayload, ModelMessage> prepareSteer, Runnable close) {

            java.util.Objects.requireNonNull(agent, "agent");
            java.util.Objects.requireNonNull(tools, "tools");
            java.util.Objects.requireNonNull(prepareSteer, "prepareSteer");
            java.util.Objects.requireNonNull(close, "close");

        this.agent = agent;
        this.tools = tools;
        this.systemPrompt = systemPrompt;
        this.prepareSteer = prepareSteer;
        this.close = close;
    }
    public GameGuideAgent agent() { return agent; }
    public AgentToolExecutor tools() { return tools; }
    public String systemPrompt() { return systemPrompt; }
    public Function<ServerAgentSteerPayload, ModelMessage> prepareSteer() { return prepareSteer; }
    public Runnable close() { return close; }
public RequestRuntime(
                GameGuideAgent agent, AgentToolExecutor tools, String systemPrompt, Runnable close) {
            this(agent, tools, systemPrompt, payload -> {
                ModelMessage message = payload.message().toModelMessage();
                if (!payload.imageAttachments().isEmpty()
                        || message.content().stream().anyMatch(content -> !(content instanceof ModelContent.Text))) {
                    throw new IllegalArgumentException("This request runtime cannot prepare steer images");
                }
                return message;
            }, close);
        }
public RequestRuntime(
                GameGuideAgent agent, AgentToolExecutor tools, Runnable close) {
            this(agent, tools, null, close);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RequestRuntime)) return false;
        RequestRuntime that = (RequestRuntime) other;
        return java.util.Objects.equals(agent, that.agent) && java.util.Objects.equals(tools, that.tools) && java.util.Objects.equals(systemPrompt, that.systemPrompt) && java.util.Objects.equals(prepareSteer, that.prepareSteer) && java.util.Objects.equals(close, that.close);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(agent);
        hash = 31 * hash + java.util.Objects.hashCode(tools);
        hash = 31 * hash + java.util.Objects.hashCode(systemPrompt);
        hash = 31 * hash + java.util.Objects.hashCode(prepareSteer);
        hash = 31 * hash + java.util.Objects.hashCode(close);
        return hash;
    }
    @Override public String toString() { return "RequestRuntime[agent=" + agent + ", tools=" + tools + ", systemPrompt=" + systemPrompt + ", prepareSteer=" + prepareSteer + ", close=" + close + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<RequestRuntime> schema() {
            return new dev.openallay.value.ValueSchema<>(RequestRuntime.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<RequestRuntime>>asList(new dev.openallay.value.ValueSchema.Component<>(RequestRuntime.class, "agent", RequestRuntime::agent), new dev.openallay.value.ValueSchema.Component<>(RequestRuntime.class, "tools", RequestRuntime::tools), new dev.openallay.value.ValueSchema.Component<>(RequestRuntime.class, "systemPrompt", RequestRuntime::systemPrompt), new dev.openallay.value.ValueSchema.Component<>(RequestRuntime.class, "prepareSteer", RequestRuntime::prepareSteer), new dev.openallay.value.ValueSchema.Component<>(RequestRuntime.class, "close", RequestRuntime::close)), arguments -> new RequestRuntime((GameGuideAgent) arguments[0], (AgentToolExecutor) arguments[1], (String) arguments[2], (Function) arguments[3], (Runnable) arguments[4]));
        }
    }
}

    private static final class Owner {
        private final UUID actorId;
        private final ServerGuideEvents events;
        private final ContextProvider contexts;
        private final String sessionId;
        private final ModelMessage userInput;
        private final List<ModelMessage> history;
        private final dev.openallay.model.CancellationSignal cancellation;
        private final RequestRuntime runtime;
        private final Set<UUID> pendingCalls = new java.util.HashSet<>();
        private final Map<UUID, ModelMessage> inbox = new java.util.LinkedHashMap<>();
        private final Map<UUID, Object> imports = new java.util.HashMap<>();
        private final Set<dev.openallay.model.image.ImageReference> allowedImages = new java.util.LinkedHashSet<>();
        private boolean reserved;
        private boolean terminal;
        private boolean engineStarted;
        private boolean engineFinished;
        private boolean released;
        private boolean disconnected;
        private boolean cleanupStarted;
        private int imageOperations;
        private final UUID imageScope;
        private final List<dev.openallay.model.image.ImageReference> requiredImages;
        private final CompletableFuture<Void> releaseCompletion = new CompletableFuture<>();

        private Owner(UUID actorId, String sessionId, ModelMessage userInput, List<ModelMessage> history,
                dev.openallay.model.CancellationSignal cancellation, RequestRuntime runtime,
                UUID imageScope, List<dev.openallay.model.image.ImageReference> requiredImages,
                ServerGuideEvents events, ContextProvider contexts) {
            this.actorId = actorId;
            this.events = events;
            this.contexts = contexts;
            this.sessionId = sessionId;
            this.userInput = userInput;
            this.imageScope = imageScope;
            this.requiredImages = List.copyOf(requiredImages);
            this.allowedImages.addAll(requiredImages);
            this.history = dev.openallay.agent.context.ModelContextCodec.safe(history);
            this.cancellation = cancellation;
            this.runtime = runtime;
        }
        private UUID actorId() { return actorId; }
        private String sessionId() { return sessionId; }
        private ModelMessage userInput() { return userInput; }
        private List<ModelMessage> history() { return history; }
        private dev.openallay.model.CancellationSignal cancellation() { return cancellation; }
        private RequestRuntime runtime() { return runtime; }
        private AgentSessionKey key() { return new AgentSessionKey(actorId, sessionId); }
    }
}
