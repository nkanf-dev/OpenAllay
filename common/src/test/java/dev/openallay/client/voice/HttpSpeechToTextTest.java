package dev.openallay.client.voice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.openallay.model.config.CredentialReference;
import dev.openallay.model.config.CredentialResolver;
import dev.openallay.model.config.SecretValue;
import dev.openallay.tool.ToolResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Local fixtures only: no microphone, native model, real credential, or paid endpoint. */
final class HttpSpeechToTextTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final CredentialResolver NO_CREDENTIAL = reference -> {
        throw new AssertionError("An anonymous local endpoint must not resolve credentials");
    };

    @Test
    void sendsWavMultipartToConfiguredSpeechModelWithOptionalAuthentication() throws Exception {
        CompletableFuture<Captured> captured = new CompletableFuture<>();
        AtomicInteger resolutions = new AtomicInteger();
        String syntheticSecret = "synthetic-local-test-only-not-a-real-key";
        CredentialReference credential = CredentialReference.environment("VOICE_TEST_KEY");
        try (Fixture fixture = new Fixture(exchange -> {
            captured.complete(capture(exchange));
            reply(exchange, 200, "{\"text\":\"  build a house  \",\"source\":\"ignored remote source\","
                    + "\"usage\":{\"audio_seconds\":1.25,\"input_tokens\":9007199254740993,\"output_tokens\":7}}");
        })) {
            HttpSpeechToText service = new HttpSpeechToText(fixture.uri("/custom/v1/"),
                    "configured-speech-model", credential, reference -> {
                        assertEquals(credential, reference);
                        resolutions.incrementAndGet();
                        return new ToolResult.Success<>(SecretValue.of(syntheticSecret));
                    }, TIMEOUT);
            PcmClip clip = new PcmClip(new byte[] {0, 1, 2, 3, 4, 5});
            SpeechToText.Result result = service.transcribe(
                    new SpeechToText.Request(clip, "en", 2), new VoiceCancellation());
            Captured request = captured.get(1, TimeUnit.SECONDS);
            assertEquals("POST", request.method());
            assertEquals("/custom/v1/audio/transcriptions", request.path());
            assertEquals("Bearer " + syntheticSecret, request.authorization());
            assertEquals(1, resolutions.get());
            assertEquals("application/json", request.accept());
            assertTrue(request.contentType().startsWith("multipart/form-data; boundary="));
            String boundary = request.contentType().substring(request.contentType().indexOf("boundary=") + 9);
            String multipart = new String(request.body(), StandardCharsets.ISO_8859_1);
            assertTrue(multipart.startsWith("--" + boundary + "\r\n"));
            assertTrue(multipart.endsWith("\r\n--" + boundary + "--\r\n"));
            assertTrue(multipart.contains("name=\"model\"\r\n\r\nconfigured-speech-model\r\n"));
            assertTrue(multipart.contains("name=\"response_format\"\r\n\r\njson\r\n"));
            assertTrue(multipart.contains("name=\"language\"\r\n\r\nen\r\n"));
            String fileHeader = "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n"
                    + "Content-Type: audio/wav\r\n\r\n";
            int fileStart = multipart.indexOf(fileHeader) + fileHeader.length();
            assertTrue(fileStart >= fileHeader.length());
            assertArrayEquals(clip.wav(), Arrays.copyOfRange(request.body(), fileStart, fileStart + clip.wav().length));
            assertFalse(multipart.contains(syntheticSecret));
            assertFalse(multipart.contains("cpuThreads"));
            assertEquals("build a house", result.text());
            assertEquals("http:configured-speech-model", result.source());
            assertEquals(1.25, result.usage().audioSeconds());
            assertEquals(9007199254740993L, result.usage().inputTokens());
            assertEquals(7L, result.usage().outputTokens());
        }
    }

    @Test
    void anonymousAutoLanguageUsesExactBaseOrFullTranscriptionPath() throws Exception {
        for (String path : new String[] {"", "/", "/v1", "/v1/", "/v1/audio/transcriptions",
                "/v1/audio/transcriptions/"}) {
            CompletableFuture<Captured> captured = new CompletableFuture<>();
            try (Fixture fixture = new Fixture(exchange -> {
                captured.complete(capture(exchange));
                reply(exchange, 200, "{\"text\":\"你好\"}");
            })) {
                SpeechToText.Result result = anonymous(fixture, path, TIMEOUT)
                        .transcribe(request("auto"), new VoiceCancellation());
                Captured sent = captured.get(1, TimeUnit.SECONDS);
                assertEquals(path.startsWith("/v1") ? "/v1/audio/transcriptions" : "/audio/transcriptions", sent.path());
                assertNull(sent.authorization());
                assertFalse(new String(sent.body(), StandardCharsets.UTF_8).contains("name=\"language\""));
                assertEquals("你好", result.text());
                assertNull(result.usage());
            }
        }
    }

    @Test
    void preservesDurationAndUnknownTokenUsageWithoutInventingCounts() throws Exception {
        for (String json : new String[] {
                "{\"text\":\"ok\",\"usage\":{\"type\":\"duration\",\"seconds\":2.5}}",
                "{\"text\":\"ok\",\"usage\":{\"audio_seconds\":2.5,\"input_tokens\":null}}"}) {
            try (Fixture fixture = jsonFixture(json)) {
                SpeechToText.Result result = anonymous(fixture, "/v1", TIMEOUT)
                        .transcribe(request("auto"), new VoiceCancellation());
                assertEquals(2.5, result.usage().audioSeconds());
                assertNull(result.usage().inputTokens());
                assertNull(result.usage().outputTokens());
            }
        }
    }

    @Test
    void emptyTranscriptsAndMalformedResponseShapesHaveSafeTypedErrors() throws Exception {
        for (String json : new String[] {"{\"text\":\"\"}", "{\"text\":\" \t \n\"}"}) {
            // The second body below is encoded to valid JSON before the server returns it.
            json = json.replace("\t", "\\t").replace("\n", "\\n");
            try (Fixture fixture = jsonFixture(json)) {
                HttpSpeechToText.Failure failure = assertThrows(HttpSpeechToText.Failure.class,
                        () -> anonymous(fixture, "/v1", TIMEOUT).transcribe(request("auto"), new VoiceCancellation()));
                assertEquals("voice_empty_transcript", failure.code());
                assertNull(failure.httpStatus());
                assertNull(failure.getCause());
            }
        }
        for (String json : new String[] {"", "{}", "[]", "{\"text\":null}", "{\"text\":5}",
                "{text:'private-response-marker'}", "{\"text\":\"ok\"} private-response-marker",
                "{\"text\":\"ok\",\"usage\":[]}",
                "{\"text\":\"ok\",\"usage\":{\"seconds\":-1}}",
                "{\"text\":\"ok\",\"usage\":{\"seconds\":1e999}}",
                "{\"text\":\"ok\",\"usage\":{\"input_tokens\":1.5}}",
                "{\"text\":\"ok\",\"usage\":{\"output_tokens\":9223372036854775808}}",
                "{\"text\":\"ok\",\"usage\":{\"input_tokens\":\"3\"}}"}) {
            try (Fixture fixture = jsonFixture(json)) {
                HttpSpeechToText.Failure failure = assertThrows(HttpSpeechToText.Failure.class,
                        () -> anonymous(fixture, "/v1", TIMEOUT).transcribe(request("auto"), new VoiceCancellation()));
                assertEquals("voice_invalid_response", failure.code());
                assertNull(failure.getCause());
                assertFalse(failure.toString().contains("private-response-marker"));
            }
        }
    }

    @Test
    void invalidUtf8AndOversizedTranscriptsAreRejected() throws Exception {
        try (Fixture fixture = new Fixture(exchange -> {
            byte[] body = new byte[] {'{', '"', 't', 'e', 'x', 't', '"', ':', '"', (byte) 0xff, '"', '}'};
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        })) {
            assertEquals("voice_invalid_response", assertThrows(HttpSpeechToText.Failure.class,
                    () -> anonymous(fixture, "/v1", TIMEOUT).transcribe(request("auto"), new VoiceCancellation())).code());
        }
        try (Fixture fixture = jsonFixture("{\"text\":\"" + "x".repeat(32_769) + "\"}")) {
            assertEquals("voice_invalid_response", assertThrows(HttpSpeechToText.Failure.class,
                    () -> anonymous(fixture, "/v1", TIMEOUT).transcribe(request("auto"), new VoiceCancellation())).code());
        }
    }

    @Test
    void boundedBodyStopsAtOneMebibyteEvenForChunkedReplies() throws Exception {
        try (Fixture fixture = new Fixture(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write("{\"text\":\"".getBytes(StandardCharsets.UTF_8));
                byte[] chunk = new byte[8192];
                Arrays.fill(chunk, (byte) 'x');
                try {
                    for (int i = 0; i < 130; i++) output.write(chunk);
                } catch (IOException closedByClient) {
                    // The bounded client owns closing the response before the whole body arrives.
                }
            }
        })) {
            HttpSpeechToText.Failure failure = assertThrows(HttpSpeechToText.Failure.class,
                    () -> anonymous(fixture, "/v1", TIMEOUT).transcribe(request("auto"), new VoiceCancellation()));
            assertEquals("voice_response_too_large", failure.code());
        }
    }

    @Test
    void httpErrorsNeverEchoBodyAndNeverRetryOrFollowRedirects() throws Exception {
        for (int status : new int[] {400, 401, 403, 429, 500, 503, 302}) {
            AtomicInteger attempts = new AtomicInteger();
            try (Fixture fixture = new Fixture(exchange -> {
                attempts.incrementAndGet();
                exchange.getResponseHeaders().add("Location", "/redirect-target");
                reply(exchange, status, "private-provider-marker synthetic-local-test-only-not-a-real-key");
            })) {
                HttpSpeechToText.Failure failure = assertThrows(HttpSpeechToText.Failure.class,
                        () -> anonymous(fixture, "/v1", TIMEOUT).transcribe(request("auto"), new VoiceCancellation()));
                assertEquals("voice_http_error", failure.code());
                assertEquals(status, failure.httpStatus());
                assertNull(failure.getCause());
                assertFalse(failure.toString().contains("private-provider-marker"));
                assertFalse(failure.toString().contains("synthetic-local-test"));
                assertEquals(1, attempts.get());
            }
        }
    }

    @Test
    void credentialFailuresStaySafeAndDoNotMakeAnyRequest() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (Fixture fixture = new Fixture(exchange -> {
            attempts.incrementAndGet();
            reply(exchange, 200, "{\"text\":\"wrong\"}");
        })) {
            for (CredentialResolver resolver : new CredentialResolver[] {
                    ignored -> new ToolResult.Failure<>("private-resolver-code", "private-resolver-secret"),
                    ignored -> { throw new IllegalStateException("private-resolver-secret"); },
                    ignored -> new ToolResult.Success<>(SecretValue.of("fake-key\r\nInjected: secret"))}) {
                HttpSpeechToText service = new HttpSpeechToText(fixture.uri("/v1"), "speech-only",
                        CredentialReference.environment("VOICE_TEST_KEY"), resolver, TIMEOUT);
                HttpSpeechToText.Failure failure = assertThrows(HttpSpeechToText.Failure.class,
                        () -> service.transcribe(request("auto"), new VoiceCancellation()));
                assertEquals("voice_credential_unavailable", failure.code());
                assertFalse(failure.toString().contains("private-resolver"));
                assertFalse(failure.toString().contains("fake-key"));
                assertNull(failure.getCause());
            }
            assertEquals(0, attempts.get());
        }
    }

    @Test
    void cancellationBeforeSendDoesNotResolveCredentialOrSendRequest() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (Fixture fixture = new Fixture(exchange -> {
            attempts.incrementAndGet();
            reply(exchange, 200, "{\"text\":\"wrong\"}");
        })) {
            VoiceCancellation cancellation = new VoiceCancellation();
            cancellation.cancel();
            assertThrows(CancellationException.class,
                    () -> anonymous(fixture, "/v1", TIMEOUT).transcribe(request("auto"), cancellation));
            assertEquals(0, attempts.get());
        }
    }

    @Test
    void cancellationClosesAStalledBodyAndReturnsWithoutWaitingForTimeout() throws Exception {
        CountDownLatch headers = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (Fixture fixture = stalledFixture(headers, release);
                ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
            VoiceCancellation cancellation = new VoiceCancellation();
            var work = workers.submit(() -> anonymous(fixture, "/v1", Duration.ofSeconds(10))
                    .transcribe(request("auto"), cancellation));
            try {
                assertTrue(headers.await(2, TimeUnit.SECONDS));
                cancellation.cancel();
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> work.get(1, TimeUnit.SECONDS));
                assertInstanceOf(CancellationException.class, failure.getCause());
            } finally {
                release.countDown();
                work.cancel(true);
            }
        }
    }

    @Test
    void totalTimeoutIncludesReadingAStalledBody() throws Exception {
        CountDownLatch headers = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (Fixture fixture = stalledFixture(headers, release);
                ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
            long started = System.nanoTime();
            var work = workers.submit(() -> anonymous(fixture, "/v1", Duration.ofMillis(600))
                    .transcribe(request("auto"), new VoiceCancellation()));
            try {
                assertTrue(headers.await(2, TimeUnit.SECONDS));
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> work.get(2, TimeUnit.SECONDS));
                assertEquals("voice_timeout", assertInstanceOf(HttpSpeechToText.Failure.class, failure.getCause()).code());
                assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 2500);
            } finally {
                release.countDown();
                work.cancel(true);
            }
        }
    }

    @Test
    void refusesEndpointSecretsAndInvalidConfigurationWithoutRenderingThem() {
        for (String uri : new String[] {"file:///tmp/private-marker", "http://private-marker@127.0.0.1/v1",
                "http://127.0.0.1/v1?key=private-marker", "http://127.0.0.1/v1#private-marker"}) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> new HttpSpeechToText(URI.create(uri), "speech-only", null, NO_CREDENTIAL, TIMEOUT));
            assertFalse(failure.toString().contains("private-marker"));
        }
        assertThrows(IllegalArgumentException.class, () -> new HttpSpeechToText(
                URI.create("http://127.0.0.1"), "bad\r\nmodel", null, NO_CREDENTIAL, TIMEOUT));
        assertThrows(IllegalArgumentException.class, () -> new HttpSpeechToText(
                URI.create("http://127.0.0.1"), "speech-only", null, NO_CREDENTIAL, Duration.ZERO));
    }

    private static HttpSpeechToText anonymous(Fixture fixture, String path, Duration timeout) {
        return new HttpSpeechToText(fixture.uri(path), "speech-only", null, NO_CREDENTIAL, timeout);
    }

    private static SpeechToText.Request request(String language) {
        return new SpeechToText.Request(new PcmClip(new byte[] {0, 0, 1, 0}), language, 1);
    }

    private static Fixture jsonFixture(String json) throws IOException {
        return new Fixture(exchange -> reply(exchange, 200, json));
    }

    private static Fixture stalledFixture(CountDownLatch headers, CountDownLatch release) throws IOException {
        return new Fixture(exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write('{');
                output.flush();
                headers.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        });
    }

    private static Captured capture(HttpExchange exchange) throws IOException {
        return new Captured(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Accept"),
                exchange.getRequestHeaders().getFirst("Content-Type"), exchange.getRequestBody().readAllBytes());
    }

    private static void reply(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
    }

    private record Captured(String method, String path, String authorization, String accept,
            String contentType, byte[] body) {}

    private static final class Fixture implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private Fixture(HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                try { handler.handle(exchange); } finally { exchange.close(); }
            });
            server.start();
        }
        private URI uri(String path) { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path); }
        @Override public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
