package dev.openallay.model.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import dev.openallay.model.CancellationSignal;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpTransportPolicy;
import dev.openallay.net.JdkHttpTransport;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ModelTransportDiagnosticsTest {
    @Test
    void capturesOnlySanitizedLoopbackDecoderFailureMetadata() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/private-endpoint", exchange -> {
            byte[] response = "private-response-body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/private-endpoint");
            HttpExchangeRequest request = HttpExchangeRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(2))
                    .header("Authorization", "Bearer private-token")
                    .get().build();
            AtomicInteger status = new AtomicInteger(-1);
            Throwable failure;
            try {
                new JdkHttpTransport(new HttpTransportPolicy(Duration.ofSeconds(2), "diagnostic-test"))
                        .execute(request, new CancellationSignal(), (received, headers, body) -> {
                            status.set(received);
                            body.readAllBytes();
                            throw new IllegalArgumentException(
                                    "private-token private-response-body " + endpoint);
                        }).join();
                throw new AssertionError("decoder did not fail");
            } catch (CompletionException exception) {
                failure = exception.getCause();
            }
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            try (PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
                output.println(ModelTransportDiagnostics.failureSummary(status.get(), failure));
            }
            String summary = captured.toString(StandardCharsets.UTF_8);
            assertTrue(summary.contains("receivedStatus=200"));
            assertTrue(summary.contains("java.lang.IllegalArgumentException"));
            assertFalse(summary.contains("private-token"));
            assertFalse(summary.contains("private-response-body"));
            assertFalse(summary.contains("private-endpoint"));
            assertFalse(summary.contains(endpoint.toString()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsKnownExceptionClassesAndFramesButNeverMessagesOrUnrelatedFrames() {
        RuntimeException failure = new IllegalArgumentException(
                "secret-token https://private.invalid/path response-body",
                new java.io.IOException("Authorization: Bearer secret-token"));
        failure.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("private.generated.secret-token", "response-body", "private", 1),
                new StackTraceElement("dev.openallay.model.openai.OpenAiStreamAccumulator",
                        "acceptChunk", "OpenAiStreamAccumulator.java", 42)});
        String summary = ModelTransportDiagnostics.failureSummary(200, failure);

        assertTrue(summary.contains("phase=response receivedStatus=200"));
        assertTrue(summary.contains("java.lang.IllegalArgumentException,java.io.IOException"));
        assertTrue(summary.contains("OpenAiStreamAccumulator.acceptChunk:42"));
        assertFalse(summary.contains("secret-token"));
        assertFalse(summary.contains("private.invalid"));
        assertFalse(summary.contains("response-body"));
        assertFalse(summary.contains("Authorization"));
    }

    @Test
    void fixedJsonShapePathsNeverRenderProviderStringValuesOrArbitraryKeys() {
        String json = """
                {"model":"secret-token", "private-endpoint":"https://private.invalid/path",
                 "choices":[{"finish_reason":"private-stop", "delta":{"content":"response-body",
                  "reasoning_content":"private-reasoning", "tool_calls":[{"index":0,
                   "id":"private-id", "type":"function", "function":{"name":"private-tool",
                    "arguments":"private-arguments"}}]}}],
                 "usage":{"prompt_tokens":7,"completion_tokens":2,
                  "prompt_tokens_details":{"cached_tokens":3}}, "error":"private-error"}
                """;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            output.println(ModelTransportDiagnostics.openAiShape(json, true));
        }
        String shape = captured.toString(StandardCharsets.UTF_8);

        assertTrue(shape.contains("rootType=object"));
        assertTrue(shape.contains("content:string,reasoning_content:string,tool_calls:array"));
        assertTrue(shape.contains("functionTypes=name:string,arguments:string"));
        assertFalse(shape.contains("secret-token"));
        assertFalse(shape.contains("private-"));
        assertFalse(shape.contains("private.invalid"));
        assertFalse(shape.contains("response-body"));
    }

    @Test
    void differentiatesTransportAndInvalidJsonWithoutRenderingFailureText() {
        String summary = ModelTransportDiagnostics.failureSummary(-1,
                new java.net.ConnectException("private-endpoint secret-token"));
        assertTrue(summary.contains("phase=transport receivedStatus=-1"));
        assertFalse(summary.contains("secret-token"));
        assertTrue(ModelTransportDiagnostics.openAiShape("invalid secret-token", true)
                .equals("rootType=invalid-json"));
    }
}
