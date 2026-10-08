package dev.openallay.trace.minecraft;

import dev.openallay.context.ToolInvocationContext;
import dev.openallay.context.minecraft.MinecraftContextCapture;
import dev.openallay.context.minecraft.MinecraftCommandCaller;
import dev.openallay.platform.minecraft.MinecraftResourceAccess;
import dev.openallay.tool.ToolResult;
import dev.openallay.trace.model.AgentTrace;
import dev.openallay.trace.replay.AgentTraceReplayer;
import dev.openallay.trace.replay.ReplayReport;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


public final class TraceReplayService {
    private final TraceRepository repository;
    private final MinecraftContextCapture contextCapture;
    private final AgentTraceReplayer replayer;

    public TraceReplayService(
            TraceRepository repository,
            MinecraftContextCapture contextCapture,
            AgentTraceReplayer replayer) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.contextCapture = Objects.requireNonNull(contextCapture, "contextCapture");
        this.replayer = Objects.requireNonNull(replayer, "replayer");
    }

    public ToolResult<List<String>> traceIds(net.minecraft.commands.CommandSourceStack source) {
        requireServerThread(source);
        ToolResult<TraceRepository.LoadedTraces> loaded = load(source);
        final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.trace.minecraft.TraceRepository.LoadedTraces> value; ToolResult.Failure<TraceRepository.LoadedTraces> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = loaded) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern0_holder.bound = (ToolResult.Failure<TraceRepository.LoadedTraces>) $oaPattern0_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern0_holder.bound.code(), $oaPattern0_holder.bound.message());
        }
        return new ToolResult.Success<>(
                ((ToolResult.Success<TraceRepository.LoadedTraces>) loaded).value().ids());
    }

    public ToolResult<ReplayReport> replay(net.minecraft.commands.CommandSourceStack source, String traceId) {
        requireServerThread(source);
        ToolResult<TraceRepository.LoadedTraces> loaded = load(source);
        final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<dev.openallay.trace.minecraft.TraceRepository.LoadedTraces> value; ToolResult.Failure<TraceRepository.LoadedTraces> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = loaded) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern1_holder.bound = (ToolResult.Failure<TraceRepository.LoadedTraces>) $oaPattern1_holder.value) != null))) {
            return new ToolResult.Failure<>($oaPattern1_holder.bound.code(), $oaPattern1_holder.bound.message());
        }
        AgentTrace trace = ((ToolResult.Success<TraceRepository.LoadedTraces>) loaded)
                .value()
                .find(traceId)
                .orElse(null);
        if (trace == null) {
            return new ToolResult.Failure<>("unknown_trace", "Unknown trace: " + traceId);
        }
        if (trace.requiredContext().contains(dev.openallay.context.ContextCapability.PLAYER)
                && MinecraftCommandCaller.player(source) == null) {
            return new ToolResult.Failure<>(
                    "player_required", "Trace " + trace.id() + " requires a player caller");
        }

        ToolInvocationContext context = contextCapture.capture(
                source,
                trace.requiredContext(),
                "trace:" + trace.id() + ":" + UUID.randomUUID());
        return new ToolResult.Success<>(replayer.replay(trace, context));
    }

    private ToolResult<TraceRepository.LoadedTraces> load(net.minecraft.commands.CommandSourceStack source) {
        return dev.openallay.platform.minecraft.MinecraftServerResources.withResources(
                dev.openallay.context.minecraft.MinecraftCommandSourceFacts.server(source), resources -> {
            List<TraceRepository.TraceSource> sources = dev.openallay.util.Java8Collections.toList(MinecraftResourceAccess
                    .listIds(resources, "agent_traces", id -> dev.openallay.platform.minecraft.MinecraftResourceId.from(id.toString()).path().endsWith(".json"))
                    .stream()
                    .map(id -> new TraceRepository.TraceSource(
                            id.toString(), () -> MinecraftResourceAccess.openSelectedReader(resources, id))));
            return repository.load(sources);
        });
    }

    private static void requireServerThread(net.minecraft.commands.CommandSourceStack source) {
        Objects.requireNonNull(source, "source");
        if (!dev.openallay.server.NativeServerOwner.isOwner(dev.openallay.context.minecraft.MinecraftCommandSourceFacts.server(source))) {
            throw new IllegalStateException("Trace replay must run on the Minecraft server thread");
        }
    }
}
