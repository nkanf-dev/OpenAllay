package dev.openallay.agent;

import com.google.gson.JsonObject;
import dev.openallay.agent.context.ContextCheckpoint;
import dev.openallay.guide.GuideToolMessage;
import dev.openallay.model.ModelEvent;
import java.util.List;
import java.util.Objects;

public sealed interface AgentEvent
        permits AgentEvent.StateChanged,
                AgentEvent.ContextCompacted,
                AgentEvent.ContextUpdated,
                AgentEvent.ContextFinalized,
                AgentEvent.SteerApplied,
                AgentEvent.SteerRejected,
                AgentEvent.ModelProgress,
                AgentEvent.ModelUsageObserved,
                AgentEvent.ModelUsageStarted,
                AgentEvent.RequestReleased,
                AgentEvent.ToolStarted,
                AgentEvent.ToolCompleted,
                AgentEvent.FinalText,
                AgentEvent.Failed {
    record StateChanged(AgentState state) implements AgentEvent {}

    /** Actual request cleanup and all dispatched call receipts have finished. */
    record RequestReleased() implements AgentEvent {}

    record ContextCompacted(ContextCheckpoint checkpoint) implements AgentEvent {
        public ContextCompacted {
            Objects.requireNonNull(checkpoint, "checkpoint");
        }
    }

    /** Actual model messages, distinct from the player-facing timeline. */
    record ContextUpdated(
            List<dev.openallay.model.ModelMessage> messages,
            List<dev.openallay.model.ModelMessage> requestMessages) implements AgentEvent {
        public ContextUpdated {
            messages = dev.openallay.agent.context.ModelContextCodec.safe(messages);
            requestMessages = dev.openallay.agent.context.ModelContextCodec.safe(requestMessages);
        }
    }

    /** One final safe handoff may archive a visibly cancelled request without reopening its UI. */
    record ContextFinalized(
            List<dev.openallay.model.ModelMessage> messages,
            List<dev.openallay.model.ModelMessage> requestMessages) implements AgentEvent {
        public ContextFinalized {
            messages = dev.openallay.agent.context.ModelContextCodec.safe(messages);
            requestMessages = dev.openallay.agent.context.ModelContextCodec.safe(requestMessages);
        }
    }

    /** Actual dispatched call, including summary calls. Never emitted for local preflight failure. */
    record ModelUsageStarted(java.util.UUID callId, String modelIdentifier) implements AgentEvent {
        public ModelUsageStarted {
            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(modelIdentifier, "modelIdentifier");
        }
    }

    /** One immutable provider-attempt receipt; no response text or private reasoning. */
    record ModelUsageObserved(java.util.UUID callId, String modelIdentifier,
            dev.openallay.model.ModelUsage usage) implements AgentEvent {
        public ModelUsageObserved {
            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(modelIdentifier, "modelIdentifier");
            Objects.requireNonNull(usage, "usage");
        }
    }

    /** The supplemental user message entered a complete structural model boundary. */
    record SteerApplied(java.util.UUID messageId, dev.openallay.model.ModelMessage message)
            implements AgentEvent {
        public SteerApplied {
            Objects.requireNonNull(messageId, "messageId");
            Objects.requireNonNull(message, "message");
            if (message.role() != dev.openallay.model.ModelRole.USER) {
                throw new IllegalArgumentException("steer must be a user message");
            }
        }
    }

    /** A sealed/released request cannot accept this instruction; retain it as follow-up. */
    record SteerRejected(java.util.UUID messageId) implements AgentEvent {
        public SteerRejected { Objects.requireNonNull(messageId, "messageId"); }
    }

    record ModelProgress(ModelEvent event) implements AgentEvent {
        public ModelProgress {
            ModelEvent.requireKnown(event);
            Objects.requireNonNull(event, "event");
            if (event instanceof ModelEvent.ReasoningDelta) {
                event = new ModelEvent.ReasoningDelta("");
            }
        }
    }

    record ToolStarted(
            String invocationId,
            String toolId,
            JsonObject arguments,
            List<GuideToolMessage> presentationMessages)
            implements AgentEvent {
        public ToolStarted {
            requireIdentity(invocationId, toolId);
            arguments = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(arguments, "arguments"));
            presentationMessages = List.copyOf(presentationMessages);
        }

        public ToolStarted(
                String invocationId,
                String toolId,
                List<GuideToolMessage> presentationMessages) {
            this(invocationId, toolId, new JsonObject(), presentationMessages);
        }

        public ToolStarted(String invocationId, String toolId) {
            this(invocationId, toolId, new JsonObject(), List.of());
        }

        @Override
        public JsonObject arguments() {
            return dev.openallay.json.JsonTrees.copy(arguments);
        }
    }

    record ToolCompleted(
            String invocationId,
            String toolId,
            boolean failure,
            JsonObject normalized)
            implements AgentEvent {
        public ToolCompleted {
            requireIdentity(invocationId, toolId);
            normalized = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(normalized, "normalized"));
        }

        @Override
        public JsonObject normalized() {
            return dev.openallay.json.JsonTrees.copy(normalized);
        }
    }

    record FinalText(String text) implements AgentEvent {}

    record Failed(String code, String message) implements AgentEvent {}

    private static void requireIdentity(String invocationId, String toolId) {
        if (invocationId == null || invocationId.isBlank()
                || toolId == null || toolId.isBlank()) {
            throw new IllegalArgumentException("tool invocation identity is required");
        }
    }
}
