package dev.openallay.extension;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ExtensionRuntimeCompatibilityTest {
    @Test void exactBracketRangeUsesTheExistingComparator() {
        assertEquals("[1.12.2]", ExtensionCompatibility.requireRange(" [1.12.2] ", "game"));
        assertTrue(ExtensionCompatibility.includes("[1.12.2]", "1.12.2"));
        assertFalse(ExtensionCompatibility.includes("[1.12.2]", "1.12.1"));
        assertFalse(ExtensionCompatibility.includes("[1.12.2]", "1.12.3"));
        assertThrows(IllegalArgumentException.class, () -> ExtensionCompatibility.requireRange("(1.12.2)", "game"));
        assertThrows(IllegalArgumentException.class, () -> ExtensionCompatibility.requireRange("[]", "game"));
    }
    @Test void oldConstructorImplementsOnlyItsDeclaredLegacyApi() {
        var environment = new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.2");
        assertEquals(Set.of("0.2.2"), environment.implementedApiVersions());
        assertEquals("incompatible_openallay_api", environment.incompatibility(descriptor("[0.3.0,0.4.0)")));
        assertEquals("", environment.incompatibility(descriptor("[0.2.0,0.3.0)")));
    }
    @Test void additiveImplementedApisPreserveTheLegacyCoordinateAndAcceptEachActualApi() {
        var environment = new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.2", Set.of("0.2.2", "0.3.0"));
        assertEquals("0.2.2", environment.openAllayApiVersion());
        assertEquals("", environment.incompatibility(descriptor("[0.3.0,0.4.0)")));
        assertEquals("", environment.incompatibility(descriptor("[0.2.0,0.3.0)")));
        assertEquals("incompatible_openallay_api", environment.incompatibility(descriptor("[0.4.0,0.5.0)")));
        assertThrows(IllegalArgumentException.class,
                () -> new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.2", Set.of("0.3.0")));
    }
    private static OpenAllayExtensionDescriptor descriptor(String range) {
        return new OpenAllayExtensionDescriptor("test:version", "Test", "1.0.0", "Test", "Test", Set.of("fabric"), "[26.2,26.3)", range, "test");
    }
}
