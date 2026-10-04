package dev.openallay.client.gui;

import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.client.gui.hud.GuideHudRenderer;
import dev.openallay.client.gui.hud.GuideNativeToastPort;
import dev.openallay.client.gui.hud.GuideVoiceIndicator;
import dev.openallay.client.observation.GuideObservationInputActions;
import dev.openallay.client.observation.GuideObservationSubmission;
import dev.openallay.client.presentation.GuidePresentationCoordinator;
import dev.openallay.client.voice.VoiceClientRuntimes;
import dev.openallay.client.voice.VoiceRuntime;
import dev.openallay.client.voice.VoiceSettingsActions;
import dev.openallay.guide.GuideService;
import dev.openallay.guide.GuideServiceManager;
import dev.openallay.guide.ui.GuideDisplayRuntime;
import dev.openallay.model.image.ImageReference;
import dev.openallay.recipe.config.RecipeClientRuntime;
import dev.openallay.settings.ClientSettingsService;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import dev.openallay.client.gui.GuideGraphics;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Native composition and rendering entry point for the shared presentation coordinator. */
public final class GuideClientUiCoordinator implements AutoCloseable {
    private final Minecraft minecraft;
    private final GuideHudRenderer renderer;
    private final GuideNativeToastPort notificationPort;
    private final GuidePresentationCoordinator presentation;

    public GuideClientUiCoordinator(Minecraft minecraft, GuideServiceManager services,
            RecipeClientRuntime recipes, GuideDisplayRuntime display, ClientSettingsService settings,
            Path configDirectory, ClientEventDispatcher dispatcher, Clock clock) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        renderer = new GuideHudRenderer(minecraft);
        notificationPort = new GuideNativeToastPort(minecraft);
        NativeGuidePresentationHost host = new NativeGuidePresentationHost(minecraft, recipes, display, settings, renderer);
        presentation = new GuidePresentationCoordinator(host, services, display, settings, dispatcher, clock,
                notificationPort, service -> GuideClientUiStates.create(service, dispatcher),
                drafts -> VoiceClientRuntimes.create(configDirectory, drafts, dispatcher::execute));
        host.bindHudView(presentation.hud()::view);
        if (minecraft.getResourceManager() instanceof ReloadableResourceManager resources) {
            resources.registerReloadListener((ResourceManagerReloadListener) ignored -> renderer.invalidateLayout());
        }
    }

    public GuideClientUiCoordinator withObservationInput(GuideObservationInputActions input) {
        presentation.withObservationInput(input); return this;
    }
    public GuideClientUiCoordinator withObservationSubmission(GuideObservationSubmission sender) {
        presentation.withObservationSubmission(sender); return this;
    }
    public GuideClientUiCoordinator withObservationImages(
            Function<GuideService, BiFunction<UUID, String, List<ImageReference>>> images) {
        presentation.withObservationImages(images); return this;
    }
    public Optional<GuideClientUiState.ObservationCapture> observationForVoice(VoiceRuntime.DraftTarget target) {
        return presentation.observationForVoice(target);
    }
    public void releaseVoiceObservation(VoiceRuntime.DraftTarget target) { presentation.releaseVoiceObservation(target); }
    public void openGuide(GuideService service) { presentation.openGuide(service); }
    public void tick() { presentation.tick(); }
    public void disconnect() { presentation.disconnect(); }

    public void extractRenderState(GuideGraphics graphics) {
        if (presentation.closed()) return;
        renderer.extractRenderState(graphics, presentation.hud().view());
        if (MinecraftClientWindow.screen(minecraft) == null && MinecraftClientWindow.overlay(minecraft) == null
                && minecraft.player != null && minecraft.level != null && !MinecraftClientWindow.hudHidden(minecraft)) {
            GuideVoiceIndicator.extract(graphics, minecraft, presentation.voiceInput());
        }
    }

    /** Read-only native extraction receipt. Never binds an actor or creates a service. */
    public Object e2eHudReceipt() {
        if (!Boolean.getBoolean("openallay.e2e.enabled")) throw new IllegalStateException("Development probe is disabled");
        return renderer.resultReceipt();
    }
    /** Returns the existing settings port without opening capture or refreshing devices. */
    public VoiceSettingsActions e2eVoiceSettings() {
        if (!Boolean.getBoolean("openallay.e2e.enabled")) throw new IllegalStateException("Development probe is disabled");
        return presentation.voiceSettings();
    }
    /** Cached native toast facts only. Reading never renders, captures audio, or shows a toast. */
    public Object e2eNotificationReceipt() {
        if (!Boolean.getBoolean("openallay.e2e.enabled")) throw new IllegalStateException("Development probe is disabled");
        return notificationPort.e2eReceipt();
    }
    @Override public void close() { presentation.close(); }
}
