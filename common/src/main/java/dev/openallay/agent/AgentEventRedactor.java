package dev.openallay.agent;

import dev.openallay.agent.context.ModelContextCodec;
import dev.openallay.agent.trace.LiveTraceJson;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelFailure;
import java.util.Set;

/** Redacts known credentials while preserving non-sensitive error diagnostics. */
public final class AgentEventRedactor {
    private AgentEventRedactor() {}

    public static AgentEvent redact(AgentEvent event, Set<String> secrets) {
        return redact(event, new LiveTraceJson.SecretPlan(secrets));
    }

    public static AgentEvent redact(AgentEvent event, LiveTraceJson.SecretPlan secrets) {
        return switch (event) {
            case AgentEvent.ContextUpdated context -> new AgentEvent.ContextUpdated(
                    ModelContextCodec.redacted(context.messages(), secrets),
                    ModelContextCodec.redacted(context.requestMessages(), secrets));
            case AgentEvent.ContextFinalized context -> new AgentEvent.ContextFinalized(
                    ModelContextCodec.redacted(context.messages(), secrets),
                    ModelContextCodec.redacted(context.requestMessages(), secrets));
            case AgentEvent.ToolStarted tool -> new AgentEvent.ToolStarted(LiveTraceJson.redactIdentity(tool.invocationId(), secrets),
                    LiveTraceJson.redact(tool.toolId(), secrets),
                    LiveTraceJson.redact(tool.arguments(), secrets).getAsJsonObject(),
                    tool.presentationMessages().stream().map(message -> new dev.openallay.guide.GuideToolMessage(
                            message.key(), message.arguments().stream().map(argument -> LiveTraceJson.redact(argument, secrets))
                                    .toList())).toList());
            case AgentEvent.ToolCompleted tool -> new AgentEvent.ToolCompleted(LiveTraceJson.redactIdentity(tool.invocationId(), secrets),
                    LiveTraceJson.redact(tool.toolId(), secrets),
                    tool.failure(), LiveTraceJson.redact(tool.normalized(), secrets).getAsJsonObject());
            case AgentEvent.FinalText text -> new AgentEvent.FinalText(LiveTraceJson.redact(text.text(), secrets));
            case AgentEvent.Failed failed -> new AgentEvent.Failed(LiveTraceJson.redact(failed.code(), secrets), LiveTraceJson.redact(failed.message(), secrets));
            case AgentEvent.ModelProgress progress -> new AgentEvent.ModelProgress(switch (progress.event()) {
                case ModelEvent.TextDelta text -> new ModelEvent.TextDelta(LiveTraceJson.redact(text.text(), secrets));
                case ModelEvent.ToolUseComplete use -> new ModelEvent.ToolUseComplete(
                        LiveTraceJson.redactIdentity(use.id(), secrets), LiveTraceJson.redact(use.name(), secrets),
                        LiveTraceJson.redact(use.input(), secrets).getAsJsonObject());
                case ModelEvent.ReasoningDelta ignored -> new ModelEvent.ReasoningDelta("");
                case ModelEvent.MessageComplete complete -> new ModelEvent.MessageComplete(
                        LiveTraceJson.redact(complete.stopReason(), secrets));
                case ModelFailure failed -> new ModelFailure(LiveTraceJson.redact(failed.code(), secrets), LiveTraceJson.redact(failed.message(), secrets), failed.httpStatus());
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
