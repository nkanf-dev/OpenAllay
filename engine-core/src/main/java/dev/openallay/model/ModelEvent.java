package dev.openallay.model;

import com.google.gson.JsonObject;
import java.util.Objects;

public sealed interface ModelEvent
        permits ModelEvent.TextDelta,
                ModelEvent.ReasoningDelta,
                ModelEvent.ToolUseComplete,
                ModelEvent.UsageUpdate,
                ModelEvent.UsageObserved,
                ModelEvent.UsageStarted,
                ModelEvent.AttemptStarted,
                ModelEvent.ResponseStarted,
                ModelEvent.RateLimited,
                ModelEvent.MessageComplete,
                ModelFailure {
    record TextDelta(String text) implements ModelEvent {
        public TextDelta {
            Objects.requireNonNull(text, "text");
        }
    }

    record ReasoningDelta(String text) implements ModelEvent {
        public ReasoningDelta {
            Objects.requireNonNull(text, "text");
        }
    }

    record ToolUseComplete(String id, String name, JsonObject input) implements ModelEvent {
        public ToolUseComplete {
            input = dev.openallay.json.JsonTrees.copy(Objects.requireNonNull(input, "input"));
        }

        @Override
        public JsonObject input() {
            return dev.openallay.json.JsonTrees.copy(input);
        }
    }

    record UsageUpdate(ModelUsage usage) implements ModelEvent {}

    /** Numeric lifecycle boundary sharing its identity with the eventual usage receipt. */
    record UsageStarted(java.util.UUID callId, String modelIdentifier) implements ModelEvent {
        public UsageStarted {
            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(modelIdentifier, "modelIdentifier");
        }
    }

    /** Counts-only receipt for one actual provider attempt, not a streamed usage delta. */
    record UsageObserved(java.util.UUID callId, String modelIdentifier, ModelUsage usage)
            implements ModelEvent {
        public UsageObserved {
            Objects.requireNonNull(callId, "callId");
            Objects.requireNonNull(modelIdentifier, "modelIdentifier");
            Objects.requireNonNull(usage, "usage");
        }
    }

    /** Redacted lifecycle boundary. The relative attempt budget may be unavailable. */
    record AttemptStarted(int attempt, Long attemptTimeoutMillis) implements ModelEvent {
        public AttemptStarted {
            if (attempt <= 0 || (attemptTimeoutMillis != null && attemptTimeoutMillis < 0)) {
                throw new IllegalArgumentException("Model attempt lifecycle is invalid");
            }
        }
    }

    /** Successful response headers arrived; model response-body decoding is about to begin. */
    record ResponseStarted() implements ModelEvent {}

    record RateLimited(long retryAfterMillis, int attempt) implements ModelEvent {
        public RateLimited {
            if (retryAfterMillis < 0 || attempt <= 0) {
                throw new IllegalArgumentException("Rate-limit delay and attempt are invalid");
            }
        }
    }

    record MessageComplete(String stopReason) implements ModelEvent {}
}
