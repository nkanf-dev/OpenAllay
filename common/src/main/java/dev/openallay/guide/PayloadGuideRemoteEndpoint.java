package dev.openallay.guide;

import com.google.gson.Gson;
import dev.openallay.agent.AgentEvent;
import dev.openallay.bridge.protocol.CapabilityPayload;
import dev.openallay.bridge.protocol.ServerAgentEventCodec;
import dev.openallay.bridge.protocol.ServerAgentEventPayload;
import dev.openallay.bridge.protocol.ServerAgentRequestPayload;
import dev.openallay.bridge.protocol.ServerAgentHistoryMessage;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import java.util.function.Consumer;

public final class PayloadGuideRemoteEndpoint implements GuideRemoteEndpoint {
    public interface Port {
        CapabilityPayload capabilities();

        boolean ask(
                ServerAgentRequestPayload request,
                Consumer<ServerAgentEventPayload> events);

        boolean cancel(UUID requestId);

        void disconnect();
    }

    private final Port port;
    private final ServerAgentEventCodec events;
    private final Object requestLock = new Object();
    private final java.util.Map<UUID, RemoteRequest> requests = new java.util.HashMap<>();
    private final java.util.concurrent.Executor payloadWorker;
    private final dev.openallay.client.ClientEventDispatcher dispatcher;

    private static final java.util.concurrent.Executor PAYLOAD_WORKER =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "openallay-guide-remote-payloads");
                thread.setDaemon(true);
                return thread;
            });

    public PayloadGuideRemoteEndpoint(Port port, Gson gson) {
        this(port, gson, PAYLOAD_WORKER, Runnable::run);
    }

    public PayloadGuideRemoteEndpoint(
            Port port, Gson gson, dev.openallay.client.ClientEventDispatcher dispatcher) {
        this(port, gson, PAYLOAD_WORKER, dispatcher);
    }

    PayloadGuideRemoteEndpoint(Port port, Gson gson, java.util.concurrent.Executor payloadWorker) {
        this(port, gson, payloadWorker, Runnable::run);
    }

    public PayloadGuideRemoteEndpoint(Port port, Gson gson, java.util.concurrent.Executor payloadWorker,
            dev.openallay.client.ClientEventDispatcher dispatcher) {
        this.port = Objects.requireNonNull(port, "port");
        events = new ServerAgentEventCodec(gson);
        this.payloadWorker = Objects.requireNonNull(payloadWorker, "payloadWorker");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    @Override
    public boolean serverModelAvailable() {
        return port.capabilities().serverModel();
    }

    @Override
    public boolean serverToolsAvailable() {
        return !port.capabilities().remoteTools().isEmpty();
    }

    @Override
    public dev.openallay.model.image.ImageInputCapability imageInputCapability() {
        return port.capabilities().serverImageInputCapability();
    }

    @Override
    public String imageInputCapabilitySource() {
        return port.capabilities().serverImageInputCapabilitySource();
    }

    @Override
    public Optional<GuideContextSpec> contextSpec() {
        CapabilityPayload capability = port.capabilities();
        if (!capability.serverModel()) return Optional.empty();
        try {
            return Optional.of(new GuideContextSpec(
                    new dev.openallay.agent.context.ContextBudget(
                            capability.serverContextWindowTokens(),
                            capability.serverMaxOutputTokens()),
                    capability.serverPromptAndToolTokens(),
                    capability.serverCanonicalModelId()));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    @Override
    public boolean ask(
            UUID requestId, String sessionId, String question, Consumer<AgentEvent> consumer) {
        return ask(requestId, sessionId, dev.openallay.model.ModelMessage.userText(question),
                dev.openallay.model.image.ImagePayloadResolver.unavailable(), consumer);
    }

    @Override
    public boolean askWithContext(
            UUID requestId, String sessionId, String question,
            List<dev.openallay.model.ModelMessage> history, Consumer<AgentEvent> consumer) {
        return askWithContext(requestId, sessionId, dev.openallay.model.ModelMessage.userText(question),
                dev.openallay.model.image.ImagePayloadResolver.unavailable(), history, consumer);
    }

    @Override
    public boolean ask(
            UUID requestId, String sessionId, dev.openallay.model.ModelMessage userInput,
            dev.openallay.model.image.ImagePayloadResolver images, Consumer<AgentEvent> consumer) {
        return askWithContext(requestId, sessionId, userInput, images, List.of(), consumer);
    }

    @Override
    public boolean askWithContext(
            UUID requestId, String sessionId, dev.openallay.model.ModelMessage userInput,
            dev.openallay.model.image.ImagePayloadResolver images,
            List<dev.openallay.model.ModelMessage> history, Consumer<AgentEvent> consumer) {
        dev.openallay.agent.AgentRequest.validateUserInput(userInput);
        Objects.requireNonNull(images, "images");
        java.util.Map<String, dev.openallay.model.image.ImageReference> references = new java.util.LinkedHashMap<>();
        java.util.stream.Stream.concat(history.stream(), java.util.stream.Stream.of(userInput))
                .flatMap(message -> message.content().stream())
                .filter(dev.openallay.model.ModelContent.Image.class::isInstance)
                .map(dev.openallay.model.ModelContent.Image.class::cast)
                .forEach(image -> {
                    var previous = references.putIfAbsent(image.reference().sha256(), image.reference());
                    if (previous != null && !previous.equals(image.reference())) {
                        throw new IllegalArgumentException("Conflicting metadata for the same image hash");
                    }
                });
        CapabilityPayload captured = port.capabilities();
        if (!references.isEmpty()
                && captured.serverImageInputCapability()
                        != dev.openallay.model.image.ImageInputCapability.SUPPORTED) {
            throw new GuideModelProfileException(
                    captured.serverImageInputCapability() == dev.openallay.model.image.ImageInputCapability.UNKNOWN
                            ? "image_input_unknown" : "image_input_unsupported",
                    "The server model has no confirmed image input support. Select an image-capable model or remove images from this conversation.");
        }
        List<ServerAgentHistoryMessage> detached = history.stream()
                .map(ServerAgentHistoryMessage::from).toList();
        if (references.isEmpty()) {
            return send(new ServerAgentRequestPayload(
                    requestId, sessionId, userInput, true, detached, List.of()), consumer);
        }
        RemoteRequest pending = prepareRequest(requestId, consumer);
        if (pending == null) return false;
        synchronized (requestLock) { pending.preparing = true; }
        try {
            payloadWorker.execute(() -> {
                List<dev.openallay.bridge.protocol.ServerAgentImageAttachment> attachments = new java.util.ArrayList<>();
                try {
                    for (var reference : references.values()) {
                        synchronized (requestLock) {
                            if (!currentPreparation(pending)) return;
                        }
                        attachments.add(dev.openallay.bridge.protocol.ServerAgentImageAttachment.from(
                                reference, images.read(reference)));
                    }
                    ServerAgentRequestPayload request = new ServerAgentRequestPayload(
                            requestId, sessionId, userInput, true, detached, attachments);
                    dispatcher.execute(() -> {
                        synchronized (requestLock) {
                            if (!currentPreparation(pending)) return;
                            try {
                                if (!sendPrepared(request, pending)) failPrepared(pending,
                                        "server_unavailable", "The server model request could not be sent");
                            } catch (RuntimeException invalid) {
                                failPrepared(pending, "server_protocol_error", "The server model request could not be sent");
                            }
                        }
                    });
                } catch (java.io.IOException | RuntimeException invalid) {
                    failPrepared(pending, "image_unavailable",
                            "A required image attachment is invalid or unavailable");
                } finally {
                    finishPreparation(pending);
                }
            });
        } catch (RuntimeException rejected) {
            failPrepared(pending, "image_unavailable", "Image attachment preparation could not be queued");
            finishPreparation(pending);
        }
        return true;
    }

    private boolean currentPreparation(RemoteRequest pending) {
        return requests.get(pending.requestId) == pending && !pending.cancelled && !pending.terminal;
    }

    private boolean send(ServerAgentRequestPayload request, Consumer<AgentEvent> consumer) {
        RemoteRequest pending = prepareRequest(request.requestId(), consumer);
        if (pending == null) return false;
        dispatcher.execute(() -> {
            try {
                if (!sendPrepared(request, pending)) failPrepared(pending,
                        "server_unavailable", "The server model request could not be sent");
            } catch (RuntimeException invalid) {
                failPrepared(pending, "server_protocol_error", "The server model request could not be sent");
            }
        });
        return true;
    }

    /** Register before a typed ask queues any file reads on the shared payload worker. */
    private RemoteRequest prepareRequest(UUID requestId, Consumer<AgentEvent> consumer) {
        synchronized (requestLock) {
            RemoteRequest pending = new RemoteRequest(requestId, consumer);
            return requests.putIfAbsent(requestId, pending) == null ? pending : null;
        }
    }

    private boolean sendPrepared(ServerAgentRequestPayload request, RemoteRequest pending) {
        synchronized (requestLock) {
            if (requests.get(request.requestId()) != pending) return false;
            if (pending.cancelled) return false;
            pending.sent = true;
            boolean sent = port.ask(request, payload -> {
                try {
                    deliver(pending, events.decode(payload, request.requestId()));
                } catch (RuntimeException failure) {
                    port.cancel(request.requestId());
                    deliver(pending, new AgentEvent.Failed(
                            "server_protocol_error",
                            failure.getMessage() == null
                                    ? "Malformed server Agent event"
                                    : failure.getMessage()));
                }
            });
            if (!sent) { pending.sent = false; return false; }
            if (requests.get(pending.requestId) != pending) return true;
            return true;
        }
    }

    private void failPrepared(RemoteRequest pending, String code, String message) {
        synchronized (requestLock) {
            if (requests.get(pending.requestId) != pending) return;
            pending.failure = new AgentEvent.Failed(code, message);
            pending.cancelled = true;
            if (pending.preparing) return;
        }
        deliver(pending, pending.failure);
        deliver(pending, new AgentEvent.RequestReleased());
    }

    private void finishPreparation(RemoteRequest pending) {
        AgentEvent.Failed failure;
        synchronized (requestLock) {
            pending.preparing = false;
            if (requests.get(pending.requestId) != pending) return;
            failure = pending.failure;
        }
        if (failure != null) {
            deliver(pending, failure);
            deliver(pending, new AgentEvent.RequestReleased());
        }
    }

    private void deliver(RemoteRequest pending, AgentEvent event) {
        dispatcher.execute(() -> {
            synchronized (requestLock) {
                if (requests.get(pending.requestId) != pending) return;
                if (event instanceof AgentEvent.FinalText || event instanceof AgentEvent.Failed) {
                    if (pending.terminal) return;
                    pending.terminal = true;
                }
                if (event instanceof AgentEvent.RequestReleased) {
                    requests.remove(pending.requestId, pending);
                }
            }
            pending.consumer.accept(event);
        });
    }

    private static final class RemoteRequest {
        private final UUID requestId;
        private final Consumer<AgentEvent> consumer;
        private boolean sent;
        private boolean cancelled;
        private boolean terminal;
        private boolean preparing;
        private AgentEvent.Failed failure;

        private RemoteRequest(UUID requestId, Consumer<AgentEvent> consumer) {
            this.requestId = requestId;
            this.consumer = Objects.requireNonNull(consumer, "consumer");
        }
    }

    @Override
    public boolean cancel(UUID requestId) {
        synchronized (requestLock) {
            RemoteRequest pending = requests.get(requestId);
            if (pending == null || pending.cancelled || pending.terminal) return false;
            pending.cancelled = true;
            if (!pending.sent) {
                failPrepared(pending, "agent_cancelled", "Agent request was cancelled");
                return true;
            }
            return port.cancel(requestId);
        }
    }

    @Override
    public void disconnect() {
        List<RemoteRequest> detached;
        synchronized (requestLock) {
            detached = List.copyOf(requests.values());
            detached.forEach(pending -> pending.cancelled = true);
        }
        detached.forEach(pending -> failPrepared(pending, "server_disconnected", "Server connection closed"));
        port.disconnect();
    }
}
