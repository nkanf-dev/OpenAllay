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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.packs.resources.ResourceManager;

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

    public ToolResult<List<String>> traceIds(CommandSourceStack source) {
        requireServerThread(source);
        ToolResult<TraceRepository.LoadedTraces> loaded = load(dev.openallay.platform.minecraft.MinecraftServerResources.resources(source.getServer()));
        if (loaded instanceof ToolResult.Failure<TraceRepository.LoadedTraces> failure) {
            return new ToolResult.Failure<>(failure.code(), failure.message());
        }
        return new ToolResult.Success<>(
                ((ToolResult.Success<TraceRepository.LoadedTraces>) loaded).value().ids());
    }

    public ToolResult<ReplayReport> replay(CommandSourceStack source, String traceId) {
        requireServerThread(source);
        ToolResult<TraceRepository.LoadedTraces> loaded = load(dev.openallay.platform.minecraft.MinecraftServerResources.resources(source.getServer()));
        if (loaded instanceof ToolResult.Failure<TraceRepository.LoadedTraces> failure) {
            return new ToolResult.Failure<>(failure.code(), failure.message());
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

    private ToolResult<TraceRepository.LoadedTraces> load(ResourceManager resources) {
        List<TraceRepository.TraceSource> sources = MinecraftResourceAccess
                .listIds(resources, "agent_traces", id -> id.getPath().endsWith(".json"))
                .stream()
                .map(id -> new TraceRepository.TraceSource(
                        id.toString(), () -> MinecraftResourceAccess.openSelectedReader(resources, id)))
                .toList();
        return repository.load(sources);
    }

    private static void requireServerThread(CommandSourceStack source) {
        Objects.requireNonNull(source, "source");
        if (!source.getServer().isSameThread()) {
            throw new IllegalStateException("Trace replay must run on the Minecraft server thread");
        }
    }
}
