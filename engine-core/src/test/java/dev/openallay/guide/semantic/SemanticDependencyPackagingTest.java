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

    private static List<String> modules() {
        return List.of("org.commonmark:commonmark:${commonmark_version}",
                "org.commonmark:commonmark-ext-gfm-tables:${commonmark_version}",
                "org.xerial:sqlite-jdbc:${sqlite_jdbc_version}");
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
