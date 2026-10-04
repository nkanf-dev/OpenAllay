package dev.openallay.extension.universal;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.api.extension.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UniversalExtensionSupportTest {
    @Test void supportsExplicitOldAndNewTargetsWithRealApiSet() {
        var forge = new SupportTarget("forge", "1.12.2", "[0.5,0.6)", "[0.3,0.4)");
        var current = UniversalExtensionFixtures.target();
        var support = new SupportDeclaration(List.of(forge, current), 8, Set.of(), Set.of("forge_1.12.2"));
        var old = new ExtensionEnvironment("forge", "1.12.2", "0.5.0", Set.of("0.2.2", "0.3.0"), 8, Set.of());
        assertEquals(forge, UniversalExtensionSupport.matchingTarget(support, old).orElseThrow());
        assertEquals(current, UniversalExtensionSupport.matchingTarget(support,
                UniversalExtensionFixtures.environment()).orElseThrow());
        var descriptor = new ExtensionDescriptor("test:union", "Test", "1", "Test", "Test", "test:source",
                support, ExtensionRequirements.EMPTY);
        assertEquals("[0.3,0.4)", UniversalExtensionSupport.legacyDescriptor(descriptor, forge)
                .openAllayApiVersionRange());
    }
    @Test void validationLabelsDoNotGrantOrGateAndCoordinatesRemainConjunctive() {
        var support = new SupportDeclaration(List.of(UniversalExtensionFixtures.target()), 8,
                Set.of(), Set.of("unrelated"));
        assertEquals("", UniversalExtensionSupport.incompatibility(support,
                UniversalExtensionFixtures.environment()));
        for (var env : List.of(
                new ExtensionEnvironment("forge", "26.2", "0.5.0", Set.of("0.3.0"), 25, Set.of()),
                new ExtensionEnvironment("fabric", "1.12.2", "0.5.0", Set.of("0.3.0"), 25, Set.of()),
                new ExtensionEnvironment("fabric", "26.2", "0.4.1", Set.of("0.3.0"), 25, Set.of()),
                new ExtensionEnvironment("fabric", "26.2", "0.5.0", Set.of("0.2.2"), 25, Set.of()))) {
            assertTrue(UniversalExtensionSupport.matchingTarget(support, env).isEmpty());
        }
    }
    @Test void requiresJavaAndActualHostFeatures() {
        var support = new SupportDeclaration(List.of(UniversalExtensionFixtures.target()), 21,
                Set.of("test:feature"), Set.of());
        assertEquals("incompatible_java_version", UniversalExtensionSupport.incompatibility(support,
                new ExtensionEnvironment("fabric", "26.2", "0.5.0", Set.of("0.3.0"), 8, Set.of())));
        assertEquals("incompatible_host_features", UniversalExtensionSupport.incompatibility(support,
                UniversalExtensionFixtures.environment()));
        assertTrue(UniversalExtensionSupport.matchingTarget(support,
                new ExtensionEnvironment("fabric", "26.2", "0.5.0", Set.of("0.3.0"), 25,
                        Set.of("test:feature"))).isPresent());
    }
    @Test void exactIntervalsUseTheMatureCoreComparator() {
        var target = new SupportTarget("fabric", "[26.2]", "[0.5.0]", "[0.3.0]");
        assertEquals(target, UniversalExtensionSupport.matchingTarget(
                new SupportDeclaration(List.of(target), 8, Set.of(), Set.of()),
                UniversalExtensionFixtures.environment()).orElseThrow());
    }
}
