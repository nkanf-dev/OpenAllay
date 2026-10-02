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
        List<ModelMessage> restored = payload.history().stream()
                .map(ServerAgentHistoryMessage::toModelMessage).toList();
        ModelMessage userInput = payload.userInput().toModelMessage();
        AgentSessionKey key = new AgentSessionKey(sender, payload.sessionId());
        Owner owner;
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
            UUID scope = imageScopes.computeIfAbsent(sender, ignored -> UUID.randomUUID());
            owner = new Owner(sender, payload.sessionId(), userInput, restored,
                    new dev.openallay.model.CancellationSignal(), runtime, scope, required);
            active.put(payload.requestId(), owner);
            sessionAdmissions.put(key, payload.requestId());
        }
        RequestRuntime runtime = owner.runtime();
        CompletableFuture<?> work;
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
                    if (!payload.imageAttachments().isEmpty()) {
                        // The S2b operation count includes actual imports, even after REMOVE or
                        // Stop has revoked their tokens. Cleanup cannot race a late store write.
                        imageOperation(payload.requestId(), owner,
                                () -> prepareSteer(payload, owner, operation));
                        return true;
                    }
                    return prepareSteer(payload, owner, operation);
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
                rejectSteer(owner.actorId(), payload);
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
            if (!accepted) rejectSteer(owner.actorId(), payload);
            return accepted;
        }
    }

    private boolean currentImport(ServerAgentSteerPayload payload, Owner owner, Object operation) {
        return ownsRequest(owner.actorId(), payload.requestId())
                && owner.imports.get(payload.messageId()) == operation;
    }

    private void rejectSteer(UUID sender, ServerAgentSteerPayload payload) {
        if (payload.operation() == ServerAgentSteerPayload.Operation.PUT) {
            events.send(sender, eventCodec.encode(
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
        synchronized (requestAdmissionLock) { scope = imageScopes.remove(sender); }
        List<AgentSessionKey> detached = sessions.sessions(sender);
        java.util.List<CompletableFuture<Void>> cleanup = new java.util.ArrayList<>();
        int count = 0;
        for (var entry : active.entrySet()) {
            Owner owner = entry.getValue();
            if (!owner.actorId().equals(sender) || !owner.imageScope.equals(scope)) continue;
            synchronized (owner) {
                if (!owns(entry.getKey(), owner)) continue;
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

    private record Disconnect(int count, CompletableFuture<Void> cleanup) {}

    /** Correlation checks never grant another actor authority over this request. */
    public boolean hasRequest(UUID actor, UUID requestId) {
        Owner owner = active.get(requestId);
        if (owner == null) owner = pendingRelease.get(requestId);
        return owner != null && owner.actorId().equals(actor);
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
                for (var pending : owner.inbox.entrySet()) {
                    ToolResult<Boolean> result = sessions.steer(
                            owner.key(), requestId, pending.getKey(), pending.getValue());
                    if (!(result instanceof ToolResult.Success<Boolean> success && success.value())) {
                        events.send(owner.actorId(), eventCodec.encode(requestId,
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
                if (!owner.disconnected) events.send(owner.actorId(), eventCodec.encode(requestId, event));
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
            try {
                owner.runtime().close().run();
            } finally {
                try {
                    if (images != null) {
                        images.release(owner.actorId(), requestImageOwner(owner.imageScope, requestId));
                        images.collect(owner.actorId());
                    }
                } catch (java.io.IOException ignored) {
                    // Retain bytes rather than damage live or durable context references.
                } finally {
                    synchronized (owner) {
                        owner.released = true;
                        synchronized (requestAdmissionLock) {
                            active.remove(requestId, owner);
                            sessionAdmissions.remove(new AgentSessionKey(owner.actorId(), owner.sessionId()), requestId);
                        }
                        try {
                            if (!owner.disconnected) events.send(owner.actorId(), eventCodec.encode(requestId,
                                    new AgentEvent.RequestReleased()));
                        } finally {
                            synchronized (requestAdmissionLock) { pendingRelease.remove(requestId, owner); }
                            owner.releaseCompletion.complete(null);
                        }
                    }
                }
            }
        };
        if (images == null) cleanup.run();
        else IMAGE_WORKER.execute(cleanup);
    }

    /** Every byte read/import/retention and its event handoff finish before the release fence. */
    private CompletableFuture<Void> imageOperation(UUID requestId, Owner owner, Runnable operation) {
        synchronized (owner) {
            if (owner.released || owner.cleanupStarted) return CompletableFuture.completedFuture(null);
            owner.imageOperations++;
        }
        CompletableFuture<Void> completed = new CompletableFuture<>();
        try {
            IMAGE_WORKER.execute(() -> {
                try { operation.run(); completed.complete(null); }
                catch (RuntimeException failure) { completed.completeExceptionally(failure); }
                finally {
                    synchronized (owner) {
                        owner.imageOperations--;
                        releaseIfFinished(requestId, owner);
                    }
                }
            });
        } catch (RuntimeException failure) {
            synchronized (owner) {
                owner.imageOperations--;
                completed.completeExceptionally(failure);
                releaseIfFinished(requestId, owner);
            }
        }
        return completed;
    }

    private void prepareImages(ServerAgentRequestPayload payload, Owner owner) {
        try {
            for (var attachment : payload.imageAttachments()) {
                if (!currentImagePreparation(payload.requestId(), owner)) return;
                var imported = images.importImage(owner.actorId(),
                        requestImageOwner(owner.imageScope, payload.requestId()), attachment.bytes());
                if (!imported.equals(attachment.reference())) {
                    throw new java.io.IOException("Uploaded metadata does not match the actual image");
                }
            }
            for (var reference : owner.requiredImages) {
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
        java.util.Map<String, dev.openallay.model.image.ImageReference> unique = new java.util.LinkedHashMap<>();
        messages.stream().flatMap(message -> message.content().stream())
                .filter(ModelContent.Image.class::isInstance).map(ModelContent.Image.class::cast)
                .forEach(image -> {
                    var previous = unique.putIfAbsent(image.reference().sha256(), image.reference());
                    if (previous != null && !previous.equals(image.reference())) {
                        throw new IllegalArgumentException("Conflicting image reference metadata");
                    }
                });
        return List.copyOf(unique.values());
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

    public record Accepted(UUID requestId, String sessionId) {}

    public record RequestRuntime(
            GameGuideAgent agent,
            AgentToolExecutor tools,
            String systemPrompt,
            Function<ServerAgentSteerPayload, ModelMessage> prepareSteer,
            Runnable close) {
        public RequestRuntime {
            java.util.Objects.requireNonNull(agent, "agent");
            java.util.Objects.requireNonNull(tools, "tools");
            java.util.Objects.requireNonNull(prepareSteer, "prepareSteer");
            java.util.Objects.requireNonNull(close, "close");
        }

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
    }

    private static final class Owner {
        private final UUID actorId;
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
                UUID imageScope, List<dev.openallay.model.image.ImageReference> requiredImages) {
            this.actorId = actorId;
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
