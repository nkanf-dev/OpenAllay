package dev.openallay.client.voice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import dev.openallay.concurrent.NamedThreads;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.util.Java8Hex;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/** Real canonical voice HTTP adapters on genuine Java8. Tiny test bytes only. */
public final class VoiceHttpJava8Fixture {
    private static final byte[] MODEL = new byte[] {1, 2, 3, 4};
    private static volatile Throwable uncaught;
    public static void main(String[] args) throws Exception {
        require("1.8".equals(System.getProperty("java.specification.version")), "genuine Java8 required");
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> uncaught = failure);
        for (Class<?> owner : new Class<?>[] {VoiceHttpJava8Fixture.class, VoiceHttpSpeechEndpoint.class,
                VoiceHttpCancellation.class, VoiceModelDownload.class, SpeechToText.class,
                SpeechToText.Request.class, SpeechToText.Result.class, SpeechToText.Usage.class,
                PcmClip.class, VoiceCancellation.class, HttpExchangeRequest.class}) {
            try (java.io.DataInputStream input = new java.io.DataInputStream(owner.getResourceAsStream(
                    "/" + owner.getName().replace('.', '/') + ".class"))) {
                input.readInt(); input.readUnsignedShort();
                require(input.readUnsignedShort() == 52, "Class52 " + owner);
            }
        }
        binarySnapshot();
        multipart();
        speechErrors();
        boundedSpeechBody();
        speechCancellationAndDeadline();
        download(Paths.get(args[0]));
        waitOwners();
        require(uncaught == null, "uncaught owner failure " + uncaught);
        System.out.println("PASS Java8 canonical voice HTTP multipart WAV usage strict JSON bounded body cancel deadline "
                + "HTTPS redirect TLS defaults tiny streaming hash size progress no-replace Class52 owners");
    }

    private static void binarySnapshot() {
        byte[] bytes = new byte[] {0, 1, (byte) 255};
        HttpExchangeRequest request = HttpExchangeRequest.newBuilder(URI.create("https://localhost/private-marker"))
                .header("content-type", "old").postBytes(bytes, "audio/wav").build();
        bytes[0] = 9;
        byte[] first = request.body(); first[1] = 9;
        require(request.body()[0] == 0 && request.body()[1] == 1, "binary snapshots");
        require(request.headers().size() == 1, "one body media type");
        require(!request.toString().contains("private-marker"), "request URI redaction");
    }

    private static void multipart() throws Exception {
        CompletableFuture<byte[]> captured = new CompletableFuture<>();
        try (Server server = new Server(false, null)) {
            server.http.createContext("/v1/audio/transcriptions", exchange -> {
                require("POST".equals(exchange.getRequestMethod()), "multipart POST");
                require("Bearer synthetic-only".equals(exchange.getRequestHeaders().getFirst("Authorization")), "authorization boundary");
                require(exchange.getRequestHeaders().getFirst("Content-Type").startsWith("multipart/form-data; boundary="), "multipart media type");
                captured.complete(read(exchange.getRequestBody()));
                reply(exchange, 200, "{\"text\":\"  build  ",\"usage\":{\"audio_seconds\":1.25,\"input_tokens\":9007199254740993,\"output_tokens\":7}}".getBytes(StandardCharsets.UTF_8));
            });
            server.http.start();
            PcmClip clip = new PcmClip(new byte[] {0, 1, 2, 3});
            SpeechToText.Result result = endpoint(server, Duration.ofSeconds(3)).transcribe(
                    new SpeechToText.Request(clip, "en", 2), new VoiceCancellation(), "Bearer synthetic-only");
            String sent = new String(captured.get(2, TimeUnit.SECONDS), StandardCharsets.ISO_8859_1);
            require(sent.contains("name=\"model\"\r\n\r\nspeech-only"), "model field");
            require(sent.contains("name=\"language\"\r\n\r\nen"), "language field");
            String fileHeader = "Content-Type: audio/wav\r\n\r\n";
            byte[] raw = captured.get();
            int start = sent.indexOf(fileHeader) + fileHeader.length();
            require(Arrays.equals(clip.wav(), Arrays.copyOfRange(raw, start, start + clip.wav().length)), "exact WAV bytes");
            require(!sent.contains("synthetic-only") && !sent.contains("cpuThreads"), "multipart secret/CPU exclusions");
            require(result.text().equals("build") && result.source().equals("http:speech-only"), "result source");
            require(result.usage().inputTokens() == 9007199254740993L, "exact reported token count");
        }
    }

    private static void speechErrors() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        try (Server server = new Server(false, null)) {
            server.http.createContext("/v1/audio/transcriptions", exchange -> {
                attempts.incrementAndGet();
                exchange.getResponseHeaders().set("Location", "/must-not-follow");
                reply(exchange, 302, "private-provider-secret".getBytes(StandardCharsets.UTF_8));
            });
            server.http.start();
            try { endpoint(server, Duration.ofSeconds(2)).transcribe(request(), new VoiceCancellation(), null); throw new AssertionError("redirect followed"); }
            catch (VoiceHttpSpeechEndpoint.Failure failure) {
                require(failure.code().equals("voice_http_error") && failure.httpStatus() == 302, "safe status error");
                require(!failure.toString().contains("private-provider-secret") && failure.getCause() == null, "safe provider failure");
            }
            require(attempts.get() == 1, "no ASR retries/redirects");
            VoiceCancellation cancelled = new VoiceCancellation(); cancelled.cancel();
            try { endpoint(server, Duration.ofSeconds(2)).transcribe(request(), cancelled, null); throw new AssertionError("pre-cancel sent"); }
            catch (CancellationException expected) { }
            require(attempts.get() == 1, "pre-cancel no send");
        }
        for (byte[] malformed : new byte[][] {"{text:'private'}".getBytes(StandardCharsets.UTF_8),
                new byte[] {'{', '"', 't', 'e', 'x', 't', '"', ':', '"', (byte) 255, '"', '}'}}) {
            try (Server server = new Server(false, null)) {
                server.http.createContext("/v1/audio/transcriptions", exchange -> reply(exchange, 200, malformed));
                server.http.start();
                try { endpoint(server, Duration.ofSeconds(2)).transcribe(request(), new VoiceCancellation(), null); throw new AssertionError("invalid response accepted"); }
                catch (VoiceHttpSpeechEndpoint.Failure failure) { require(failure.code().equals("voice_invalid_response"), "strict JSON/UTF8"); }
            }
        }
    }

    private static void boundedSpeechBody() throws Exception {
        try (Server server = new Server(false, null)) {
            server.http.createContext("/v1/audio/transcriptions", exchange -> {
                byte[] bytes = new byte[1024 * 1024 + 1];
                Arrays.fill(bytes, (byte) 'x'); reply(exchange, 200, bytes);
            });
            server.http.start();
            try { endpoint(server, Duration.ofSeconds(3)).transcribe(request(), new VoiceCancellation(), null); throw new AssertionError("oversized speech body"); }
            catch (VoiceHttpSpeechEndpoint.Failure failure) { require(failure.code().equals("voice_response_too_large"), "bounded speech body"); }
        }
    }

    private static void speechCancellationAndDeadline() throws Exception {
        for (boolean cancelNow : new boolean[] {false, true}) {
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            try (Server server = new Server(false, null)) {
                server.http.createContext("/v1/audio/transcriptions", exchange -> {
                    read(exchange.getRequestBody());
                    exchange.sendResponseHeaders(200, 0);
                    exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
                    entered.countDown(); await(release); exchange.close();
                });
                server.http.start();
                VoiceCancellation cancellation = new VoiceCancellation();
                CompletableFuture<SpeechToText.Result> work = new CompletableFuture<>();
                NamedThreads.startDaemon("fixture-voice-call", () -> {
                    try { work.complete(endpoint(server, Duration.ofMillis(600)).transcribe(request(), cancellation, null)); }
                    catch (Throwable failure) { work.completeExceptionally(failure); }
                });
                require(entered.await(2, TimeUnit.SECONDS), "stalled ASR entered");
                if (cancelNow) { long started = System.nanoTime(); cancellation.cancel(); require(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 300, "nonblocking voice cancel"); }
                try { work.get(2, TimeUnit.SECONDS); throw new AssertionError("stalled speech accepted"); }
                catch (ExecutionException failure) {
                    if (cancelNow) require(failure.getCause() instanceof CancellationException, "speech cancellation");
                    else require(((VoiceHttpSpeechEndpoint.Failure) failure.getCause()).code().equals("voice_timeout"), "full speech deadline");
                }
            } finally { release.countDown(); }
        }
    }

    private static void download(Path keys) throws Exception {
        Path directory = Files.createTempDirectory("voice-http-fixture-");
        try (Server server = new Server(true, keys)) {
            server.http.createContext("/redirect", exchange -> {
                exchange.getResponseHeaders().set("Location", "/model"); exchange.sendResponseHeaders(302, -1); exchange.close();
            });
            server.http.createContext("/model", exchange -> reply(exchange, 200, MODEL));
            server.http.createContext("/downgrade", exchange -> {
                exchange.getResponseHeaders().set("Location", "http://127.0.0.1:1/secret"); exchange.sendResponseHeaders(302, -1); exchange.close();
            });
            server.http.createContext("/loop", exchange -> {
                exchange.getResponseHeaders().set("Location", "/loop"); exchange.sendResponseHeaders(302, -1); exchange.close();
            });
            CountDownLatch[] stalled = {new CountDownLatch(1), new CountDownLatch(1)};
            CountDownLatch releaseDownload = new CountDownLatch(1);
            for (int index = 0; index < stalled.length; index++) {
                final int selected = index;
                server.http.createContext("/stalled" + index, exchange -> {
                    exchange.sendResponseHeaders(200, 0); exchange.getResponseBody().write(1); exchange.getResponseBody().flush();
                    stalled[selected].countDown(); await(releaseDownload); exchange.close();
                });
            }
            server.http.start();
            String hash = Java8Hex.formatHex(MessageDigest.getInstance("SHA-256").digest(MODEL));
            AtomicInteger updates = new AtomicInteger();
            Path file = directory.resolve("model.onnx");
            VoiceModelDownload.download(server.uri("/redirect"), MODEL.length, hash, file, new VoiceCancellation(),
                    (bytes, total) -> { require(bytes <= total && total == MODEL.length, "download progress"); updates.incrementAndGet(); });
            require(Arrays.equals(Files.readAllBytes(file), MODEL) && updates.get() > 0, "pinned streaming bytes");
            try { VoiceModelDownload.download(server.uri("/model"), MODEL.length, hash, file, new VoiceCancellation(), (bytes, total) -> {}); throw new AssertionError("existing file replaced"); }
            catch (IOException expected) { require(Arrays.equals(Files.readAllBytes(file), MODEL), "last-valid bytes preserved"); }
            for (String path : new String[] {"/downgrade", "/loop"}) {
                Path absent = directory.resolve(path.substring(1));
                try { VoiceModelDownload.download(server.uri(path), MODEL.length, hash, absent, new VoiceCancellation(), (bytes, total) -> {}); throw new AssertionError("unsafe redirect accepted"); }
                catch (IOException expected) { require(!Files.exists(absent), "redirect rejection does not publish bytes"); }
            }
            try { VoiceModelDownload.download(server.uri("/model"), MODEL.length, repeat('0', 64), directory.resolve("bad-hash"), new VoiceCancellation(), (bytes, total) -> {}); throw new AssertionError("bad hash accepted"); }
            catch (VoiceModelDownload.IntegrityFailure expected) { }
            try { VoiceModelDownload.download(server.uri("/model"), MODEL.length + 1, hash, directory.resolve("bad-size"), new VoiceCancellation(), (bytes, total) -> {}); throw new AssertionError("bad size accepted"); }
            catch (VoiceModelDownload.IntegrityFailure expected) { }
            for (boolean cancelNow : new boolean[] {false, true}) {
                VoiceCancellation signal = new VoiceCancellation();
                CompletableFuture<Void> result = new CompletableFuture<>();
                Path partial = directory.resolve(cancelNow ? "cancelled-stream" : "timed-stream");
                NamedThreads.startDaemon("fixture-voice-call-download", () -> {
                    try { VoiceModelDownload.download(server.uri(cancelNow ? "/stalled1" : "/stalled0"), MODEL.length, hash, partial, signal,
                            (bytes, total) -> {}, Duration.ofMillis(600)); result.complete(null); }
                    catch (Throwable failure) { result.completeExceptionally(failure); }
                });
                require(stalled[cancelNow ? 1 : 0].await(2, TimeUnit.SECONDS), "download stream started");
                if (cancelNow) signal.cancel();
                try { result.get(2, TimeUnit.SECONDS); throw new AssertionError("stalled download accepted"); }
                catch (ExecutionException failure) {
                    require(cancelNow ? failure.getCause() instanceof CancellationException
                            : failure.getCause() instanceof dev.openallay.net.HttpTimeoutException, "download deadline/cancel");
                }
            }
            releaseDownload.countDown();
            VoiceCancellation cancelled = new VoiceCancellation(); cancelled.cancel();
            try { VoiceModelDownload.download(server.uri("/model"), MODEL.length, hash, directory.resolve("cancelled"), cancelled, (bytes, total) -> {}); throw new AssertionError("cancelled install"); }
            catch (CancellationException expected) { require(!Files.exists(directory.resolve("cancelled")), "no cancelled file"); }
        } finally {
            try (java.util.stream.Stream<Path> entries = Files.walk(directory)) {
                for (Path path : (Iterable<Path>) entries.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
            }
        }
    }

    private static VoiceHttpSpeechEndpoint endpoint(Server server, Duration timeout) { return new VoiceHttpSpeechEndpoint(server.uri("/v1"), "speech-only", timeout); }
    private static SpeechToText.Request request() { return new SpeechToText.Request(new PcmClip(new byte[] {0, 0}), "auto", 1); }
    private static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count); return bytes.toByteArray();
    }
    private static void reply(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        read(exchange.getRequestBody()); exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes); exchange.close();
    }
    private static void await(CountDownLatch latch) {
        try { latch.await(4, TimeUnit.SECONDS); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
    }
    private static String repeat(char value, int count) { char[] chars = new char[count]; Arrays.fill(chars, value); return new String(chars); }
    private static void waitOwners() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            boolean alive = false;
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (thread.isAlive() && (thread.getName().startsWith("openallay-voice-http")
                        || thread.getName().startsWith("openallay-voice-model-download") || thread.getName().startsWith("fixture-voice-call"))) {
                    alive = true; thread.join(50);
                }
            }
            if (!alive) return;
        }
        throw new AssertionError("voice HTTP owner threads did not settle");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class Server implements AutoCloseable {
        final HttpServer http;
        final ExecutorService executor = Executors.newCachedThreadPool(NamedThreads.daemonFactory("fixture-voice-server-", 0));
        final boolean secure;
        Server(boolean secure, Path keys) throws Exception {
            this.secure = secure;
            if (secure) {
                KeyStore store = KeyStore.getInstance("JKS");
                try (InputStream input = Files.newInputStream(keys)) { store.load(input, "fixture-only".toCharArray()); }
                KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                managers.init(store, "fixture-only".toCharArray());
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(managers.getKeyManagers(), null, null);
                HttpsServer https = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                https.setHttpsConfigurator(new HttpsConfigurator(context)); http = https;
            } else http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.setExecutor(executor);
        }
        URI uri(String path) { return URI.create((secure ? "https://localhost:" : "http://127.0.0.1:") + http.getAddress().getPort() + path); }
        @Override public void close() throws IOException {
            http.stop(0); executor.shutdownNow();
            try { require(executor.awaitTermination(2, TimeUnit.SECONDS), "server workers settled"); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IOException(failure); }
        }
    }
}
