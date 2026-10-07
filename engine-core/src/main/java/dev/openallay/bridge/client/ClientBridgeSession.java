package dev.openallay.bridge.client;

import com.google.gson.Gson;
import dev.openallay.agent.tool.ToolRuntimeCatalog;
import dev.openallay.bridge.protocol.BridgeJsonCodec;
import dev.openallay.bridge.protocol.CapabilityPayload;
import dev.openallay.bridge.protocol.ClientToolCallPayload;
import dev.openallay.bridge.protocol.ClientToolCancelPayload;
import dev.openallay.bridge.protocol.RemoteToolResultChunkPayload;
import dev.openallay.bridge.protocol.ResultChunker;
import dev.openallay.bridge.protocol.ServerAgentCancelPayload;
import dev.openallay.bridge.protocol.ServerAgentEventChunkPayload;
import dev.openallay.bridge.protocol.ServerAgentEventPayload;
import dev.openallay.bridge.protocol.ServerAgentRequestChunker;
import dev.openallay.bridge.protocol.ServerAgentRequestPayload;
import dev.openallay.bridge.protocol.ServerAgentSteerPayload;
import dev.openallay.client.ClientEventDispatcher;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Native-neutral client bridge state and request custody. Loader subclasses only register
 * packets, dispatch native callbacks, and supply detached actor/connection facts and sends.
 * Protocol payloads, codec validation, Tool execution and request fences have one owner.
 */
public class ClientBridgeSession {
    /** Small native transport boundary; no native object is dispatched through this contract. */
    public interface NativeHost {
        Optional<Connection> captureConnection();

        /** Fabric checks its negotiated channel; NeoForge preserves its existing unguarded sends. */
        boolean canSend();

        void send(String kind, String json);
    }

    /** Immutable actor identity plus an exact native connection identity check, not an API handle. */
    public record Connection(UUID actorId, BooleanSupplier current) {
        public Connection {
            java.util.Objects.requireNonNull(actorId, "actorId");
            java.util.Objects.requireNonNull(current, "current");
        }
    }

    private final NativeHost host;
    private final ClientEventDispatcher dispatcher;
    private final Executor resultImageWorker;
    private final Executor clientToolWorker;

    private final BridgeJsonCodec codec = new BridgeJsonCodec();
    private final RemoteCapabilityStore capabilities = new RemoteCapabilityStore();
    private final Map<UUID, ServerRequest> serverRequests = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> agentEventIds = new ConcurrentHashMap<>();
    private final Object serverRequestLock = new Object();
    private final ServerAgentRequestChunker requestChunker = new ServerAgentRequestChunker();
    private final dev.openallay.bridge.protocol.ServerAgentSteerChunker steerChunker =
            new dev.openallay.bridge.protocol.ServerAgentSteerChunker();
    private final ResultChunker.Reassembler agentEventChunks = new ResultChunker.Reassembler();
    private final java.util.List<Runnable> disconnectListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<Runnable> capabilityListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile Supplier<ToolRuntimeCatalog> localToolCatalog;
    private volatile ClientToolExecutionEndpoint clientTools;
    private Object connectionScope = new Object();
    private final RemoteToolExecutor remoteTools = new RemoteToolExecutor(
            capabilities,
            new RemoteToolExecutor.Transport() {
                @Override
                public void call(dev.openallay.bridge.protocol.RemoteToolCallPayload payload) {
                    send("tool_call", payload);
                }

                @Override
                public void cancel(dev.openallay.bridge.protocol.RemoteCancelPayload payload) {
                    if (host.canSend()) {
                        send("tool_cancel", payload);
                    }
                }

                @Override
                public void close(
                        dev.openallay.bridge.protocol.RemoteToolRequestClosePayload payload) {
                    if (host.canSend()) {
                        send("tool_request_close", payload);
                    }
                }
            });

    public ClientBridgeSession(NativeHost host, ClientEventDispatcher dispatcher) {
        this(host, dispatcher,
                command -> dev.openallay.concurrent.NamedThreads.startDaemon("openallay-client-result-images", command),
                null);
    }

    /** Deterministic engine tests supply manual workers; native construction keeps the endpoint default. */
    ClientBridgeSession(NativeHost host, ClientEventDispatcher dispatcher,
            Executor resultImageWorker, Executor clientToolWorker) {
        this.host = java.util.Objects.requireNonNull(host, "host");
        this.dispatcher = java.util.Objects.requireNonNull(dispatcher, "dispatcher");
        this.resultImageWorker = java.util.Objects.requireNonNull(resultImageWorker, "resultImageWorker");
        this.clientToolWorker = clientToolWorker;
    }

    /** Capture the exact scope before a native loader queues an inbound callback. */
    protected final Runnable inboundCallback(
            String kind, String json, BooleanSupplier connectionCurrent) {
        java.util.Objects.requireNonNull(connectionCurrent, "connectionCurrent");
        Object scope;
        synchronized (serverRequestLock) { scope = connectionScope; }
        return () -> {
            synchronized (serverRequestLock) {
                if (scope != connectionScope || !connectionCurrent.getAsBoolean()) return;
            }
            receive(kind, json);
        };
    }

    /** Native disconnect registration calls this on its existing callback thread. */
    protected final void disconnected() {
        disconnectState();
        disconnectListeners.forEach(Runnable::run);
    }

    public final RemoteToolExecutor remoteTools() {
        return remoteTools;
    }

    public final CapabilityPayload capabilities() {
        return capabilities.snapshot();
    }

    /** Installs the local, dynamically filtered Tool source used by future server-model requests. */
    public final void configureClientTools(
            Supplier<ToolRuntimeCatalog> localToolCatalog,
            ClientToolExecutionEndpoint.ContextProvider contexts,
            Gson gson) {
        this.localToolCatalog = java.util.Objects.requireNonNull(
                localToolCatalog, "localToolCatalog");
        this.clientTools = clientToolWorker == null
                ? new ClientToolExecutionEndpoint(contexts, this::queueClientToolResult, gson,
                        dev.openallay.bridge.protocol.BridgeProtocol.TRANSPORT_CHUNK_BYTES)
                : new ClientToolExecutionEndpoint(contexts, this::queueClientToolResult, gson,
                        dev.openallay.bridge.protocol.BridgeProtocol.TRANSPORT_CHUNK_BYTES,
                        clientToolWorker);
    }

    /** Existing actor store only. Endpoint close revokes native work, not producer custody. */
    public final void configureResultImages(dev.openallay.model.image.ImageAttachmentStore store,
            dev.openallay.guide.GuideContextProvider contexts) {
        java.util.Objects.requireNonNull(store, "store");
        java.util.Objects.requireNonNull(contexts, "contexts");
        ClientToolExecutionEndpoint endpoint = java.util.Objects.requireNonNull(clientTools, "clientTools");
        endpoint.configureResultImages(new ClientToolExecutionEndpoint.ResultImages() {
            @Override
            public java.util.concurrent.CompletableFuture<java.util.List<
                    dev.openallay.bridge.protocol.ServerAgentImageAttachment>> prepare(
                    UUID requestId, UUID invocationId, String sessionId,
                    java.util.List<dev.openallay.model.image.ImageReference> references,
                    dev.openallay.model.CancellationSignal cancellation) {
                ServerRequest request;
                synchronized (serverRequestLock) {
                    request = serverRequests.get(requestId);
                    if (!current(requestId, request) || !request.sessionId.equals(sessionId)) {
                        return java.util.concurrent.CompletableFuture.failedFuture(
                                new IllegalStateException("Client image request is no longer active"));
                    }
                }
                java.util.List<dev.openallay.model.image.ImageReference> captured = dev.openallay.model.image.ModelImages.unique(references);
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    java.util.List<dev.openallay.bridge.protocol.ServerAgentImageAttachment> attachments =
                            new java.util.ArrayList<>();
                    for (dev.openallay.model.image.ImageReference reference : captured) {
                        cancellation.throwIfCancelled();
                        requireCurrent(requestId, request);
                        try {
                            byte[] bytes = store.read(request.actorId, reference);
                            cancellation.throwIfCancelled();
                            requireCurrent(requestId, request);
                            attachments.add(dev.openallay.bridge.protocol.ServerAgentImageAttachment.from(reference, bytes));
                        } catch (java.io.IOException failure) {
                            throw new java.io.UncheckedIOException(failure);
                        }
                    }
                    cancellation.throwIfCancelled();
                    requireCurrent(requestId, request);
                    return java.util.List.copyOf(attachments);
                }, resultImageWorker);
            }

            @Override
            public void close(UUID requestId) {
                contexts.closeRequest(requestId.toString());
            }
        });
    }

    private void queueClientToolResult(dev.openallay.bridge.protocol.ClientToolResultChunkPayload chunk) {
        ServerRequest request;
        synchronized (serverRequestLock) {
            request = serverRequests.get(chunk.requestId());
            if (!current(chunk.requestId(), request)) return;
        }
        dispatcher.execute(() -> {
            synchronized (serverRequestLock) {
                // Recheck the exact request and native connection after this queued callback runs.
                if (!current(chunk.requestId(), request)) return;
                if (host.canSend()) {
                    send("client_tool_result", chunk);
                }
            }
        });
    }

    /** Capture exact custody before posting a native context callback. */
    public final java.util.function.BooleanSupplier clientToolAdmission(String correlationId) {
        UUID requestId = UUID.fromString(correlationId);
        ServerRequest request;
        synchronized (serverRequestLock) { request = serverRequests.get(requestId); }
        return () -> {
            synchronized (serverRequestLock) { return current(requestId, request); }
        };
    }

    private void requireCurrent(UUID requestId, ServerRequest request) {
        synchronized (serverRequestLock) {
            if (!current(requestId, request)) throw new IllegalStateException(
                    "Client image request is no longer active");
        }
    }

    private boolean current(UUID requestId, ServerRequest request) {
        return request != null && serverRequests.get(requestId) == request
                && request.connectionScope == connectionScope && !request.cancelled && !request.terminal
                && request.connectionCurrent.getAsBoolean();
    }

    /** Admitted player reference for this exact server-model request, not the latest UI draft. */
    public final java.util.Optional<dev.openallay.world.ClientObservationAnchor> clientToolInputObservation(
            String correlationId) {
        UUID requestId = UUID.fromString(correlationId);
        synchronized (serverRequestLock) {
            ServerRequest request = serverRequests.get(requestId);
            if (!current(requestId, request)) throw new IllegalStateException(
                    "Client input reference request is no longer active");
            return request.inputObservation;
        }
    }

    public final void onDisconnect(Runnable listener) { disconnectListeners.add(listener); }
    public final void onCapabilitiesChanged(Runnable listener) { capabilityListeners.add(listener); }
    public final void disconnectState() {
        capabilities.clear();
        remoteTools.disconnect();
        synchronized (serverRequestLock) {
            connectionScope = new Object();
            serverRequests.clear();
            agentEventIds.clear();
            agentEventChunks.clear();
        }
        ClientToolExecutionEndpoint endpoint = clientTools;
        if (endpoint != null) endpoint.disconnect();
    }

    public final boolean askServer(
            ServerAgentRequestPayload request, Consumer<ServerAgentEventPayload> events) {
        if (!capabilities.snapshot().serverModel()
                || !host.canSend()) {
            return false;
        }
        ClientToolExecutionEndpoint endpoint = clientTools;
        ServerAgentRequestPayload outbound = request.withClientTools(
                java.util.List.of(), dev.openallay.skill.SkillCatalogManifest.EMPTY);
        if (endpoint != null) {
            Supplier<ToolRuntimeCatalog> catalogs = localToolCatalog;
            if (catalogs == null) return false;
            dev.openallay.tool.ToolResult<ClientToolExecutionEndpoint.OpenedRequest> opened =
                    endpoint.open(request.requestId(), request.sessionId(), catalogs.get());
            if (!(opened instanceof dev.openallay.tool.ToolResult.Success<
                    ClientToolExecutionEndpoint.OpenedRequest> success)) {
                return false;
            }
            outbound = request.withClientTools(
                    success.value().clientToolIds(), success.value().skillDocuments());
        }
        synchronized (serverRequestLock) {
            Optional<Connection> connection = host.captureConnection();
            if (connection.isEmpty()) {
                if (endpoint != null) endpoint.close(request.requestId());
                return false;
            }
            Connection captured = connection.orElseThrow();
            serverRequests.put(request.requestId(), new ServerRequest(
                    events, request.sessionId(), captured.actorId(), captured.current(), connectionScope,
                    request.userInput().toModelMessage().inputObservation()));
        }
        try {
            for (dev.openallay.bridge.protocol.ServerAgentRequestChunkPayload chunk : requestChunker.split(
                    outbound.requestId(), codec.encode(outbound),
                    dev.openallay.bridge.protocol.BridgeProtocol.TRANSPORT_CHUNK_BYTES)) {
                send("agent_request_chunk", chunk);
            }
            return true;
        } catch (RuntimeException failure) {
            synchronized (serverRequestLock) {
                serverRequests.remove(request.requestId());
                clearAgentEventChunksLocked(request.requestId());
            }
            if (endpoint != null) endpoint.close(request.requestId());
            return false;
        }
    }

    public final boolean steerServer(ServerAgentSteerPayload payload) {
        synchronized (serverRequestLock) {
            ServerRequest request = serverRequests.get(payload.requestId());
            if (request == null || request.cancelled || request.terminal) return false;
            try {
                if (payload.operation() == ServerAgentSteerPayload.Operation.REMOVE) {
                    send("agent_steer", payload);
                } else {
                    for (dev.openallay.bridge.protocol.ServerAgentSteerChunkPayload chunk : steerChunker.split(
                            payload.requestId(), payload.messageId(), codec.encode(payload),
                            dev.openallay.bridge.protocol.BridgeProtocol.TRANSPORT_CHUNK_BYTES)) {
                        send("agent_steer_chunk", chunk);
                    }
                }
                return true;
            } catch (RuntimeException failure) {
                return false;
            }
        }
    }

    public final boolean cancelServer(UUID requestId) {
        boolean cancelled;
        synchronized (serverRequestLock) {
            ServerRequest request = serverRequests.get(requestId);
            cancelled = request != null && !request.cancelled;
            if (cancelled) request.cancelled = true;
        }
        ClientToolExecutionEndpoint endpoint = clientTools;
        if (endpoint != null) endpoint.close(requestId);
        if (!cancelled || !host.canSend()) {
            return false;
        }
        send("agent_cancel", new ServerAgentCancelPayload(
                requestId));
        return true;
    }

    protected final void receive(String kind, String json) {
        switch (kind) {
            case "capabilities" -> {
                capabilities.replace(codec.decode(json, CapabilityPayload.class));
                capabilityListeners.forEach(Runnable::run);
            }
            case "tool_result" -> remoteTools.receive(
                    codec.decode(json, RemoteToolResultChunkPayload.class));
            case "client_tool_call" -> {
                ClientToolExecutionEndpoint endpoint = clientTools;
                if (endpoint != null) {
                    endpoint.handle(codec.decode(json, ClientToolCallPayload.class));
                }
            }
            case "client_tool_cancel" -> {
                ClientToolExecutionEndpoint endpoint = clientTools;
                if (endpoint != null) {
                    endpoint.cancel(codec.decode(json, ClientToolCancelPayload.class));
                }
            }
            case "agent_event" -> receiveAgentEvent(
                    codec.decode(json, ServerAgentEventPayload.class));
            case "agent_event_chunk" -> {
                ServerAgentEventChunkPayload chunk =
                        codec.decode(json, ServerAgentEventChunkPayload.class);
                receiveAgentEventChunk(chunk);
            }
            default -> dev.openallay.OpenAllayConstants.LOGGER.warn(
                    "Ignored unknown client bridge packet {}", kind);
        }
    }

    private void receiveAgentEventChunk(ServerAgentEventChunkPayload chunk) {
        ServerAgentEventPayload completed = null;
        synchronized (serverRequestLock) {
            if (!serverRequests.containsKey(chunk.requestId())) {
                return;
            }
            agentEventIds.computeIfAbsent(
                    chunk.requestId(), ignored -> ConcurrentHashMap.newKeySet())
                    .add(chunk.eventId());
            try {
                java.util.Optional<String> json = agentEventChunks.accept(chunk.asRemoteChunk());
                if (json.isEmpty()) {
                    return;
                }
                forgetAgentEventChunkLocked(chunk.requestId(), chunk.eventId());
                completed = codec.decode(json.orElseThrow(), ServerAgentEventPayload.class);
                if (!completed.requestId().equals(chunk.requestId())) {
                    throw new IllegalArgumentException(
                            "Server Agent event request correlation changed");
                }
            } catch (RuntimeException failure) {
                agentEventChunks.cancel(chunk.eventId());
                forgetAgentEventChunkLocked(chunk.requestId(), chunk.eventId());
                throw failure;
            }
        }
        receiveAgentEvent(completed);
    }

    private void receiveAgentEvent(ServerAgentEventPayload event) {
        ServerRequest request;
        boolean terminal = event.terminal();
        boolean released = "request_released".equals(event.eventType());
        synchronized (serverRequestLock) {
            request = serverRequests.get(event.requestId());
            if (request == null) return;
            if (terminal) request.terminal = true;
        }
        // Release can dispatch a successor synchronously. Revoke old local tools first.
        if (terminal || released) {
            ClientToolExecutionEndpoint endpoint = clientTools;
            if (endpoint != null) endpoint.close(event.requestId());
        }
        try {
            request.events.accept(event);
        } finally {
            synchronized (serverRequestLock) {
                if ("context_finalized".equals(event.eventType())) request.contextFinalized = true;
                if (terminal) request.terminal = true;
                // Terminal UI must not discard a later actual-call usage receipt.
                if (released && serverRequests.remove(event.requestId(), request)) {
                    clearAgentEventChunksLocked(event.requestId());
                }
            }
        }
    }

    /** One pending cancellation handshake, owned and cleared with its request callback. */
    private static final class ServerRequest {
        private final Consumer<ServerAgentEventPayload> events;
        private boolean cancelled;
        private boolean contextFinalized;
        private boolean terminal;

        private final String sessionId;
        private final UUID actorId;
        private final BooleanSupplier connectionCurrent;
        private final Object connectionScope;
        private final java.util.Optional<dev.openallay.world.ClientObservationAnchor> inputObservation;

        private ServerRequest(Consumer<ServerAgentEventPayload> events, String sessionId, UUID actorId,
                BooleanSupplier connectionCurrent, Object connectionScope,
                java.util.Optional<dev.openallay.world.ClientObservationAnchor> inputObservation) {
            this.events = java.util.Objects.requireNonNull(events, "events");
            this.sessionId = sessionId;
            this.actorId = actorId;
            this.connectionCurrent = java.util.Objects.requireNonNull(connectionCurrent, "connectionCurrent");
            this.connectionScope = connectionScope;
            this.inputObservation = java.util.Objects.requireNonNull(inputObservation, "inputObservation");
        }
    }

    private void clearAgentEventChunksLocked(UUID requestId) {
        Set<UUID> eventIds = agentEventIds.remove(requestId);
        if (eventIds != null) {
            eventIds.forEach(agentEventChunks::cancel);
        }
    }

    private void forgetAgentEventChunkLocked(UUID requestId, UUID eventId) {
        Set<UUID> eventIds = agentEventIds.get(requestId);
        if (eventIds == null) {
            return;
        }
        eventIds.remove(eventId);
        if (eventIds.isEmpty()) {
            agentEventIds.remove(requestId, eventIds);
        }
    }

    private void send(String kind, Object payload) {
        host.send(kind, codec.encode(payload));
    }
}
