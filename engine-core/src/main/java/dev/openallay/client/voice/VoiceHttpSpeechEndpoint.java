package dev.openallay.client.voice;

import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpTransport;
import dev.openallay.net.HttpTransportPolicy;
import dev.openallay.net.JdkHttpTransport;
import dev.openallay.util.Java8Strings;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import dev.openallay.client.voice.SpeechToText.Request;
import dev.openallay.client.voice.SpeechToText.Result;
import dev.openallay.client.voice.SpeechToText.Usage;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** One cancellable multipart request to the configured speech endpoint. Never retries. */
public final class VoiceHttpSpeechEndpoint {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final String TRANSCRIPTIONS_PATH = "/audio/transcriptions";
    private final URI endpoint;
    private final String model;
    private final Duration timeout;
    private final HttpTransport transport;

    VoiceHttpSpeechEndpoint(URI baseUrl, String model, Duration timeout) {
        this.endpoint = endpoint(baseUrl);
        if (model == null || Java8Strings.isBlank(model) || model.length() > 256
                || model.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("Invalid speech model");
        }
        this.model = model;
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Invalid speech timeout");
        }
        try {
            timeout.toNanos();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("Invalid speech timeout");
        }
        this.timeout = timeout;
        transport = new JdkHttpTransport(new HttpTransportPolicy(timeout, "openallay-voice-http-body"));
    }

    Result transcribe(Request request, VoiceCancellation cancellation, String authorization) throws Exception {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellation, "cancellation");
        cancellation.check();
        if (!request.language().matches("auto|[a-z]{2,3}(-[A-Z]{2})?")) {
            throw new Failure("voice_invalid_request", null);
        }
        String boundary = "openallay-" + UUID.randomUUID();
        HttpExchangeRequest.Builder encoded = HttpExchangeRequest.newBuilder(endpoint).timeout(timeout)
                .header("Accept", "application/json")
                .postBytes(multipart(request, boundary), "multipart/form-data; boundary=" + boundary);
        if (authorization != null) {
            if (authorization.indexOf('\r') >= 0 || authorization.indexOf('\n') >= 0) {
                throw new Failure("voice_credential_unavailable", null);
            }
            encoded.header("Authorization", authorization);
        }
        cancellation.check();
        VoiceHttpCancellation exchangeCancellation = new VoiceHttpCancellation(cancellation);
        CompletableFuture<Result> result = null;
        try (AutoCloseable registration = cancellation.onCancel(exchangeCancellation::cancel)) {
            result = transport.execute(encoded.build(), exchangeCancellation, (status, headers, body) -> {
                cancellation.check();
                if (status < 200 || status >= 300) {
                    throw new java.util.concurrent.CompletionException(new Failure("voice_http_error", status));
                }
                try {
                    Result decoded = decode(readBounded(body, cancellation));
                    cancellation.check();
                    return decoded;
                } catch (Failure known) {
                    throw new java.util.concurrent.CompletionException(known);
                } catch (RuntimeException invalid) {
                    if (invalid instanceof CancellationException) throw invalid;
                    throw new java.util.concurrent.CompletionException(new Failure("voice_invalid_response", null));
                }
            });
            try {
                Result value = result.get();
                cancellation.check();
                return value;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                exchangeCancellation.cancel();
                throw new CancellationException("Voice cancelled");
            } catch (ExecutionException failure) {
                cancellation.check();
                Throwable cause = unwrap(failure);
                if (cause instanceof Failure) throw (Failure) cause;
                if (cause instanceof CancellationException) throw (CancellationException) cause;
                throw transportFailure(cause);
            }
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (Failure known) {
            throw known;
        } catch (RuntimeException failure) {
            throw new Failure("voice_transport_error", null);
        } finally {
            // Caller interruption revokes the shared exchange without closing streams on the caller.
            exchangeCancellation.cancel();
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
        byte[] wav = request.clip().wav();
        bytes.write(wav, 0, wav.length);
        write(bytes, "\r\n--" + boundary + "--\r\n");
        return bytes.toByteArray();
    }

    private static void field(ByteArrayOutputStream bytes, String boundary, String name, String value) {
        write(bytes, "--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                + name + "\"\r\n\r\n" + value + "\r\n");
    }

    private static void write(ByteArrayOutputStream bytes, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        bytes.write(encoded, 0, encoded.length);
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
            String text = Java8Strings.strip(value.getAsString());
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

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof ExecutionException) && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static Failure transportFailure(Throwable failure) {
        return new Failure(failure instanceof dev.openallay.net.HttpTimeoutException
                || failure instanceof TimeoutException ? "voice_timeout" : "voice_transport_error", null);
    }

    /** Typed, fixed diagnostics: no provider body, URL, credential, or nested cause. */
    public static class Failure extends Exception {
        private static final long serialVersionUID = 1L;
        private final String code;
        private final Integer httpStatus;
        Failure(String code, Integer httpStatus) {
            super(message(code, httpStatus));
            this.code = code;
            this.httpStatus = httpStatus;
        }
        private static String message(String code, Integer status) {
            switch (code) {
                case "voice_http_error": return "Speech endpoint returned HTTP " + status;
                case "voice_timeout": return "Speech request timed out";
                case "voice_credential_unavailable": return "Speech credential is unavailable";
                case "voice_empty_transcript": return "Speech endpoint returned no transcript";
                case "voice_response_too_large": return "Speech response exceeded the size limit";
                case "voice_invalid_request": return "Invalid speech request";
                case "voice_invalid_response": return "Invalid speech response";
                default: return "Speech transport is unavailable";
            }
        }
        public String code() { return code; }
        public Integer httpStatus() { return httpStatus; }
    }
}
