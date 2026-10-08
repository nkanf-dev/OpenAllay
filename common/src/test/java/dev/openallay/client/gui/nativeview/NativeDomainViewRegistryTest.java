package dev.openallay.client.gui.nativeview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.openallay.context.RecipeReference;
import dev.openallay.guide.semantic.RichComponent;
import dev.openallay.guide.ui.GuideRecipeCard;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class NativeDomainViewRegistryTest {
    @Test
    void providerFailureFallsThroughAndOffscreenFrameReleasesView() {
        AtomicInteger closed = new AtomicInteger();
        NativeDomainView fallbackView = view("openallay:generic", closed);
        NativeDomainViewProvider failing = provider(
                "viewer:test", 100,
                new NativeDomainViewProvider.Attempt.Unsupported("native_view_unavailable"));
        NativeDomainViewProvider fallback = provider(
                "openallay:generic", Integer.MIN_VALUE,
                new NativeDomainViewProvider.Attempt.Ready(fallbackView));
        NativeDomainViewRegistry registry = new NativeDomainViewRegistry(
                () -> List.of(failing), fallback, () -> true);
        NativeDomainViewBinding.Recipe binding = binding();

        registry.beginFrame();
        assertSame(fallbackView, registry.resolve(binding));
        assertSame(fallbackView, registry.resolve(binding));
        registry.endFrame();
        assertEquals(1, registry.activeViewCount());
        assertEquals("native_view_unavailable", registry.diagnostics().getFirst().code());

        registry.beginFrame();
        registry.endFrame();
        assertEquals(0, registry.activeViewCount());
        assertEquals(1, closed.get());

        registry.beginFrame();
        registry.resolve(binding);
        registry.endFrame();
        assertEquals(1, registry.diagnostics().size());
    }

    private static final class ForeignAttempt implements NativeDomainViewProvider.Attempt { }

    @Test
    void foreignSuccessfulProviderResultPropagatesWithoutFallbackOrDiagnostic() {
        AtomicInteger fallbackCalls = new AtomicInteger();
        NativeDomainViewProvider foreign = provider("viewer:foreign", 100, new ForeignAttempt());
        NativeDomainViewProvider fallback = new NativeDomainViewProvider() {
            @Override public String providerId() { return "openallay:generic"; }
            @Override public int priority() { return Integer.MIN_VALUE; }
            @Override public boolean supports(NativeDomainViewBinding binding) { return true; }
            @Override public Attempt create(NativeDomainViewBinding binding) {
                fallbackCalls.incrementAndGet();
                return new Attempt.Unsupported("native_view_unavailable");
            }
        };
        NativeDomainViewRegistry registry = new NativeDomainViewRegistry(
                () -> List.of(foreign), fallback, () -> true);
        IncompatibleClassChangeError failure = org.junit.jupiter.api.Assertions.assertThrows(
                IncompatibleClassChangeError.class, () -> registry.resolve(binding()));
        assertEquals("Unknown native Attempt subtype", failure.getMessage());
        assertEquals(0, fallbackCalls.get());
        assertEquals(0, registry.activeViewCount());
        assertEquals(0, registry.diagnostics().size());
    }

    @Test
    void foreignRenderFallbackPropagatesAfterExistingViewCleanup() {
        AtomicInteger closed = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        NativeDomainView broken = new NativeDomainView() {
            @Override public String providerId() { return "viewer:broken"; }
            @Override public NativeDomainViewBinding.Family family() { return NativeDomainViewBinding.Family.RECIPE; }
            @Override public void render(RenderContext context) { throw new IllegalStateException("paint failed"); }
            @Override public void close() { closed.incrementAndGet(); }
        };
        NativeDomainViewProvider primary = provider("viewer:broken", 100, new NativeDomainViewProvider.Attempt.Ready(broken));
        NativeDomainViewProvider fallback = new NativeDomainViewProvider() {
            @Override public String providerId() { return "openallay:generic"; }
            @Override public int priority() { return Integer.MIN_VALUE; }
            @Override public boolean supports(NativeDomainViewBinding binding) { return true; }
            @Override public Attempt create(NativeDomainViewBinding binding) {
                fallbackCalls.incrementAndGet();
                return new ForeignAttempt();
            }
        };
        NativeDomainViewRegistry registry = new NativeDomainViewRegistry(
                () -> List.of(primary), fallback, () -> true);
        IncompatibleClassChangeError failure = org.junit.jupiter.api.Assertions.assertThrows(
                IncompatibleClassChangeError.class, () -> registry.render(binding(), null));
        assertEquals("Unknown native Attempt subtype", failure.getMessage());
        assertEquals(1, closed.get());
        assertEquals(1, fallbackCalls.get());
        assertEquals(1, registry.diagnostics().size());
        assertEquals("native_view_failed", registry.diagnostics().get(0).code());
    }

    private static NativeDomainViewProvider provider(
            String id, int priority, NativeDomainViewProvider.Attempt attempt) {
        return new NativeDomainViewProvider() {
            @Override public String providerId() { return id; }
            @Override public int priority() { return priority; }
            @Override public boolean supports(NativeDomainViewBinding binding) { return true; }
            @Override public Attempt create(NativeDomainViewBinding binding) { return attempt; }
        };
    }

    private static NativeDomainView view(String providerId, AtomicInteger closed) {
        return new NativeDomainView() {
            @Override public String providerId() { return providerId; }
            @Override public NativeDomainViewBinding.Family family() {
                return NativeDomainViewBinding.Family.RECIPE;
            }
            @Override public void render(RenderContext context) {}
            @Override public void close() { closed.incrementAndGet(); }
        };
    }

    private static NativeDomainViewBinding.Recipe binding() {
        RecipeReference reference = new RecipeReference(
                "minecraft:recipe_manager", "a".repeat(64), "minecraft:apple");
        RichComponent.RecipeGrid component = new RichComponent.RecipeGrid(
                "c".repeat(64), reference, "call-1", "Apple", "Apple recipe", "Apple recipe");
        GuideRecipeCard card = new GuideRecipeCard(
                reference,
                List.of(reference),
                "minecraft:apple",
                "minecraft:crafting",
                "minecraft:crafting_table",
                List.of(new GuideRecipeCard.Output("minecraft:apple", 1, "Apple")));
        return new NativeDomainViewBinding.Recipe("row:recipe-grid", component, card);
    }
}
