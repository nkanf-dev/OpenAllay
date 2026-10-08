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

import dev.openallay.client.gui.GuideGraphics;



/** Native composition and rendering entry point for the shared presentation coordinator. */
public final class GuideClientUiCoordinator implements AutoCloseable {
    private final net.minecraft.client.Minecraft minecraft;
    private final GuideHudRenderer renderer;
    private final GuideNativeToastPort notificationPort;
    private final GuidePresentationCoordinator presentation;
    private final Runnable releaseResourceReload;

    public GuideClientUiCoordinator(net.minecraft.client.Minecraft minecraft, GuideServiceManager services,
            RecipeClientRuntime recipes, GuideDisplayRuntime display, ClientSettingsService settings,
            Path configDirectory, ClientEventDispatcher dispatcher, Clock clock) {
        this(minecraft, services, recipes, display, settings, configDirectory, dispatcher, clock, null);
    }

    /** The loader installs its native listener early, then binds this renderer's lifetime. */
    public GuideClientUiCoordinator(net.minecraft.client.Minecraft minecraft, GuideServiceManager services,
            RecipeClientRuntime recipes, GuideDisplayRuntime display, ClientSettingsService settings,
            Path configDirectory, ClientEventDispatcher dispatcher, Clock clock,
            Function<Runnable, Runnable> resourceReloadRegistration) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        renderer = new GuideHudRenderer(minecraft);
        notificationPort = new GuideNativeToastPort(minecraft);
        NativeGuidePresentationHost host = new NativeGuidePresentationHost(minecraft, recipes, display, settings, renderer);
        presentation = new GuidePresentationCoordinator(host, services, display, settings, dispatcher, clock,
                notificationPort, service -> GuideClientUiStates.create(service, dispatcher),
                drafts -> VoiceClientRuntimes.create(configDirectory, drafts, dispatcher::execute));
        host.bindHudView(presentation.hud()::view);
        Runnable invalidateLayout = () -> {
            if (!presentation.closed()) renderer.invalidateLayout();
        };
        if (resourceReloadRegistration != null) {
            releaseResourceReload = Objects.requireNonNull(
                    resourceReloadRegistration.apply(invalidateLayout), "releaseResourceReload");
        } else {
            // Preserve the existing Fabric/older-loader registration path and constructor ABI.
            final class $oaPattern0_Holder { net.minecraft.server.packs.resources.ResourceManager value; net.minecraft.server.packs.resources.ReloadableResourceManager bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = minecraft.getResourceManager()) instanceof net.minecraft.server.packs.resources.ReloadableResourceManager && (($oaPattern0_holder.bound = (net.minecraft.server.packs.resources.ReloadableResourceManager) $oaPattern0_holder.value) != null))) {
                $oaPattern0_holder.bound.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) ignored -> invalidateLayout.run());
            }
            releaseResourceReload = null;
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
        graphics.paint(() -> {
            renderer.extractRenderState(graphics, presentation.hud().view());
            if (MinecraftClientWindow.screen(minecraft) == null && !MinecraftClientWindow.overlayPresent(minecraft)
                    && minecraft.player != null && MinecraftClientWindow.world(minecraft) != null && !MinecraftClientWindow.hudHidden(minecraft)) {
                GuideVoiceIndicator.extract(graphics, minecraft, presentation.voiceInput());
            }
        });
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
    @Override public void close() {
        if (releaseResourceReload != null) releaseResourceReload.run();
        presentation.close();
    }
}
