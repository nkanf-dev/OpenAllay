package dev.openallay.guide.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SemanticDependencyPackagingTest {
    @Test
    void bothLoadersBundleTheSamePinnedHistoryAndSemanticModules() throws Exception {
        Path root = repositoryRoot();
        String properties = Files.readString(root.resolve("gradle.properties"));
        assertTrue(properties.contains("commonmark_version=0.28.0"));
        String engine = Files.readString(root.resolve("engine-core/build.gradle"));
        String fabric = Files.readString(root.resolve("fabric/build.gradle"));
        String fml = Files.readString(root.resolve("gradle/fml-loader.gradle"));
        for (String loader : List.of("neoforge", "forge")) {
            String source = Files.readString(root.resolve(loader + "/build.gradle"));
            assertTrue(source.contains("apply from: rootProject.file('gradle/fml-loader.gradle')"), loader);
            for (String module : modules()) assertEquals(0, occurrences(source, module), loader);
        }
        for (String module : modules()) {
            assertEquals(1, occurrences(engine, module), "one canonical engine dependency: " + module);
            assertTrue(fabric.contains("implementation(include(\"" + module + "\"))"), module);
            assertTrue(fml.contains("implementation(\"" + module + "\")"), module);
            assertTrue(fml.contains("jarJar(\"" + module + "\")"), module);
            assertEquals(1, occurrences(fabric, module), module);
            assertEquals(2, occurrences(fml, module), module);
        }
        for (String source : List.of(fabric, fml)) {
            assertTrue(source.contains("implementation(project(\":engine-core\"))"));
            assertTrue(source.contains("from({ project(':engine-core').sourceSets.main.output })"));
        }
    }

    @Test
    void commonMarkHasOneProvedSourceOwnerAcrossEngineAndLoaders() throws Exception {
        Path root = repositoryRoot();
        String engine = Files.readString(root.resolve("engine-core/build.gradle"));
        String common = Files.readString(root.resolve("common/build.gradle"));
        String fabric = Files.readString(root.resolve("fabric/build.gradle"));
        String fml = Files.readString(root.resolve("gradle/fml-loader.gradle"));
        for (String source : List.of(engine, common, fabric, fml)) {
            assertEquals(0, occurrences(source, "org.commonmark:commonmark:"));
            assertEquals(0, occurrences(source, "org.commonmark:commonmark-ext-gfm-tables:"));
        }
        assertEquals(1, occurrences(engine, "implementation(project(':runtime-commonmark'))"));
        assertEquals(1, occurrences(common, "implementation(project(':runtime-commonmark'))"));
        assertEquals(1, occurrences(fabric, "implementation(include(project(':runtime-commonmark')))"));
        assertEquals(1, occurrences(fml, "implementation(project(':runtime-commonmark'))"));
        assertEquals(1, occurrences(fml, "jarJar(project(':runtime-commonmark'))"));
        assertEquals(1, occurrences(Files.readString(root.resolve("settings.gradle")), "include('runtime-commonmark')"));
        String runtime = Files.readString(root.resolve("runtime-commonmark/build.gradle"));
        assertEquals(1, occurrences(runtime, "upstreamCoreSources(\"org.commonmark:commonmark:${commonmark_version}:sources@jar\")"));
        assertEquals(1, occurrences(runtime, "upstreamTablesSources(\"org.commonmark:commonmark-ext-gfm-tables:${commonmark_version}:sources@jar\")"));
        assertTrue(runtime.contains("options.release = 8"));
        assertTrue(runtime.contains("options.compilerArgs.add('-Xpkginfo:always')"));
        assertTrue(runtime.contains("withSourcesJar()"));
        String license = Files.readString(root.resolve("runtime-commonmark/LICENSE-commonmark.txt"));
        assertTrue(license.contains("Copyright (c) 2015, Atlassian Pty Ltd"));
        assertTrue(license.contains("Redistribution and use in source and binary forms"));
        String packaging = Files.readString(root.resolve("scripts/build-minecraft-artifacts.py"));
        assertTrue(packaging.contains("dff5404332182c794aec52538a9a620b61032041a3b08ddbb972ddf246021a02"));
        assertTrue(packaging.contains("commonmark_package(archive, entries, family[\"loader\"])"));
        assertTrue(packaging.contains("len(classes) == 213"));
    }

    private static List<String> modules() {
        return List.of("org.xerial:sqlite-jdbc:${sqlite_jdbc_version}");
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }

    private static int occurrences(String source, String value) {
        return source.split(java.util.regex.Pattern.quote(value), -1).length - 1;
    }
}
