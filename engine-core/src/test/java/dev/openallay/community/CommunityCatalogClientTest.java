package dev.openallay.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.model.CancellationSignal;
import dev.openallay.net.HttpExchangeRequest;
import dev.openallay.net.HttpResponseHeaders;
import dev.openallay.net.HttpTransport;
import dev.openallay.tool.ToolResult;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CommunityCatalogClientTest {
    @TempDir Path temporaryDirectory;

    @Test
    void schemaOneCacheIsAbsentStateAndOnlySchemaTwoRefreshReplacesIt() throws Exception {
        Path cache = temporaryDirectory.resolve("skills.json");
        Files.writeString(
                cache,
                catalog("legacy").replace("\"schemaVersion\":2", "\"schemaVersion\":1"));

        CommunityCatalogClient client = new CommunityCatalogClient(
                URI.create("https://example.test/catalog.json"),
                cache,
                transport(200, catalog("current")),
                Duration.ofSeconds(5));

        assertTrue(client.current().isEmpty());
        assertInstanceOf(
                ToolResult.Success.class,
                client.refresh(new CancellationSignal()).join());
        assertEquals(
                "current",
                client.current().orElseThrow().packages().getFirst().id());
        assertEquals(
                CommunityCatalogManifest.SCHEMA_VERSION,
                new CommunityCatalogCodec().decode(Files.readString(cache)).schemaVersion());
    }

    @Test
    void failedRefreshRetainsLastValidatedCache() throws Exception {
        Path cache = temporaryDirectory.resolve("skills.json");
        CommunityCatalogClient first = new CommunityCatalogClient(
                URI.create("https://example.test/catalog.json"),
                cache,
                transport(200, catalog("alpha")),
                Duration.ofSeconds(5));
        assertInstanceOf(ToolResult.Success.class,
                first.refresh(new CancellationSignal()).join());

        CommunityCatalogClient failing = new CommunityCatalogClient(
                URI.create("https://example.test/catalog.json"),
                cache,
                transport(200, "{\"schemaVersion\":99}"),
                Duration.ofSeconds(5));
        ToolResult<CommunityCatalogManifest> result =
                failing.refresh(new CancellationSignal()).join();

        assertEquals("catalog_refresh_failed",
                assertInstanceOf(ToolResult.Failure.class, result).code());
        assertEquals("alpha", failing.current().orElseThrow().packages().getFirst().id());
        assertEquals("alpha", new CommunityCatalogCodec()
                .decode(Files.readString(cache)).packages().getFirst().id());
    }

    @Test
    void concurrentRefreshesShareOneInFlightGeneration() {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        AtomicInteger requests = new AtomicInteger();
        HttpTransport delayed = new HttpTransport() {
            @Override
            public <T> CompletableFuture<T> execute(
                    HttpExchangeRequest request,
                    dev.openallay.net.HttpCancellation cancellation,
                    ResponseDecoder<T> decoder) {
                requests.incrementAndGet();
                return gate.thenApply(ignored -> {
                    try {
                        return decoder.decode(
                                200,
                                new HttpResponseHeaders(Map.of()),
                                new ByteArrayInputStream(catalog("alpha")
                                        .getBytes(StandardCharsets.UTF_8)));
                    } catch (Exception failure) {
                        throw new java.util.concurrent.CompletionException(failure);
                    }
                });
            }
        };
        CommunityCatalogClient client = new CommunityCatalogClient(
                URI.create("https://example.test/catalog.json"),
                temporaryDirectory.resolve("cache.json"),
                delayed,
                Duration.ofSeconds(5));

        CompletableFuture<ToolResult<CommunityCatalogManifest>> first =
                client.refresh(new CancellationSignal());
        CompletableFuture<ToolResult<CommunityCatalogManifest>> second =
                client.refresh(new CancellationSignal());

        assertSame(first, second);
        assertEquals(1, requests.get());
        gate.complete(null);
        assertInstanceOf(ToolResult.Success.class, first.join());
    }

    private static HttpTransport transport(int status, String body) {
        return new HttpTransport() {
            @Override
            public <T> CompletableFuture<T> execute(
                    HttpExchangeRequest request,
                    dev.openallay.net.HttpCancellation cancellation,
                    ResponseDecoder<T> decoder) {
                try {
                    return CompletableFuture.completedFuture(decoder.decode(
                            status,
                            new HttpResponseHeaders(Map.of()),
                            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))));
                } catch (Exception failure) {
                    return CompletableFuture.failedFuture(failure);
                }
            }
        };
    }

    static String catalog(String id) {
        return """
                {"schemaVersion":2,"kind":"skill","generatedAt":"2026-07-25T00:00:00Z",
                 "packages":[{"id":"%s","displayName":"Test Skill",
                 "description":"A test community Skill.","publisher":"Test Publisher",
                 "version":"1.0.0",
                 "archive":"https://example.test/%s.zip",
                 "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "compatibility":{"minecraft":"26.2","openallayApi":"0.2"},
                 "source":"https://example.test/%s"}]}
                """.formatted(id, id, id);
    }
}
