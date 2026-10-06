package dev.openallay.extension.universal;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundledUniversalExtensionsCacheTest {
    private static final String RESOURCE = "META-INF/openallay/bundled-extensions/fixture.jar";
    @TempDir Path temporary;

    @Test void provenanceIdAndVersionMustMatchTheArtifactBeforeAdmission() throws Exception {
        byte[] bytes = packageBytes();
        for (String field : List.of("extensionId", "version")) {
            JsonObject provenance = provenance(bytes);
            provenance.addProperty(field, field.equals("extensionId") ? "test:different" : "2.0.0");
            var registry = UniversalExtensionFixtures.registry();
            assertEquals("Bundled Extension provenance and declaration differ", assertThrows(IOException.class,
                    () -> open(temporary.resolve(field), provenance, bytes, registry)).getMessage());
            assertEquals(0, registry.snapshot().generation());
        }
    }

    @Test void extraCachedJarIsNeverDiscoveredAndIsNotRemoved() throws Exception {
        byte[] bytes = packageBytes(), sentinel = "unique extra package".getBytes(StandardCharsets.UTF_8);
        JsonObject provenance = provenance(bytes);
        Path cache = temporary.resolve("cache");
        Path directory = Files.createDirectories(cache.resolve(digest(bytes)));
        Path target = Files.write(directory.resolve("fixture.jar"), bytes);
        Path extra = Files.write(directory.resolve("unexpected.jar"), sentinel);
        var registry = UniversalExtensionFixtures.registry();
        assertEquals("Bundled Extension cache contains an unexpected package", assertThrows(IOException.class,
                () -> open(cache, provenance, bytes, registry)).getMessage());
        assertEquals(0, registry.snapshot().generation());
        assertArrayEquals(bytes, Files.readAllBytes(target));
        assertArrayEquals(sentinel, Files.readAllBytes(extra));
    }

    @Test void redirectedOwnedCacheDirectoriesArePreserved() throws Exception {
        byte[] bytes = packageBytes(), sentinel = "outside owned cache".getBytes(StandardCharsets.UTF_8);
        JsonObject provenance = provenance(bytes);
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path existing = Files.write(outside.resolve("unique.txt"), sentinel);
        Path rootLink = Files.createSymbolicLink(temporary.resolve("root-link"), outside);
        Path cache = Files.createDirectory(temporary.resolve("cache"));
        Path digestLink = Files.createSymbolicLink(cache.resolve(digest(bytes)), outside);
        for (Path root : List.of(rootLink, cache)) {
            var registry = UniversalExtensionFixtures.registry();
            assertEquals("Bundled Extension cache path is a symlink", assertThrows(IOException.class,
                    () -> open(root, provenance, bytes, registry)).getMessage());
            assertEquals(0, registry.snapshot().generation());
        }
        assertTrue(Files.isSymbolicLink(rootLink));
        assertTrue(Files.isSymbolicLink(digestLink));
        assertArrayEquals(sentinel, Files.readAllBytes(existing));
        try (var paths = Files.list(outside)) { assertEquals(List.of(existing), paths.toList()); }
    }

    private static BundledUniversalExtensions open(Path cache, JsonObject provenance, byte[] bytes,
            dev.openallay.extension.OpenAllayExtensionRegistry registry) throws IOException {
        return BundledUniversalExtensions.open(cache, registry,
                UniversalExtensionFixtures.host(new AtomicInteger()),
                BundledUniversalExtensionsCacheTest.class.getClassLoader(), path ->
                    new ByteArrayInputStream(path.equals(BundledUniversalExtensions.PROVENANCE)
                            ? provenance.toString().getBytes(StandardCharsets.UTF_8) : bytes));
    }
    private static JsonObject provenance(byte[] bytes) throws Exception {
        JsonObject value = dev.openallay.json.JsonTrees.parse("""
                {"source":{"repository":"test:fixture","revision":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "dirty":false,"pinned":true},"project":"fixture","version":"1.0.0",
                 "extensionId":"test:extension","openAllayApiVersion":"0.3.0",
                 "artifact":{"path":"META-INF/openallay/bundled-extensions/fixture.jar","sha256":""}}
                """).getAsJsonObject();
        value.getAsJsonObject("artifact").addProperty("sha256", digest(bytes));
        return value;
    }
    private static byte[] packageBytes() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new JarOutputStream(bytes)) {
            output.putNextEntry(new JarEntry(UniversalExtensionManifest.JAR_PATH));
            output.write(UniversalExtensionFixtures.manifest("test:extension", "community.Absent")
                    .getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return bytes.toByteArray();
    }
    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
