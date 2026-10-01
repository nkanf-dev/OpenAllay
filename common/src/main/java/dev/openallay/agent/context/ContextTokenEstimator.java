package dev.openallay.agent.context;

import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelToolDefinition;
import java.util.List;
import dev.openallay.model.tokenizer.TokenizerMetadata;

public interface ContextTokenEstimator {
    int estimate(
            String systemPrompt,
            List<ModelMessage> messages,
            List<ModelToolDefinition> tools);

    /** Counts plain text in the same units as this estimator's message budget. */
    default int estimateText(String text) {
        return estimate("", List.of(ModelMessage.userText(text)), List.of());
    }

    default TokenizerMetadata metadata() {
        return new TokenizerMetadata("custom", "", "custom", TokenizerMetadata.Mode.CUSTOM);
    }
}
