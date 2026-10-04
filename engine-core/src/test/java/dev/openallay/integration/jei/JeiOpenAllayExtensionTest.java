package dev.openallay.integration.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.extension.OpenAllayExtensionEnvironment;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.extension.OpenAllayExtensionState;
import dev.openallay.integration.RecipeViewerExtensionSnapshot;
import dev.openallay.integration.RecipeViewerExtensionTestFixtures;
import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class JeiOpenAllayExtensionTest {
    @Test
    void registersDetachedProviderProjectionThroughExtensionSpi() {
        JavascriptDataModuleRegistry modules = new JavascriptDataModuleRegistry();
        OpenAllayExtensionRegistry registry = registry(modules);

        OpenAllayExtensionRegistry.Registration registration =
                registry.register(JeiOpenAllayExtension.instance());
        var capture = modules.capture(RecipeViewerExtensionTestFixtures.context(
                RecipeViewerExtensionTestFixtures.provider(
                        "viewer:jei", "test:iron_block", "test:crafting")));

        assertEquals(OpenAllayExtensionState.ACTIVE, registration.state());
        assertEquals(List.of("openallay:jei"), registry.snapshot().extensions().stream()
                .map(extension -> extension.descriptor().id())
                .toList());
        var extensionView = registry.snapshot().extensions().getFirst();
        assertEquals(List.of("openallay:jei"), extensionView.dataModules());
        assertEquals(List.of("openallay:jei_recipe"), extensionView.resultViews());
        RecipeViewerExtensionSnapshot value = assertInstanceOf(
                RecipeViewerExtensionSnapshot.class,
                capture.values().get("openallay:jei"));
        assertTrue(value.available());
        assertEquals(List.of("test:crafting"), value.categories());
        assertEquals(1, value.recipeCount());
        assertEquals("viewer:jei", value.recipes().getFirst().sourceId());
        assertTrue(value.focusSupported());
        assertTrue(value.navigationSupported());
        assertTrue(value.exactNavigationSupported());
        assertTrue(capture.diagnostics().isEmpty());
        assertTrue(capture.evidence().stream()
                .anyMatch(evidence -> evidence.sourceId().equals("viewer:jei")));
        assertEquals(
                Set.of("fabric", "neoforge"),
                JeiOpenAllayExtension.instance().descriptor().loaders());
    }

    private static OpenAllayExtensionRegistry registry(
            JavascriptDataModuleRegistry modules) {
        return new OpenAllayExtensionRegistry(
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0"),
                modules,
                new JavascriptModuleCatalog(Map.of()),
                new SkillRepository(new SkillParser(), List.of("openallay:run_javascript")),
                Set.of("jei"));
    }
}
