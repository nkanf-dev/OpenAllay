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
    public interface ContextProvider extends RemoteToolServer.ContextProvider, ServerAgentService.ContextProvider {
        @Override default ContextProvider bind(UUID actor) { return this; }
    }

    @FunctionalInterface
    public interface Transport {
        boolean send(UUID actor, String kind, String json);
        /** Pure compatibility default; native hosts must capture the original actor admission. */
        default Transport bind(UUID actor) { return this; }
        /** Native override preserves the actual scheduler for observation work quanta. */
        default boolean dispatchLater(UUID actor, Runnable action, Runnable retired) {
            return dispatch(actor, action, retired);
        }
        default boolean dispatch(UUID actor, Runnable action, Runnable retired) {
            RuntimeException runtimeFailure = null;
            Error fatalFailure = null;
            try { action.run(); }
            catch (RuntimeException failure) { runtimeFailure = failure; }
            catch (Error failure) { fatalFailure = failure; }
            try { retired.run(); }
            catch (RuntimeException | Error failure) {
                if (fatalFailure != null) { if (fatalFailure != failure) fatalFailure.addSuppressed(failure); }
                else {
final class $oaPattern0_Holder { java.lang.Throwable value; Error bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = failure) instanceof java.lang.Error && (($oaPattern0_holder.bound = (Error) $oaPattern0_holder.value) != null))) {
                    if (runtimeFailure != null && (Throwable) $oaPattern0_holder.bound != runtimeFailure) $oaPattern0_holder.bound.addSuppressed(runtimeFailure);
                    fatalFailure = $oaPattern0_holder.bound;
                } else if (runtimeFailure != null) { if (runtimeFailure != failure) runtimeFailure.addSuppressed(failure); }
                else runtimeFailure = (RuntimeException) failure;
}
            }
            if (fatalFailure != null) throw fatalFailure;
            if (runtimeFailure != null) throw runtimeFailure;
            return true;
        }
    }

    private final FeatureServices runtime;
    private final Transport transport;
    private final BridgeJsonCodec codec = new BridgeJsonCodec();
    private final Gson gson = dev.openallay.json.EngineJson.create();
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
        final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<dev.openallay.server.ServerGuideRuntime> value; ToolResult.Success<ServerGuideRuntime> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = serverGuide) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern1_holder.bound = (ToolResult.Success<ServerGuideRuntime>) $oaPattern1_holder.value) != null))) {
            $oaPattern1_holder.bound.value().service().disconnect(actor);
            $oaPattern1_holder.bound.value().clientTools().disconnect(actor);
        }
    }

    /** Call only after the native player identity has been admitted on the game thread. */
    public void receive(UUID actor, String kind, String json) {
        java.util.Objects.requireNonNull(actor, "actor");
        try {
            switch ((kind)) {
case "tool_call":
{
remoteTools.handle(
                        actor, codec.decode(json, RemoteToolCallPayload.class));
break;
}
case "tool_cancel":
{
remoteTools.cancel(
                        actor, codec.decode(json, RemoteCancelPayload.class));
break;
}
case "tool_request_close":
{
remoteTools.closeRequest(
                        actor,
                        codec.decode(
                                json,
                                dev.openallay.bridge.protocol.RemoteToolRequestClosePayload.class));
break;
}
case "agent_request_chunk":
{
{
                    final class $oaPattern2_Holder { dev.openallay.tool.ToolResult<dev.openallay.server.ServerGuideRuntime> value; ToolResult.Success<ServerGuideRuntime> bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = serverGuide) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern2_holder.bound = (ToolResult.Success<ServerGuideRuntime>) $oaPattern2_holder.value) != null))) {
                        ServerAgentRequestChunkPayload chunk = codec.decode(
                                json, ServerAgentRequestChunkPayload.class);
                        requestChunks.accept(actor, chunk).ifPresent(assembled -> {
                            ServerAgentRequestPayload request =
                                    codec.decode(assembled, ServerAgentRequestPayload.class);
                            ToolResult<ServerAgentService.Accepted> accepted =
                                    $oaPattern2_holder.bound.value().service().ask(actor, request);
                            final class $oaPattern3_Holder { dev.openallay.tool.ToolResult<dev.openallay.server.ServerAgentService.Accepted> value; ToolResult.Failure<
                                    ServerAgentService.Accepted> bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = accepted) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern3_holder.bound = (ToolResult.Failure<
                                    ServerAgentService.Accepted>) $oaPattern3_holder.value) != null))
                                        && !$oaPattern2_holder.bound.value().service().hasRequest(actor, request.requestId())) {
                                sendAgentEvent(actor, agentEvents.encode(
                                        request.requestId(),
                                        new dev.openallay.agent.AgentEvent.Failed(
                                                $oaPattern3_holder.bound.code(), $oaPattern3_holder.bound.message())));
                                sendAgentEvent(actor, agentEvents.encode(request.requestId(),
                                        new dev.openallay.agent.AgentEvent.RequestReleased()));
                            }
                        });
                    }
                }
break;
}
case "agent_steer_chunk":
{
receiveSteerChunk(actor, json);
break;
}
case "agent_steer":
{
receiveSteer(
                        actor, codec.decode(json, ServerAgentSteerPayload.class));
break;
}
case "agent_cancel":
{
{
                    final class $oaPattern4_Holder { dev.openallay.tool.ToolResult<dev.openallay.server.ServerGuideRuntime> value; ToolResult.Success<ServerGuideRuntime> bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = serverGuide) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern4_holder.bound = (ToolResult.Success<ServerGuideRuntime>) $oaPattern4_holder.value) != null))) {
                        UUID requestId = codec.decode(
                                json, ServerAgentCancelPayload.class).requestId();
                        steerChunks.clearRequest(actor, requestId);
                        boolean assembling = requestChunks.cancel(actor, requestId);
                        if (!$oaPattern4_holder.bound.value().service().cancel(actor, requestId)
                                && assembling && !$oaPattern4_holder.bound.value().service().hasRequest(actor, requestId)) {
                            sendAgentEvent(actor, agentEvents.encode(requestId,
                                    new dev.openallay.agent.AgentEvent.Failed("agent_cancelled", "Agent request was cancelled")));
                            sendAgentEvent(actor, agentEvents.encode(requestId,
                                    new dev.openallay.agent.AgentEvent.RequestReleased()));
                        }
                    }
                }
break;
}
case "client_tool_result":
{
{
                    final class $oaPattern5_Holder { dev.openallay.tool.ToolResult<dev.openallay.server.ServerGuideRuntime> value; ToolResult.Success<ServerGuideRuntime> bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = serverGuide) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern5_holder.bound = (ToolResult.Success<ServerGuideRuntime>) $oaPattern5_holder.value) != null))) {
                        $oaPattern5_holder.bound.value().clientTools().receive(
                                actor,
                                codec.decode(
                                        json,
                                        dev.openallay.bridge.protocol.ClientToolResultChunkPayload.class));
                    }
                }
break;
}
default:
{
throw new IllegalArgumentException("Unknown bridge packet " + kind);
}
}

        } catch (RuntimeException failure) {
                if ("agent_request_chunk".equals(kind)) {
                    BridgeFrameCorrelation.read(json).ifPresent(requestId -> {
                        if (requestChunks != null) requestChunks.cancel(actor, requestId);
                        final class $oaPattern6_Holder { dev.openallay.tool.ToolResult<dev.openallay.server.ServerGuideRuntime> value; ToolResult.Success<ServerGuideRuntime> bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = serverGuide) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern6_holder.bound = (ToolResult.Success<ServerGuideRuntime>) $oaPattern6_holder.value) != null))
                                && !$oaPattern6_holder.bound.value().service().hasRequest(actor, requestId)) {
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
        dev.openallay.bridge.protocol.ServerAgentSteerChunkPayload chunk = codec.decode(json, dev.openallay.bridge.protocol.ServerAgentSteerChunkPayload.class);
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
        final class $oaPattern7_Holder { dev.openallay.tool.ToolResult<dev.openallay.server.ServerGuideRuntime> value; ToolResult.Success<ServerGuideRuntime> bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
return (($oaPattern7_holder.value = serverGuide) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern7_holder.bound = (ToolResult.Success<ServerGuideRuntime>) $oaPattern7_holder.value) != null))
                && $oaPattern7_holder.bound.value().service().ownsRequest(actor, requestId);
    }

    private void receiveSteer(UUID actor, ServerAgentSteerPayload steer) {
        if (steer.operation() == ServerAgentSteerPayload.Operation.REMOVE) {
            steerChunks.cancel(actor, steer.requestId(), steer.messageId());
        }
        final class $oaPattern8_Holder { dev.openallay.tool.ToolResult<dev.openallay.server.ServerGuideRuntime> value; ToolResult.Success<ServerGuideRuntime> bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = serverGuide) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern8_holder.bound = (ToolResult.Success<ServerGuideRuntime>) $oaPattern8_holder.value) != null))) {
            $oaPattern8_holder.bound.value().service().steer(actor, steer);
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
                new RemoteToolServer.ResponseSink() {
                    @Override public void send(UUID actor, dev.openallay.bridge.protocol.RemoteToolResultChunkPayload chunk) {
                        transport.send(actor, "tool_result", codec.encode(chunk));
                    }
                    @Override public RemoteToolServer.ResponseSink bind(UUID actor) {
                        Transport bound = transport.bind(actor);
                        return (ignored, chunk) -> bound.send(actor, "tool_result", codec.encode(chunk));
                    }
                },
                new CorrelationRegistry(), gson, BridgeProtocol.TRANSPORT_CHUNK_BYTES);
        serverGuide = ServerGuideRuntime.create(
                runtime,
                configPath,
                environment,
                contexts,
                new dev.openallay.server.ServerGuideEvents() {
                    @Override public void send(UUID actor, dev.openallay.bridge.protocol.ServerAgentEventPayload event) {
                        bind(actor).send(actor, event);
                    }
                    @Override public dev.openallay.server.ServerGuideEvents bind(UUID actor) {
                        Transport bound = transport.bind(actor);
                        return new dev.openallay.server.ServerGuideEvents() {
                            @Override public void send(UUID ignored, dev.openallay.bridge.protocol.ServerAgentEventPayload event) {
                                send(actor, event, () -> {});
                            }
                            @Override public void send(UUID ignored, dev.openallay.bridge.protocol.ServerAgentEventPayload event,
                                    Runnable retired) {
                                // Encoding happens inside the validated action; setup failures retire too.
                                bound.dispatch(actor, () -> sendAgentEvent(bound, actor, event), retired);
                            }
                        };
                    }
                },
                new dev.openallay.bridge.server.PlayerClientToolRouter.Transport() {
                    @Override public dev.openallay.bridge.server.PlayerClientToolRouter.Transport bind(UUID actor) {
                        return clientToolTransport(transport.bind(actor));
                    }
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
        final class $oaPattern9_Holder { dev.openallay.tool.ToolResult<dev.openallay.server.ServerGuideRuntime> value; ToolResult.Success<ServerGuideRuntime> bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
requestChunks = new ServerAgentRequestChunker.Reassembler(
                (($oaPattern9_holder.value = serverGuide) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern9_holder.bound = (ToolResult.Success<ServerGuideRuntime>) $oaPattern9_holder.value) != null))
                        ? $oaPattern9_holder.bound.value().requestBodyLimit()
                        : dev.openallay.bridge.protocol.BridgeProtocol.MAX_OPENAI_REQUEST_BYTES);
        final class $oaPattern10_Holder { dev.openallay.tool.ToolResult<dev.openallay.server.ServerGuideRuntime> value; ToolResult.Failure<ServerGuideRuntime> bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = serverGuide) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern10_holder.bound = (ToolResult.Failure<ServerGuideRuntime>) $oaPattern10_holder.value) != null))) {
            dev.openallay.OpenAllayConstants.LOGGER.info(
                    "Server model is not advertised ({}): {}",
                    $oaPattern10_holder.bound.code(),
                    $oaPattern10_holder.bound.message());
        }
    }

    private PlayerClientToolRouter.Transport clientToolTransport(Transport bound) {
        return new PlayerClientToolRouter.Transport() {
            @Override public boolean call(UUID actor, dev.openallay.bridge.protocol.ClientToolCallPayload payload) {
                // Native bound send has no-op retirement. It cannot enter a service Owner under Pending.
                return bound.send(actor, "client_tool_call", codec.encode(payload));
            }
            @Override public void cancel(UUID actor, dev.openallay.bridge.protocol.ClientToolCancelPayload payload) {
                bound.send(actor, "client_tool_cancel", codec.encode(payload));
            }
        };
    }

    private CapabilityPayload capabilities() {
        ToolSchemaGenerator schemas = new ToolSchemaGenerator();
        List<CapabilityPayload.RemoteToolCapability> tools = dev.openallay.util.Java8Collections.toList(runtime.tools().descriptors().stream()
                .filter(ExportedToolPolicy::isRemotelyReadable)
                .map(descriptor -> new CapabilityPayload.RemoteToolCapability(
                        descriptor.id(), descriptor.description(),
                        schemas.generate(descriptor.inputType()).toString())));
        final class $oaPattern11_Holder { dev.openallay.tool.ToolResult<dev.openallay.server.ServerGuideRuntime> value; ToolResult.Success<ServerGuideRuntime> bound; }
final $oaPattern11_Holder $oaPattern11_holder = new $oaPattern11_Holder();
final class $oaPattern12_Holder { dev.openallay.tool.ToolResult<dev.openallay.server.ServerGuideRuntime> value; ToolResult.Success<ServerGuideRuntime> bound; }
final $oaPattern12_Holder $oaPattern12_holder = new $oaPattern12_Holder();
return ServerModelCapabilityProjection.from(
                tools,
                (($oaPattern11_holder.value = serverGuide) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern11_holder.bound = (ToolResult.Success<ServerGuideRuntime>) $oaPattern11_holder.value) != null))
                        ? java.util.Optional.of($oaPattern11_holder.bound.value().contextSpec())
                        : java.util.Optional.empty(),
                (($oaPattern12_holder.value = serverGuide) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern12_holder.bound = (ToolResult.Success<ServerGuideRuntime>) $oaPattern12_holder.value) != null))
                        ? $oaPattern12_holder.bound.value().imageCapability()
                        : dev.openallay.model.metadata.ModelImageCapabilityResolution.unknown());
    }

    private boolean send(UUID actor, String kind, Object payload) {
        return transport.send(actor, kind, codec.encode(payload));
    }

    private void sendAgentEvent(UUID actor, dev.openallay.bridge.protocol.ServerAgentEventPayload event) {
        Transport bound = transport.bind(actor);
        bound.dispatch(actor, () -> sendAgentEvent(bound, actor, event), () -> {});
    }

    private void sendAgentEvent(Transport bound,
            UUID actor, dev.openallay.bridge.protocol.ServerAgentEventPayload event) {
        if (event.eventType().equals("request_released")) steerChunks.clearRequest(actor, event.requestId());
        UUID eventId = UUID.randomUUID();
        for (dev.openallay.bridge.protocol.RemoteToolResultChunkPayload chunk : new dev.openallay.bridge.protocol.ResultChunker().split(
                eventId,
                codec.encode(event),
                BridgeProtocol.TRANSPORT_CHUNK_BYTES)) {
            bound.send(actor, "agent_event_chunk", codec.encode(
                    dev.openallay.bridge.protocol.ServerAgentEventChunkPayload.from(event.requestId(), chunk)));
        }
    }
}
