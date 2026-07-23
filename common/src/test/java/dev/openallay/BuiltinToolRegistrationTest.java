package dev.openallay;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.openallay.platform.PlatformService;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class BuiltinToolRegistrationTest {
    @Test
    void exposesJavascriptAnalysisAndOnlyTheNarrowDeterministicAction() {
        Set<String> toolIds = OpenAllayBootstrap.builtinTools(testPlatform()).stream()
                .map(tool -> tool.descriptor().id())
                .collect(Collectors.toSet());

        assertTrue(toolIds.contains("openallay:run_javascript"));
        assertTrue(toolIds.contains("openallay:calculate_craftability"));
        assertFalse(toolIds.contains("openallay:search_recipes"));
        assertFalse(toolIds.contains("openallay:inspect_game_state"));
        assertFalse(toolIds.contains("openallay:resolve_resource"));
        assertTrue(toolIds.size() == 2);
    }

    private static PlatformService testPlatform() {
        return new PlatformService() {
            @Override public String platformName() { return "common-test"; }
            @Override public String gameVersion() { return "test"; }
            @Override public boolean isModLoaded(String modId) { return false; }
            @Override public boolean isDevelopmentEnvironment() { return true; }
        };
    }
}
