package dev.openallay.client.gui;

import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.client.gui.hud.GuideChatLiteScreen;
import dev.openallay.client.gui.hud.GuideHudEditorScreen;
import dev.openallay.client.gui.hud.GuideHudRenderer;
import dev.openallay.client.gui.hud.GuideNativeToastPort;
import dev.openallay.client.gui.hud.GuideVoiceIndicator;
import dev.openallay.client.voice.VoiceClientRuntime;
import dev.openallay.client.voice.VoiceRuntime;
import dev.openallay.client.presentation.GuideNotificationController;
import dev.openallay.guide.GuidePresentationEvent;
import dev.openallay.guide.GuidePresentationListener;
import dev.openallay.guide.GuideService;
import dev.openallay.guide.GuideServiceManager;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideDisplayRuntime;
import dev.openallay.guide.ui.hud.GuideHudController;
import dev.openallay.recipe.config.RecipeClientRuntime;
import dev.openallay.settings.ClientSettingsService;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Shared client presentation owner. Live sinks bind before the manager admits any task. */
public final class GuideClientUiCoordinator implements AutoCloseable {
    private final Minecraft minecraft;
    private final GuideServiceManager services;
    private final RecipeClientRuntime recipes;
    private final GuideDisplayRuntime display;
    private final ClientSettingsService settings;
    private final GuideHudController hud;
    private final GuideHudRenderer renderer;
    private final ClientEventDispatcher dispatcher;
    private final GuideNotificationController notifications;
    private final VoiceClientRuntime voice;
    private final AutoCloseable binding;
    private final AutoCloseable notificationBinding;
    private final AutoCloseable settingsBinding;
    private GuideService bound;
    private GuideClientUiState state;
    private boolean pttDown;
    private boolean closed;

    public GuideClientUiCoordinator(Minecraft minecraft, GuideServiceManager services,
            RecipeClientRuntime recipes, GuideDisplayRuntime display, ClientSettingsService settings,
            Path configDirectory, ClientEventDispatcher dispatcher, Clock clock) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.services = Objects.requireNonNull(services, "services");
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        this.display = Objects.requireNonNull(display, "display");
        this.settings = settings;
        hud = new GuideHudController(services, display::config);
        renderer = new GuideHudRenderer(minecraft);
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        notifications = new GuideNotificationController(() -> display.config().ui().notifications(), clock,
                new GuideNativeToastPort(minecraft));
        // Both result delivery and draft ownership bind before forActor returns for admission.
        notificationBinding = services.listenPresentation(notifications);
        binding = services.listenPresentation(new GuidePresentationListener() {
            @Override public void bound(GuideService next) {
                if (closed || bound == next) return;
                closeState();
                bound = next;
                state = GuideClientUiState.create(next, GuideClientUiCoordinator.this.dispatcher);
            }
            @Override public void event(GuidePresentationEvent event) {}
            @Override public void invalidated(UUID generation) {
                if (bound != null && generation.equals(bound.presentationGeneration())) {
                    closeState();
                    hud.disconnect();
                }
            }
        });
        voice = VoiceClientRuntime.create(configDirectory, new VoiceRuntime.DraftPort() {
            @Override public VoiceRuntime.DraftTarget capture() {
                if (state == null || state.closed()) return null;
                state.selectSession(bound.snapshot().selectedSession());
                return new VoiceRuntime.DraftTarget(state.ownerId(), state.selectedSession(),
                        state.revision(state.selectedSession()));
            }
            @Override public VoiceRuntime.Insertion append(VoiceRuntime.DraftTarget target, String text) {
                if (state == null || state.closed() || !state.ownerId().equals(target.actorId())) {
                    return VoiceRuntime.Insertion.REJECTED;
                }
                GuideClientUiState.Insertion captured = new GuideClientUiState.Insertion(
                        target.actorId(), state.generation(), target.sessionId(), target.draftRevision());
                return switch (state.insertTranscript(captured, text)) {
                    case INSERTED -> VoiceRuntime.Insertion.INSERTED;
                    case PENDING -> VoiceRuntime.Insertion.PENDING;
                    case REJECTED -> VoiceRuntime.Insertion.REJECTED;
                };
            }
        }, dispatcher::execute);
        settingsBinding = settings == null ? () -> {} : settings.listen(ignored -> notifications.settingsChanged());
        if (minecraft.getResourceManager() instanceof ReloadableResourceManager resources) {
            resources.registerReloadListener((ResourceManagerReloadListener) ignored -> renderer.invalidateLayout());
        }
    }

    public void openGuide(GuideService service) {
        if (closed || service != bound || state == null || state.closed()) return;
        GuideClientUiState owner = state;
        Runnable openSettings = settings == null ? null : () -> {
            if (!valid(service, owner)) return;
            OpenAllaySettingsScreen screen = new OpenAllaySettingsScreen(settings, () -> openGuide(service))
                    .withUiActions(new OpenAllaySettingsScreen.UiActions() {
                        @Override public void editHud(OpenAllaySettingsScreen returnScreen,
                                GuideDisplayConfig draft, java.util.function.Consumer<GuideDisplayConfig> applied) {
                            if (!valid(service, owner)) return;
                            minecraft.gui.setScreen(new GuideHudEditorScreen(draft, applied, returnScreen,
                                    () -> valid(service, owner), renderer, hud::view));
                        }
                        @Override public void previewNotification(dev.openallay.guide.ui.GuideUiConfig.Notifications config) {
                            if (valid(service, owner)) notifications.testNotification(config);
                        }
                    }).withVoiceActions(voice.settings());
            minecraft.gui.setScreen(screen);
        };
        minecraft.gui.setScreen(new OpenAllayScreen(service, recipes, display, openSettings, owner)
                .withNotifications(notifications).withVoice(voice.input()));
    }

    /** Client tick only. Disabled passive surfaces never create a GuideService or capture context. */
    public void tick() {
        if (closed) return;
        if (state != null && bound != null) state.selectSession(bound.snapshot().selectedSession());
        hud.tick();
        notifications.tick();
        boolean gameplay = minecraft.player != null && minecraft.level != null
                && minecraft.gui.screen() == null && minecraft.gui.overlay() == null;
        Screen activeScreen = minecraft.gui.screen();
        boolean feedback = minecraft.gui.overlay() == null && !minecraft.gui.hud.isHidden()
                && (activeScreen == null || activeScreen instanceof OpenAllayScreen
                        || activeScreen instanceof GuideChatLiteScreen);
        voice.input().setFeedbackVisible(feedback);
        while (OpenAllayKeyMappings.TOGGLE_HUD.consumeClick()) {
            if (gameplay && settings != null) {
                GuideDisplayConfig current = display.config();
                settings.saveDisplay(current.withUi(current.ui().withHud(
                        current.ui().hud().withEnabled(!current.ui().hud().enabled()))));
            }
        }
        while (OpenAllayKeyMappings.EDIT_HUD.consumeClick()) {
            if (gameplay && settings != null) {
                Object level = minecraft.level;
                UUID actor = minecraft.player.getUUID();
                minecraft.gui.setScreen(new GuideHudEditorScreen(display.config(), settings::saveDisplay, null,
                        () -> minecraft.level == level && minecraft.player != null
                                && actor.equals(minecraft.player.getUUID()), renderer, hud::view));
            }
        }
        while (OpenAllayKeyMappings.INTERACT_HUD.consumeClick()) {
            if (gameplay) {
                GuideService service = services.forActor(minecraft.player.getUUID());
                if (state != null) minecraft.gui.setScreen(new GuideChatLiteScreen(service, state, display,
                        () -> openGuide(service), voice.input()).withRecipes(recipes));
            }
        }
        boolean physicalDown = OpenAllayKeyMappings.VOICE_PTT.isDown();
        if (gameplay && physicalDown && !pttDown && feedback && voice.input().enabled()) {
            services.forActor(minecraft.player.getUUID()); // Explicit PTT captures this session draft, not a task.
            voice.input().pressPtt();
        } else if (pttDown && !physicalDown) {
            voice.input().release();
        }
        pttDown = physicalDown;
        voice.input().tick(minecraft.isWindowActive(), minecraft.player != null && minecraft.level != null,
                physicalDown, feedback);
    }

    public void extractRenderState(GuiGraphicsExtractor graphics) {
        if (closed) return;
        renderer.extractRenderState(graphics, hud.view());
        if (minecraft.gui.screen() == null && minecraft.gui.overlay() == null
                && minecraft.player != null && minecraft.level != null && !minecraft.gui.hud.isHidden()) {
            GuideVoiceIndicator.extract(graphics, minecraft, voice.input());
        }
    }

    /** Read-only native extraction receipt. Never binds an actor or creates a service. */
    public Object e2eHudReceipt() {
        if (!Boolean.getBoolean("openallay.e2e.enabled")) throw new IllegalStateException("Development probe is disabled");
        return renderer.resultReceipt();
    }

    /** Returns the existing settings port. It never opens capture or refreshes devices. */
    public dev.openallay.client.voice.VoiceSettingsActions e2eVoiceSettings() {
        if (!Boolean.getBoolean("openallay.e2e.enabled")) throw new IllegalStateException("Development probe is disabled");
        return voice.settings();
    }

    public void disconnect() {
        if (bound != null) notifications.invalidated(bound.presentationGeneration());
        closeState(); // Fence the original draft before asynchronous device cleanup.
        voice.input().cancel(VoiceRuntime.CancelReason.DISCONNECTED);
        pttDown = false;
        hud.disconnect();
    }

    private boolean valid(GuideService service, GuideClientUiState owner) {
        return !closed && service == bound && owner == state && !owner.closed()
                && minecraft.player != null && minecraft.level != null;
    }
    private void closeState() {
        if (state != null) state.close();
        if (voice != null) voice.input().cancel(VoiceRuntime.CancelReason.DISCONNECTED);
        state = null;
        bound = null;
    }
    private static void close(AutoCloseable resource) {
        try { resource.close(); } catch (Exception ignored) {}
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        disconnect();
        voice.close();
        hud.close();
        notifications.close();
        close(settingsBinding);
        close(binding);
        close(notificationBinding);
    }
}
