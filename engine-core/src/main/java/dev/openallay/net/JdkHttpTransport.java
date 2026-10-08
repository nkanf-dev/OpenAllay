package dev.openallay.net;

import dev.openallay.concurrent.NamedThreads;
import dev.openallay.util.Java8Futures;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Shared Java 8 JDK transport. Domain adapters own response semantics. */
public final class JdkHttpTransport implements HttpTransport {
    private final HttpTransportPolicy policy;

    public JdkHttpTransport(HttpTransportPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public <T> CompletableFuture<T> execute(HttpExchangeRequest request,
            HttpCancellation cancellation, ResponseDecoder<T> decoder) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(decoder, "decoder");
        if (cancellation.isCancelled()) {
            return Java8Futures.failedFuture(new CancellationException("HTTP request cancelled"));
        }
        Exchange<T> exchange = new Exchange<>(request, cancellation, decoder);
        exchange.start();
        return exchange.result;
    }

    /** Package-visible acceptance evidence: cleanup includes both I/O owners, not just settlement. */
    static CompletableFuture<Void> cleanupOf(CompletableFuture<?> result) {
        return result instanceof ExchangeFuture
                ? ((ExchangeFuture<?>) result).cleanup : CompletableFuture.completedFuture(null);
    }

    private static final class ExchangeFuture<T> extends CompletableFuture<T> {
        final CompletableFuture<Void> workerFinished = new CompletableFuture<>();
        final CompletableFuture<Void> abortFinished = new CompletableFuture<>();
        final CompletableFuture<Void> cleanup = CompletableFuture.allOf(workerFinished, abortFinished);
    }

    private final class Exchange<T> {
        final HttpExchangeRequest request;
        final HttpCancellation cancellation;
        final ResponseDecoder<T> decoder;
        final ExchangeFuture<T> result = new ExchangeFuture<>();
        final AtomicBoolean settled = new AtomicBoolean();
        final AtomicReference<Throwable> cleanupFailure = new AtomicReference<>();
        final long started = System.nanoTime();
        final long budget;
        final Thread worker;
        final Thread watchdog;
        volatile HttpURLConnection connection;
        volatile InputStream body;
        volatile OutputStream output;

        Exchange(HttpExchangeRequest request, HttpCancellation cancellation, ResponseDecoder<T> decoder) {
            this.request = request;
            this.cancellation = cancellation;
            this.decoder = decoder;
            budget = nanos(request.timeout());
            worker = NamedThreads.unstartedDaemon(policy.decoderThreadName(), this::run);
            watchdog = NamedThreads.unstartedDaemon(policy.decoderThreadName() + "-watchdog", this::watch);
        }

        void start() {
            cancellation.onCancel(() -> fail(new CancellationException("HTTP request cancelled")));
            result.whenComplete((value, failure) -> {
                if (result.isCancelled()) fail(new CancellationException("HTTP request cancelled"));
            });
            // A cancellation listener may run immediately. Still start the owner so cleanup settles.
            worker.start();
            watchdog.start();
        }

        long remaining() { return budget - (System.nanoTime() - started); }

        void check() throws HttpTimeoutException {
            if (settled.get() || cancellation.isCancelled()) {
                throw new CancellationException("HTTP request cancelled");
            }
            if (remaining() <= 0) throw new HttpTimeoutException("HTTP response timed out");
        }

        void run() {
            T value = null;
            Throwable failure = null;
            boolean decoding = false;
            try {
                check();
                String scheme = request.uri().getScheme();
                if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                    throw new IOException("HTTP endpoint must use HTTP or HTTPS");
                }
                connection = (HttpURLConnection) request.uri().toURL().openConnection();
                check();
                connection.setInstanceFollowRedirects(false);
                connection.setUseCaches(false);
                connection.setConnectTimeout(millis(Math.min(nanos(policy.connectTimeout()), remaining())));
                connection.setReadTimeout(millis(remaining()));
                connection.setRequestMethod(request.method());
                request.headers().forEach((name, values) ->
                        values.forEach(entry -> connection.addRequestProperty(name, entry)));
                if ("POST".equals(request.method())) {
                    byte[] bytes = request.body();
                    connection.setDoOutput(true);
                    connection.setFixedLengthStreamingMode(bytes.length);
                    output = connection.getOutputStream();
                    check();
                    output.write(bytes);
                    // close can block too; it remains inside the same watchdog budget.
                    output.close();
                    output = null;
                }
                check();
                connection.setReadTimeout(millis(remaining()));
                int status = connection.getResponseCode();
                check();
                body = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                if (body == null) body = new ByteArrayInputStream(new byte[0]);
                HttpResponseHeaders headers = responseHeaders(connection);
                body = checkedBody(status, headers, body);
                check();
                decoding = true;
                value = decoder.decode(status, headers, body);
                check();
            } catch (Throwable thrown) {
                failure = thrown instanceof SocketTimeoutException
                        ? new HttpTimeoutException("HTTP response timed out")
                        : !decoding && thrown instanceof IOException
                                ? new IOException("HTTP exchange failed") : thrown;
            } finally {
                clean();
                Throwable cleanup = cleanupFailure.get();
                if (failure == null && cleanup != null) failure = cleanup;
                if (cleanup == null) result.workerFinished.complete(null);
                else result.workerFinished.completeExceptionally(cleanup);
            }
            if (failure != null) fail(failure);
            else if (settled.compareAndSet(false, true)) {
                watchdog.interrupt();
                result.abortFinished.complete(null);
                result.complete(value);
            }
        }

        void watch() {
            try {
                while (!settled.get()) {
                    long left = remaining();
                    if (left <= 0) {
                        fail(new HttpTimeoutException("HTTP response timed out"));
                        return;
                    }
                    TimeUnit.NANOSECONDS.sleep(left);
                }
            } catch (InterruptedException ignored) {
                // Only terminal settlement interrupts this owner.
            }
        }

        void fail(Throwable failure) {
            if (!settled.compareAndSet(false, true)) return;
            worker.interrupt();
            watchdog.interrupt();
            // Never close a URLConnection stream on the cancelling caller or watchdog:
            // stock JDK streams can hold their monitor while blocked in a socket read.
            NamedThreads.startDaemon(policy.decoderThreadName() + "-abort", () -> {
                disconnect();
                Throwable cleanup = cleanupFailure.get();
                if (cleanup == null) result.abortFinished.complete(null);
                else result.abortFinished.completeExceptionally(cleanup);
            });
            result.completeExceptionally(failure);
        }

        void disconnect() {
            HttpURLConnection active = connection;
            try {
                if (active != null) active.disconnect();
            } catch (Throwable failure) {
                cleanupFailure.compareAndSet(null, failure);
            }
        }

        void clean() {
            disconnect();
            close(output);
            close(body);
        }

        void close(Closeable stream) {
            if (stream == null) return;
            try { stream.close(); }
            catch (Throwable failure) { cleanupFailure.compareAndSet(null, failure); }
        }
    }

    private static InputStream checkedBody(int status, HttpResponseHeaders headers, InputStream body) throws IOException {
        // URLConnection can silently return EOF for a truncated fixed-length response.
        // Preserve the streaming decoder's IOException boundary without buffering the body.
        if (status < 200 || status == 204 || status == 205 || status == 304
                || headers.firstValue("transfer-encoding").isPresent()) return body;
        List<String> lengths = headers.values().get("content-length");
        if (lengths == null || lengths.isEmpty()) return body;
        Long expected = null;
        try {
            for (String header : lengths) {
                for (String token : header.split(",", -1)) {
                    String value = token.trim();
                    if (value.isEmpty()) throw new NumberFormatException();
                    for (int index = 0; index < value.length(); index++) {
                        char digit = value.charAt(index);
                        if (digit < '0' || digit > '9') throw new NumberFormatException();
                    }
                    long length = Long.parseLong(value);
                    if (expected != null && expected.longValue() != length) {
                        throw new IOException("HTTP response has conflicting declared lengths");
                    }
                    expected = length;
                }
            }
        } catch (NumberFormatException invalidLength) {
            throw new IOException("HTTP response has an invalid declared length");
        }
        return new LengthCheckedBody(body, expected.longValue());
    }

    private static final class LengthCheckedBody extends FilterInputStream {
        private final long expected;
        private long received;
        private long marked;

        LengthCheckedBody(InputStream body, long expected) {
            super(body);
            this.expected = expected;
        }

        @Override public int read() throws IOException {
            int value = in.read();
            account(value < 0 ? -1 : 1);
            return value;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = in.read(buffer, offset, length);
            account(count);
            return count;
        }

        @Override public long skip(long count) throws IOException {
            long skipped = in.skip(count);
            received += skipped;
            return skipped;
        }

        @Override public synchronized void mark(int readLimit) {
            in.mark(readLimit);
            marked = received;
        }

        @Override public synchronized void reset() throws IOException {
            in.reset();
            received = marked;
        }

        private void account(int count) throws IOException {
            if (count < 0 && received < expected) {
                throw new IOException("HTTP response body ended before its declared length");
            }
            if (count > 0) received += count;
        }
    }

    private static HttpResponseHeaders responseHeaders(HttpURLConnection connection) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        // Indexed fields keep duplicate values in wire order; getHeaderFields reverses them.
        for (int index = 1; ; index++) {
            String name = connection.getHeaderFieldKey(index);
            String value = connection.getHeaderField(index);
            if (name == null && value == null) break;
            if (name != null) values.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
        return new HttpResponseHeaders(values);
    }

    private static long nanos(Duration duration) {
        try { return duration.toNanos(); }
        catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
    }

    private static int millis(long nanoseconds) {
        if (nanoseconds <= 0) return 1;
        long rounded = nanoseconds / 1_000_000L + (nanoseconds % 1_000_000L == 0 ? 0 : 1);
        return (int) Math.min(Integer.MAX_VALUE, rounded);
    }
}
