package dev.openallay.client.presentation;

import dev.openallay.client.gui.GuideClientUiState;
import dev.openallay.client.observation.GuideObservationInputActions;
import dev.openallay.client.observation.GuideObservationSubmission;
import dev.openallay.client.voice.VoiceRuntime;
import dev.openallay.client.voice.VoiceSettingsActions;
import dev.openallay.guide.GuideService;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.model.image.ImageReference;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Small native presentation seam. Native views and handles never cross this boundary. */
public interface GuidePresentationHost {
    Facts facts();
    Input pollInput();
    /** Captures native identity privately; VIEW includes the current menu as well as the connection. */
    BooleanSupplier captureFence(FenceScope scope);
    void showGuide(View view, Runnable openSettings);
    void showSettings(Runnable returnToGuide, BooleanSupplier ownerValid, VoiceSettingsActions voice,
            Consumer<GuideUiConfig.Notifications> previewNotification);
    void showHudEditor(GuideDisplayConfig draft, Consumer<GuideDisplayConfig> applied, BooleanSupplier ownerValid);
    void showHudInput(View view, Runnable openGuide);
    void focusComposerAfterVoiceDraft();

    enum FenceScope { VIEW, CONNECTION }
    enum Surface { GAMEPLAY, GUIDE, HUD_INPUT, OTHER }
    record Facts(UUID actorId, UUID worldConnection, Surface surface, boolean overlayPresent,
            boolean hudHidden, boolean windowActive) {
        public boolean connected() { return actorId != null && worldConnection != null; }
    }
    record Input(int toggleHudClicks, int editHudClicks, int interactHudClicks, boolean pttDown) {}
    record View(GuideService service, GuideClientUiState state, GuideNotificationController notifications,
            VoiceRuntime voice, GuideObservationInputActions observationInput,
            GuideObservationSubmission observationSubmission,
            BiFunction<UUID, String, List<ImageReference>> observationImages) {}
}
