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
    @dev.openallay.value.ValueType(Facts.ValueSchemaProvider.class)
public static final class Facts {
    private final UUID actorId;
    private final UUID worldConnection;
    private final Surface surface;
    private final boolean overlayPresent;
    private final boolean hudHidden;
    private final boolean windowActive;
    public Facts(UUID actorId, UUID worldConnection, Surface surface, boolean overlayPresent, boolean hudHidden, boolean windowActive) {
        this.actorId = actorId;
        this.worldConnection = worldConnection;
        this.surface = surface;
        this.overlayPresent = overlayPresent;
        this.hudHidden = hudHidden;
        this.windowActive = windowActive;
    }
    public UUID actorId() { return actorId; }
    public UUID worldConnection() { return worldConnection; }
    public Surface surface() { return surface; }
    public boolean overlayPresent() { return overlayPresent; }
    public boolean hudHidden() { return hudHidden; }
    public boolean windowActive() { return windowActive; }
public boolean connected() { return actorId != null && worldConnection != null; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Facts)) return false;
        Facts that = (Facts) other;
        return java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(worldConnection, that.worldConnection) && java.util.Objects.equals(surface, that.surface) && overlayPresent == that.overlayPresent && hudHidden == that.hudHidden && windowActive == that.windowActive;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(worldConnection);
        hash = 31 * hash + java.util.Objects.hashCode(surface);
        hash = 31 * hash + Boolean.hashCode(overlayPresent);
        hash = 31 * hash + Boolean.hashCode(hudHidden);
        hash = 31 * hash + Boolean.hashCode(windowActive);
        return hash;
    }
    @Override public String toString() { return "Facts[actorId=" + actorId + ", worldConnection=" + worldConnection + ", surface=" + surface + ", overlayPresent=" + overlayPresent + ", hudHidden=" + hudHidden + ", windowActive=" + windowActive + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Facts> schema() {
            return new dev.openallay.value.ValueSchema<>(Facts.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Facts>>asList(new dev.openallay.value.ValueSchema.Component<>(Facts.class, "actorId", Facts::actorId), new dev.openallay.value.ValueSchema.Component<>(Facts.class, "worldConnection", Facts::worldConnection), new dev.openallay.value.ValueSchema.Component<>(Facts.class, "surface", Facts::surface), new dev.openallay.value.ValueSchema.Component<>(Facts.class, "overlayPresent", Facts::overlayPresent), new dev.openallay.value.ValueSchema.Component<>(Facts.class, "hudHidden", Facts::hudHidden), new dev.openallay.value.ValueSchema.Component<>(Facts.class, "windowActive", Facts::windowActive)), arguments -> new Facts((UUID) arguments[0], (UUID) arguments[1], (Surface) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4], (Boolean) arguments[5]));
        }
    }
}
    @dev.openallay.value.ValueType(Input.ValueSchemaProvider.class)
public static final class Input {
    private final int toggleHudClicks;
    private final int editHudClicks;
    private final int interactHudClicks;
    private final boolean pttDown;
    public Input(int toggleHudClicks, int editHudClicks, int interactHudClicks, boolean pttDown) {
        this.toggleHudClicks = toggleHudClicks;
        this.editHudClicks = editHudClicks;
        this.interactHudClicks = interactHudClicks;
        this.pttDown = pttDown;
    }
    public int toggleHudClicks() { return toggleHudClicks; }
    public int editHudClicks() { return editHudClicks; }
    public int interactHudClicks() { return interactHudClicks; }
    public boolean pttDown() { return pttDown; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Input)) return false;
        Input that = (Input) other;
        return toggleHudClicks == that.toggleHudClicks && editHudClicks == that.editHudClicks && interactHudClicks == that.interactHudClicks && pttDown == that.pttDown;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(toggleHudClicks);
        hash = 31 * hash + Integer.hashCode(editHudClicks);
        hash = 31 * hash + Integer.hashCode(interactHudClicks);
        hash = 31 * hash + Boolean.hashCode(pttDown);
        return hash;
    }
    @Override public String toString() { return "Input[toggleHudClicks=" + toggleHudClicks + ", editHudClicks=" + editHudClicks + ", interactHudClicks=" + interactHudClicks + ", pttDown=" + pttDown + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Input> schema() {
            return new dev.openallay.value.ValueSchema<>(Input.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Input>>asList(new dev.openallay.value.ValueSchema.Component<>(Input.class, "toggleHudClicks", Input::toggleHudClicks), new dev.openallay.value.ValueSchema.Component<>(Input.class, "editHudClicks", Input::editHudClicks), new dev.openallay.value.ValueSchema.Component<>(Input.class, "interactHudClicks", Input::interactHudClicks), new dev.openallay.value.ValueSchema.Component<>(Input.class, "pttDown", Input::pttDown)), arguments -> new Input((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Boolean) arguments[3]));
        }
    }
}
    @dev.openallay.value.ValueType(View.ValueSchemaProvider.class)
public static final class View {
    private final GuideService service;
    private final GuideClientUiState state;
    private final GuideNotificationController notifications;
    private final VoiceRuntime voice;
    private final GuideObservationInputActions observationInput;
    private final GuideObservationSubmission observationSubmission;
    private final BiFunction<UUID, String, List<ImageReference>> observationImages;
    public View(GuideService service, GuideClientUiState state, GuideNotificationController notifications, VoiceRuntime voice, GuideObservationInputActions observationInput, GuideObservationSubmission observationSubmission, BiFunction<UUID, String, List<ImageReference>> observationImages) {
        this.service = service;
        this.state = state;
        this.notifications = notifications;
        this.voice = voice;
        this.observationInput = observationInput;
        this.observationSubmission = observationSubmission;
        this.observationImages = observationImages;
    }
    public GuideService service() { return service; }
    public GuideClientUiState state() { return state; }
    public GuideNotificationController notifications() { return notifications; }
    public VoiceRuntime voice() { return voice; }
    public GuideObservationInputActions observationInput() { return observationInput; }
    public GuideObservationSubmission observationSubmission() { return observationSubmission; }
    public BiFunction<UUID, String, List<ImageReference>> observationImages() { return observationImages; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof View)) return false;
        View that = (View) other;
        return java.util.Objects.equals(service, that.service) && java.util.Objects.equals(state, that.state) && java.util.Objects.equals(notifications, that.notifications) && java.util.Objects.equals(voice, that.voice) && java.util.Objects.equals(observationInput, that.observationInput) && java.util.Objects.equals(observationSubmission, that.observationSubmission) && java.util.Objects.equals(observationImages, that.observationImages);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(service);
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(notifications);
        hash = 31 * hash + java.util.Objects.hashCode(voice);
        hash = 31 * hash + java.util.Objects.hashCode(observationInput);
        hash = 31 * hash + java.util.Objects.hashCode(observationSubmission);
        hash = 31 * hash + java.util.Objects.hashCode(observationImages);
        return hash;
    }
    @Override public String toString() { return "View[service=" + service + ", state=" + state + ", notifications=" + notifications + ", voice=" + voice + ", observationInput=" + observationInput + ", observationSubmission=" + observationSubmission + ", observationImages=" + observationImages + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<View> schema() {
            return new dev.openallay.value.ValueSchema<>(View.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<View>>asList(new dev.openallay.value.ValueSchema.Component<>(View.class, "service", View::service), new dev.openallay.value.ValueSchema.Component<>(View.class, "state", View::state), new dev.openallay.value.ValueSchema.Component<>(View.class, "notifications", View::notifications), new dev.openallay.value.ValueSchema.Component<>(View.class, "voice", View::voice), new dev.openallay.value.ValueSchema.Component<>(View.class, "observationInput", View::observationInput), new dev.openallay.value.ValueSchema.Component<>(View.class, "observationSubmission", View::observationSubmission), new dev.openallay.value.ValueSchema.Component<>(View.class, "observationImages", View::observationImages)), arguments -> new View((GuideService) arguments[0], (GuideClientUiState) arguments[1], (GuideNotificationController) arguments[2], (VoiceRuntime) arguments[3], (GuideObservationInputActions) arguments[4], (GuideObservationSubmission) arguments[5], (BiFunction) arguments[6]));
        }
    }
}
}
