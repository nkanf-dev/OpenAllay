package dev.openallay.client.voice;

import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpTransport;
import dev.openallay.net.HttpTransportPolicy;
import dev.openallay.net.JdkHttpTransport;
import dev.openallay.util.Java8Hex;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import dev.openallay.concurrent.NamedThreads;

/** One canonical streaming, pinned download algorithm used by the native installer. */
final class VoiceModelDownload {
    @FunctionalInterface interface Progress { void update(long bytes, long total); }
    static final class IntegrityFailure extends IOException {
        private static final long serialVersionUID = 1L;
        IntegrityFailure() { super("model_integrity"); }
    }
    private static final Duration TRANSFER_TIMEOUT = Duration.ofMinutes(10);
    private static final int MAX_REDIRECTS = 5;
    private static final HttpTransport TRANSPORT = new JdkHttpTransport(
            new HttpTransportPolicy(Duration.ofSeconds(30), "openallay-voice-model-download"));
    private VoiceModelDownload() { }

    static void download(URI uri, long bytes, String sha256, Path file,
            VoiceCancellation cancellation, Progress progress) throws Exception {
        download(uri, bytes, sha256, file, cancellation, progress, TRANSFER_TIMEOUT);
    }

    static void download(URI uri, long bytes, String sha256, Path file,
            VoiceCancellation cancellation, Progress progress, Duration timeout) throws Exception {
        if (bytes <= 0 || sha256 == null || !sha256.matches("[a-f0-9]{64}")) throw new IntegrityFailure();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout");
        long budget;
        try { budget = timeout.toNanos(); }
        catch (ArithmeticException overflow) { budget = Long.MAX_VALUE; }
        long started = System.nanoTime();
        Set<URI> visited = new HashSet<>();
        URI selected = uri;
        for (int redirects = 0; ; redirects++) {
            cancellation.check();
            requireHttps(selected);
            if (!visited.add(selected) || redirects > MAX_REDIRECTS) throw new IOException("Download redirect rejected");
            long remaining = budget - (System.nanoTime() - started);
            if (remaining <= 0) throw new dev.openallay.net.HttpTimeoutException("Download timed out");
            VoiceHttpCancellation exchangeCancellation = new VoiceHttpCancellation(cancellation);
            CompletableFuture<URI> transfer;
            final URI requested = selected;
            CountDownLatch headersReceived = new CountDownLatch(1);
            AtomicBoolean headersTimedOut = new AtomicBoolean();
            long headerBudget = Math.min(remaining, TimeUnit.SECONDS.toNanos(60) - (System.nanoTime() - started));
            if (headerBudget <= 0) throw new dev.openallay.net.HttpTimeoutException("Download timed out");
            Thread headerWatchdog = NamedThreads.startDaemon("openallay-voice-model-download-headers", () -> {
                try {
                    if (!headersReceived.await(headerBudget, TimeUnit.NANOSECONDS)) {
                        headersTimedOut.set(true);
                        exchangeCancellation.cancel();
                    }
                } catch (InterruptedException ignored) {
                    // Received headers or a terminal transfer owns cleanup.
                }
            });
            try (AutoCloseable hook = cancellation.onCancel(exchangeCancellation::cancel)) {
                transfer = TRANSPORT.execute(HttpExchangeRequest.newBuilder(selected)
                        .timeout(Duration.ofNanos(remaining)).get().build(), exchangeCancellation,
                        (status, headers, input) -> {
                            headersReceived.countDown();
                            cancellation.check();
                            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                                String location = headers.firstValue("location").orElse(null);
                                if (location == null) throw new IOException("Download redirect rejected");
                                try {
                                    URI next = requested.resolve(location);
                                    requireHttps(next);
                                    return next;
                                } catch (IllegalArgumentException invalid) {
                                    throw new IOException("Download redirect rejected");
                                }
                            }
                            if (status != 200) throw new IOException("Download rejected");
                            String announced = headers.firstValue("content-length").orElse(null);
                            if (announced != null) {
                                try { if (Long.parseLong(announced.trim()) != bytes) throw new IntegrityFailure(); }
                                catch (NumberFormatException malformed) { throw new IntegrityFailure(); }
                            }
                            // CREATE_NEW preserves an existing file. Only the enclosing installer owns staging.
                            boolean created = false;
                            boolean verified = false;
                            try {
                                OutputStream opened = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW);
                                created = true;
                                try (OutputStream output = opened) {
                                byte[] buffer = new byte[64 * 1024];
                                long count = 0;
                                MessageDigest digest = digest();
                                int length;
                                while ((length = input.read(buffer)) != -1) {
                                    cancellation.check();
                                    count += length;
                                    if (count > bytes) throw new IntegrityFailure();
                                    output.write(buffer, 0, length);
                                    digest.update(buffer, 0, length);
                                    progress.update(count, bytes);
                                }
                                cancellation.check();
                                if (count != bytes || !Java8Hex.formatHex(digest.digest()).equals(sha256)) throw new IntegrityFailure();
                                cancellation.check();
                                }
                                verified = true;
                            } finally {
                                if (created && !verified) Files.deleteIfExists(file);
                            }
                            cancellation.check();
                            return null;
                        });
                try {
                    URI next = transfer.get();
                    cancellation.check();
                    if (next == null) return;
                    selected = next;
                } catch (CancellationException cancelled) {
                    cancellation.check();
                    if (headersTimedOut.get()) throw new dev.openallay.net.HttpTimeoutException("Download timed out");
                    throw cancelled;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    exchangeCancellation.cancel();
                    throw new CancellationException("Voice cancelled");
                } catch (ExecutionException failure) {
                    cancellation.check();
                    if (headersTimedOut.get()) throw new dev.openallay.net.HttpTimeoutException("Download timed out");
                    Throwable cause = failure.getCause();
                    while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    if (cause instanceof IntegrityFailure) throw (IntegrityFailure) cause;
                    if (cause instanceof CancellationException) throw (CancellationException) cause;
                    if (cause instanceof dev.openallay.net.HttpTimeoutException) throw (dev.openallay.net.HttpTimeoutException) cause;
                    throw new IOException("Download failed");
                }
            } finally {
                headerWatchdog.interrupt();
                exchangeCancellation.cancel();
            }
        }
    }

    private static void requireHttps(URI uri) throws IOException {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) throw new IOException("HTTPS is required");
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
