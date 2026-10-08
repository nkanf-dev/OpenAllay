package dev.openallay.integration.jei;

import dev.openallay.extension.OpenAllayExtension;
import dev.openallay.extension.OpenAllayExtensionContribution;
import dev.openallay.extension.OpenAllayExtensionDescriptor;
import dev.openallay.integration.RecipeViewerExtensionDataModule;
import dev.openallay.script.result.JavascriptResultViewProvider;
import dev.openallay.script.result.JavascriptSemanticKind;
import java.util.List;
import java.util.Set;

/** First-party JEI projection registered through the public OpenAllay Extension SPI. */
public final class JeiOpenAllayExtension implements OpenAllayExtension {
    private static final JeiOpenAllayExtension INSTANCE = new JeiOpenAllayExtension();

    private JeiOpenAllayExtension() {}

    public static JeiOpenAllayExtension instance() {
        return INSTANCE;
    }

    @Override
    public OpenAllayExtensionDescriptor descriptor() {
        return new OpenAllayExtensionDescriptor(
                "openallay:jei",
                "JEI",
                "0.2.0",
                "OpenAllay",
                "JEI recipe catalog, focus navigation, and exact native recipe layouts",
                dev.openallay.util.Java8Collections.setOf("fabric", "neoforge"),
                "[26.2,26.3)",
                "[0.2,0.3)",
                "builtin:openallay/jei");
    }

    @Override
    public OpenAllayExtensionContribution contribution() {
        return new OpenAllayExtensionContribution(
                dev.openallay.util.Java8Collections.listOf(new RecipeViewerExtensionDataModule(
                        "openallay:jei",
                        "viewer:jei",
                        "Detached JEI provider, category, recipe, focus, and navigation state",
                        true,
                        true,
                        true)),
                dev.openallay.util.Java8Collections.listOf(),
                dev.openallay.util.Java8Collections.listOf(),
                dev.openallay.util.Java8Collections.listOf(new JavascriptResultViewProvider.Declaration(
                        "openallay:jei_recipe",
                        JavascriptSemanticKind.RECIPE,
                        "JEI-backed native recipe presentation")));
    }
}
