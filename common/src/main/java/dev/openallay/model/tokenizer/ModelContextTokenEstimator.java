package dev.openallay.model.tokenizer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import dev.openallay.agent.context.ContextTokenEstimator;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.anthropic.AnthropicJsonCodec;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.openai.OpenAiJsonCodec;
import java.util.List;
import java.util.Objects;

/**
 * Offline JTokkit BPE accounting. Plain text counts are exact for the selected
 * encoding; provider input JSON is a conservative framing estimate, not an API
 * token-count result. Unknown models use the larger supported BPE count. That
 * surrogate is not a proven upper bound for an unpublished tokenizer.
 */
public final class ModelContextTokenEstimator implements ContextTokenEstimator {
    private static final EncodingRegistry ENCODINGS = Encodings.newLazyEncodingRegistry();
    private static final ModelContextTokenEstimator CONSERVATIVE = create(
            ModelProtocol.OPENAI_CHAT, "", ModelTokenEncoding.AUTO);
    private final ModelProtocol protocol;
    private final Encoding encoding;
    private final Encoding alternative;
    private final TokenizerMetadata metadata;
    private final Gson gson = new Gson();
    private final OpenAiJsonCodec openAi = new OpenAiJsonCodec(gson);
    private final AnthropicJsonCodec anthropic = new AnthropicJsonCodec(gson);

    private ModelContextTokenEstimator(
            ModelProtocol protocol, Encoding encoding, Encoding alternative, TokenizerMetadata metadata) {
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.encoding = encoding;
        this.alternative = alternative;
        this.metadata = metadata;
    }

    public static ModelContextTokenEstimator conservative() {
        return CONSERVATIVE;
    }

    public static ModelContextTokenEstimator create(
            ModelProtocol protocol, String canonicalModelId, ModelTokenEncoding selection) {
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(canonicalModelId, "canonicalModelId");
        Objects.requireNonNull(selection, "selection");
        ModelTokenEncoding resolved = selection;
        TokenizerMetadata.Mode mode = TokenizerMetadata.Mode.EXPLICIT_ENCODING;
        if (selection == ModelTokenEncoding.AUTO) {
            resolved = protocol == ModelProtocol.OPENAI_CHAT
                    ? knownOpenAiEncoding(canonicalModelId) : ModelTokenEncoding.AUTO;
            mode = resolved == ModelTokenEncoding.AUTO
                    ? TokenizerMetadata.Mode.CONSERVATIVE_SURROGATE
                    : TokenizerMetadata.Mode.MODEL_MAPPING;
        }
        if (resolved == ModelTokenEncoding.AUTO) {
            return new ModelContextTokenEstimator(protocol,
                    ENCODINGS.getEncoding(EncodingType.CL100K_BASE),
                    ENCODINGS.getEncoding(EncodingType.O200K_BASE),
                    new TokenizerMetadata("JTokkit", "1.1.0", "max(cl100k_base,o200k_base)", mode));
        }
        EncodingType type = resolved == ModelTokenEncoding.CL100K_BASE
                ? EncodingType.CL100K_BASE : EncodingType.O200K_BASE;
        return new ModelContextTokenEstimator(protocol, ENCODINGS.getEncoding(type), null,
                new TokenizerMetadata("JTokkit", "1.1.0", resolved.encoded(), mode));
    }

    @Override
    public int estimate(String systemPrompt, List<ModelMessage> messages, List<ModelToolDefinition> tools) {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        List<ModelMessage> detachedMessages = List.copyOf(messages);
        List<ModelToolDefinition> detachedTools = List.copyOf(tools);
        JsonObject input = switch (protocol) {
            case OPENAI_CHAT -> openAi.contextInput(systemPrompt, detachedMessages, detachedTools);
            case ANTHROPIC_MESSAGES -> anthropic.contextInput(systemPrompt, detachedMessages, detachedTools);
        };
        // Includes role/content fields, tool-call IDs/arguments, tool-result JSON,
        // reasoning and the full tool schemas using the same projection as HTTP.
        return estimateText(gson.toJson(input));
    }

    @Override
    public int estimateText(String text) {
        Objects.requireNonNull(text, "text");
        int tokens = encoding.countTokensOrdinary(text);
        return alternative == null ? tokens : Math.max(tokens, alternative.countTokensOrdinary(text));
    }

    @Override
    public TokenizerMetadata metadata() {
        return metadata;
    }

    /**
     * Published external model mapping, not a context-window catalog.
     * https://github.com/openai/tiktoken/blob/main/tiktoken/model.py
     * JTokkit 1.1.0's broad gpt-4 prefix misclassifies 4.1/4.5, so do not use it.
     */
    static ModelTokenEncoding knownOpenAiEncoding(String model) {
        // Provider-qualified catalog IDs are accepted; arbitrary aliases stay unknown.
        String name = model.startsWith("openai/") ? model.substring("openai/".length()) : model;
        if (name.equals("o1") || name.startsWith("o1-")
                || name.equals("o3") || name.startsWith("o3-")
                || name.equals("o4-mini") || name.startsWith("o4-mini-")
                || name.startsWith("gpt-5")
                || name.equals("gpt-4.1") || name.startsWith("gpt-4.1-")
                || name.startsWith("gpt-4.5-")
                || name.equals("gpt-4o") || name.startsWith("gpt-4o-")
                || name.startsWith("chatgpt-4o-") || name.startsWith("ft:gpt-4o")) {
            return ModelTokenEncoding.O200K_BASE;
        }
        if (name.equals("gpt-4") || name.startsWith("gpt-4-")
                || name.equals("gpt-3.5") || name.equals("gpt-3.5-turbo")
                || name.startsWith("gpt-3.5-turbo-")
                || name.equals("gpt-35-turbo") || name.startsWith("gpt-35-turbo-")
                || name.startsWith("ft:gpt-4") || name.startsWith("ft:gpt-3.5-turbo")
                || name.equals("text-embedding-ada-002") || name.equals("text-embedding-3-small")
                || name.equals("text-embedding-3-large")
                || name.equals("davinci-002") || name.equals("babbage-002")) {
            return ModelTokenEncoding.CL100K_BASE;
        }
        // gpt-oss uses o200k_harmony, which this published component does not support.
        return ModelTokenEncoding.AUTO;
    }
}
