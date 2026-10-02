package dev.openallay.client.gui;

import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.guide.GuideService;
import dev.openallay.guide.GuideServiceManager;
import dev.openallay.guide.ui.GuideDisplayRuntime;
import dev.openallay.recipe.config.RecipeClientRuntime;
import dev.openallay.settings.ClientSettingsService;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import net.minecraft.client.Minecraft;

/** Connection-scoped fullscreen draft owner. Temporary Screens only attach views. */
public final class GuideClientUiCoordinator implements AutoCloseable {
    private final Minecraft minecraft;
    private final GuideServiceManager services;
    private final RecipeClientRuntime recipes;
    private final GuideDisplayRuntime display;
    private final ClientSettingsService settings;
    private final ClientEventDispatcher dispatcher;
    private GuideService bound;
    private GuideClientUiState state;
    private boolean closed;

    public GuideClientUiCoordinator(Minecraft minecraft, GuideServiceManager services,
            RecipeClientRuntime recipes, GuideDisplayRuntime display, ClientSettingsService settings,
            Path configDirectory, ClientEventDispatcher dispatcher, Clock clock) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.services = Objects.requireNonNull(services, "services");
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.display = Objects.requireNonNull(display, "display");
        this.settings = settings;
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        Objects.requireNonNull(configDirectory, "configDirectory");
        Objects.requireNonNull(clock, "clock");
    }

    public void openGuide(GuideService service) {
        bindCurrent();
        if (closed || service != bound || state == null || state.closed()) return;
        GuideClientUiState owner = state;
        Runnable openSettings = settings == null ? null : () -> {
            if (!valid(service, owner)) return;
            minecraft.gui.setScreen(new OpenAllaySettingsScreen(settings, () -> openGuide(service)));
        };
        minecraft.gui.setScreen(new OpenAllayScreen(service, recipes, display, openSettings, owner));
    }

    /** Existing manager identity only: this never creates a service or captures task context. */
    public void tick() {
        if (closed) return;
        bindCurrent();
        if (state != null && bound != null) state.selectSession(bound.snapshot().selectedSession());
    }

    private void bindCurrent() {
        GuideService next = services.current();
        if (closed || bound == next) return;
        closeState();
        if (next != null) {
            bound = next;
            state = GuideClientUiState.create(next, dispatcher);
        }
    }

    public void disconnect() {
        closeState();
    }

    private boolean valid(GuideService service, GuideClientUiState owner) {
        return !closed && service == bound && owner == state && !owner.closed()
                && minecraft.player != null && minecraft.level != null;
    }

    private void closeState() {
        if (state != null) state.close();
        state = null;
        bound = null;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        disconnect();
    }
}
