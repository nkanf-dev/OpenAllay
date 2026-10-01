package dev.openallay.model.config;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Requested provider effort, not a promise of the model's actual reasoning depth. */
public enum ModelReasoningEffort {
    AUTO,
    NONE,
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
    MAX;

    public String encoded() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Exact current profile shape: no aliases or guessed provider defaults. */
    public static ModelReasoningEffort parse(String value) {
        for (ModelReasoningEffort effort : values()) {
            if (effort.encoded().equals(value)) return effort;
        }
        throw new IllegalArgumentException("reasoningEffort must be a supported effort name");
    }

    /** Protocol-level choices only. A specific model or gateway can reject an explicit value. */
    public static List<ModelReasoningEffort> choices(ModelProtocol protocol) {
        Objects.requireNonNull(protocol, "protocol");
        return switch (protocol) {
            case OPENAI_CHAT -> List.of(values());
            case ANTHROPIC_MESSAGES -> List.of(AUTO, LOW, MEDIUM, HIGH, XHIGH, MAX);
        };
    }

    public void requireSupported(ModelProtocol protocol) {
        if (!choices(protocol).contains(this)) {
            throw new IllegalArgumentException("reasoningEffort is not supported by this protocol");
        }
    }
}
