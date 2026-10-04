package dev.openallay;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.platform.PlatformService;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class UniversalBootstrapEnvironmentTest {
    @ParameterizedTest
    @CsvSource({"Fabric,fabric", "NeoForge,neoforge"})
    void mapsRealLoaderDisplayNamesToPublicSupportIds(String displayName, String expectedId) {
        PlatformService platform = new PlatformService() {
            @Override public String platformName() { return displayName; }
            @Override public String gameVersion() { return "26.2"; }
            @Override public String productVersion() { return "0.4.1"; }
            @Override public boolean isModLoaded(String id) { return false; }
            @Override public boolean isDevelopmentEnvironment() { return true; }
        };
        var environment = OpenAllayBootstrap.universalEnvironment(platform);
        assertEquals(expectedId, environment.loader());
        assertEquals("26.2", environment.minecraftVersion());
        assertEquals("0.4.1", environment.openAllayVersion());
        assertEquals(Set.of("0.2.2", "0.3.0"), environment.openAllayApiVersions());
        assertFalse(environment.hostFeatures().contains("openallay:minecraft_world"));
    }
}
