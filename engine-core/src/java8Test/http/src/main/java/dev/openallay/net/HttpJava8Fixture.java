package dev.openallay.net;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.openallay.concurrent.NamedThreads;
import dev.openallay.util.Java8Collections;
import dev.openallay.util.Java8Futures;
import dev.openallay.util.Java8Strings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** No JUnit or engine dependency graph: canonical sources, real Java 8 HTTP sockets. */
public final class HttpJava8Fixture {
    private static final String PREFIX = "fixture-http-";
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final AtomicReference<Throwable> UNCAUGHT = new AtomicReference<>();

    public static void main(String[] args) throws Exception {
        require("1.8".equals(System.getProperty("java.specification.version")), "genuine Java 8 required");
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> UNCAUGHT.compareAndSet(null, failure));
        for (Class<?> type : new Class<?>[] {HttpJava8Fixture.class, JdkHttpTransport.class,
                HttpExchangeRequest.class, HttpResponseHeaders.class, HttpTransportPolicy.class,
                HttpCancellation.class, HttpTransport.class, HttpTimeoutException.class,
                NamedThreads.class, Java8Collections.class, Java8Strings.class, Java8Futures.class}) {
            try (InputStream input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                byte[] header = new byte[8];
                int used = 0;
                while (used < header.length) {
                    int count = input.read(header, used, header.length - used);
                    require(count > 0, "class header missing");
                    used += count;
                }
                require(((header[6] & 255) << 8 | header[7] & 255) == 52, "class major must be 52: " + type);
            }
        }
        if (args.length != 0) {
            require(args.length == 2 && "--case".equals(args[0])
                    && "incomplete-response".equals(args[1]), "unknown fixture case");
            incompleteErrorBody();
            waitForThreads();
            require(UNCAUGHT.get() == null, "uncaught owner failure: " + UNCAUGHT.get());
            System.out.println("PASS Java8 HTTP incomplete-response: observed200/400 IOException chunked bounded-read no-body closure cleanup Class52 threads");
            return;
        }
        values();
        basic();
        incompleteErrorBody();
        preCancelled();
        System.out.println("CASE blocked response body deadline");
        blockedBody(false);
        System.out.println("CASE blocked response body cancellation");
        blockedBody(true);
        System.out.println("CASE decoder exception and closure");
        decoderFailure();
        System.out.println("CASE late decoder and terminal once");
        lateDecoder();
        System.out.println("CASE blocked POST deadline with cleanup before server release");
        blockedPost(false);
        System.out.println("CASE blocked POST cancellation with cleanup before server release");
        blockedPost(true);
        waitForThreads();
        require(UNCAUGHT.get() == null, "uncaught owner failure: " + UNCAUGHT.get());
        System.out.println("PASS Java8 HTTP: GET POST duplicate headers error body redirect pre-cancel "
                + "body cancel/deadline decoder closure late finish terminal once blocked POST cleanup Class52 threads");
    }

    private static void values() {
        HttpTransportPolicy policy = new HttpTransportPolicy(Duration.ofSeconds(2), "name");
        require(policy.equals(new HttpTransportPolicy(Duration.ofSeconds(2), "name")), "policy equality");
        require(policy.hashCode() == 31 * Duration.ofSeconds(2).hashCode() + "name".hashCode(), "record hash seed");
        require(policy.toString().equals("HttpTransportPolicy[connectTimeout=PT2S, decoderThreadName=name]"), "policy string");
        List<String> entries = new ArrayList<>(Arrays.asList("one", "two"));
        Map<String, List<String>> map = new java.util.LinkedHashMap<>();
        map.put("X-Test", entries);
        HttpResponseHeaders headers = new HttpResponseHeaders(map);
        entries.clear();
        require(headers.values().get("x-test").size() == 2, "header snapshot");
        require(headers.firstValue("X-TEST").get().equals("one"), "header case folding");
        require(headers.hashCode() == headers.values().hashCode(), "header record hash seed");
        try { headers.values().get("x-test").clear(); throw new AssertionError("mutable headers"); }
        catch (UnsupportedOperationException expected) { }
    }

    private static void basic() throws Exception {
        AtomicInteger targetHits = new AtomicInteger();
        try (Server server = new Server()) {
            server.http.createContext("/echo", exchange -> {
                String received = read(exchange.getRequestBody());
                require(exchange.getRequestHeaders().get("X-Duplicate").size() == 2, "duplicate request headers");
                exchange.getResponseHeaders().add("X-Reply", "one");
                exchange.getResponseHeaders().add("X-Reply", "two");
                send(exchange, 422, exchange.getRequestMethod() + ":" + received);
            });
            server.http.createContext("/target", exchange -> { targetHits.incrementAndGet(); send(exchange, 200, "target"); });
            server.http.createContext("/redirect", exchange -> {
                exchange.getResponseHeaders().set("Location", "/target");
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            });
            server.http.createContext("/get", exchange -> send(exchange, 200, exchange.getRequestMethod()));
            server.http.start();
            HttpExchangeRequest request = HttpExchangeRequest.newBuilder(server.uri("/echo?token=secret-query"))
                    .header("X-Duplicate", "one").header("X-Duplicate", "two")
                    .header("Authorization", "secret-header").postJson("{\"secret-body\":true}").build();
            require(!request.toString().contains("secret"), "request secret rendering");
            CompletableFuture<String> post = transport().execute(request, new Cancel(), (status, headers, body) -> {
                require(status == 422, "status error reaches decoder");
                require(headers.values().get("x-reply").size() == 2, "duplicate response headers");
                require(headers.firstValue("X-Reply").get().equals("one"), "response header wire order");
                require(Thread.currentThread().isDaemon(), "ordinary daemon decoder");
                return read(body);
            });
            require(post.get(3, TimeUnit.SECONDS).equals("POST:{\"secret-body\":true}"), "JSON error body");
            cleanup(post, 3);
            CompletableFuture<String> get = transport().execute(request(server, "/get", 2), new Cancel(),
                    (status, headers, body) -> read(body));
            require(get.get(3, TimeUnit.SECONDS).equals("GET"), "GET method");
            cleanup(get, 3);
            CompletableFuture<Integer> redirect = transport().execute(request(server, "/redirect", 2), new Cancel(),
                    (status, headers, body) -> status);
            require(redirect.get(3, TimeUnit.SECONDS) == 302 && targetHits.get() == 0, "redirect not followed");
            cleanup(redirect, 3);
            // Overflow must not produce an immediate negative deadline or connect/read timeout.
            CompletableFuture<String> huge = transport().execute(HttpExchangeRequest.newBuilder(server.uri("/get"))
                    .timeout(Duration.ofSeconds(Long.MAX_VALUE)).get().build(), new Cancel(), (status, headers, body) -> read(body));
            require(huge.get(3, TimeUnit.SECONDS).equals("GET"), "overflow guarded duration");
            cleanup(huge, 3);
        }
    }

    private static void incompleteErrorBody() throws Exception {
        for (int rejectedStatus : new int[] {200, 400}) {
            CountDownLatch decoding = new CountDownLatch(1);
            AtomicInteger observedStatus = new AtomicInteger(-1);
            AtomicReference<InputStream> bodyRef = new AtomicReference<>();
            try (Server server = new Server()) {
                server.http.createContext("/incomplete-error", exchange -> {
                    exchange.sendResponseHeaders(rejectedStatus, 200);
                    exchange.getResponseBody().write('{');
                    exchange.getResponseBody().flush();
                    try { require(decoding.await(2, TimeUnit.SECONDS), "incomplete-response decoder entered"); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                    finally { exchange.close(); }
                });
                server.http.start();
                CompletableFuture<String> result = transport().execute(request(server, "/incomplete-error", 2), new Cancel(),
                        (status, headers, body) -> {
                            observedStatus.set(status);
                            bodyRef.set(body);
                            decoding.countDown();
                            return read(body);
                        });
                expect(result, IOException.class);
                require(observedStatus.get() == rejectedStatus, "known status must reach decoder");
                cleanup(result, 3);
                try { bodyRef.get().read(); throw new AssertionError("incomplete body not closed"); }
                catch (IOException expected) { }
            }
        }
        try (Server server = new Server()) {
            server.http.createContext("/chunked", exchange -> {
                exchange.sendResponseHeaders(400, 0);
                exchange.getResponseBody().write('{');
                exchange.close();
            });
            server.http.createContext("/bounded", exchange -> send(exchange, 400, "longer-than-decoder-needs"));
            server.http.createContext("/no-content", exchange -> {
                exchange.getResponseHeaders().add("Content-Length", "200");
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
            });
            server.http.start();
            CompletableFuture<String> chunked = transport().execute(request(server, "/chunked", 2), new Cancel(),
                    (status, headers, body) -> {
                        require(headers.firstValue("transfer-encoding").isPresent(), "actual chunked response");
                        return read(body);
                    });
            require(chunked.get(3, TimeUnit.SECONDS).equals("{"), "chunked EOF is not fixed-length truncation");
            cleanup(chunked, 3);
            CompletableFuture<Integer> bounded = transport().execute(request(server, "/bounded", 2), new Cancel(),
                    (status, headers, body) -> body.read());
            require(bounded.get(3, TimeUnit.SECONDS) == (int) 'l', "intentional bounded read can close without EOF");
            cleanup(bounded, 3);
            CompletableFuture<String> noContent = transport().execute(request(server, "/no-content", 2), new Cancel(),
                    (status, headers, body) -> read(body));
            require(noContent.get(3, TimeUnit.SECONDS).isEmpty(), "304 length header is representation metadata");
            cleanup(noContent, 3);
        }
    }

    private static void preCancelled() throws Exception {
        Cancel cancel = new Cancel();
        cancel.cancel();
        CompletableFuture<Integer> result = transport().execute(HttpExchangeRequest.newBuilder(
                URI.create("http://127.0.0.1:1/must-not-connect")).build(), cancel,
                (status, headers, body) -> { throw new AssertionError("pre-cancel decoder"); });
        require(result.isDone(), "pre-cancel is immediate");
        expect(result, CancellationException.class);
        cleanup(result, 1);
    }

    private static void blockedBody(boolean cancelNow) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch decoder = new CountDownLatch(1);
        try (Server server = new Server()) {
            server.http.createContext("/blocked", exchange -> {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write('x');
                exchange.getResponseBody().flush();
                await(release);
                exchange.close();
            });
            server.http.start();
            Cancel cancel = new Cancel();
            AtomicInteger terminal = new AtomicInteger();
            long started = System.nanoTime();
            CompletableFuture<String> result = transport().execute(request(server, "/blocked", 1), cancel,
                    (status, headers, body) -> { decoder.countDown(); return read(body); });
            result.whenComplete((value, failure) -> terminal.incrementAndGet());
            require(decoder.await(2, TimeUnit.SECONDS), "blocked body decoder entered");
            if (cancelNow) {
                long called = System.nanoTime();
                cancel.cancel();
                require(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - called) < 300, "cancel caller must not block on stream lock");
            }
            expect(result, cancelNow ? CancellationException.class : HttpTimeoutException.class);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            require(elapsed < 1800, "deadline bounded terminal result");
            if (!cancelNow) require(elapsed >= 700, "not an artificial short read timeout");
            // Do not release the server to make cleanup pass. The configured socket deadline must free owners.
            cleanup(result, 2);
            require(terminal.get() == 1, "exactly one terminal completion");
        } finally { release.countDown(); }
    }

    private static void decoderFailure() throws Exception {
        AtomicReference<InputStream> bodyRef = new AtomicReference<>();
        IOException marker = new IOException("decoder marker");
        try (Server server = new Server()) {
            server.http.createContext("/failure", exchange -> send(exchange, 200, "failure"));
            server.http.start();
            CompletableFuture<String> result = transport().execute(request(server, "/failure", 2), new Cancel(),
                    (status, headers, body) -> { bodyRef.set(body); throw marker; });
            try { result.get(3, TimeUnit.SECONDS); throw new AssertionError("missing decoder failure"); }
            catch (ExecutionException failure) { require(failure.getCause() == marker, "decoder exception preserved"); }
            cleanup(result, 3);
            try { bodyRef.get().read(); throw new AssertionError("body was not closed"); }
            catch (IOException expected) { }
        }
    }

    private static void lateDecoder() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<InputStream> bodyRef = new AtomicReference<>();
        try (Server server = new Server()) {
            server.http.createContext("/late", exchange -> send(exchange, 200, "late"));
            server.http.start();
            Cancel cancel = new Cancel();
            AtomicInteger terminal = new AtomicInteger();
            CompletableFuture<String> result = transport().execute(request(server, "/late", 2), cancel,
                    (status, headers, body) -> {
                        bodyRef.set(body);
                        entered.countDown();
                        boolean done = false;
                        while (!done) {
                            try { release.await(); done = true; }
                            catch (InterruptedException ignored) { /* Deliberately noncooperative decoder. */ }
                        }
                        return "too late";
                    });
            result.whenComplete((value, failure) -> terminal.incrementAndGet());
            require(entered.await(2, TimeUnit.SECONDS), "late decoder entered");
            cancel.cancel();
            expect(result, CancellationException.class);
            release.countDown();
            cleanup(result, 3);
            expect(result, CancellationException.class);
            require(terminal.get() == 1, "late decoder cannot overwrite cancellation");
            try { bodyRef.get().read(); throw new AssertionError("late body not closed"); }
            catch (IOException expected) { }
        } finally { release.countDown(); }
    }

    private static void blockedPost(boolean cancelNow) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (Server server = new Server()) {
            server.http.createContext("/no-read", exchange -> {
                entered.countDown();
                // Never read request body or return response until all client owners have terminated.
                await(release);
                exchange.close();
            });
            server.http.start();
            char[] chars = new char[16 * 1024 * 1024];
            Arrays.fill(chars, 'x');
            HttpExchangeRequest request = HttpExchangeRequest.newBuilder(server.uri("/no-read"))
                    .timeout(Duration.ofSeconds(1)).postJson(new String(chars)).build();
            Cancel cancel = new Cancel();
            AtomicInteger decoded = new AtomicInteger();
            CompletableFuture<String> result = transport().execute(request, cancel,
                    (status, headers, body) -> { decoded.incrementAndGet(); return read(body); });
            require(entered.await(2, TimeUnit.SECONDS), "blocked POST reached server");
            if (cancelNow) {
                long called = System.nanoTime();
                cancel.cancel();
                require(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - called) < 300, "blocked POST cancellation caller");
            }
            expect(result, cancelNow ? CancellationException.class : HttpTimeoutException.class);
            // This is a genuine blocked upload test, not a fake HttpClient. No server release before cleanup.
            cleanup(result, 2);
            require(decoded.get() == 0, "blocked POST must not decode");
        } finally { release.countDown(); }
    }

    private static JdkHttpTransport transport() {
        return new JdkHttpTransport(new HttpTransportPolicy(Duration.ofSeconds(2), PREFIX + IDS.incrementAndGet()));
    }
    private static HttpExchangeRequest request(Server server, String path, int seconds) {
        return HttpExchangeRequest.newBuilder(server.uri(path)).timeout(Duration.ofSeconds(seconds)).get().build();
    }
    private static void cleanup(CompletableFuture<?> result, int seconds) throws Exception {
        try { JdkHttpTransport.cleanupOf(result).get(seconds, TimeUnit.SECONDS); }
        catch (java.util.concurrent.TimeoutException failure) {
            for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                if (entry.getKey().getName().startsWith(PREFIX)) {
                    System.err.println("CLEANUP TIMEOUT OWNER " + entry.getKey());
                    for (StackTraceElement frame : entry.getValue()) System.err.println("  " + frame);
                }
            }
            throw failure;
        }
    }
    private static void expect(CompletableFuture<?> result, Class<? extends Throwable> type) throws Exception {
        try { result.get(3, TimeUnit.SECONDS); throw new AssertionError("missing " + type.getName()); }
        catch (CancellationException failure) { require(type.isInstance(failure), "wrong cancellation failure"); }
        catch (ExecutionException failure) { require(type.isInstance(failure.getCause()), "wrong failure " + failure.getCause()); }
    }
    private static void waitForThreads() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        List<Thread> owners;
        do {
            owners = new ArrayList<>();
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (thread.isAlive() && thread.getName().startsWith(PREFIX)) owners.add(thread);
            }
            for (Thread thread : owners) thread.join(50);
        } while (!owners.isEmpty() && System.nanoTime() < deadline);
        require(owners.isEmpty(), "leaked HTTP owners " + owners);
    }
    private static String read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
    private static void send(HttpExchange exchange, int status, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
    private static void await(CountDownLatch latch) {
        try { require(latch.await(8, TimeUnit.SECONDS), "server fixture release missing"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static final class Cancel implements HttpCancellation {
        private boolean cancelled;
        private final List<Runnable> listeners = new ArrayList<>();
        @Override public synchronized boolean isCancelled() { return cancelled; }
        @Override public void onCancel(Runnable listener) {
            synchronized (this) { if (!cancelled) { listeners.add(listener); return; } }
            listener.run();
        }
        void cancel() {
            List<Runnable> copy;
            synchronized (this) {
                if (cancelled) return;
                cancelled = true;
                copy = new ArrayList<>(listeners);
                listeners.clear();
            }
            for (Runnable listener : copy) listener.run();
        }
    }
    private static final class Server implements AutoCloseable {
        final HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final ExecutorService executor = Executors.newCachedThreadPool(NamedThreads.daemonFactory("fixture-server-", 0));
        Server() throws IOException { http.setExecutor(executor); }
        URI uri(String path) { return URI.create("http://127.0.0.1:" + http.getAddress().getPort() + path); }
        @Override public void close() throws IOException {
            http.stop(0);
            executor.shutdownNow();
            try {
                require(executor.awaitTermination(2, TimeUnit.SECONDS), "server executor did not terminate");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IOException("server fixture cleanup interrupted", failure);
            }
        }
    }
}
