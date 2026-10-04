package dev.openallay.extension.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExtensionCatalogClientTest {
    @TempDir Path temporary;

    @Test
    void invalidRefreshRetainsLastValidatedDiskGeneration() throws Exception {
        Path cache = temporary.resolve("extensions.json");
        ExtensionCatalogClient first = new ExtensionCatalogClient(
                URI.create("https://example.test/catalog.json"),
                cache,
                transport(200, catalog("sample:one", "1.0.0")),
                Duration.ofSeconds(5),
                new ExtensionCatalogCodec());
        assertInstanceOf(
                ToolResult.Success.class, first.refresh(new CancellationSignal()).join());

        ExtensionCatalogClient failing = new ExtensionCatalogClient(
                URI.create("https://example.test/catalog.json"),
                cache,
                transport(200, "{\"schemaVersion\":99}"),
                Duration.ofSeconds(5),
                new ExtensionCatalogCodec());
        ToolResult<ExtensionCatalogManifest> result =
                failing.refresh(new CancellationSignal()).join();

        assertEquals(
                "catalog_refresh_failed",
                assertInstanceOf(ToolResult.Failure.class, result).code());
        assertEquals(
                "sample:one",
                failing.current().orElseThrow().extensions().getFirst().id());
        assertEquals(
                "sample:one",
                new ExtensionCatalogCodec()
                        .decode(Files.readString(cache))
                        .extensions()
                        .getFirst()
                        .id());
    }

    @Test
    void concurrentRefreshesShareOneInFlightRequest() {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        AtomicInteger requests = new AtomicInteger();
        HttpTransport delayed = new HttpTransport() {
            @Override
            public <T> CompletableFuture<T> execute(
                    HttpExchangeRequest request,
                    dev.openallay.net.HttpCancellation cancellation,
                    ResponseDecoder<T> decoder) {
                requests.incrementAndGet();
                return gate.thenApply(ignored -> decode(
                        decoder, 200, catalog("sample:one", "1.0.0")));
            }
        };
        ExtensionCatalogClient client = new ExtensionCatalogClient(
                URI.create("https://example.test/catalog.json"),
                temporary.resolve("cache.json"),
                delayed,
                Duration.ofSeconds(5),
                new ExtensionCatalogCodec());

        CompletableFuture<ToolResult<ExtensionCatalogManifest>> first =
                client.refresh(new CancellationSignal());
        CompletableFuture<ToolResult<ExtensionCatalogManifest>> second =
                client.refresh(new CancellationSignal());

        assertSame(first, second);
        assertEquals(1, requests.get());
        gate.complete(null);
        assertInstanceOf(ToolResult.Success.class, first.join());
    }

    @Test
    void schemaTwoSelectsIndependentArtifactsForEachLoader() {
        ExtensionCatalogManifest decoded = new ExtensionCatalogCodec().decode("""
                {"schemaVersion":2,"kind":"extension","generatedAt":"2026-07-25T00:00:00Z",
                 "extensions":[{
                   "id":"sample:one","name":"Sample","version":"1.0.0",
                   "provider":"Community","summary":"Sample Extension",
                   "minecraftVersionRange":"[26.2,26.3)",
                   "openAllayApiVersionRange":"[0.2,0.3)",
                   "artifacts":[
                     {"loader":"neoforge","artifact":"https://example.test/sample-neoforge.jar",
                      "sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                      "modIds":["sample_neoforge"]},
                     {"loader":"fabric","artifact":"https://example.test/sample-fabric.jar",
                      "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "modIds":["sample_fabric"]}
                   ],"source":"https://example.test/sample"
                 }]}
                """);

        ExtensionCatalogEntry entry = decoded.extensions().getFirst();
        assertEquals(Set.of("fabric", "neoforge"), entry.loaders());
        assertEquals(
                "sample_fabric",
                entry.artifactFor("FABRIC").orElseThrow().modIds().iterator().next());
        assertEquals(
                "sample_neoforge",
                entry.artifactFor("neoforge").orElseThrow().modIds().iterator().next());
        assertEquals("fabric", entry.artifacts().getFirst().loader());
    }

    @Test
    void rejectsLegacySchemaAndDuplicateLoaderArtifacts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExtensionCatalogCodec().decode(
                        catalog("sample:one", "1.0.0").replace(
                                "\"schemaVersion\":2", "\"schemaVersion\":1")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExtensionCatalogCodec().decode("""
                        {"schemaVersion":2,"kind":"extension","generatedAt":"2026-07-25T00:00:00Z",
                         "extensions":[{
                           "id":"sample:one","name":"Sample","version":"1.0.0",
                           "provider":"Community","summary":"Sample Extension",
                           "minecraftVersionRange":"[26.2,26.3)",
                           "openAllayApiVersionRange":"[0.2,0.3)",
                           "artifacts":[
                             {"loader":"fabric","artifact":"https://example.test/a.jar",
                              "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                              "modIds":["sample"]},
                             {"loader":"fabric","artifact":"https://example.test/b.jar",
                              "sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                              "modIds":["sample"]}
                           ],"source":"https://example.test/sample"
                         }]}
                        """));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExtensionCatalogArtifact(
                        "other",
                        "https://example.test/other.jar",
                        "a".repeat(64),
                        Set.of("sample")));
    }

    private static HttpTransport transport(int status, String body) {
        return new HttpTransport() {
            @Override
            public <T> CompletableFuture<T> execute(
                    HttpExchangeRequest request,
                    dev.openallay.net.HttpCancellation cancellation,
                    ResponseDecoder<T> decoder) {
                return CompletableFuture.completedFuture(decode(decoder, status, body));
            }
        };
    }

    private static <T> T decode(
            HttpTransport.ResponseDecoder<T> decoder, int status, String body) {
        try {
            return decoder.decode(
                    status,
                    new HttpResponseHeaders(Map.of()),
                    new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new java.util.concurrent.CompletionException(failure);
        }
    }

    static String catalog(String id, String version) {
        return """
                {"schemaVersion":2,"kind":"extension","generatedAt":"2026-07-25T00:00:00Z",
                 "extensions":[{
                   "id":"%s","name":"Sample","version":"%s",
                   "provider":"Community","summary":"Sample Extension",
                   "minecraftVersionRange":"[26.2,26.3)",
                   "openAllayApiVersionRange":"[0.2,0.3)",
                   "artifacts":[{
                     "loader":"fabric","artifact":"https://example.test/sample.jar",
                     "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                     "modIds":["sample_extension"]
                   }],"source":"https://example.test/sample"
                 }]}
                """.formatted(id, version);
    }
}
