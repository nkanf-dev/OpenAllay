package dev.openallay.fabric.network;

import com.google.gson.Gson;
import dev.openallay.OpenAllayRuntime;
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
import dev.openallay.bridge.server.ExportedToolPolicy;
import dev.openallay.bridge.server.RemoteToolServer;
import dev.openallay.server.MinecraftServerGuideContextProvider;
import dev.openallay.server.ServerAgentService;
import dev.openallay.server.ServerGuideRuntime;
import dev.openallay.server.ServerModelCapabilityProjection;
import dev.openallay.tool.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class FabricServerBridge {
    private final OpenAllayRuntime runtime;
    private final BridgeJsonCodec codec = new BridgeJsonCodec();
    private final Gson gson = new Gson();
    private final ServerAgentEventCodec agentEvents = new ServerAgentEventCodec(gson);
    private final Map<UUID, ServerPlayer> players = new java.util.concurrent.ConcurrentHashMap<>();
    private ServerAgentRequestChunker.Reassembler requestChunks;
    private final dev.openallay.bridge.protocol.ServerAgentSteerChunker.Reassembler steerChunks =
            new dev.openallay.bridge.protocol.ServerAgentSteerChunker.Reassembler();
    private RemoteToolServer remoteTools;
    private ToolResult<ServerGuideRuntime> serverGuide =
            new ToolResult.Failure<>("model_not_configured", "Server has not started");

    private FabricServerBridge(OpenAllayRuntime runtime) {
        this.runtime = runtime;
    }

    public static void register(OpenAllayRuntime runtime) {
        FabricServerBridge bridge = new FabricServerBridge(runtime);
        ServerLifecycleEvents.SERVER_STARTED.register(bridge::ensureServices);
        ServerPlayConnectionEvents.JOIN.register(bridge::join);
        ServerPlayConnectionEvents.DISCONNECT.register(bridge::disconnect);
        ServerPlayNetworking.registerGlobalReceiver(
                FabricBridgePayloads.Packet.TYPE, bridge::receive);
    }

    private void join(
            net.minecraft.server.network.ServerGamePacketListenerImpl handler,
            net.fabricmc.fabric.api.networking.v1.PacketSender sender,
            MinecraftServer server) {
        ServerPlayer player = handler.getPlayer();
        players.put(player.getUUID(), player);
        ensureServices(server);
        sender.sendPacket(packet("capabilities", capabilities()));
    }

    private void disconnect(
            net.minecraft.server.network.ServerGamePacketListenerImpl handler,
            MinecraftServer server) {
        UUID actor = handler.getPlayer().getUUID();
        players.remove(actor);
        if (requestChunks != null) requestChunks.clearActor(actor);
        steerChunks.clearActor(actor);
        if (remoteTools != null) {
            remoteTools.disconnect(actor);
        }
        if (serverGuide instanceof ToolResult.Success<ServerGuideRuntime> success) {
            success.value().service().disconnect(actor);
            success.value().clientTools().disconnect(actor);
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

    private void ensureServices(MinecraftServer server) {
        if (remoteTools != null) {
            return;
        }
        Set<String> exported = runtime.tools().descriptors().stream()
                .filter(ExportedToolPolicy::isRemotelyReadable)
                .map(descriptor -> descriptor.id())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        MinecraftServerGuideContextProvider contexts =
                new MinecraftServerGuideContextProvider(runtime, server, gson);
        remoteTools = new RemoteToolServer(
                new ExportedToolPolicy(runtime.tools(), exported),
                contexts,
                (actor, chunk) -> send(actor, "tool_result", chunk),
                new CorrelationRegistry(),
                gson,
                BridgeProtocol.TRANSPORT_CHUNK_BYTES);
        serverGuide = ServerGuideRuntime.create(
                runtime,
                FabricLoader.getInstance().getConfigDir().resolve("openallay/server-model.json"),
                System.getenv(),
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
                server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("openallay/images"));
        requestChunks = new ServerAgentRequestChunker.Reassembler(
                serverGuide instanceof ToolResult.Success<ServerGuideRuntime> imageRuntime
                        ? imageRuntime.value().requestBodyLimit()
                        : dev.openallay.bridge.protocol.BridgeProtocol.MAX_OPENAI_REQUEST_BYTES);
        if (serverGuide instanceof ToolResult.Failure<ServerGuideRuntime> failure) {
            dev.openallay.OpenAllayConstants.LOGGER.info(
                    "Fabric server model is not advertised ({}): {}",
                    failure.code(),
                    failure.message());
        }
    }

    private void receive(
            FabricBridgePayloads.Packet packet, ServerPlayNetworking.Context context) {
        UUID actor = context.player().getUUID();
        context.server().execute(() -> {
            try {
                switch (packet.kind()) {
                    case "tool_call" -> remoteTools.handle(
                            actor, codec.decode(packet.json(), RemoteToolCallPayload.class));
                    case "tool_cancel" -> remoteTools.cancel(
                            actor, codec.decode(packet.json(), RemoteCancelPayload.class));
                    case "tool_request_close" -> remoteTools.closeRequest(
                            actor,
                            codec.decode(
                                    packet.json(),
                                    dev.openallay.bridge.protocol.RemoteToolRequestClosePayload.class));
                    case "agent_request_chunk" -> {
                        if (serverGuide instanceof ToolResult.Success<ServerGuideRuntime> success) {
                            ServerAgentRequestChunkPayload chunk = codec.decode(
                                    packet.json(), ServerAgentRequestChunkPayload.class);
                            requestChunks.accept(actor, chunk).ifPresent(json -> {
                                ServerAgentRequestPayload request =
                                        codec.decode(json, ServerAgentRequestPayload.class);
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
                    case "agent_steer_chunk" -> receiveSteerChunk(actor, packet.json());
                    case "agent_steer" -> receiveSteer(
                            actor, codec.decode(packet.json(), ServerAgentSteerPayload.class));
                    case "agent_cancel" -> {
                        if (serverGuide instanceof ToolResult.Success<ServerGuideRuntime> success) {
                            UUID requestId = codec.decode(
                                    packet.json(), ServerAgentCancelPayload.class).requestId();
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
                                            packet.json(),
                                            dev.openallay.bridge.protocol.ClientToolResultChunkPayload.class));
                        }
                    }
                    default -> throw new IllegalArgumentException("Unknown bridge packet " + packet.kind());
                }
            } catch (RuntimeException failure) {
                if ("agent_request_chunk".equals(packet.kind())) {
                    BridgeFrameCorrelation.read(packet.json()).ifPresent(requestId -> {
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
                        "Rejected Fabric bridge packet {} from {}: {}",
                        packet.kind(), actor, failure.getMessage());
            }
        });
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
        ServerPlayer player = players.get(actor);
        if (player != null && ServerPlayNetworking.canSend(player, FabricBridgePayloads.Packet.TYPE)) {
            ServerPlayNetworking.send(player, packet(kind, payload));
            return true;
        }
        return false;
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

    private FabricBridgePayloads.Packet packet(String kind, Object payload) {
        return new FabricBridgePayloads.Packet(kind, codec.encode(payload));
    }
}
