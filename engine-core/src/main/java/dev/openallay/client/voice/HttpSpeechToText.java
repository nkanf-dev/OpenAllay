package dev.openallay.client.voice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import dev.openallay.model.config.CredentialReference;
import dev.openallay.model.config.CredentialResolver;
import dev.openallay.model.config.SecretValue;
import dev.openallay.tool.ToolResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** One cancellable multipart request to the configured speech endpoint. Never retries. */
public final class HttpSpeechToText implements SpeechToText {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final String TRANSCRIPTIONS_PATH = "/audio/transcriptions";
    private final URI endpoint;
    private final String model;
    private final CredentialReference credential;
    private final CredentialResolver resolver;
    private final Duration timeout;
    private final long timeoutNanos;
    private final HttpClient client;

    public HttpSpeechToText(URI baseUrl, String model, CredentialReference credential,
            CredentialResolver resolver, Duration timeout) {
        this.endpoint = endpoint(baseUrl);
        if (model == null || model.isBlank() || model.length() > 256
                || model.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("Invalid speech model");
        }
        this.model = model;
        this.credential = credential;
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Invalid speech timeout");
        }
        try {
            timeoutNanos = timeout.toNanos();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("Invalid speech timeout");
        }
        this.timeout = timeout;
        client = HttpClient.newBuilder().connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public Result transcribe(Request request, VoiceCancellation cancellation) throws Exception {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellation, "cancellation");
        cancellation.check();
        if (!request.language().matches("auto|[a-z]{2,3}(-[A-Z]{2})?")) {
            throw new Failure("voice_invalid_request", null);
        }
        String boundary = "openallay-" + UUID.randomUUID();
        HttpRequest.Builder encoded = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(request, boundary)));
        authorize(encoded);
        cancellation.check();
        long started = System.nanoTime();
        CompletableFuture<HttpResponse<InputStream>> response;
        try {
            response = client.sendAsync(encoded.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (RuntimeException failure) {
            throw new Failure("voice_transport_error", null);
        }
        AtomicReference<InputStream> activeBody = new AtomicReference<>();
        AtomicReference<Thread> reader = new AtomicReference<>();
        CompletableFuture<Result> result = new CompletableFuture<>();
        Runnable stop = () -> {
            response.cancel(true);
            Thread worker = reader.get();
            if (worker != null && worker != Thread.currentThread()) worker.interrupt();
            close(activeBody.getAndSet(null));
        };
        try (AutoCloseable registration = cancellation.onCancel(() -> {
            result.completeExceptionally(new CancellationException("Voice cancelled"));
            stop.run();
        })) {
            response.whenComplete((received, failure) -> {
                if (failure != null) {
                    result.completeExceptionally(transportFailure(failure));
                    return;
                }
                InputStream body = received.body();
                activeBody.set(body);
                if (result.isDone()) {
                    close(activeBody.getAndSet(null));
                    return;
                }
                Thread worker = Thread.ofVirtual().name("openallay-voice-http-body").unstarted(() -> {
                    try (body) {
                        cancellation.check();
                        if (result.isDone()) return;
                        int status = received.statusCode();
                        if (status < 200 || status >= 300) {
                            throw new Failure("voice_http_error", status);
                        }
                        Result decoded = decode(readBounded(body, cancellation));
                        cancellation.check();
                        result.complete(decoded);
                    } catch (Failure | CancellationException known) {
                        result.completeExceptionally(known);
                    } catch (IOException failureReading) {
                        result.completeExceptionally(new Failure("voice_transport_error", null));
                    } catch (RuntimeException invalid) {
                        result.completeExceptionally(new Failure("voice_invalid_response", null));
                    } finally {
                        activeBody.compareAndSet(body, null);
                    }
                });
                reader.set(worker);
                if (result.isDone()) {
                    close(activeBody.getAndSet(null));
                    return;
                }
                worker.start();
            });
            try {
                long remaining = timeoutNanos - (System.nanoTime() - started);
                if (remaining <= 0) throw new TimeoutException();
                Result value = result.get(remaining, TimeUnit.NANOSECONDS);
                cancellation.check();
                return value;
            } catch (TimeoutException failure) {
                cancellation.check();
                Failure safe = new Failure("voice_timeout", null);
                result.completeExceptionally(safe);
                throw safe;
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                result.completeExceptionally(new CancellationException("Voice cancelled"));
                throw new CancellationException("Voice cancelled");
            } catch (ExecutionException failure) {
                cancellation.check();
                Throwable cause = failure.getCause();
                if (cause instanceof Failure known) throw known;
                if (cause instanceof CancellationException cancelled) throw cancelled;
                throw new Failure("voice_transport_error", null);
            }
        } finally {
            // Also covers a caller interrupted while waiting. Late bodies are closed by the callback.
            result.completeExceptionally(new CancellationException("Voice cancelled"));
            stop.run();
        }
    }

    private void authorize(HttpRequest.Builder request) throws Failure {
        if (credential == null) return;
        try {
            ToolResult<SecretValue> resolved = resolver.resolve(credential);
            if (!(resolved instanceof ToolResult.Success<SecretValue> success)) {
                throw new Failure("voice_credential_unavailable", null);
            }
            // Reveal only at the outbound request boundary, never in settings or diagnostics.
            request.header("Authorization", "Bearer " + success.value().reveal());
        } catch (RuntimeException failure) {
            throw new Failure("voice_credential_unavailable", null);
        }
    }

    private byte[] multipart(Request request, String boundary) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        field(bytes, boundary, "model", model);
        field(bytes, boundary, "response_format", "json");
        if (!request.language().equals("auto")) {
            field(bytes, boundary, "language", request.language());
        }
        write(bytes, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n");
        bytes.writeBytes(request.clip().wav());
        write(bytes, "\r\n--" + boundary + "--\r\n");
        return bytes.toByteArray();
    }

    private static void field(ByteArrayOutputStream bytes, String boundary, String name, String value) {
        write(bytes, "--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                + name + "\"\r\n\r\n" + value + "\r\n");
    }

    private static void write(ByteArrayOutputStream bytes, String value) {
        bytes.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readBounded(InputStream body, VoiceCancellation cancellation)
            throws IOException, Failure {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (true) {
            cancellation.check();
            int read = body.read(buffer, 0, Math.min(buffer.length, MAX_RESPONSE_BYTES - bytes.size() + 1));
            if (read < 0) return bytes.toByteArray();
            if (read > MAX_RESPONSE_BYTES - bytes.size()) {
                throw new Failure("voice_response_too_large", null);
            }
            bytes.write(buffer, 0, read);
        }
    }

    private Result decode(byte[] bytes) throws Failure {
        try {
            String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            JsonReader reader = dev.openallay.json.JsonReaders.strict(new java.io.StringReader(json));

            JsonElement parsed = dev.openallay.json.JsonReaders.read(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT || !parsed.isJsonObject()) {
                throw new Failure("voice_invalid_response", null);
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement value = root.get("text");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new Failure("voice_invalid_response", null);
            }
            String text = value.getAsString().strip();
            if (text.isEmpty()) throw new Failure("voice_empty_transcript", null);
            if (text.length() > 32_768) throw new Failure("voice_invalid_response", null);
            Usage usage = null;
            if (root.has("usage") && !root.get("usage").isJsonNull()) {
                if (!root.get("usage").isJsonObject()) throw new Failure("voice_invalid_response", null);
                JsonObject reported = root.getAsJsonObject("usage");
                Double seconds = decimal(reported, reported.has("audio_seconds") ? "audio_seconds" : "seconds");
                usage = new Usage(seconds, integer(reported, "input_tokens"), integer(reported, "output_tokens"));
            }
            return new Result(text, "http:" + model, usage);
        } catch (CharacterCodingException failure) {
            throw new Failure("voice_invalid_response", null);
        } catch (IOException | RuntimeException failure) {
            // Parser and provider messages may contain the transcript, body, URL, or secret.
            throw new Failure("voice_invalid_response", null);
        }
    }

    private static Double decimal(JsonObject object, String field) throws Failure {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new Failure("voice_invalid_response", null);
        }
        double result = value.getAsDouble();
        if (!Double.isFinite(result) || result < 0) throw new Failure("voice_invalid_response", null);
        return result;
    }

    private static Long integer(JsonObject object, String field) throws Failure {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new Failure("voice_invalid_response", null);
        }
        try {
            long result = value.getAsBigDecimal().longValueExact();
            if (result < 0) throw new Failure("voice_invalid_response", null);
            return result;
        } catch (ArithmeticException failure) {
            throw new Failure("voice_invalid_response", null);
        }
    }

    private static URI endpoint(URI base) {
        if (base == null || !("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null
                || base.getRawQuery() != null || base.getRawFragment() != null) {
            throw new IllegalArgumentException("Invalid speech endpoint");
        }
        String path = base.getRawPath();
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (!path.endsWith(TRANSCRIPTIONS_PATH)) path += TRANSCRIPTIONS_PATH;
        return URI.create(base.getScheme() + "://" + base.getRawAuthority() + path);
    }

    private static Failure transportFailure(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return new Failure(current instanceof java.net.http.HttpTimeoutException
                ? "voice_timeout" : "voice_transport_error", null);
    }

    private static void close(InputStream body) {
        if (body == null) return;
        try { body.close(); } catch (IOException ignored) { /* Terminal outcome owns the exchange. */ }
    }

    /** Typed, fixed diagnostics: no provider body, URL, credential, or nested cause. */
    public static final class Failure extends Exception {
        private final String code;
        private final Integer httpStatus;
        private Failure(String code, Integer httpStatus) {
            super(switch (code) {
                case "voice_http_error" -> "Speech endpoint returned HTTP " + httpStatus;
                case "voice_timeout" -> "Speech request timed out";
                case "voice_credential_unavailable" -> "Speech credential is unavailable";
                case "voice_empty_transcript" -> "Speech endpoint returned no transcript";
                case "voice_response_too_large" -> "Speech response exceeded the size limit";
                case "voice_invalid_request" -> "Invalid speech request";
                case "voice_invalid_response" -> "Invalid speech response";
                default -> "Speech transport is unavailable";
            });
            this.code = code;
            this.httpStatus = httpStatus;
        }
        public String code() { return code; }
        public Integer httpStatus() { return httpStatus; }
    }
}
