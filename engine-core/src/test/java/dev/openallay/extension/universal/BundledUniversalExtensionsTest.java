package dev.openallay.extension.universal;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.extension.OpenAllayExtensionState;
import java.io.*;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundledUniversalExtensionsTest {
    private static final String ID = "test:extension", ENTRY = "bundledfixture.Entry";
    private static final String RESOURCE = "META-INF/openallay/bundled-extensions/fixture.jar";
    @TempDir Path temporary;
    private final String marker = "openallay.bundled.test." + UUID.randomUUID();
    private final AtomicInteger worldOpens = new AtomicInteger();
    private final OpenAllayExtensionRegistry registry = UniversalExtensionFixtures.registry();
    private final List<String> reads = new ArrayList<>();

    @AfterEach void clearMarkers() {
        for (String suffix : List.of("bundled", "community")) {
            System.clearProperty(marker + "." + suffix + ".init");
            System.clearProperty(marker + "." + suffix + ".contribution");
        }
        assertEquals(0, worldOpens.get(), "Admission must not open a world session");
        assertEquals(0, registry.activeJavascriptInvocations());
    }

    @Test void absentProvenanceCreatesNoDirectories() throws Exception {
        Path cache = temporary.resolve("absent/cache");
        var bundled = open(cache, null, null);
        assertTrue(bundled.results().isEmpty());
        assertEquals("", bundled.artifactSha256());
        assertEquals(List.of(BundledUniversalExtensions.PROVENANCE), reads);
        assertFalse(Files.exists(cache.getParent()));
        assertEquals(0, registry.snapshot().generation());
        bundled.close();
        assertTrue(bundled.closed());
        assertDoesNotThrow(bundled::close);
    }

    @Test void validArtifactIsActiveInHashOwnedCacheAndLoaderCloses() throws Exception {
        byte[] bytes = fixture();
        String digest = sha256(bytes);
        Path cache = temporary.resolve("cache");
        try (var bundled = open(cache, provenance(digest), bytes)) {
            assertEquals(digest, bundled.artifactSha256());
            assertEquals(List.of(new UniversalExtensionDiscovery.DiscoveryResult(
                    "fixture.jar", ID, OpenAllayExtensionState.ACTIVE, "")), bundled.results());
            assertEquals(List.of(BundledUniversalExtensions.PROVENANCE, RESOURCE), reads);
            assertArrayEquals(bytes, Files.readAllBytes(cache.resolve(digest).resolve("fixture.jar")));
            assertEquals(1, registry.snapshot().generation());
            assertEquals(ID, registry.snapshot().extensions().getFirst().descriptor().id());
            assertEquals(OpenAllayExtensionState.ACTIVE, registry.snapshot().extensions().getFirst().state());
            assertEquals("1", System.getProperty(marker + ".bundled.contribution"));
            var field = BundledUniversalExtensions.class.getDeclaredField("discovery");
            field.setAccessible(true);
            var discovery = (UniversalExtensionDiscovery) field.get(bundled);
            var loadersField = UniversalExtensionDiscovery.class.getDeclaredField("loaders");
            loadersField.setAccessible(true);
            var loaders = (List<?>) loadersField.get(discovery);
            assertEquals(1, loaders.size());
            var loader = (URLClassLoader) loaders.getFirst();
            assertNotNull(loader.findResource("bundledfixture/payload.txt"));
            assertFalse(bundled.closed());
            bundled.close();
            assertTrue(bundled.closed());
            assertNull(loader.findResource("bundledfixture/payload.txt"));
            assertTrue(loaders.isEmpty());
            assertDoesNotThrow(bundled::close);
        }
    }

    @Test void validReadOnlyCacheIsReusedWithoutPayloadWrite() throws Exception {
        byte[] bytes = fixture();
        String digest = sha256(bytes);
        Path cache = temporary.resolve("cache");
        Path directory = Files.createDirectories(cache.resolve(digest));
        Path target = directory.resolve("fixture.jar");
        Files.write(target, bytes);
        Files.setLastModifiedTime(target, FileTime.fromMillis(123456000));
        var before = Files.readAttributes(target, BasicFileAttributes.class);
        var directoryMode = Files.getPosixFilePermissions(directory);
        var fileMode = Files.getPosixFilePermissions(target);
        try {
            Files.setPosixFilePermissions(target, Set.of(PosixFilePermission.OWNER_READ));
            Files.setPosixFilePermissions(directory, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
            try (var bundled = open(cache, provenance(digest), bytes)) {
                assertEquals(OpenAllayExtensionState.ACTIVE, bundled.results().getFirst().state());
                assertEquals(digest, sha256(Files.readAllBytes(target)));
                var after = Files.readAttributes(target, BasicFileAttributes.class);
                assertEquals(before.fileKey(), after.fileKey());
                assertEquals(before.lastModifiedTime(), after.lastModifiedTime());
                assertEquals(Set.of(PosixFilePermission.OWNER_READ), Files.getPosixFilePermissions(target));
                try (var files = Files.list(directory)) { assertEquals(List.of(target), files.toList()); }
            }
        } finally {
            Files.setPosixFilePermissions(directory, directoryMode);
            Files.setPosixFilePermissions(target, fileMode);
        }
    }

    @Test void checksumMismatchCreatesNoCacheAndPreservesCommunityFile() throws Exception {
        byte[] bytes = fixture(), sentinel = "unique-checksum-community-bytes".getBytes(StandardCharsets.UTF_8);
        Path community = temporary.resolve("community.jar"), cache = temporary.resolve("cache");
        Files.write(community, sentinel);
        assertNotEquals("0".repeat(64), sha256(bytes));
        assertEquals("Bundled Extension checksum mismatch", assertThrows(IOException.class,
                () -> open(cache, provenance("0".repeat(64)), bytes)).getMessage());
        assertEquals(List.of(BundledUniversalExtensions.PROVENANCE, RESOURCE), reads);
        assertFalse(Files.exists(cache));
        assertArrayEquals(sentinel, Files.readAllBytes(community));
        assertEquals(0, registry.snapshot().generation());
        assertNull(System.getProperty(marker + ".bundled.init"));
    }

    @Test void malformedUnknownFieldAndEscapingProvenanceNeverReadsPayload() throws Exception {
        byte[] bytes = fixture(), sentinel = "unique-provenance-community-bytes".getBytes(StandardCharsets.UTF_8);
        Path community = temporary.resolve("community.jar"), cache = temporary.resolve("cache");
        Files.write(community, sentinel);
        String valid = provenance(sha256(bytes));
        for (String invalid : List.of("{", valid.replace("\"project\":", "\"unknown\":true,\"project\":"),
                valid.replace("\"pinned\":true", "\"pinned\":true,\"unknown\":true"),
                valid.replace(RESOURCE, "META-INF/openallay/bundled-extensions/../community.jar"),
                valid.replace(RESOURCE, "META-INF/openallay/elsewhere/fixture.jar"))) {
            reads.clear();
            assertThrows(IllegalArgumentException.class, () -> open(cache, invalid, bytes));
            assertEquals(List.of(BundledUniversalExtensions.PROVENANCE), reads);
            assertFalse(Files.exists(cache));
            assertArrayEquals(sentinel, Files.readAllBytes(community));
        }
        assertEquals(0, registry.snapshot().generation());
        assertNull(System.getProperty(marker + ".bundled.init"));
    }

    @Test void activeCommunitySameIdWinsWithoutCachePayloadReadOrBundledInitialization() throws Exception {
        Path directory = Files.createDirectory(temporary.resolve("community"));
        byte[] communityBytes = jar(UniversalExtensionFixtures.manifest(ID, "communityfixture.Entry"),
                "communityfixture", compile("communityfixture", "community"));
        Path communityJar = Files.write(directory.resolve("community.jar"), communityBytes);
        byte[] bytes = fixture();
        try (var community = new UniversalExtensionDiscovery(directory, registry,
                UniversalExtensionFixtures.host(worldOpens), getClass().getClassLoader())) {
            assertEquals(OpenAllayExtensionState.ACTIVE, community.discover().getFirst().state());
            assertEquals("1", System.getProperty(marker + ".community.contribution"));
            var snapshot = registry.snapshot();
            Path cache = temporary.resolve("cache");
            try (var bundled = open(cache, provenance(sha256(bytes)), null)) {
                assertEquals(List.of(new UniversalExtensionDiscovery.DiscoveryResult(
                        RESOURCE, ID, OpenAllayExtensionState.ACTIVE, "bundled_extension_overridden")), bundled.results());
                assertEquals(snapshot, registry.snapshot());
                assertEquals(List.of(BundledUniversalExtensions.PROVENANCE), reads);
                assertFalse(Files.exists(cache));
                assertNull(System.getProperty(marker + ".bundled.init"));
                assertNull(System.getProperty(marker + ".bundled.contribution"));
                assertArrayEquals(communityBytes, Files.readAllBytes(communityJar));
            }
        }
    }

    @Test void incompatibleAndUnavailablePackagesStayNonActiveBeforeInitialization() throws Exception {
        byte[] entry = compile("bundledfixture", "bundled");
        String valid = UniversalExtensionFixtures.manifest(ID, ENTRY);
        List<String> declarations = List.of(valid.replace("\"loader\":\"fabric\"", "\"loader\":\"forge\""),
                valid.replace(ENTRY, "bundledfixture.Absent"));
        var states = List.of(OpenAllayExtensionState.INCOMPATIBLE, OpenAllayExtensionState.UNAVAILABLE);
        var diagnostics = List.of("incompatible_loader", "extension_entrypoint_unavailable");
        for (int index = 0; index < declarations.size(); index++) {
            byte[] bytes = jar(declarations.get(index), "bundledfixture", entry);
            String digest = sha256(bytes);
            Path cache = temporary.resolve("nonactive-" + index);
            try (var bundled = open(cache, provenance(digest), bytes)) {
                assertEquals(1, bundled.results().size());
                assertEquals(states.get(index), bundled.results().getFirst().state());
                assertEquals(diagnostics.get(index), bundled.results().getFirst().diagnostic());
                assertArrayEquals(bytes, Files.readAllBytes(cache.resolve(digest).resolve("fixture.jar")));
                assertEquals(0, registry.snapshot().generation());
                assertTrue(registry.snapshot().extensions().isEmpty());
                assertNull(System.getProperty(marker + ".bundled.init"));
            }
        }
    }

    @Test void corruptAndSymlinkedCacheArePreservedWithoutCommunityOverwrite() throws Exception {
        byte[] bytes = fixture(), sentinel = "unique-cache-community-bytes".getBytes(StandardCharsets.UTF_8);
        String digest = sha256(bytes);
        Path community = Files.write(temporary.resolve("community.jar"), sentinel);
        Path corruptCache = temporary.resolve("corrupt"), linkedCache = temporary.resolve("linked");
        Path corrupt = Files.createDirectories(corruptCache.resolve(digest)).resolve("fixture.jar");
        Files.write(corrupt, sentinel);
        Path linked = Files.createDirectories(linkedCache.resolve(digest)).resolve("fixture.jar");
        Files.createSymbolicLink(linked, community);
        for (Path cache : List.of(corruptCache, linkedCache)) {
            assertEquals("Existing bundled Extension cache does not match its checksum", assertThrows(IOException.class,
                    () -> open(cache, provenance(digest), bytes)).getMessage());
            assertArrayEquals(sentinel, Files.readAllBytes(community));
            assertArrayEquals(sentinel, Files.readAllBytes(corrupt));
            assertTrue(Files.isSymbolicLink(linked));
            assertEquals(community, Files.readSymbolicLink(linked));
        }
        assertEquals(0, registry.snapshot().generation());
        assertNull(System.getProperty(marker + ".bundled.init"));
    }

    private BundledUniversalExtensions open(Path cache, String provenance, byte[] bytes) throws IOException {
        return BundledUniversalExtensions.open(cache, registry, UniversalExtensionFixtures.host(worldOpens),
                getClass().getClassLoader(), path -> {
                    reads.add(path);
                    if (path.equals(BundledUniversalExtensions.PROVENANCE)) {
                        return provenance == null ? null : new ByteArrayInputStream(provenance.getBytes(StandardCharsets.UTF_8));
                    }
                    assertEquals(RESOURCE, path);
                    assertNotNull(bytes, "Community precedence must not read bundled payload");
                    return new ByteArrayInputStream(bytes);
                });
    }
    private static String provenance(String digest) {
        return """
                {"source":{"repository":"https://example.invalid/core-fixture","revision":"%s","dirty":false,"pinned":true},
                "project":"Fixture","version":"1.0.0","extensionId":"test:extension","openAllayApiVersion":"0.4.0",
                "artifact":{"path":"%s","sha256":"%s"}}
                """.formatted("a".repeat(40), RESOURCE, digest);
    }
    private byte[] fixture() throws Exception {
        return jar(UniversalExtensionFixtures.manifest(ID, ENTRY), "bundledfixture", compile("bundledfixture", "bundled"));
    }
    private byte[] compile(String packageName, String suffix) throws Exception {
        Path output = Files.createTempDirectory(temporary, "compiler-"), source = output.resolve("Entry.java");
        Files.writeString(source, """
                package %s;
                import dev.openallay.api.extension.*;
                import java.util.*;
                public final class Entry implements OpenAllayExtension {
                    static { System.setProperty("%s.%s.init", "1"); }
                    public Entry() {}
                    public ExtensionDescriptor descriptor() {
                        return new ExtensionDescriptor("test:extension", "Test", "1.0.0", "Test", "Test Extension", "test:source",
                            new SupportDeclaration(Arrays.asList(new SupportTarget("fabric", "[26.2,26.3)",
                                "[0.5,0.6)", "[0.4,0.5)")), 8, Collections.<String>emptySet(),
                                Collections.<String>emptySet()), ExtensionRequirements.EMPTY);
                    }
                    public ExtensionContribution contribution(ExtensionHost host) {
                        System.setProperty("%s.%s.contribution", "1");
                        return ExtensionContribution.empty();
                    }
                }
                """.formatted(packageName, marker, suffix, marker, suffix));
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Bundled fixtures require the root's native JDK test environment");
        Path sdk = Path.of(dev.openallay.api.extension.OpenAllayExtension.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        assertEquals(0, compiler.run(null, null, null, "--release", "8", "-classpath", sdk.toString(),
                "-d", output.toString(), source.toString()));
        return Files.readAllBytes(output.resolve(packageName + "/Entry.class"));
    }
    private static byte[] jar(String manifest, String packageName, byte[] entrypoint) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var jar = new JarOutputStream(bytes)) {
            var entries = new LinkedHashMap<String, byte[]>();
            entries.put(UniversalExtensionManifest.JAR_PATH, manifest.getBytes(StandardCharsets.UTF_8));
            entries.put(packageName + "/Entry.class", entrypoint);
            entries.put(packageName + "/payload.txt", new byte[] {1});
            for (var value : entries.entrySet()) {
                var entry = new JarEntry(value.getKey());
                entry.setTime(0);
                jar.putNextEntry(entry);
                jar.write(value.getValue());
                jar.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
