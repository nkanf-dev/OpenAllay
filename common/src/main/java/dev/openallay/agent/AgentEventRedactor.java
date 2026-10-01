package dev.openallay.agent;

import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.agent.trace.LiveTraceJson;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelFailure;
import java.util.Set;

/** Redacts exported state without weakening model-visible error diagnostics. */
public final class AgentEventRedactor {
    private AgentEventRedactor() {}

    public static AgentEvent redact(AgentEvent event, Set<String> secrets) {
        return switch (event) {
            case AgentEvent.ContextUpdated context -> new AgentEvent.ContextUpdated(
                    ModelContextCodec.redacted(context.messages(), secrets),
                    ModelContextCodec.redacted(context.requestMessages(), secrets));
            case AgentEvent.ContextFinalized context -> new AgentEvent.ContextFinalized(
                    ModelContextCodec.redacted(context.messages(), secrets),
                    ModelContextCodec.redacted(context.requestMessages(), secrets));
            case AgentEvent.ToolStarted tool -> new AgentEvent.ToolStarted(tool.invocationId(), tool.toolId(),
                    LiveTraceJson.redact(tool.arguments(), secrets).getAsJsonObject(),
                    tool.presentationMessages().stream().map(message -> new dev.openallay.guide.GuideToolMessage(
                            message.key(), message.arguments().stream().map(argument -> LiveTraceJson.redact(argument, secrets))
                                    .toList())).toList());
            case AgentEvent.ToolCompleted tool -> new AgentEvent.ToolCompleted(tool.invocationId(), tool.toolId(),
                    tool.failure(), LiveTraceJson.redact(tool.normalized(), secrets).getAsJsonObject());
            case AgentEvent.FinalText text -> new AgentEvent.FinalText(LiveTraceJson.redact(text.text(), secrets));
            case AgentEvent.Failed failed -> new AgentEvent.Failed(failed.code(), LiveTraceJson.redact(failed.message(), secrets));
            case AgentEvent.ModelProgress progress -> new AgentEvent.ModelProgress(switch (progress.event()) {
                case ModelEvent.TextDelta text -> new ModelEvent.TextDelta(LiveTraceJson.redact(text.text(), secrets));
                case ModelFailure failed -> new ModelFailure(failed.code(), LiveTraceJson.redact(failed.message(), secrets), failed.httpStatus());
                default -> progress.event();
            });
            case AgentEvent.ContextCompacted eventCheckpoint -> {
                var checkpoint = eventCheckpoint.checkpoint();
                yield new AgentEvent.ContextCompacted(new dev.openallay.agent.context.ContextCheckpoint(
                        checkpoint.checkpointId(), checkpoint.sourceFromIndex(), checkpoint.sourceToIndexExclusive(),
                        checkpoint.sourceHash(), checkpoint.modelIdentifier(),
                        checkpoint.createdAt(), checkpoint.status(),
                        checkpoint.summary() == null ? null : LiveTraceJson.redact(checkpoint.summary(), secrets),
                        checkpoint.failureCode(), checkpoint.failureMessage() == null ? null
                                : LiveTraceJson.redact(checkpoint.failureMessage(), secrets), checkpoint.estimatedProjectionTokens()));
            }
            case AgentEvent.StateChanged state -> state;
        };
    }
}
