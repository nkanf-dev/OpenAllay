package dev.openallay.extension.universal;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.extension.OpenAllayExtensionState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UniversalExtensionDiscoveryTest {
    @TempDir Path temporary;

    @Test void missingDirectoryIsEmptyAndDiscoveryIsSingleShot() {
        Path missing = temporary.resolve("missing");
        try (var discovery = discovery(missing)) {
            assertTrue(discovery.discover().isEmpty());
            assertSame(discovery.discover(), discovery.discover());
            assertFalse(Files.exists(missing));
        }
    }
    @Test void malformedIncompatibleAndShadowPackagesNeverInitializeEntrypoints() throws Exception {
        Path directory = Files.createDirectory(temporary.resolve("extensions"));
        String property = "openallay.test." + UUID.randomUUID();
        String manifest = UniversalExtensionFixtures.manifest("test:extension", "community.Entry");
        byte[] entrypoint = compile("Entry", property, false);
        jar(directory.resolve("a-incompatible.jar"), manifest.replace("\"loader\":\"fabric\"", "\"loader\":\"forge\""),
                Map.of("community/Entry.class", entrypoint));
        jar(directory.resolve("b-malformed.jar"), manifest.replace("\"schemaVersion\":2", "\"schemaVersion\":3"),
                Map.of("community/Entry.class", entrypoint));
        jar(directory.resolve("c-sdk-shadow.jar"), manifest,
                Map.of("community/Entry.class", entrypoint, "dev/openallay/api/extension/Shadow.class", new byte[] {1}));
        jar(directory.resolve("d-core-shadow.jar"), manifest,
                Map.of("community/Entry.class", entrypoint,
                        "dev/openallay/bridge/protocol/BridgeJsonCodec.class", new byte[] {1}));
        jar(directory.resolve("e-native.jar"), manifest,
                Map.of("community/Entry.class", entrypoint, "native/lib.dll", new byte[] {1}));
        try (var discovery = discovery(directory)) {
            var results = discovery.discover();
            assertEquals(List.of("a-incompatible.jar", "b-malformed.jar", "c-sdk-shadow.jar", "d-core-shadow.jar", "e-native.jar"),
                    results.stream().map(UniversalExtensionDiscovery.DiscoveryResult::filename).toList());
            assertEquals(OpenAllayExtensionState.INCOMPATIBLE, results.getFirst().state(),
                    results.getFirst().toString());
            assertTrue(results.stream().skip(1).allMatch(value -> value.state() == OpenAllayExtensionState.UNAVAILABLE));
            assertNull(System.getProperty(property + ".init"));
            assertNull(System.getProperty(property + ".contribution"));
        } finally { clear(property); }
    }
    @Test void duplicateIdsAreRejectedAsAnAmbiguousBatchBeforeClassInitialization() throws Exception {
        Path directory = Files.createDirectory(temporary.resolve("extensions"));
        String property = "openallay.test." + UUID.randomUUID();
        String manifest = UniversalExtensionFixtures.manifest("test:extension", "community.Entry");
        byte[] entrypoint = compile("Entry", property, false);
        jar(directory.resolve("b.jar"), manifest, Map.of("community/Entry.class", entrypoint));
        jar(directory.resolve("a.jar"), manifest, Map.of("community/Entry.class", entrypoint));
        try (var discovery = discovery(directory)) {
            assertEquals(List.of("duplicate_extension_id", "duplicate_extension_id"), discovery.discover().stream()
                    .map(UniversalExtensionDiscovery.DiscoveryResult::diagnostic).toList());
            assertNull(System.getProperty(property + ".init"));
        } finally { clear(property); }
    }
    @Test void descriptorMismatchRunsNoContributionAndPublishesNothing() throws Exception {
        Path directory = Files.createDirectory(temporary.resolve("extensions"));
        String property = "openallay.test." + UUID.randomUUID();
        jar(directory.resolve("extension.jar"), UniversalExtensionFixtures.manifest("test:extension", "community.Entry"),
                Map.of("community/Entry.class", compile("Entry", property, true)));
        var registry = UniversalExtensionFixtures.registry();
        try (var discovery = new UniversalExtensionDiscovery(directory, registry,
                UniversalExtensionFixtures.host(new AtomicInteger()), getClass().getClassLoader())) {
            assertEquals("extension_descriptor_mismatch", discovery.discover().getFirst().diagnostic());
            assertEquals("1", System.getProperty(property + ".init"));
            assertNull(System.getProperty(property + ".contribution"));
            assertEquals(0, registry.snapshot().generation());
        } finally { clear(property); }
    }
    @Test void acceptsOneExplicitEntrypointAndKeepsLoaderUntilFrameworkClose() throws Exception {
        Path directory = Files.createDirectory(temporary.resolve("extensions"));
        String property = "openallay.test." + UUID.randomUUID();
        jar(directory.resolve("extension.jar"), UniversalExtensionFixtures.manifest("test:extension", "community.Entry"),
                Map.of("community/Entry.class", compile("Entry", property, false), "community/payload.txt", new byte[] {1}));
        AtomicInteger worldOpens = new AtomicInteger();
        var registry = UniversalExtensionFixtures.registry();
        var discovery = new UniversalExtensionDiscovery(directory, registry,
                UniversalExtensionFixtures.host(worldOpens), getClass().getClassLoader());
        try {
            assertEquals(OpenAllayExtensionState.ACTIVE, discovery.discover().getFirst().state());
            assertEquals("1", System.getProperty(property + ".contribution"));
            assertEquals(0, worldOpens.get());
            assertEquals(1, registry.snapshot().generation());
            var field = UniversalExtensionDiscovery.class.getDeclaredField("loaders");
            field.setAccessible(true);
            var loaders = (List<?>) field.get(discovery);
            assertEquals(1, loaders.size());
            var loader = (java.net.URLClassLoader) loaders.getFirst();
            assertNotNull(loader.findResource("community/payload.txt"));
            discovery.close();
            assertNull(loader.findResource("community/payload.txt"));
            assertTrue(loaders.isEmpty());
            assertDoesNotThrow(discovery::close);
            assertThrows(IllegalStateException.class, discovery::discover);
        } finally { discovery.close(); clear(property); }
    }
    @Test void allowsCommunityFixtureAndBuilderPackagesWithinTheBrandNamespace() throws Exception {
        for (String packageName : List.of("dev.openallay.fixture", "dev.openallay.builder")) {
            Path directory = Files.createTempDirectory(temporary, "extensions-");
            String property = "openallay.test." + UUID.randomUUID();
            String className = packageName + ".HelloExtension";
            jar(directory.resolve("extension.jar"), UniversalExtensionFixtures.manifest("test:extension", className),
                    Map.of(className.replace('.', '/') + ".class", compile(packageName, "HelloExtension", property, false)));
            try (var discovery = discovery(directory)) {
                assertEquals(OpenAllayExtensionState.ACTIVE, discovery.discover().getFirst().state());
                assertEquals("1", System.getProperty(property + ".contribution"));
            } finally { clear(property); }
        }
    }
    @Test void requiresPublicNoArgAndRejectsExternalManifestClasspathBeforeInitialization() throws Exception {
        Path directory = Files.createDirectory(temporary.resolve("extensions"));
        String property = "openallay.test." + UUID.randomUUID();
        byte[] entrypoint = compile("community", "Entry", property, false, "private");
        jar(directory.resolve("private.jar"), UniversalExtensionFixtures.manifest("test:private", "community.Entry"),
                Map.of("community/Entry.class", entrypoint));
        byte[] publicEntry = compile("community", "Entry", property, false);
        jar(directory.resolve("classpath.jar"), UniversalExtensionFixtures.manifest("test:classpath", "community.Entry"),
                Map.of("community/Entry.class", publicEntry, "META-INF/MANIFEST.MF",
                        "Manifest-Version: 1.0\r\nClass-Path: elsewhere.jar\r\n\r\n".getBytes(StandardCharsets.UTF_8)));
        try (var discovery = discovery(directory)) {
            assertEquals(List.of("extension_package_external_classpath", "extension_entrypoint_failed"),
                    discovery.discover().stream().map(UniversalExtensionDiscovery.DiscoveryResult::diagnostic).toList());
            assertNull(System.getProperty(property + ".init"));
        } finally { clear(property); }
    }
    @Test void resourceLimitsFailExplicitlyWithoutDroppingEntries() throws Exception {
        Path directory = Files.createDirectory(temporary.resolve("extensions"));
        jar(directory.resolve("a.jar"), "{}", Map.of());
        jar(directory.resolve("b.jar"), "{}", Map.of());
        try (var discovery = new UniversalExtensionDiscovery(directory, UniversalExtensionFixtures.registry(),
                UniversalExtensionFixtures.host(new AtomicInteger()), getClass().getClassLoader(),
                new UniversalExtensionDiscovery.Limits(10_000, 20, 10_000, 1_000, 1))) {
            assertEquals("extension_package_limit_exceeded", discovery.discover().getFirst().diagnostic());
            assertEquals(1, discovery.discover().size());
        }
    }
    private UniversalExtensionDiscovery discovery(Path directory) {
        return new UniversalExtensionDiscovery(directory, UniversalExtensionFixtures.registry(),
                UniversalExtensionFixtures.host(new AtomicInteger()), getClass().getClassLoader());
    }
    private byte[] compile(String name, String property, boolean mismatch) throws IOException {
        return compile("community", name, property, mismatch);
    }
    private byte[] compile(String packageName, String name, String property, boolean mismatch) throws IOException {
        return compile(packageName, name, property, mismatch, "public");
    }
    private byte[] compile(String packageName, String name, String property, boolean mismatch,
            String constructorAccess) throws IOException {
        Path output = Files.createTempDirectory(temporary, "compiler-");
        Path source = output.resolve(name + ".java");
        String code = """
                package %s;
                import dev.openallay.api.extension.*;
                import java.util.*;
                public final class %s implements OpenAllayExtension {
                    static { System.setProperty("%s.init", "1"); }
                    %s %s() {}
                    public ExtensionDescriptor descriptor() {
                        return new ExtensionDescriptor("%s", "Test", "1.0.0", "Test", "Test Extension", "test:source",
                            new SupportDeclaration(Arrays.asList(new SupportTarget("fabric", "[26.2,26.3)",
                                "[0.5,0.6)", "[0.3,0.4)")), 8, Collections.<String>emptySet(),
                                Collections.<String>emptySet()), ExtensionRequirements.EMPTY);
                    }
                    public ExtensionContribution contribution(ExtensionHost host) {
                        System.setProperty("%s.contribution", "1");
                        return ExtensionContribution.empty();
                    }
                }
                """.formatted(packageName, name, property, constructorAccess, name,
                        mismatch ? "test:mismatch" : "test:extension", property);
        Files.writeString(source, code);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Discovery fixtures require the root's native JDK test environment");
        Path sdk;
        try {
            sdk = Path.of(dev.openallay.api.extension.OpenAllayExtension.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (java.net.URISyntaxException invalid) {
            throw new IOException("SDK test classpath URI is invalid", invalid);
        }
        assertEquals(0, compiler.run(null, null, null, "--release", "8", "-classpath", sdk.toString(),
                "-d", output.toString(), source.toString()));
        return Files.readAllBytes(output.resolve(packageName.replace('.', '/') + "/" + name + ".class"));
    }
    private static void jar(Path target, String manifest, Map<String, byte[]> files) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(UniversalExtensionManifest.JAR_PATH, manifest.getBytes(StandardCharsets.UTF_8));
        entries.putAll(files);
        try (var output = new JarOutputStream(Files.newOutputStream(target))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
    }
    private static void clear(String property) {
        System.clearProperty(property + ".init");
        System.clearProperty(property + ".contribution");
    }
}
