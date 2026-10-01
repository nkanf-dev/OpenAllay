package dev.openallay.client.gui.settings;

import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.ModelReasoningEffort;
import java.util.List;
import java.util.Objects;

/** Credential-free request-setting display. Never claims to know provider runtime defaults. */
public record ModelReasoningSettingsProjection(
        ModelReasoningEffort selected,
        List<ModelReasoningEffort> choices,
        String wireField,
        String explanationKey) {
    public ModelReasoningSettingsProjection {
        Objects.requireNonNull(selected, "selected");
        choices = List.copyOf(choices);
        Objects.requireNonNull(wireField, "wireField");
        Objects.requireNonNull(explanationKey, "explanationKey");
    }

    public static ModelReasoningSettingsProjection from(
            ModelProtocol protocol, ModelReasoningEffort effort) {
        List<ModelReasoningEffort> choices = ModelReasoningEffort.choices(protocol);
        String field = switch (protocol) {
            case OPENAI_CHAT -> "reasoning_effort";
            case ANTHROPIC_MESSAGES -> "output_config.effort";
        };
        String explanation = effort == ModelReasoningEffort.AUTO
                ? "screen.openallay.settings.models.reasoning.auto_description"
                : choices.contains(effort)
                        ? "screen.openallay.settings.models.reasoning.explicit_description"
                        : "screen.openallay.settings.models.reasoning.unsupported_description";
        return new ModelReasoningSettingsProjection(effort, choices, field, explanation);
    }

    public String selectedLabelKey() {
        return "screen.openallay.settings.models.reasoning." + selected.encoded();
    }

    public ModelReasoningEffort next() {
        return choices.get((choices.indexOf(selected) + 1) % choices.size());
    }
}
