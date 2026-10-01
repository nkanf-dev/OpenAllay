package dev.openallay.client.gui.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.ModelReasoningEffort;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ModelReasoningSettingsProjectionTest {
    @Test
    void exposesFullProtocolChoicesWithoutInventingModelSupport() {
        var openAi = ModelReasoningSettingsProjection.from(
                ModelProtocol.OPENAI_CHAT, ModelReasoningEffort.AUTO);
        assertEquals(List.of(ModelReasoningEffort.values()), openAi.choices());
        assertEquals("reasoning_effort", openAi.wireField());
        assertEquals(ModelReasoningEffort.NONE, openAi.next());
        assertTrue(openAi.explanationKey().endsWith("auto_description"));
        assertTrue(openAi.selectedLabelKey().endsWith(".auto"));
        var anthropic = ModelReasoningSettingsProjection.from(
                ModelProtocol.ANTHROPIC_MESSAGES, ModelReasoningEffort.AUTO);
        assertEquals(List.of(ModelReasoningEffort.AUTO, ModelReasoningEffort.LOW,
                ModelReasoningEffort.MEDIUM, ModelReasoningEffort.HIGH,
                ModelReasoningEffort.XHIGH, ModelReasoningEffort.MAX), anthropic.choices());
        assertEquals("output_config.effort", anthropic.wireField());
        assertEquals(ModelReasoningEffort.LOW, anthropic.next());
        assertFalse(anthropic.choices().contains(ModelReasoningEffort.NONE));
    }

    @Test
    void selectedExplicitValueIsNeverSilentlyResetWhenProtocolChanges() {
        var unsupported = ModelReasoningSettingsProjection.from(
                ModelProtocol.ANTHROPIC_MESSAGES, ModelReasoningEffort.MINIMAL);
        assertEquals(ModelReasoningEffort.MINIMAL, unsupported.selected());
        assertTrue(unsupported.explanationKey().endsWith("unsupported_description"));
        assertEquals(ModelReasoningEffort.AUTO, unsupported.next());
        for (ModelProtocol protocol : ModelProtocol.values()) {
            var explicit = ModelReasoningSettingsProjection.from(protocol, ModelReasoningEffort.HIGH);
            assertTrue(explicit.explanationKey().endsWith("explicit_description"));
            var maximum = ModelReasoningSettingsProjection.from(protocol, ModelReasoningEffort.MAX);
            assertEquals(ModelReasoningEffort.AUTO, maximum.next());
        }
    }
}
