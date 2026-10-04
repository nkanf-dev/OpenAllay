package dev.openallay.extension.universal;

import static org.junit.jupiter.api.Assertions.*;
import java.util.jar.JarFile;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class UniversalSdkFixturePackageTest {
    @Test void sdkHelloFixtureManifestMatchesItsDescriptorAndHasNoBundledSdkClasses() throws Exception {
        var path = java.nio.file.Path.of(System.getProperty("sdkFixtureJar"));
        try (var jar = new JarFile(path.toFile(), false)) {
            var entry = jar.getJarEntry(UniversalExtensionManifest.JAR_PATH);
            assertNotNull(entry);
            String text;
            try (var in = jar.getInputStream(entry)) { text = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
            var manifest = UniversalExtensionManifest.decode(text);
            assertEquals("fixture:hello", manifest.descriptor().id());
            assertEquals("dev.openallay.fixture.HelloExtension", manifest.entrypoint());
            assertEquals(8, manifest.descriptor().support().minimumJavaVersion());
            assertTrue(jar.stream().noneMatch(e -> e.getName().startsWith("dev/openallay/api/extension/")));
            var parent = dev.openallay.api.extension.OpenAllayExtension.class.getClassLoader();
            try (var loader = new java.net.URLClassLoader(new java.net.URL[] {path.toUri().toURL()}, parent)) {
                var type = Class.forName(manifest.entrypoint(), true, loader);
                var fixture = (dev.openallay.api.extension.OpenAllayExtension) type.getConstructor().newInstance();
                assertEquals(manifest.descriptor(), fixture.descriptor());
            }
        }
    }
}
