package dev.openallay.guide.e2e;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source dependency guard; no native class loading or simulated palette acceptance. */
final class NativeMaterialPaletteOracleArchitectureTest {
    private static Path root() {
        Path cwd=Path.of("").toAbsolutePath();
        return Files.isDirectory(cwd.resolve("common/src")) ? cwd : cwd.getParent();
    }
    @Test void commonOracleHasNoAdapterDependencyAndBothClientLoadersRegisterNativeCapture() throws Exception {
        Path root=root();
        String probe=Files.readString(root.resolve("common/src/main/java/dev/openallay/guide/e2e/GuideBuilderE2EProbe.java"));
        String port=Files.readString(root.resolve("common/src/main/java/dev/openallay/guide/e2e/NativeMaterialPaletteOracle.java"));
        assertFalse(probe.contains("dev.openallay.adapter."));
        assertFalse(port.contains("dev.openallay.adapter."));
        assertTrue(probe.contains("NativeMaterialPaletteOracle.capture()"));
        assertTrue(port.contains("public interface Capture { JsonObject capture(); }"));
        assertTrue(port.contains("if(nativeCapture==null)"));
        assertTrue(port.contains("Boolean.getBoolean(\"openallay.e2e.enabled\")"));
        for(String loader:java.util.List.of("fabric/Fabric","neoforge/NeoForge")) {
            String[] parts=loader.split("/");
            String source=Files.readString(root.resolve(parts[0]+"/src/main/java/dev/openallay/"+parts[0]+"/"+parts[1]+"PlatformService.java"));
            assertTrue(source.contains("NativeMaterialPaletteOracle.registerOnce("));
            assertTrue(source.contains("NativeBuilderPaletteProbe::capture"));
            assertTrue(source.indexOf("return java.util.Optional.empty()")<source.indexOf("NativeMaterialPaletteOracle.registerOnce("));
        }
    }
}
