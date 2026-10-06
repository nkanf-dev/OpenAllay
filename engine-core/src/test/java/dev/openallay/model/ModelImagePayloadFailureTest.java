package dev.openallay.model;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import dev.openallay.model.anthropic.AnthropicMessagesClient;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.image.ImagePayloadResolver;
import dev.openallay.model.image.ImageReference;
import dev.openallay.model.openai.OpenAiChatClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ModelImagePayloadFailureTest {
    @Test
    void missingAndUnauthorizedAssetsFinishFutureWithoutHttpOrPendingState() throws Exception {
        var networkRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", exchange -> {
            networkRequests.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
            var image = new ImageReference("a".repeat(64), "image/png", 32, 24, 100);
            var message = ModelMessage.userInput(null, List.of(image));
            for (ModelProtocol protocol : ModelProtocol.values()) {
                ModelConfig config = new ModelConfig(true, protocol, baseUri, "compatible-model",
                        SecretValue.of("test-secret"), 128_000, 1024,
                        Duration.ofSeconds(5), Duration.ofSeconds(10));
                ModelClient client = protocol == ModelProtocol.OPENAI_CHAT
                        ? new OpenAiChatClient(config, dev.openallay.json.EngineJson.create())
                        : new AnthropicMessagesClient(config, dev.openallay.json.EngineJson.create());
                for (boolean stream : List.of(false, true)) {
                    for (ImagePayloadResolver resolver : List.of(ImagePayloadResolver.unavailable(),
                            (ImagePayloadResolver) reference -> { throw new IOException("wrong player"); })) {
                        var events = new ArrayList<ModelEvent>();
                        var pending = new AtomicBoolean(true);
                        var request = new ModelRequest("System", List.of(message), List.of(), stream,
                                "session", null, resolver);
                        var future = assertDoesNotThrow(() -> client.complete(
                                request, events::add, new CancellationSignal()));
                        future.whenComplete((turn, failure) -> pending.set(false));
                        assertTrue(future.isCompletedExceptionally());
                        var failure = assertThrows(CompletionException.class, future::join);
                        assertInstanceOf(UncheckedIOException.class, failure.getCause());
                        assertFalse(pending.get());
                        assertTrue(events.isEmpty());
                    }
                }
            }
            assertEquals(0, networkRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void realImageProviderPreflightCreatesNoUsageAttemptButDispatchedFailureCreatesOne() throws Exception {
        // Requires the ObservingModelClient numeric-lifecycle integration. Use the real
        // provider encoder/transport against localhost, never a paid provider endpoint.
        var networkRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            networkRequests.incrementAndGet();
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        });
        server.start();
        try {
            URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
            var image = new ImageReference("a".repeat(64), "image/png", 32, 24, 6);
            var message = ModelMessage.userInput(null, List.of(image));
            for (ModelProtocol protocol : ModelProtocol.values()) {
                ModelConfig config = new ModelConfig(true, protocol, baseUri, "compatible-model",
                        SecretValue.of("test-secret"), 128_000, 1024,
                        Duration.ofSeconds(5), Duration.ofSeconds(10));
                ModelClient provider = protocol == ModelProtocol.OPENAI_CHAT
                        ? new OpenAiChatClient(config, dev.openallay.json.EngineJson.create())
                        : new AnthropicMessagesClient(config, dev.openallay.json.EngineJson.create());
                var observed = ObservingModelClient.observe(provider, config.model());
                for (boolean stream : List.of(false, true)) {
                    int before = networkRequests.get();
                    var preflightEvents = new ArrayList<ModelEvent>();
                    var unavailable = new ModelRequest("System", List.of(message), List.of(), stream);
                    var preflight = assertDoesNotThrow(() -> observed.complete(
                            unavailable, preflightEvents::add, new CancellationSignal()));
                    assertInstanceOf(UncheckedIOException.class,
                            assertThrows(CompletionException.class, preflight::join).getCause());
                    assertEquals(before, networkRequests.get());
                    assertEquals(0, preflightEvents.stream()
                            .filter(ModelEvent.UsageStarted.class::isInstance).count());
                    assertEquals(0, preflightEvents.stream()
                            .filter(ModelEvent.UsageObserved.class::isInstance).count());
                    assertEquals(0, preflightEvents.stream()
                            .filter(ModelEvent.AttemptStarted.class::isInstance).count());

                    var dispatchedEvents = new ArrayList<ModelEvent>();
                    var available = new ModelRequest("System", List.of(message), List.of(), stream,
                            "session", null, reference -> new byte[6]);
                    var dispatched = observed.complete(available, dispatchedEvents::add, new CancellationSignal());
                    assertInstanceOf(ModelClientException.class,
                            assertThrows(CompletionException.class, dispatched::join).getCause());
                    assertEquals(before + 1, networkRequests.get());
                    var started = dispatchedEvents.stream().filter(ModelEvent.UsageStarted.class::isInstance)
                            .map(ModelEvent.UsageStarted.class::cast).toList();
                    var receipts = dispatchedEvents.stream().filter(ModelEvent.UsageObserved.class::isInstance)
                            .map(ModelEvent.UsageObserved.class::cast).toList();
                    assertEquals(1, started.size());
                    assertEquals(1, receipts.size());
                    assertEquals(started.getFirst().callId(), receipts.getFirst().callId());
                    assertEquals(config.model(), receipts.getFirst().modelIdentifier());
                    assertFalse(receipts.getFirst().usage().reported(), "HTTP error must not fabricate token counts");
                    assertEquals(1, dispatchedEvents.stream()
                            .filter(ModelEvent.AttemptStarted.class::isInstance).count());
                }
            }
            assertEquals(4, networkRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void invalidProviderImageLimitsAlsoFailThroughFuture() {
        var image = new ImageReference("a".repeat(64), "image/png", 8_001, 24, 100);
        var config = new ModelConfig(true, ModelProtocol.ANTHROPIC_MESSAGES,
                URI.create("https://example.invalid/v1/"), "compatible-model",
                SecretValue.of("test-secret"), 128_000, 1024,
                Duration.ofSeconds(5), Duration.ofSeconds(10));
        var request = new ModelRequest("System", List.of(ModelMessage.userInput(null, List.of(image))),
                List.of(), false, "session", null,
                reference -> { throw new AssertionError("invalid image read"); });
        var future = assertDoesNotThrow(() -> new AnthropicMessagesClient(config, dev.openallay.json.EngineJson.create())
                .complete(request, ignored -> {}, new CancellationSignal()));
        assertTrue(future.isCompletedExceptionally());
        assertInstanceOf(IllegalArgumentException.class,
                assertThrows(CompletionException.class, future::join).getCause());
    }
}
