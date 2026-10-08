package dev.openallay.community;

import dev.openallay.model.CancellationSignal;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpTransport;
import dev.openallay.net.HttpTransportPolicy;
import dev.openallay.net.JdkHttpTransport;
import dev.openallay.settings.AtomicSettingsFile;
import dev.openallay.tool.ToolResult;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Asynchronous configuration-layer catalog reader with last-valid cache semantics. */
public final class CommunityCatalogClient {
    private final URI catalogUri;
    private final Path cachePath;
    private final HttpTransport transport;
    private final Duration requestTimeout;
    private final CommunityCatalogCodec codec = new CommunityCatalogCodec();
    private final AtomicSettingsFile files = new AtomicSettingsFile();
    private volatile CommunityCatalogManifest current;
    private CompletableFuture<ToolResult<CommunityCatalogManifest>> activeRefresh;

    public CommunityCatalogClient(
            URI catalogUri, Path cachePath, Duration connectTimeout, Duration requestTimeout) {
        this(
                catalogUri,
                cachePath,
                new JdkHttpTransport(new HttpTransportPolicy(
                        connectTimeout, "openallay-community-catalog-http")),
                requestTimeout);
    }

    public CommunityCatalogClient(
            URI catalogUri,
            Path cachePath,
            HttpTransport transport,
            Duration requestTimeout) {
        this.catalogUri = Objects.requireNonNull(catalogUri, "catalogUri");
        CommunityCatalogManifest.validateRemoteUri(catalogUri, "catalog");
        this.cachePath = Objects.requireNonNull(cachePath, "cachePath")
                .toAbsolutePath().normalize();
        this.transport = Objects.requireNonNull(transport, "transport");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("Catalog request timeout must be positive");
        }
        loadCache();
    }

    public Optional<CommunityCatalogManifest> current() {
        return Optional.ofNullable(current);
    }

    public synchronized CompletableFuture<ToolResult<CommunityCatalogManifest>> refresh(
            CancellationSignal cancellation) {
        Objects.requireNonNull(cancellation, "cancellation");
        if (activeRefresh != null) {
            return activeRefresh;
        }
        if (cancellation.isCancelled()) {
            return CompletableFuture.completedFuture(
                    new ToolResult.Failure<>("catalog_refresh_cancelled", "Catalog refresh was cancelled"));
        }
        CompletableFuture<ToolResult<CommunityCatalogManifest>> started = startRefresh(cancellation);
        activeRefresh = started;
        started.whenComplete((ignored, failure) -> clearActive(started));
        return started;
    }

    private CompletableFuture<ToolResult<CommunityCatalogManifest>> startRefresh(
            CancellationSignal cancellation) {
        CompletableFuture<Response> response;
        try {
            response = transport.execute(
                    HttpExchangeRequest.newBuilder(catalogUri)
                            .timeout(requestTimeout)
                            .header("accept", "application/json")
                            .get()
                            .build(),
                    cancellation,
                    (status, headers, body) -> new Response(
                            status, new String(dev.openallay.util.Java8Streams.readAllBytes(body), StandardCharsets.UTF_8)));
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(refreshFailure());
        }
        return response.handle((received, failure) -> {
            if (cancellation.isCancelled()) {
                return new ToolResult.Failure<CommunityCatalogManifest>(
                        "catalog_refresh_cancelled", "Catalog refresh was cancelled");
            }
            if (failure != null || received == null || received.status() != 200) {
                return refreshFailure();
            }
            try {
                CommunityCatalogManifest candidate = codec.decode(received.body());
                files.replace(cachePath, codec.encode(candidate));
                current = candidate;
                return new ToolResult.Success<>(candidate);
            } catch (RuntimeException invalid) {
                return refreshFailure();
            }
        });
    }

    private synchronized void clearActive(
            CompletableFuture<ToolResult<CommunityCatalogManifest>> completed) {
        if (activeRefresh == completed) {
            activeRefresh = null;
        }
    }

    private void loadCache() {
        if (!Files.isRegularFile(cachePath)) {
            return;
        }
        try {
            current = codec.decode(dev.openallay.util.Java8Files.readString(cachePath));
        } catch (Exception ignored) {
            // An invalid cache is absent state; refresh may establish a valid generation.
        }
    }

    private static ToolResult.Failure<CommunityCatalogManifest> refreshFailure() {
        return new ToolResult.Failure<>(
                "catalog_refresh_failed",
                "The community catalog could not be refreshed; the last valid catalog remains available");
    }

    @dev.openallay.value.ValueType(Response.ValueSchemaProvider.class)
private static final class Response {
    private final int status;
    private final String body;
    private Response(int status, String body) {
        this.status = status;
        this.body = body;
    }
    public int status() { return status; }
    public String body() { return body; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Response)) return false;
        Response that = (Response) other;
        return status == that.status && java.util.Objects.equals(body, that.body);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(status);
        hash = 31 * hash + java.util.Objects.hashCode(body);
        return hash;
    }
    @Override public String toString() { return "Response[status=" + status + ", body=" + body + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Response> schema() {
            return new dev.openallay.value.ValueSchema<>(Response.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Response>>asList(new dev.openallay.value.ValueSchema.Component<>(Response.class, "status", Response::status), new dev.openallay.value.ValueSchema.Component<>(Response.class, "body", Response::body)), arguments -> new Response((Integer) arguments[0], (String) arguments[1]));
        }
    }
}
}
