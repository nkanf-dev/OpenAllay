package dev.tomewisp.model;

import com.google.gson.JsonObject;
import java.util.Objects;

public sealed interface ModelEvent
        permits ModelEvent.TextDelta,
                ModelEvent.ReasoningDelta,
                ModelEvent.ToolUseComplete,
                ModelEvent.UsageUpdate,
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
            input = Objects.requireNonNull(input, "input").deepCopy();
        }

        @Override
        public JsonObject input() {
            return input.deepCopy();
        }
    }

    record UsageUpdate(ModelUsage usage) implements ModelEvent {}

    record MessageComplete(String stopReason) implements ModelEvent {}
}
