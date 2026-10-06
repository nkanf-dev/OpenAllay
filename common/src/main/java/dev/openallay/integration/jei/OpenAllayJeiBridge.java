package dev.openallay.integration.jei;

import dev.openallay.OpenAllayBootstrap;
import dev.openallay.recipe.RecipeViewerNavigatorRegistry;
import dev.openallay.recipe.RecipeViewerProviderRegistry;
import dev.openallay.client.gui.nativeview.NativeDomainViewProviderRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import mezz.jei.api.runtime.IJeiRuntime;

/** Loader JEI plugins delegate lifecycle state into this common integration boundary. */
public final class OpenAllayJeiBridge {
    private static volatile IJeiRuntime runtime;
    private static Runnable retireShutdown = () -> {};
    private static final AtomicBoolean extensionRegistered = new AtomicBoolean();

    static {
        RecipeViewerProviderRegistry.register(
                "viewer:jei",
                (capturedAt, platform) -> new JeiRecipeProvider(runtime, capturedAt, platform));
        RecipeViewerNavigatorRegistry.register(new JeiRecipeNavigator());
        NativeDomainViewProviderRegistry.register(new JeiNativeRecipeViewProvider(() -> runtime));
    }

    private OpenAllayJeiBridge() {}

    /** Registers the first-party Extension as soon as JEI discovers its loader plugin. */
    public static void registerExtension() {
        if (extensionRegistered.compareAndSet(false, true)) {
            OpenAllayBootstrap.registerExtension(JeiOpenAllayExtension.instance());
        }
    }

    public static synchronized void runtimeAvailable(IJeiRuntime value) {
        runtime = java.util.Objects.requireNonNull(value, "value");
        retireShutdown.run();
        retireShutdown = dev.openallay.client.lifecycle.OptionalClientIntegrationShutdown.register("viewer:jei", () -> clearRuntime(value));
        registerExtension();
    }

    private static synchronized void clearRuntime(IJeiRuntime owned) {
        if (runtime == owned) runtimeUnavailable();
    }

    public static synchronized void runtimeUnavailable() {
        runtime = null;
        retireShutdown.run();
        retireShutdown = () -> {};
    }

    static IJeiRuntime runtime() {
        return runtime;
    }
}
