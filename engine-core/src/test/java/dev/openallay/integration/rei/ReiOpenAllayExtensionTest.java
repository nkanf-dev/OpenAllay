package dev.openallay.integration.rei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openallay.extension.OpenAllayExtensionEnvironment;
import dev.openallay.extension.OpenAllayExtensionRegistry;
import dev.openallay.extension.OpenAllayExtensionState;
import dev.openallay.integration.RecipeViewerExtensionSnapshot;
import dev.openallay.integration.RecipeViewerExtensionTestFixtures;
import dev.openallay.recipe.RecipeProviderSnapshot;
import dev.openallay.script.JavascriptModuleCatalog;
import dev.openallay.script.extension.JavascriptDataModuleRegistry;
import dev.openallay.skill.SkillParser;
import dev.openallay.skill.SkillRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ReiOpenAllayExtensionTest {
    @Test
    void keepsJeiFailureIndependentFromReiProjection() {
        JavascriptDataModuleRegistry modules = new JavascriptDataModuleRegistry();
        OpenAllayExtensionRegistry registry = registry(modules);
        assertEquals(
                OpenAllayExtensionState.ACTIVE,
                registry.register(
                                dev.openallay.integration.jei.JeiOpenAllayExtension.instance())
                        .state());
        assertEquals(
                OpenAllayExtensionState.ACTIVE,
                registry.register(ReiOpenAllayExtension.instance()).state());

        var capture = modules.capture(RecipeViewerExtensionTestFixtures.context(
                RecipeProviderSnapshot.failed(
                        "viewer:jei", "runtime_failure", "JEI fixture failed"),
                RecipeViewerExtensionTestFixtures.provider(
                        "viewer:rei", "test:gold_block", "test:crafting")));

        RecipeViewerExtensionSnapshot jei = assertInstanceOf(
                RecipeViewerExtensionSnapshot.class,
                capture.values().get("openallay:jei"));
        RecipeViewerExtensionSnapshot rei = assertInstanceOf(
                RecipeViewerExtensionSnapshot.class,
                capture.values().get("openallay:rei"));
        assertFalse(jei.available());
        assertEquals("runtime_failure", jei.diagnostics().getFirst().code());
        assertTrue(rei.available());
        assertEquals(1, rei.recipeCount());
        assertTrue(rei.focusSupported());
        assertTrue(rei.navigationSupported());
        assertFalse(rei.exactNavigationSupported());
        assertTrue(capture.diagnostics().isEmpty());
        assertEquals(
                Set.of("fabric", "neoforge"),
                ReiOpenAllayExtension.instance().descriptor().loaders());
    }

    private static OpenAllayExtensionRegistry registry(
            JavascriptDataModuleRegistry modules) {
        return new OpenAllayExtensionRegistry(
                new OpenAllayExtensionEnvironment("fabric", "26.2", "0.2.0"),
                modules,
                new JavascriptModuleCatalog(Map.of()),
                new SkillRepository(new SkillParser(), List.of("openallay:run_javascript")),
                Set.of("jei", "roughlyenoughitems"));
    }
}
