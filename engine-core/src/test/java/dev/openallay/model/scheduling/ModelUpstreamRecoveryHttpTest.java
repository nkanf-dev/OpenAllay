package dev.openallay.model.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelRole;
import dev.openallay.model.ModelToolDefinition;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.anthropic.AnthropicMessagesClient;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.openai.OpenAiChatClient;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Local-only fixtures exercising the actual JDK transport and both provider adapters. */
final class ModelUpstreamRecoveryHttpTest {
    @Test
    void recoversEachGatewayStatusWithoutReplayingCompletedToolHistory() throws Exception {
        for (ModelProtocol protocol : ModelProtocol.values()) {
            for (int status : List.of(502, 503, 504)) {
                List<String> bodies = new CopyOnWriteArrayList<>();
                AtomicInteger calls = new AtomicInteger();
                try (Server server = new Server(exchange -> {
                    bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    if (calls.incrementAndGet() == 1) {
                        exchange.getResponseHeaders().add("retry-after", "0");
                        respond(exchange, status, "text/html", "private gateway error");
                    } else {
                        respond(exchange, 200, "application/json", success(protocol));
                    }
                })) {
                    List<ModelEvent> events = new CopyOnWriteArrayList<>();
                    ModelRequestScheduler scheduler = new ModelRequestScheduler(
                            client(protocol, server.uri(), Duration.ofSeconds(5)), Duration.ofMillis(1), 2);
                    ModelTurn result = scheduler.complete(continuation(false), events::add, new CancellationSignal())
                            .get(5, TimeUnit.SECONDS);
                    assertEquals("done", result.text());
                    assertEquals(2, calls.get());
                    assertEquals(bodies.getFirst(), bodies.getLast());
                    assertTrue(bodies.getFirst().contains("completed_operation"));
                    assertEquals(1, events.stream().filter(ModelEvent.ResponseStarted.class::isInstance).count());
                    assertEquals(1, events.stream().filter(ModelEvent.TextDelta.class::isInstance).count());
                    assertFalse(events.stream().anyMatch(ModelEvent.ToolUseComplete.class::isInstance));
                    assertFalse(events.stream().anyMatch(ModelEvent.RateLimited.class::isInstance));
                }
            }
        }
    }

    @Test
    void repeatedGatewayFailureIsStillARealFailureWithItsStatus() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (Server server = new Server(exchange -> {
            exchange.getRequestBody().readAllBytes();
            calls.incrementAndGet();
            respond(exchange, 502, "text/html", "private upstream body");
        })) {
            List<ModelEvent> events = new CopyOnWriteArrayList<>();
            ModelRequestScheduler scheduler = new ModelRequestScheduler(
                    client(ModelProtocol.OPENAI_CHAT, server.uri(), Duration.ofSeconds(5)), Duration.ofMillis(1), 2);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> scheduler.complete(continuation(false), events::add, new CancellationSignal())
                            .get(5, TimeUnit.SECONDS));
            ModelClientException modelFailure = assertInstanceOf(ModelClientException.class, failure.getCause());
            assertEquals("model_upstream_error", modelFailure.failure().code());
            assertEquals(502, modelFailure.failure().httpStatus());
            assertFalse(modelFailure.toString().contains("private upstream body"));
            assertEquals(3, calls.get());
            assertFalse(events.stream().anyMatch(ModelEvent.ResponseStarted.class::isInstance));
        }
    }

    @Test
    void rejected400WithAnIncompleteErrorBodyDoesNotBecomeATransportRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger observedStatus = new AtomicInteger(-1);
        CountDownLatch decoding = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean bodyReadFailed = new java.util.concurrent.atomic.AtomicBoolean();
        try (Server server = new Server(exchange -> {
            exchange.getRequestBody().readAllBytes();
            calls.incrementAndGet();
            exchange.sendResponseHeaders(400, 200);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try {
                // A truncated connection before HttpResponse exists is a transport failure.
                // Establish the known-400 boundary before cutting this response body short.
                decoding.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        })) {
            ModelConfig config = new ModelConfig(true, ModelProtocol.OPENAI_CHAT, server.uri(),
                    "fixture-model", SecretValue.of("local-test-key"), 128_000, 512,
                    Duration.ofSeconds(2), Duration.ofSeconds(2));
            var transport = new dev.openallay.model.http.HttpModelTransport(config);
            ModelClient client = (request, events, cancellation) -> transport.execute(
                    dev.openallay.net.HttpExchangeRequest.newBuilder(server.uri().resolve("chat/completions"))
                            .timeout(Duration.ofSeconds(2)).postJson("{}").build(),
                    cancellation, events, (status, headers, body, safeEvents) -> {
                        observedStatus.set(status);
                        decoding.countDown();
                        try {
                            dev.openallay.model.http.ModelHttpErrors.requireSuccess(status, headers, body);
                        } catch (IOException truncatedBody) {
                            bodyReadFailed.set(true);
                            throw truncatedBody;
                        }
                        throw new AssertionError("The known HTTP 400 must fail");
                    });
            ModelRequestScheduler scheduler = new ModelRequestScheduler(client, Duration.ofMillis(1), 2);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> scheduler.complete(continuation(false), event -> {}, new CancellationSignal())
                            .get(5, TimeUnit.SECONDS));
            ModelClientException modelFailure = assertInstanceOf(ModelClientException.class, failure.getCause());
            assertEquals(400, observedStatus.get());
            assertTrue(bodyReadFailed.get());
            assertEquals("model_http_error", modelFailure.failure().code());
            assertEquals(400, modelFailure.failure().httpStatus());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void successfulHeadersAndPartialStreamTimeoutNeverReplayVisibleText() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        try (Server server = new Server(exchange -> {
            exchange.getRequestBody().readAllBytes();
            calls.incrementAndGet();
            exchange.getResponseHeaders().add("content-type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(("data: {\"model\":\"fixture-model\",\"choices\":[{"
                    + "\"delta\":{\"content\":\"partial\"}}]}\n\n")
                    .getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        })) {
            List<ModelEvent> events = new CopyOnWriteArrayList<>();
            ModelRequestScheduler scheduler = new ModelRequestScheduler(
                    client(ModelProtocol.OPENAI_CHAT, server.uri(), Duration.ofMillis(300)), Duration.ofMillis(1), 2);
            try {
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> scheduler.complete(continuation(true), events::add, new CancellationSignal())
                                .get(5, TimeUnit.SECONDS));
                assertEquals("model_timeout",
                        assertInstanceOf(ModelClientException.class, failure.getCause()).failure().code());
                assertEquals(1, calls.get());
                assertEquals(1, events.stream().filter(ModelEvent.TextDelta.class::isInstance).count());
                assertTrue(events.stream().anyMatch(ModelEvent.ResponseStarted.class::isInstance));
            } finally {
                release.countDown();
            }
        }
    }

    private static ModelClient client(ModelProtocol protocol, URI uri, Duration timeout) {
        ModelConfig config = new ModelConfig(true, protocol, uri, "fixture-model",
                SecretValue.of("local-test-key"), 128_000, 512, Duration.ofSeconds(2), timeout);
        return protocol == ModelProtocol.OPENAI_CHAT
                ? new OpenAiChatClient(config, new Gson())
                : new AnthropicMessagesClient(config, new Gson());
    }

    private static ModelRequest continuation(boolean stream) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject completed = new JsonObject();
        completed.addProperty("operation", "completed_operation");
        return new ModelRequest("Report completed work", List.of(
                ModelMessage.userText("Do work"),
                new ModelMessage(ModelRole.ASSISTANT, List.of(
                        new ModelContent.ToolUse("call_done", "test_tool", new JsonObject()))),
                new ModelMessage(ModelRole.USER, List.of(new ModelContent.ToolResult("call_done", completed, false)))),
                List.of(new ModelToolDefinition("test_tool", "Test tool", schema)), stream, "same-round");
    }

    private static String success(ModelProtocol protocol) {
        return protocol == ModelProtocol.OPENAI_CHAT
                ? """
                  {"model":"fixture-model","choices":[{"finish_reason":"stop",
                   "message":{"role":"assistant","content":"done"}}],
                   "usage":{"prompt_tokens":10,"completion_tokens":1}}
                  """
                : """
                  {"model":"fixture-model","stop_reason":"end_turn",
                   "content":[{"type":"text","text":"done"}],
                   "usage":{"input_tokens":10,"output_tokens":1}}
                  """;
    }

    private static void respond(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", type);
        exchange.sendResponseHeaders(status, encoded.length);
        exchange.getResponseBody().write(encoded);
        exchange.close();
    }

    private static final class Server implements AutoCloseable {
        private final HttpServer server;

        private Server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/", handler);
            server.start();
        }

        private URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
