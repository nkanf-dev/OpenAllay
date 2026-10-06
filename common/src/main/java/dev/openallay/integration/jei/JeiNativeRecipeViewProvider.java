package dev.openallay.integration.jei;

import dev.openallay.client.gui.nativeview.NativeDomainView;
import dev.openallay.client.gui.nativeview.NativeDomainViewBinding;
import dev.openallay.client.gui.nativeview.NativeDomainViewProvider;
import dev.openallay.context.RecipeReference;
import dev.openallay.platform.PlatformServices;
import dev.openallay.recipe.RecipeProviderSnapshot;
import dev.openallay.recipe.RecipeProviderState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;

/** Exact JEI layout embedding, loaded only from the JEI plugin bridge. */
final class JeiNativeRecipeViewProvider implements NativeDomainViewProvider {
    private final Supplier<IJeiRuntime> runtime;

    JeiNativeRecipeViewProvider(Supplier<IJeiRuntime> runtime) {
        this.runtime = java.util.Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public String providerId() {
        return "viewer:jei";
    }

    @Override
    public int priority() {
        return 300;
    }

    @Override
    public boolean supports(NativeDomainViewBinding binding) {
        return binding instanceof NativeDomainViewBinding.Recipe recipe
                && recipe.recipe().references().stream()
                        .anyMatch(reference -> reference.sourceId().equals("viewer:jei"));
    }

    @Override
    public Attempt create(NativeDomainViewBinding binding) {
        IJeiRuntime current = runtime.get();
        if (current == null) return new Attempt.Unsupported("native_view_unavailable");
        NativeDomainViewBinding.Recipe recipe = (NativeDomainViewBinding.Recipe) binding;
        RecipeReference exact = recipe.recipe().references().stream()
                .filter(reference -> reference.sourceId().equals("viewer:jei"))
                .findFirst().orElseThrow();
        JeiRecipeProvider references = new JeiRecipeProvider(
                current, Instant.EPOCH, PlatformServices.load());
        RecipeProviderSnapshot snapshot = references.capture();
        if (!currentGenerationContains(snapshot, exact)) {
            return new Attempt.Unsupported("stale_reference");
        }
        NativeRecipeLayout<?> layout = find(current, references, exact).orElse(null);
        return layout == null
                ? new Attempt.Unsupported("native_view_unavailable")
                : new Attempt.Ready(new View(
                        layout, runtime, current, layout.gameTick()));
    }

    static boolean currentGenerationContains(
            RecipeProviderSnapshot snapshot, RecipeReference exact) {
        return snapshot.state() == RecipeProviderState.AVAILABLE
                && exact.sourceId().equals(snapshot.sourceId())
                && exact.generation().equals(snapshot.generation())
                && snapshot.recipes().stream()
                        .anyMatch(recipe -> recipe.reference().equals(exact));
    }

    private static Optional<NativeRecipeLayout<?>> find(
            IJeiRuntime runtime, JeiRecipeProvider references, RecipeReference exact) {
        List<IRecipeCategory<?>> categories = runtime.getRecipeManager()
                .createRecipeCategoryLookup()
                .includeHidden()
                .get()
                .toList();
        for (IRecipeCategory<?> category : categories) {
            Optional<NativeRecipeLayout<?>> found = findInCategory(
                    runtime, references, category, exact.recipeId());
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    private static <T> Optional<NativeRecipeLayout<?>> findInCategory(
            IJeiRuntime runtime,
            JeiRecipeProvider references,
            IRecipeCategory<T> category,
            String recipeId) {
        List<T> recipes = runtime.getRecipeManager()
                .createRecipeLookup(category.getRecipeType())
                .includeHidden()
                .get()
                .toList();
        for (T recipe : recipes) {
            if (!references.referenceIdIfSupported(category, recipe)
                    .filter(recipeId::equals)
                    .isPresent()) {
                continue;
            }
            return MinecraftJeiRecipeApi.createLayout(
                    runtime.getRecipeManager(),
                    category,
                    recipe,
                    runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup())
                    .<NativeRecipeLayout<?>>map(layout -> layout);
        }
        return Optional.empty();
    }

    private record View(
            NativeRecipeLayout<?> layout,
            Supplier<IJeiRuntime> runtime,
            IJeiRuntime capturedRuntime,
            Optional<Runnable> nativeGameTick) implements NativeDomainView {
        @Override
        public String providerId() {
            return "viewer:jei";
        }

        @Override
        public NativeDomainViewBinding.Family family() {
            return NativeDomainViewBinding.Family.RECIPE;
        }

        @Override
        public void tick() {
            if (runtime.get() != capturedRuntime) {
                throw new IllegalStateException("JEI runtime generation changed");
            }
            // Draw-only JEI publications have no layout game-tick callback. The runtime
            // generation guard still runs on every tick; native drawing remains in render.
            nativeGameTick.ifPresent(Runnable::run);
        }

        @Override
        public void render(RenderContext context) {
            int layoutWidth = layout.getRecipeCategory().getWidth();
            int layoutHeight = layout.getRecipeCategory().getHeight();
            int contentHeight = context.bounds().height() - 16;
            if (layoutWidth > context.bounds().width()
                    || layoutHeight > contentHeight) {
                throw new IllegalStateException("JEI layout exceeds native component bounds");
            }
            int x = context.bounds().x() + Math.max(0, (context.bounds().width() - layoutWidth) / 2);
            int y = context.bounds().y() + Math.max(0, (contentHeight - layoutHeight) / 2);
            layout.setPosition(x, y);
            layout.drawRecipe(context.graphics(), context.mouseX(), context.mouseY());
            layout.drawOverlays(context.graphics(), context.mouseX(), context.mouseY());
        }
    }
}
