package dev.openallay.internal.maven;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolutionException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

final class PrivateMavenRuntimeTest {
    private static final String UPSTREAM = "org/apache/maven/";
    private static final String PRIVATE = "dev/openallay/internal/maven/";
    private static final Set<String> ROOTS = Set.of(
            UPSTREAM + "artifact/versioning/ComparableVersion",
            UPSTREAM + "artifact/versioning/DefaultArtifactVersion",
            UPSTREAM + "artifact/versioning/InvalidVersionSpecificationException",
            UPSTREAM + "artifact/versioning/VersionRange");
    @TempDir Path temporary;

    private static Path artifact(String name) {
        return Path.of(System.getProperty(name));
    }

    private static Map<String, byte[]> classes(Path path) throws IOException {
        Map<String, byte[]> result = new HashMap<>();
        try (var jar = new JarFile(path.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    assertNull(result.put(entry.getName().replaceFirst("\\.class$", ""),
                            jar.getInputStream(entry).readAllBytes()), "Duplicate class");
                }
            }
        }
        return result;
    }

    // ASM's remapper visits bytecode types, field/method descriptors, signatures,
    // annotations and exceptions. This checks unused public signatures as well.
    private static Set<String> references(byte[] bytes) {
        Set<String> result = new HashSet<>();
        var remapper = new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) {
                result.add(name);
                return name;
            }
        };
        new ClassReader(bytes).accept(new ClassRemapper(new ClassWriter(0), remapper), 0);
        return result;
    }

    @Test void exactUpstreamClosureIsRelocatedAndHasNoHiddenDependency() throws Exception {
        var original = classes(artifact("upstreamMaven.jar"));
        Set<String> derived = new TreeSet<>();
        var pending = new ArrayDeque<>(ROOTS);
        while (!pending.isEmpty()) {
            var name = pending.removeFirst();
            if (!derived.add(name)) continue;
            assertNotNull(original.get(name), name);
            for (var reference : references(original.get(name))) {
                if (original.containsKey(reference)) pending.add(reference);
                else assertTrue(reference.startsWith("java/"), "Unbundled upstream type: " + reference);
            }
        }
        var declared = new TreeSet<>(Arrays.asList(System.getProperty("privateMaven.classes").split(",")));
        assertEquals(derived, declared.stream().map(s -> s.replaceFirst("\\.class$", ""))
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new)));
        var relocated = classes(artifact("privateMaven.jar"));
        assertEquals(derived.stream().map(s -> PRIVATE + s.substring(UPSTREAM.length()))
                .collect(java.util.stream.Collectors.toSet()), relocated.keySet());
        for (var entry : relocated.entrySet()) {
            byte[] bytes = entry.getValue();
            assertTrue((((bytes[6] & 255) << 8) | (bytes[7] & 255)) <= 61, "Java17 floor");
            for (var reference : references(bytes)) {
                assertTrue(reference.startsWith("java/") || relocated.containsKey(reference),
                        "Unbundled relocated type: " + reference);
            }
        }
        try (var loader = new URLClassLoader(new java.net.URL[]{artifact("privateMaven.jar").toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            for (var name : relocated.keySet()) {
                var type = Class.forName(name.replace('/', '.'), false, loader);
                type.getDeclaredConstructors();
                type.getDeclaredMethods();
                type.getDeclaredFields();
            }
        }
        try (var input = new JarFile(artifact("upstreamMaven.jar").toFile());
             var output = new JarFile(artifact("privateMaven.jar").toFile())) {
            for (var name : List.of("LICENSE", "NOTICE", "DEPENDENCIES")) {
                assertArrayEquals(input.getInputStream(input.getJarEntry("META-INF/" + name)).readAllBytes(),
                        output.getInputStream(output.getJarEntry("META-INF/licenses/maven-artifact/" + name)).readAllBytes());
            }
        }
    }

    private static Configuration layer(Path jar, String root, Configuration... parents) {
        return Configuration.resolveAndBind(ModuleFinder.of(jar), List.of(parents), ModuleFinder.of(), Set.of(root));
    }

    @Test void duplicateRawMavenFailsButThePrivateRuntimeResolvesAndExecutes() throws Exception {
        var historical = artifact("loaderMaven.jar");
        var current = artifact("upstreamMaven.jar");
        assertEquals("maven.artifact", ModuleFinder.of(historical).findAll().iterator().next().descriptor().name());
        assertEquals("maven.artifact", ModuleFinder.of(current).findAll().iterator().next().descriptor().name());
        var library = layer(historical, "maven.artifact", ModuleLayer.boot().configuration());
        var plugin = layer(current, "maven.artifact", ModuleLayer.boot().configuration());
        var reader = temporary.resolve("openallay-reader.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(reader))) {
            // A real automatic reader reproduces the parent-configuration ambiguity.
            output.putNextEntry(new JarEntry("reader.txt"));
            output.write(1);
            output.closeEntry();
        }
        assertThrows(ResolutionException.class, () -> layer(reader, "openallay.reader", library, plugin));
        var privateJar = artifact("privateMaven.jar");
        var descriptor = ModuleFinder.of(privateJar).findAll().iterator().next().descriptor();
        assertNotEquals("maven.artifact", descriptor.name());
        assertTrue(descriptor.packages().stream().allMatch(p -> p.startsWith("dev.openallay.internal.maven.")));
        var privatePlugin = layer(privateJar, descriptor.name(), ModuleLayer.boot().configuration());
        assertDoesNotThrow(() -> layer(reader, "openallay.reader", library, privatePlugin));
        var game = layer(privateJar, descriptor.name(), library);
        var libraryLayer = ModuleLayer.boot().defineModulesWithOneLoader(library, ClassLoader.getPlatformClassLoader());
        var gameLayer = ModuleLayer.defineModulesWithOneLoader(game, List.of(libraryLayer),
                ClassLoader.getPlatformClassLoader()).layer();
        var type = gameLayer.findLoader(descriptor.name()).loadClass(
                "dev.openallay.internal.maven.artifact.versioning.VersionRange");
        var range = type.getMethod("createFromVersionSpec", String.class).invoke(null, "[1.20.1,1.21.2)");
        var version = gameLayer.findLoader(descriptor.name()).loadClass(
                "dev.openallay.internal.maven.artifact.versioning.DefaultArtifactVersion");
        var abi = gameLayer.findLoader(descriptor.name()).loadClass(
                "dev.openallay.internal.maven.artifact.versioning.ArtifactVersion");
        assertEquals(true, type.getMethod("containsVersion", abi).invoke(range,
                version.getConstructor(String.class).newInstance("1.21.1")));
    }
}
