package dev.openallay.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class RecipeViewerApiCompatibilityTest {
    @Test
    void jeiPublicApiExposesEnumerationSlotsLifecycleAndNavigation() throws IOException {
        assertSymbols("mezz.jei.api.IModPlugin", "onRuntimeAvailable", "onRuntimeUnavailable");
        assertSymbols("mezz.jei.api.runtime.IJeiRuntime", "getRecipeManager", "getRecipesGui");
        assertSymbols("mezz.jei.api.recipe.IRecipeManager", "createRecipeCategoryLookup");
        assertSymbols("mezz.jei.api.recipe.IRecipeCategoriesLookup", "includeHidden", "get");
        assertSymbols(
                "mezz.jei.api.gui.IRecipeLayoutDrawable",
                "getRecipeSlotsView",
                "setPosition",
                "drawRecipe",
                "drawOverlays",
                "tick");
        assertSymbols("mezz.jei.api.gui.ingredient.IRecipeSlotDrawablesView", "getSlots");
        assertSymbols("mezz.jei.api.runtime.IRecipesGui", "show");
    }

    @Test
    void reiPublicApiExposesDisplaysGroupedEntriesAndNavigation() throws IOException {
        assertSymbols(
                "me.shedaniel.rei.api.client.registry.display.DisplayRegistry", "getInstance");
        assertSymbols(
                "me.shedaniel.rei.api.common.registry.display.DisplayRegistryCommon", "getAll");
        assertSymbols(
                "me.shedaniel.rei.api.common.display.Display",
                "getInputEntries",
                "getOutputEntries",
                "getDisplayLocation");
        assertSymbols("me.shedaniel.rei.api.common.entry.EntryStack", "getIdentifier");
        assertSymbols(
                "me.shedaniel.rei.api.client.view.ViewSearchBuilder",
                "builder",
                "addRecipesFor",
                "addUsagesFor",
                "open");
        assertSymbols(
                "me.shedaniel.rei.api.client.registry.category.CategoryRegistry",
                "getInstance",
                "tryGet");
        assertSymbols(
                "me.shedaniel.rei.api.client.registry.display.DisplayCategoryView",
                "setupDisplay");
        assertSymbols(
                "me.shedaniel.rei.api.client.gui.widgets.Widget",
                "extractRenderState");
    }

    @Test
    void fabricMetadataRejectsOnlyKnownBrokenArchitecturyRange() throws IOException {
        Path root = repositoryRoot();
        var metadata = com.google.gson.JsonParser.parseString(Files.readString(
                root.resolve("fabric/src/main/resources/fabric.mod.json"))).getAsJsonObject();
        String properties = Files.readString(root.resolve("gradle.properties"));
        String readme = Files.readString(root.resolve("README.md"));
        String development = Files.readString(root.resolve("docs/development.md"));
        String chineseReadme = Files.readString(root.resolve("README.zh-CN.md"));

        assertEquals("<=21.0.2", metadata.getAsJsonObject("breaks")
                .get("architectury").getAsString());
        assertTrue(properties.contains("architectury_version=21.0.4"));
        assertTrue(readme.contains("use **21.0.4** for working text"));
        assertTrue(readme.contains("**21.0.2 and earlier** prevent text input"));
        assertTrue(development.contains("Version 21.0.3 has not been verified"));
        assertTrue(chineseReadme.contains("请使用 **21.0.4**"));
        assertTrue(chineseReadme.contains("**21.0.2 及更早版本**会导致文字输入失效"));
    }

    private void assertSymbols(String className, String... symbols) throws IOException {
        String resource = className.replace('.', '/') + ".class";
        byte[] classfile;
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertTrue(input != null, () -> "Missing API class " + className);
            classfile = input.readAllBytes();
        }
        String constantPool = new String(classfile, StandardCharsets.ISO_8859_1);
        for (String symbol : symbols) {
            assertTrue(
                    constantPool.contains(symbol),
                    () -> className + " no longer exposes symbol " + symbol);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("common"))) {
            return current;
        }
        if (current.getFileName() != null && current.getFileName().toString().equals("common")) {
            return current.getParent();
        }
        throw new IllegalStateException("Unable to locate repository root");
    }
}
