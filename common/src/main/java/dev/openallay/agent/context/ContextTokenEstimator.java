package dev.openallay.agent.context;

import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelToolDefinition;
import java.util.List;
import dev.openallay.model.tokenizer.TokenizerMetadata;

public interface ContextTokenEstimator {
    /** Text and native framing only when images are present; see imageAccounting. */
    int estimate(
            String systemPrompt,
            List<ModelMessage> messages,
            List<ModelToolDefinition> tools);

    /** Counts plain text in the same units as this estimator's message budget. */
    default int estimateText(String text) {
        return estimate("", List.of(ModelMessage.userText(text)), List.of());
    }

    /** No image cost is inferred from byte size, dimensions or provider protocol. */
    default TokenizerMetadata.ImageAccounting imageAccounting(List<ModelMessage> messages) {
        return messages.stream().flatMap(message -> message.content().stream())
                        .anyMatch(ModelContent.Image.class::isInstance)
                ? TokenizerMetadata.ImageAccounting.UNKNOWN
                : TokenizerMetadata.ImageAccounting.TEXT_ONLY;
    }

    default TokenizerMetadata metadata() {
        return new TokenizerMetadata("custom", "", "custom", TokenizerMetadata.Mode.CUSTOM);
    }
}
