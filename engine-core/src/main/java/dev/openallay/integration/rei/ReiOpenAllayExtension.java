package dev.openallay.integration.rei;

import dev.openallay.extension.OpenAllayExtension;
import dev.openallay.extension.OpenAllayExtensionContribution;
import dev.openallay.extension.OpenAllayExtensionDescriptor;
import dev.openallay.integration.RecipeViewerExtensionDataModule;
import dev.openallay.script.result.JavascriptResultViewProvider;
import dev.openallay.script.result.JavascriptSemanticKind;
import java.util.List;
import java.util.Set;

/** First-party REI projection registered through the public OpenAllay Extension SPI. */
public final class ReiOpenAllayExtension implements OpenAllayExtension {
    private static final ReiOpenAllayExtension INSTANCE = new ReiOpenAllayExtension();

    private ReiOpenAllayExtension() {}

    public static ReiOpenAllayExtension instance() {
        return INSTANCE;
    }

    @Override
    public OpenAllayExtensionDescriptor descriptor() {
        return new OpenAllayExtensionDescriptor(
                "openallay:rei",
                "REI",
                "0.2.0",
                "OpenAllay",
                "REI recipe catalog and item-focused recipe or usage navigation",
                dev.openallay.util.Java8Collections.setOf("fabric", "neoforge"),
                "[26.2,26.3)",
                "[0.2,0.3)",
                "builtin:openallay/rei");
    }

    @Override
    public OpenAllayExtensionContribution contribution() {
        return new OpenAllayExtensionContribution(
                dev.openallay.util.Java8Collections.listOf(new RecipeViewerExtensionDataModule(
                        "openallay:rei",
                        "viewer:rei",
                        "Detached REI provider, category, recipe, focus, and navigation state",
                        true,
                        true,
                        false)),
                dev.openallay.util.Java8Collections.listOf(),
                dev.openallay.util.Java8Collections.listOf(),
                dev.openallay.util.Java8Collections.listOf(new JavascriptResultViewProvider.Declaration(
                        "openallay:rei_recipe",
                        JavascriptSemanticKind.RECIPE,
                        "REI-backed recipe presentation with neutral exact-layout fallback")));
    }
}
