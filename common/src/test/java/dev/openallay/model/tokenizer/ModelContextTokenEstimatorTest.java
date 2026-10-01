package dev.openallay.model.tokenizer;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;
import dev.openallay.agent.context.ContextBudget;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.anthropic.AnthropicJsonCodec;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.openai.OpenAiJsonCodec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ModelContextTokenEstimatorTest {
    @Test
    void countsPublishedBpeVectorsRatherThanBytes() {
        // OpenAI cookbook's How_to_count_tokens_with_tiktoken vectors.
        var cl100k = estimator(ModelProtocol.OPENAI_CHAT, "alias", ModelTokenEncoding.CL100K_BASE);
        var o200k = estimator(ModelProtocol.OPENAI_CHAT, "alias", ModelTokenEncoding.O200K_BASE);
        assertEquals(6, cl100k.estimateText("antidisestablishmentarianism"));
        assertEquals(6, o200k.estimateText("antidisestablishmentarianism"));
        assertEquals(7, cl100k.estimateText("2 + 2 = 4"));
        assertEquals(7, o200k.estimateText("2 + 2 = 4"));
        assertEquals(9, cl100k.estimateText("お誕生日おめでとう"));
        assertEquals(8, o200k.estimateText("お誕生日おめでとう"));
        String repeated = "世界观测结果：石头、草方块、橡木。".repeat(10_000);
        assertTrue(o200k.estimateText(repeated) < repeated.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void currentKnownModelEncodingDoesNotUseTheLibrariesOldGpt4Prefix() {
        for (String model : List.of("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini",
                "gpt-4.5-preview", "gpt-5", "gpt-5.2", "o1", "o3", "o4-mini", "openai/gpt-5")) {
            var tokenizer = estimator(ModelProtocol.OPENAI_CHAT, model, ModelTokenEncoding.AUTO);
            assertEquals("o200k_base", tokenizer.metadata().encoding(), model);
            assertEquals(TokenizerMetadata.Mode.MODEL_MAPPING, tokenizer.metadata().mode(), model);
        }
        for (String model : List.of("gpt-4", "gpt-4-turbo", "gpt-3.5-turbo-0125")) {
            assertEquals("cl100k_base", estimator(ModelProtocol.OPENAI_CHAT, model,
                    ModelTokenEncoding.AUTO).metadata().encoding(), model);
        }
    }

    @Test
    void unknownModelsAndClaudeNeverClaimAnExactTokenizerOrAddAnInventedMargin() {
        String text = "Chinese 中文, emoji 🧱, JSON {\"n\":12345}, <|endoftext|>";
        var registry = Encodings.newLazyEncodingRegistry();
        int expected = Math.max(registry.getEncoding(EncodingType.CL100K_BASE).countTokensOrdinary(text),
                registry.getEncoding(EncodingType.O200K_BASE).countTokensOrdinary(text));
        for (var protocol : ModelProtocol.values()) {
            for (String model : List.of("opaque-gateway-model", "claude-sonnet-4-5", "gpt-oss-120b")) {
                var fallback = estimator(protocol, model, ModelTokenEncoding.AUTO);
                assertEquals(expected, fallback.estimateText(text));
                assertEquals(TokenizerMetadata.Mode.CONSERVATIVE_SURROGATE, fallback.metadata().mode());
            }
        }
        var anthropic = estimator(ModelProtocol.ANTHROPIC_MESSAGES, "gpt-5", ModelTokenEncoding.AUTO);
        assertEquals(TokenizerMetadata.Mode.CONSERVATIVE_SURROGATE, anthropic.metadata().mode());
        var explicit = estimator(ModelProtocol.ANTHROPIC_MESSAGES, "opaque", ModelTokenEncoding.O200K_BASE);
        assertEquals(TokenizerMetadata.Mode.EXPLICIT_ENCODING, explicit.metadata().mode());
        assertEquals("o200k_base", explicit.metadata().encoding());
    }

    @Test
    void includesTheActualNativeCodecJsonForReasoningToolIdsArgumentsResultsAndSchemas() {
        var gson = new Gson();
        var arguments = new JsonObject();
        arguments.addProperty("resource", "农夫乐事:苹果酒");
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.addProperty("description", "嵌套结构和所有工具定义都必须计数");
        var messages = List.of(
                ModelMessage.userText("查询"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.Text("先查询"), new ModelContent.Reasoning("推理", "signature"),
                        new ModelContent.ToolUse("call:one", "lookup", arguments))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult(
                        "call:one", new JsonPrimitive("结果\n\"quoted\""), false))));
        var tools = List.of(new ModelToolDefinition("lookup", "读取精确配方", schema));
        for (ModelProtocol protocol : ModelProtocol.values()) {
            var tokenizer = estimator(protocol, "opaque", ModelTokenEncoding.O200K_BASE);
            var nativeInput = protocol == ModelProtocol.OPENAI_CHAT
                    ? new OpenAiJsonCodec(gson).contextInput("系统", messages, tools)
                    : new AnthropicJsonCodec(gson).contextInput("系统", messages, tools);
            var config = new dev.openallay.model.config.ModelConfig(true, protocol,
                    java.net.URI.create("https://example.invalid/api/"), "opaque",
                    dev.openallay.model.config.SecretValue.of("test-key"), 8192, 1024,
                    java.time.Duration.ofSeconds(10), java.time.Duration.ofSeconds(30));
            var request = new dev.openallay.model.ModelRequest("系统", messages, tools, true);
            String body = protocol == ModelProtocol.OPENAI_CHAT
                    ? new OpenAiJsonCodec(gson).requestBody(config, request)
                    : new AnthropicJsonCodec(gson).requestBody(config, request);
            var actualInput = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            actualInput.remove("model");
            actualInput.remove("stream");
            actualInput.remove(protocol == ModelProtocol.OPENAI_CHAT ? "max_completion_tokens" : "max_tokens");
            assertEquals(actualInput, nativeInput);
            assertEquals(tokenizer.estimateText(gson.toJson(nativeInput)),
                    tokenizer.estimate("系统", messages, tools));
            int counted = tokenizer.estimate("系统", messages, tools);
            arguments.addProperty("ignored_after_copy", "x".repeat(10_000));
            schema.addProperty("ignored_after_copy", "y".repeat(10_000));
            assertEquals(counted, tokenizer.estimate("系统", messages, tools));
        }
    }

    @Test
    void chineseEmojiAndNestedJsonUseThePublishedEncodingWithoutByteMath() {
        String text = "中文：附近🧱、🌳和水。\n{\"layers\":[{\"y\":64,\"blocks\":[\"minecraft:stone\"]}]}";
        var registry = Encodings.newLazyEncodingRegistry();
        for (var selected : List.of(ModelTokenEncoding.CL100K_BASE, ModelTokenEncoding.O200K_BASE)) {
            var type = selected == ModelTokenEncoding.CL100K_BASE
                    ? EncodingType.CL100K_BASE : EncodingType.O200K_BASE;
            int expected = registry.getEncoding(type).encodeOrdinary(text).size();
            assertEquals(expected, estimator(ModelProtocol.OPENAI_CHAT, "opaque", selected).estimateText(text));
        }
    }

    @Test
    void ordinaryContentCannotInvokeSpecialTokens() {
        var tokenizer = estimator(ModelProtocol.OPENAI_CHAT, "gpt-4o", ModelTokenEncoding.AUTO);
        assertTrue(tokenizer.estimateText("<|endoftext|> <|im_start|>") > 0);
        assertEquals(0, tokenizer.estimateText(""));
        assertThrows(NullPointerException.class, () -> tokenizer.estimateText(null));
        assertEquals("1.1.0", tokenizer.metadata().backendVersion());
    }

    @Test
    void retainsRealBudgetUnitsAndOutputReservations() {
        var budget = new ContextBudget(128_000, 8_192);
        assertEquals(111_616, budget.inputTokens());
        assertEquals(16_384, budget.reservedTokens());
    }

    private static ModelContextTokenEstimator estimator(
            ModelProtocol protocol, String model, ModelTokenEncoding encoding) {
        return ModelContextTokenEstimator.create(protocol, model, encoding);
    }
}
