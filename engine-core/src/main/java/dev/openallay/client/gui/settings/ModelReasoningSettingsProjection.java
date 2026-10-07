package dev.openallay.client.gui.settings;

import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.ModelReasoningEffort;
import java.util.List;
import java.util.Objects;

/** Credential-free request-setting display. Never claims to know provider runtime defaults. */
@dev.openallay.value.ValueType(ModelReasoningSettingsProjection.ValueSchemaProvider.class)
public final class ModelReasoningSettingsProjection {
    private final ModelReasoningEffort selected;
    private final List<ModelReasoningEffort> choices;
    private final String wireField;
    private final String explanationKey;
    public ModelReasoningSettingsProjection(ModelReasoningEffort selected, List<ModelReasoningEffort> choices, String wireField, String explanationKey) {

        Objects.requireNonNull(selected, "selected");
        choices = dev.openallay.util.Java8Collections.listCopyOf(choices);
        Objects.requireNonNull(wireField, "wireField");
        Objects.requireNonNull(explanationKey, "explanationKey");

        this.selected = selected;
        this.choices = choices;
        this.wireField = wireField;
        this.explanationKey = explanationKey;
    }
    public ModelReasoningEffort selected() { return selected; }
    public List<ModelReasoningEffort> choices() { return choices; }
    public String wireField() { return wireField; }
    public String explanationKey() { return explanationKey; }
public static ModelReasoningSettingsProjection from(
            ModelProtocol protocol, ModelReasoningEffort effort) {
        List<ModelReasoningEffort> choices = ModelReasoningEffort.choices(protocol);
        java.lang.String $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((protocol)) {
case OPENAI_CHAT:
{
$oaSwitch0_exit_result = "reasoning_effort"; break $oaSwitch0_exit;
}
case ANTHROPIC_MESSAGES:
{
$oaSwitch0_exit_result = "output_config.effort"; break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
String field = $oaSwitch0_exit_result;
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
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModelReasoningSettingsProjection)) return false;
        ModelReasoningSettingsProjection that = (ModelReasoningSettingsProjection) other;
        return java.util.Objects.equals(selected, that.selected) && java.util.Objects.equals(choices, that.choices) && java.util.Objects.equals(wireField, that.wireField) && java.util.Objects.equals(explanationKey, that.explanationKey);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(selected);
        hash = 31 * hash + java.util.Objects.hashCode(choices);
        hash = 31 * hash + java.util.Objects.hashCode(wireField);
        hash = 31 * hash + java.util.Objects.hashCode(explanationKey);
        return hash;
    }
    @Override public String toString() { return "ModelReasoningSettingsProjection[selected=" + selected + ", choices=" + choices + ", wireField=" + wireField + ", explanationKey=" + explanationKey + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ModelReasoningSettingsProjection> schema() {
            return new dev.openallay.value.ValueSchema<>(ModelReasoningSettingsProjection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ModelReasoningSettingsProjection>>asList(new dev.openallay.value.ValueSchema.Component<>(ModelReasoningSettingsProjection.class, "selected", ModelReasoningSettingsProjection::selected), new dev.openallay.value.ValueSchema.Component<>(ModelReasoningSettingsProjection.class, "choices", ModelReasoningSettingsProjection::choices), new dev.openallay.value.ValueSchema.Component<>(ModelReasoningSettingsProjection.class, "wireField", ModelReasoningSettingsProjection::wireField), new dev.openallay.value.ValueSchema.Component<>(ModelReasoningSettingsProjection.class, "explanationKey", ModelReasoningSettingsProjection::explanationKey)), arguments -> new ModelReasoningSettingsProjection((ModelReasoningEffort) arguments[0], (List) arguments[1], (String) arguments[2], (String) arguments[3]));
        }
    }
}
