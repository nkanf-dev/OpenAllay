package dev.openallay.model.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.google.gson.Gson;
import dev.openallay.tool.ToolResult;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ModelConfigLoaderTest {
    private final ModelConfigLoader loader = new ModelConfigLoader();

    @Test
    void missingOrInvalidServerBootstrapConfigFailsWithoutEchoingCredentials(
            @TempDir Path directory) {
        ToolResult.Failure<ModelConfig> missing =
                failure(loader.load(directory.resolve("server-model.json"), Map.of()));
        assertEquals("model_not_configured", missing.code());

        String secret = "server-bootstrap-secret";
        ToolResult.Failure<ModelConfig> invalid = failure(loader.load(
                new StringReader("""
                        {"protocol":"openai_chat","baseUrl":"https://example.test/v1",
                         "model":"server/model","apiKey":"%s",
                         "contextWindowTokens":"invalid"}
                        """.formatted(secret)),
                Map.of()));
        assertEquals("invalid_model_config", invalid.code());
        assertFalse(invalid.message().contains(secret));
    }

    @Test
    void environmentOverridesFileAndSecretsNeverRender() {
        ToolResult.Success<ModelConfig> result = success(loader.load(
                new StringReader("""
                        {"protocol":"openai_chat","baseUrl":"https://example.test/v1",
                         "model":"old","apiKey":"file-secret","contextWindowTokens":128000,
                         "maxOutputTokens":1024}
                        """),
                Map.of(
                        "OPENALLAY_MODEL_PROTOCOL", "anthropic_messages",
                        "OPENALLAY_MODEL", "mimo-v2.5-pro",
                        "OPENALLAY_API_KEY", "environment-secret")));

        ModelConfig config = result.value();
        assertEquals(ModelProtocol.ANTHROPIC_MESSAGES, config.protocol());
        assertEquals("mimo-v2.5-pro", config.model());
        assertEquals("environment-secret", config.apiKey().reveal());
        assertFalse(config.toString().contains("environment-secret"));
        assertFalse(new Gson().toJson(config.diagnosticView()).contains("environment-secret"));
        assertEquals("https://example.test/v1/", config.baseUri().toString());
        assertEquals(128_000, config.contextWindowTokens());
        assertEquals(128_000 - 2 * 1024, config.contextBudget().inputTokens());
    }

    @Test
    void contextWindowCanBeOverriddenAndMustExceedDoubleOutputReserve() {
        ModelConfig overridden = success(loader.load(
                new StringReader("""
                        {"protocol":"anthropic_messages","baseUrl":"https://example.test/v1",
                         "model":"m","apiKey":"k","contextWindowTokens":64000,
                         "maxOutputTokens":4096}
                        """),
                Map.of("OPENALLAY_CONTEXT_WINDOW_TOKENS", "96000"))).value();
        assertEquals(96_000, overridden.contextWindowTokens());

        assertEquals("invalid_model_config", failure(loader.load(
                new StringReader("""
                        {"protocol":"anthropic_messages","baseUrl":"https://example.test/v1",
                         "model":"m","apiKey":"k","contextWindowTokens":8192,
                         "maxOutputTokens":4096}
                        """), Map.of())).code());

        ToolResult.Failure<ModelConfig> missing = failure(loader.load(
                new StringReader("""
                        {"protocol":"anthropic_messages","baseUrl":"https://example.test/v1",
                         "model":"m","apiKey":"k"}
                        """), Map.of()));
        assertEquals(
                "contextWindowTokens is required unless trusted model metadata resolves it",
                missing.message());
    }

    @Test
    void allowsLoopbackHttpButRejectsRemoteHttpAndUnknownFields() {
        assertInstanceOf(
                ToolResult.Success.class,
                loader.load(
                        new StringReader("""
                                {"protocol":"anthropic_messages","baseUrl":"http://127.0.0.1:8080/v1",
                                 "model":"local","apiKey":"test","contextWindowTokens":128000,
                                 "maxOutputTokens":4096}
                                """),
                        Map.of()));

        assertEquals(
                "invalid_model_config",
                failure(config("http://example.test/v1")).code());
        assertEquals(
                "invalid_model_config",
                failure(loader.load(
                                new StringReader("""
                                        {"protocol":"anthropic_messages","baseUrl":"https://example.test/v1",
                                         "model":"m","apiKey":"k","contextWindowTokens":128000,
                                         "maxOutputTokens":4096,"surprise":true}
                                        """),
                                Map.of()))
                        .code());
    }

    @Test
    void automaticOutputUsesPublishedMaximumAndNeverChangesExplicitContext() {
        String automatic = """
                {"protocol":"openai_chat","baseUrl":"https://example.test/v1",
                 "model":"gpt-6-luna","apiKey":"fixture-key","contextWindowTokens":1000000}
                """;
        ModelConfig config = success(loader.load(new StringReader(automatic), Map.of())).value();
        assertEquals(128_000, config.maxOutputTokens());
        assertEquals(1_000_000, config.contextWindowTokens());
        assertEquals(744_000, config.contextBudget().inputTokens());
        String nullable = automatic.replace("1000000}", "1000000,\"maxOutputTokens\":null}");
        assertEquals(128_000, success(loader.load(new StringReader(nullable), Map.of()))
                .value().maxOutputTokens());
        assertEquals("invalid_model_config", failure(loader.load(new StringReader(
                automatic.replace("1000000", "256000")), Map.of())).code());
        ToolResult.Failure<ModelConfig> unknown = failure(loader.load(new StringReader(
                automatic.replace("gpt-6-luna", "unpublished-model")), Map.of()));
        assertEquals("maxOutputTokens is required unless model metadata publishes its maximum",
                unknown.message());
    }

    @Test
    void explicitEnvironmentAndJsonOutputBudgetsWinAndInvalidNumbersFailStrictly() {
        String json = """
                {"protocol":"openai_chat","baseUrl":"https://example.test/v1",
                 "model":"gpt-6-luna","apiKey":"fixture-key","contextWindowTokens":1000000,
                 "maxOutputTokens":8192}
                """;
        for (int budget : java.util.List.of(4_096, 8_192, 128_000)) {
            assertEquals(budget, success(loader.load(new StringReader(json.replace("8192",
                    Integer.toString(budget))), Map.of())).value().maxOutputTokens());
            assertEquals(budget, success(loader.load(new StringReader(json), Map.of(
                    "OPENALLAY_MAX_OUTPUT_TOKENS", Integer.toString(budget))))
                    .value().maxOutputTokens());
        }
        for (String value : java.util.List.of("true", "1.5", "\"8192\"", "0", "-1")) {
            assertEquals("invalid_model_config", failure(loader.load(new StringReader(
                    json.replace("8192", value)), Map.of())).code());
        }
        for (String value : java.util.List.of("true", "1.5", "", "0", "-1")) {
            assertEquals("invalid_model_config", failure(loader.load(new StringReader(json),
                    Map.of("OPENALLAY_MAX_OUTPUT_TOKENS", value))).code());
        }
    }

    private ToolResult<ModelConfig> config(String url) {
        return loader.load(
                new StringReader(("""
                        {"protocol":"anthropic_messages","baseUrl":"%s",
                         "model":"m","apiKey":"k","contextWindowTokens":128000,
                         "maxOutputTokens":4096}
                        """).formatted(url)),
                Map.of());
    }

    @SuppressWarnings("unchecked")
    private static ToolResult.Success<ModelConfig> success(ToolResult<ModelConfig> result) {
        return (ToolResult.Success<ModelConfig>)
                assertInstanceOf(ToolResult.Success.class, result);
    }

    @SuppressWarnings("unchecked")
    private static ToolResult.Failure<ModelConfig> failure(ToolResult<ModelConfig> result) {
        return (ToolResult.Failure<ModelConfig>)
                assertInstanceOf(ToolResult.Failure.class, result);
    }
}
