package dev.openallay.extension.catalog;

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

/**
 * Asynchronous Extension catalog reader.
 *
 * <p>The last fully validated generation remains readable while a refresh is in flight or fails.
 */
public final class ExtensionCatalogClient {
    private final URI catalogUri;
    private final Path cachePath;
    private final HttpTransport transport;
    private final Duration requestTimeout;
    private final ExtensionCatalogCodec codec;
    private final AtomicSettingsFile files = new AtomicSettingsFile();
    private volatile ExtensionCatalogManifest current;
    private CompletableFuture<ToolResult<ExtensionCatalogManifest>> activeRefresh;

    public ExtensionCatalogClient(
            URI catalogUri,
            Path cachePath,
            Duration connectTimeout,
            Duration requestTimeout) {
        this(
                catalogUri,
                cachePath,
                new JdkHttpTransport(new HttpTransportPolicy(
                        connectTimeout, "openallay-extension-catalog-http")),
                requestTimeout,
                new ExtensionCatalogCodec());
    }

    ExtensionCatalogClient(
            URI catalogUri,
            Path cachePath,
            HttpTransport transport,
            Duration requestTimeout,
            ExtensionCatalogCodec codec) {
        this.catalogUri = secureUri(catalogUri);
        this.cachePath = Objects.requireNonNull(cachePath, "cachePath")
                .toAbsolutePath()
                .normalize();
        this.transport = Objects.requireNonNull(transport, "transport");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.codec = Objects.requireNonNull(codec, "codec");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("Catalog request timeout must be positive");
        }
        loadCache();
    }

    public Optional<ExtensionCatalogManifest> current() {
        return Optional.ofNullable(current);
    }

    public synchronized CompletableFuture<ToolResult<ExtensionCatalogManifest>> refresh(
            CancellationSignal cancellation) {
        Objects.requireNonNull(cancellation, "cancellation");
        if (activeRefresh != null) {
            return activeRefresh;
        }
        if (cancellation.isCancelled()) {
            return CompletableFuture.completedFuture(new ToolResult.Failure<>(
                    "catalog_refresh_cancelled", "Extension catalog refresh was cancelled"));
        }
        CompletableFuture<ToolResult<ExtensionCatalogManifest>> started =
                startRefresh(cancellation);
        activeRefresh = started;
        started.whenComplete((ignored, failure) -> clearActive(started));
        return started;
    }

    private CompletableFuture<ToolResult<ExtensionCatalogManifest>> startRefresh(
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
                            status, new String(body.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(refreshFailure());
        }
        return response.handle((received, failure) -> {
            if (cancellation.isCancelled()) {
                return new ToolResult.Failure<ExtensionCatalogManifest>(
                        "catalog_refresh_cancelled", "Extension catalog refresh was cancelled");
            }
            if (failure != null || received == null || received.status() != 200) {
                return refreshFailure();
            }
            try {
                ExtensionCatalogManifest candidate = codec.decode(received.body());
                files.replace(cachePath, codec.encode(candidate));
                current = candidate;
                return new ToolResult.Success<>(candidate);
            } catch (RuntimeException invalid) {
                return refreshFailure();
            }
        });
    }

    private synchronized void clearActive(
            CompletableFuture<ToolResult<ExtensionCatalogManifest>> completed) {
        if (activeRefresh == completed) {
            activeRefresh = null;
        }
    }

    private void loadCache() {
        if (!Files.isRegularFile(cachePath)) {
            return;
        }
        try {
            current = codec.decode(Files.readString(cachePath));
        } catch (Exception ignored) {
            // Invalid cache is absent state; the next refresh may establish a valid generation.
        }
    }

    private static URI secureUri(URI uri) {
        Objects.requireNonNull(uri, "catalogUri");
        String host = uri.getHost();
        boolean loopback = host != null
                && (host.equalsIgnoreCase("localhost")
                        || host.equals("127.0.0.1")
                        || host.equals("::1"));
        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !("http".equalsIgnoreCase(uri.getScheme()) && loopback)) {
            throw new IllegalArgumentException(
                    "Extension catalog URI must use HTTPS or loopback HTTP");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "Extension catalog URI must not contain credentials");
        }
        return uri;
    }

    private static ToolResult.Failure<ExtensionCatalogManifest> refreshFailure() {
        return new ToolResult.Failure<>(
                "catalog_refresh_failed",
                "The Extension community catalog could not be refreshed; "
                        + "the last valid catalog remains available");
    }

    private record Response(int status, String body) {}
}
