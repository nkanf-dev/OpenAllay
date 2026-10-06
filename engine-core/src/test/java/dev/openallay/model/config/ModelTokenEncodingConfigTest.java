package dev.openallay.model.config;

import static org.junit.jupiter.api.Assertions.*;

import dev.openallay.client.gui.settings.ModelProfileDraft;
import dev.openallay.model.tokenizer.ModelContextTokenEstimator;
import dev.openallay.model.tokenizer.ModelTokenEncoding;
import dev.openallay.model.tokenizer.TokenizerMetadata;
import dev.openallay.tool.ToolResult;
import java.io.StringReader;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ModelTokenEncodingConfigTest {
    @Test
    void explicitStableEncodingRoundTripsWithoutChangingModelEndpointOrLimits() {
        var definition = definition(ModelTokenEncoding.O200K_BASE);
        var config = new ModelProfilesConfig("gateway", List.of(definition));
        String encoded = new ModelProfilesConfigWriter().encode(config);
        assertEquals("o200k_base", dev.openallay.json.JsonTrees.parse(encoded).getAsJsonObject()
                .getAsJsonArray("profiles").get(0).getAsJsonObject().get("tokenEncoding").getAsString());
        var loaded = new ModelProfilesConfigLoader().load(new StringReader(encoded), Map.of("KEY", "test-key"));
        var value = ((ToolResult.Success<ModelProfilesConfigLoader.Load>) loaded).value();
        assertEquals(definition, value.config().profiles().getFirst());
        var runtime = value.profiles().getFirst().runtimeConfig();
        assertNotNull(runtime);
        assertEquals(definition.model(), runtime.model());
        assertEquals(definition.baseUri(), runtime.baseUri());
        assertEquals(8_192, runtime.contextWindowTokens());
        assertEquals(1_024, runtime.maxOutputTokens());
        assertEquals(ModelTokenEncoding.O200K_BASE, runtime.tokenEncoding());
        var estimator = ModelContextTokenEstimator.create(runtime.protocol(), runtime.model(), runtime.tokenEncoding());
        assertEquals(TokenizerMetadata.Mode.EXPLICIT_ENCODING, estimator.metadata().mode());
    }

    @Test
    void omittedEncodingIsAutoAndUnsupportedEncodingsAreRejected() {
        String auto = new ModelProfilesConfigWriter().encode(
                new ModelProfilesConfig("gateway", List.of(definition(ModelTokenEncoding.AUTO))));
        assertFalse(auto.contains("tokenEncoding"));
        var loaded = new ModelProfilesConfigLoader().load(new StringReader(auto), Map.of("KEY", "test-key"));
        assertEquals(ModelTokenEncoding.AUTO, ((ToolResult.Success<ModelProfilesConfigLoader.Load>) loaded)
                .value().profiles().getFirst().runtimeConfig().tokenEncoding());
        var root = dev.openallay.json.JsonTrees.parse(auto).getAsJsonObject();
        root.getAsJsonArray("profiles").get(0).getAsJsonObject().addProperty("tokenEncoding", "claude-exact");
        assertInstanceOf(ToolResult.Failure.class, new ModelProfilesConfigLoader()
                .load(new StringReader(root.toString()), Map.of("KEY", "test-key")));
        assertThrows(IllegalArgumentException.class, () -> ModelTokenEncoding.parse(""));
    }

    @Test
    void draftPreservesSelectionAcrossUnrelatedEditsAndSave() {
        var draft = ModelProfileDraft.from(definition(ModelTokenEncoding.CL100K_BASE))
                .withReasoningEffort(ModelReasoningEffort.HIGH)
                .withContextWindow("8192").withMaxOutput("1024")
                .withModel("another-opaque-alias");
        assertEquals(ModelTokenEncoding.CL100K_BASE, draft.tokenEncoding());
        var saved = ((ToolResult.Success<ModelProfileDefinition>) draft.validate()).value();
        assertEquals(ModelTokenEncoding.CL100K_BASE, saved.tokenEncoding());
        assertEquals("another-opaque-alias", saved.model());
    }

    @Test
    void serverConfigSupportsTheSameStableSelectionWithoutEnvironmentGuessing() {
        String json = """
                {"enabled":true,"protocol":"anthropic_messages","baseUrl":"https://example.invalid/api/",
                 "model":"opaque-alias","apiKey":"test-key","contextWindowTokens":8192,
                 "maxOutputTokens":1024,"tokenEncoding":"cl100k_base"}
                """;
        var result = new ModelConfigLoader().load(new StringReader(json), Map.of());
        var config = ((ToolResult.Success<ModelConfig>) result).value();
        assertEquals(ModelTokenEncoding.CL100K_BASE, config.tokenEncoding());
        assertEquals("opaque-alias", config.model());
        assertEquals(URI.create("https://example.invalid/api/"), config.baseUri());
        assertEquals(ModelTokenEncoding.CL100K_BASE, config.diagnosticView().tokenEncoding());
    }

    private static ModelProfileDefinition definition(ModelTokenEncoding encoding) {
        return new ModelProfileDefinition("gateway", "Gateway", true, ModelProtocol.OPENAI_CHAT,
                URI.create("https://example.invalid/v1/"), "opaque-alias", "env:KEY", 8192, 1024,
                Duration.ofSeconds(10), Duration.ofSeconds(30), null, ModelReasoningEffort.AUTO, encoding);
    }
}
