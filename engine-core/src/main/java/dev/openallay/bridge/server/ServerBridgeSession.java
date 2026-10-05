package dev.openallay.bridge.server;

import com.google.gson.Gson;
import dev.openallay.FeatureServices;
import dev.openallay.agent.tool.ToolSchemaGenerator;
import dev.openallay.bridge.CorrelationRegistry;
import dev.openallay.bridge.protocol.BridgeJsonCodec;
import dev.openallay.bridge.protocol.BridgeProtocol;
import dev.openallay.bridge.protocol.CapabilityPayload;
import dev.openallay.bridge.protocol.RemoteCancelPayload;
import dev.openallay.bridge.protocol.RemoteToolCallPayload;
import dev.openallay.bridge.protocol.ServerAgentRequestPayload;
import dev.openallay.bridge.protocol.ServerAgentRequestChunkPayload;
import dev.openallay.bridge.protocol.ServerAgentRequestChunker;
import dev.openallay.bridge.protocol.ServerAgentCancelPayload;
import dev.openallay.bridge.protocol.ServerAgentSteerPayload;
import dev.openallay.bridge.protocol.ServerAgentEventCodec;
import dev.openallay.server.ServerGuideRuntime;
import dev.openallay.server.ServerModelCapabilityProjection;
import dev.openallay.server.ServerAgentService;
import dev.openallay.tool.ToolResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** One server request/chunk/custody owner; native bindings only admit actors and move strings. */
public final class ServerBridgeSession {
    public interface ContextProvider extends RemoteToolServer.ContextProvider, ServerAgentService.ContextProvider {}

    @FunctionalInterface
    public interface Transport {
        boolean send(UUID actor, String kind, String json);
    }

    private final FeatureServices runtime;
    private final Transport transport;
    private final BridgeJsonCodec codec = new BridgeJsonCodec();
    private final Gson gson = new Gson();
    private final ServerAgentEventCodec agentEvents = new ServerAgentEventCodec(gson);
    private ServerAgentRequestChunker.Reassembler requestChunks;
    private final dev.openallay.bridge.protocol.ServerAgentSteerChunker.Reassembler steerChunks =
            new dev.openallay.bridge.protocol.ServerAgentSteerChunker.Reassembler();
    private RemoteToolServer remoteTools;
    private ToolResult<ServerGuideRuntime> serverGuide =
            new ToolResult.Failure<>("model_not_configured", "Server has not started");

    public ServerBridgeSession(FeatureServices runtime, Transport transport) {
        this.runtime = java.util.Objects.requireNonNull(runtime, "runtime");
        this.transport = java.util.Objects.requireNonNull(transport, "transport");
    }

    public void connected(UUID actor) {
        connected(actor, transport);
    }

    /** Fabric JOIN has an initial PacketSender; later responses use the normal sendability guard. */
    public void connected(UUID actor, Transport initialTransport) {
        initialTransport.send(actor, "capabilities", codec.encode(capabilities()));
    }

    public void disconnected(UUID actor) {
        if (requestChunks != null) requestChunks.clearActor(actor);
        steerChunks.clearActor(actor);
        if (remoteTools != null) remoteTools.disconnect(actor);
        if (serverGuide instanceof ToolResult.Success<ServerGuideRuntime> success) {
            success.value().service().disconnect(actor);
            success.value().clientTools().disconnect(actor);
        }
    }

    /** Call only after the native player identity has been admitted on the game thread. */
    public void receive(UUID actor, String kind, String json) {
        java.util.Objects.requireNonNull(actor, "actor");
        try {
            switch (kind) {
                case "tool_call" -> remoteTools.handle(
                        actor, codec.decode(json, RemoteToolCallPayload.class));
                case "tool_cancel" -> remoteTools.cancel(
                        actor, codec.decode(json, RemoteCancelPayload.class));
                case "tool_request_close" -> remoteTools.closeRequest(
                        actor,
                        codec.decode(
                                json,
                                dev.openallay.bridge.protocol.RemoteToolRequestClosePayload.class));
                case "agent_request_chunk" -> {
                    if (serverGuide instanceof ToolResult.Success<ServerGuideRuntime> success) {
                        ServerAgentRequestChunkPayload chunk = codec.decode(
                                json, ServerAgentRequestChunkPayload.class);
                        requestChunks.accept(actor, chunk).ifPresent(assembled -> {
                            ServerAgentRequestPayload request =
                                    codec.decode(assembled, ServerAgentRequestPayload.class);
                            ToolResult<ServerAgentService.Accepted> accepted =
                                    success.value().service().ask(actor, request);
                            if (accepted instanceof ToolResult.Failure<
                                    ServerAgentService.Accepted> failure
                                        && !success.value().service().hasRequest(actor, request.requestId())) {
                                sendAgentEvent(actor, agentEvents.encode(
                                        request.requestId(),
                                        new dev.openallay.agent.AgentEvent.Failed(
                                                failure.code(), failure.message())));
                                sendAgentEvent(actor, agentEvents.encode(request.requestId(),
                                        new dev.openallay.agent.AgentEvent.RequestReleased()));
                            }
                        });
                    }
                }
                case "agent_steer_chunk" -> receiveSteerChunk(actor, json);
                case "agent_steer" -> receiveSteer(
                        actor, codec.decode(json, ServerAgentSteerPayload.class));
                case "agent_cancel" -> {
                    if (serverGuide instanceof ToolResult.Success<ServerGuideRuntime> success) {
                        UUID requestId = codec.decode(
                                json, ServerAgentCancelPayload.class).requestId();
                        steerChunks.clearRequest(actor, requestId);
                        boolean assembling = requestChunks.cancel(actor, requestId);
                        if (!success.value().service().cancel(actor, requestId)
                                && assembling && !success.value().service().hasRequest(actor, requestId)) {
                            sendAgentEvent(actor, agentEvents.encode(requestId,
                                    new dev.openallay.agent.AgentEvent.Failed("agent_cancelled", "Agent request was cancelled")));
                            sendAgentEvent(actor, agentEvents.encode(requestId,
                                    new dev.openallay.agent.AgentEvent.RequestReleased()));
                        }
                    }
                }
                case "client_tool_result" -> {
                    if (serverGuide instanceof ToolResult.Success<ServerGuideRuntime> success) {
                        success.value().clientTools().receive(
                                actor,
                                codec.decode(
                                        json,
                                        dev.openallay.bridge.protocol.ClientToolResultChunkPayload.class));
                    }
                }
                default -> throw new IllegalArgumentException("Unknown bridge packet " + kind);
            }
        } catch (RuntimeException failure) {
                if ("agent_request_chunk".equals(kind)) {
                    BridgeFrameCorrelation.read(json).ifPresent(requestId -> {
                        if (requestChunks != null) requestChunks.cancel(actor, requestId);
                        if (serverGuide instanceof ToolResult.Success<ServerGuideRuntime> success
                                && !success.value().service().hasRequest(actor, requestId)) {
                            sendAgentEvent(actor, agentEvents.encode(requestId,
                                    new dev.openallay.agent.AgentEvent.Failed(
                                            "server_protocol_error", "The server rejected this malformed request")));
                            sendAgentEvent(actor, agentEvents.encode(requestId,
                                    new dev.openallay.agent.AgentEvent.RequestReleased()));
                        }
                    });
                }

            dev.openallay.OpenAllayConstants.LOGGER.warn(
                    "Rejected bridge packet {} from {}: {}",
                    kind, actor, failure.getMessage());
        }
    }

    private void receiveSteerChunk(UUID actor, String json) {
        var chunk = codec.decode(json, dev.openallay.bridge.protocol.ServerAgentSteerChunkPayload.class);
        if (!ownsRequest(actor, chunk.requestId())) {
            steerChunks.clearRequest(actor, chunk.requestId());
            sendAgentEvent(actor, agentEvents.encode(chunk.requestId(),
                    new dev.openallay.agent.AgentEvent.SteerRejected(chunk.messageId())));
            return;
        }
        steerChunks.accept(actor, chunk).ifPresent(assembled -> {
            ServerAgentSteerPayload steer = codec.decode(assembled, ServerAgentSteerPayload.class);
            if (!steer.requestId().equals(chunk.requestId()) || !steer.messageId().equals(chunk.messageId())) {
                throw new IllegalArgumentException("Server steer correlation changed during assembly");
            }
            receiveSteer(actor, steer);
        });
    }

    private boolean ownsRequest(UUID actor, UUID requestId) {
        return serverGuide instanceof ToolResult.Success<ServerGuideRuntime> success
                && success.value().service().ownsRequest(actor, requestId);
    }

    private void receiveSteer(UUID actor, ServerAgentSteerPayload steer) {
        if (steer.operation() == ServerAgentSteerPayload.Operation.REMOVE) {
            steerChunks.cancel(actor, steer.requestId(), steer.messageId());
        }
        if (serverGuide instanceof ToolResult.Success<ServerGuideRuntime> success) {
            success.value().service().steer(actor, steer);
        } else if (steer.operation() == ServerAgentSteerPayload.Operation.PUT) {
            sendAgentEvent(actor, agentEvents.encode(steer.requestId(),
                    new dev.openallay.agent.AgentEvent.SteerRejected(steer.messageId())));
        }
    }

    public void started(ContextProvider contexts, java.nio.file.Path configPath,
            java.util.Map<String, String> environment, java.nio.file.Path imageDirectory) {
        if (remoteTools != null) return;
        Set<String> exported = runtime.tools().descriptors().stream()
                .filter(ExportedToolPolicy::isRemotelyReadable)
                .map(descriptor -> descriptor.id())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        remoteTools = new RemoteToolServer(
                new ExportedToolPolicy(runtime.tools(), exported), contexts,
                (actor, chunk) -> send(actor, "tool_result", chunk),
                new CorrelationRegistry(), gson, BridgeProtocol.TRANSPORT_CHUNK_BYTES);
        serverGuide = ServerGuideRuntime.create(
                runtime,
                configPath,
                environment,
                contexts,
                this::sendAgentEvent,
                new dev.openallay.bridge.server.PlayerClientToolRouter.Transport() {
                    @Override
                    public boolean call(
                            UUID actor,
                            dev.openallay.bridge.protocol.ClientToolCallPayload payload) {
                        return send(actor, "client_tool_call", payload);
                    }

                    @Override
                    public void cancel(
                            UUID actor,
                            dev.openallay.bridge.protocol.ClientToolCancelPayload payload) {
                        send(actor, "client_tool_cancel", payload);
                    }
                },
                imageDirectory);
        requestChunks = new ServerAgentRequestChunker.Reassembler(
                serverGuide instanceof ToolResult.Success<ServerGuideRuntime> imageRuntime
                        ? imageRuntime.value().requestBodyLimit()
                        : dev.openallay.bridge.protocol.BridgeProtocol.MAX_OPENAI_REQUEST_BYTES);
        if (serverGuide instanceof ToolResult.Failure<ServerGuideRuntime> failure) {
            dev.openallay.OpenAllayConstants.LOGGER.info(
                    "Server model is not advertised ({}): {}",
                    failure.code(),
                    failure.message());
        }
    }

    private CapabilityPayload capabilities() {
        ToolSchemaGenerator schemas = new ToolSchemaGenerator();
        List<CapabilityPayload.RemoteToolCapability> tools = runtime.tools().descriptors().stream()
                .filter(ExportedToolPolicy::isRemotelyReadable)
                .map(descriptor -> new CapabilityPayload.RemoteToolCapability(
                        descriptor.id(), descriptor.description(),
                        schemas.generate(descriptor.inputType()).toString()))
                .toList();
        return ServerModelCapabilityProjection.from(
                tools,
                serverGuide instanceof ToolResult.Success<ServerGuideRuntime> success
                        ? java.util.Optional.of(success.value().contextSpec())
                        : java.util.Optional.empty(),
                serverGuide instanceof ToolResult.Success<ServerGuideRuntime> imageRuntime
                        ? imageRuntime.value().imageCapability()
                        : dev.openallay.model.metadata.ModelImageCapabilityResolution.unknown());
    }

    private boolean send(UUID actor, String kind, Object payload) {
        return transport.send(actor, kind, codec.encode(payload));
    }

    private void sendAgentEvent(
            UUID actor, dev.openallay.bridge.protocol.ServerAgentEventPayload event) {
        if (event.eventType().equals("request_released")) steerChunks.clearRequest(actor, event.requestId());
        UUID eventId = UUID.randomUUID();
        for (var chunk : new dev.openallay.bridge.protocol.ResultChunker().split(
                eventId,
                codec.encode(event),
                BridgeProtocol.TRANSPORT_CHUNK_BYTES)) {
            send(actor, "agent_event_chunk",
                    dev.openallay.bridge.protocol.ServerAgentEventChunkPayload.from(
                            event.requestId(), chunk));
        }
    }
}
