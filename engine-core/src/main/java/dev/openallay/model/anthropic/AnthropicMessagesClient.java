package dev.openallay.model.anthropic;

import com.google.gson.Gson;
import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.config.ModelConfig;
import dev.openallay.model.http.HttpModelTransport;
import dev.openallay.model.http.ModelHttpErrors;
import dev.openallay.model.http.SseParser;
import dev.openallay.net.HttpExchangeRequest;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class AnthropicMessagesClient implements ModelClient {
    private final ModelConfig config;
    private final AnthropicJsonCodec codec;
    private final HttpModelTransport transport;

    public AnthropicMessagesClient(ModelConfig config, Gson gson) {
        this.config = Objects.requireNonNull(config, "config");
        codec = new AnthropicJsonCodec(gson);
        transport = new HttpModelTransport(config);
    }

    @Override
    public boolean reportsAttemptStarted() { return true; }

    @Override
    public CompletableFuture<ModelTurn> complete(
            ModelRequest request,
            Consumer<ModelEvent> events,
            CancellationSignal cancellation) {
        HttpExchangeRequest httpRequest;
        try {
            httpRequest = HttpExchangeRequest.newBuilder(
                            config.baseUri().resolve("messages"))
                    .timeout(config.requestTimeout())
                    .header("x-api-key", config.apiKey().reveal())
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .postJson(codec.requestBody(config, request))
                    .build();
        } catch (RuntimeException failure) {
            // Missing/unauthorized assets and invalid payloads must finish the
            // agent request through its future, not throw before pending state is cleared.
            return dev.openallay.util.Java8Futures.failedFuture(failure);
        }
        return transport.execute(httpRequest, cancellation, events, (status, headers, body, safeEvents) -> {
            ModelHttpErrors.requireSuccess(status, headers, body);
            return request.stream()
                            || headers.firstValue("content-type")
                                    .orElse("")
                                    .contains("text/event-stream")
                    ? decodeStream(body, safeEvents, cancellation)
                    : parseBody(body, safeEvents, cancellation);
        });
    }

    private ModelTurn parseBody(
            InputStream body,
            Consumer<ModelEvent> events,
            CancellationSignal cancellation) throws java.io.IOException {
        byte[] encoded = dev.openallay.util.Java8Streams.readAllBytes(body);
        cancellation.throwIfCancelled();
        return codec.parseTurn(new String(encoded, StandardCharsets.UTF_8), events);
    }

    private static ModelTurn decodeStream(
            InputStream body,
            Consumer<ModelEvent> events,
            CancellationSignal cancellation)
            throws java.io.IOException {
        AnthropicStreamAccumulator accumulator = new AnthropicStreamAccumulator(events);
        SseParser parser = new SseParser(accumulator::accept);
        byte[] buffer = new byte[4096];
        int read;
        while ((read = body.read(buffer)) >= 0) {
            cancellation.throwIfCancelled();
            parser.accept(java.util.Arrays.copyOf(buffer, read));
        }
        parser.finish();
        return accumulator.finish();
    }
}
