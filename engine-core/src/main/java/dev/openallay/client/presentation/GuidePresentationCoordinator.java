package dev.openallay.client.presentation;

import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.client.gui.GuideClientUiState;
import dev.openallay.client.observation.ClientObservationInputCoordinator;
import dev.openallay.client.observation.GuideObservationInputActions;
import dev.openallay.client.observation.GuideObservationSubmission;
import dev.openallay.client.voice.VoiceClientRuntime;
import dev.openallay.client.voice.VoiceRuntime;
import dev.openallay.client.voice.VoiceSettingsActions;
import dev.openallay.guide.GuidePresentationEvent;
import dev.openallay.guide.GuidePresentationListener;
import dev.openallay.guide.GuideService;
import dev.openallay.guide.GuideServiceManager;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideDisplayRuntime;
import dev.openallay.guide.ui.hud.GuideHudController;
import dev.openallay.model.image.ImageReference;
import dev.openallay.settings.ClientSettingsService;
import java.time.Clock;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Shared draft lifetime and presentation decisions. Native views remain host-owned. */
public final class GuidePresentationCoordinator implements AutoCloseable {
    private final GuidePresentationHost host;
    private final GuideServiceManager services;
    private final GuideDisplayRuntime display;
    private final ClientSettingsService settings;
    private final GuideHudController hud;
    private final ClientEventDispatcher dispatcher;
    private final GuideNotificationController notifications;
    private final VoiceClientRuntime voice;
    private final AutoCloseable binding;
    private final AutoCloseable notificationBinding;
    private final AutoCloseable settingsBinding;
    private GuideService bound;
    private GuideClientUiState state;
    private ClientObservationInputCoordinator observationInput;
    private GuideObservationInputActions observationActions;
    private GuideObservationSubmission observationSubmission;
    private Function<GuideService, BiFunction<UUID, String, List<ImageReference>>> observationImages;
    private final Map<VoiceRuntime.DraftTarget, GuideClientUiState.ObservationLease> voiceObservations = new IdentityHashMap<>();
    private boolean pttDown;
    private boolean closed;

    public GuidePresentationCoordinator(GuidePresentationHost host, GuideServiceManager services,
            GuideDisplayRuntime display, ClientSettingsService settings, ClientEventDispatcher dispatcher,
            Clock clock, GuideNotificationPort notificationPort, Function<GuideService, GuideClientUiState> states,
            Function<VoiceRuntime.DraftPort, VoiceClientRuntime> voices) {
        this.host = Objects.requireNonNull(host, "host");
        this.services = Objects.requireNonNull(services, "services");
        this.display = Objects.requireNonNull(display, "display");
        this.settings = settings;
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        Objects.requireNonNull(states, "states");
        Objects.requireNonNull(voices, "voices");
        hud = new GuideHudController(services, display::config);
        notifications = new GuideNotificationController(() -> display.config().ui().notifications(), clock,
                Objects.requireNonNull(notificationPort, "notificationPort"));
        // Both result delivery and draft ownership bind before forActor returns for admission.
        notificationBinding = services.listenPresentation(notifications);
        binding = services.listenPresentation(new GuidePresentationListener() {
            @Override public void bound(GuideService next) {
                if (closed || bound == next) return;
                closeState();
                bound = next;
                state = Objects.requireNonNull(states.apply(next), "state");
            }
            @Override public void event(GuidePresentationEvent event) {}
            @Override public void invalidated(UUID generation) {
                if (bound != null && generation.equals(bound.presentationGeneration())) {
                    closeState();
                    hud.disconnect();
                }
            }
        });
        voice = Objects.requireNonNull(voices.apply(new VoiceRuntime.DraftPort() {
            @Override public VoiceRuntime.DraftTarget capture() {
                GuidePresentationHost.Facts facts = host.facts();
                if (state == null || state.closed() || bound == null || !facts.connected()
                        || !facts.actorId().equals(bound.snapshot().actorId())) return null;
                state.selectSession(bound.snapshot().selectedSession());
                UUID sessionOwner = bound.presentationSessionOwner(state.selectedSession()).orElse(null);
                if (sessionOwner == null) return null;
                closeVoiceObservations();
                if (observationInput != null) observationInput.seedFocus(state, state.selectedSession());
                VoiceRuntime.DraftTarget target = new VoiceRuntime.DraftTarget(bound.snapshot().actorId(), state.ownerId(), state.generation(),
                        state.selectedSession(), sessionOwner, bound.presentationGeneration(), state.revision(state.selectedSession()));
                voiceObservations.put(target, state.leaseObservation(state.captureObservation(state.selectedSession())));
                return target;
            }
            @Override public VoiceRuntime.Insertion append(VoiceRuntime.DraftTarget target, String text) {
                try {
                    if (!validVoiceTarget(target)) return VoiceRuntime.Insertion.REJECTED;
                    GuideClientUiState.Insertion captured = voiceInsertion(target);
                    {
dev.openallay.client.voice.VoiceRuntime.Insertion $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((state.insertTranscript(captured, text, observationForVoice(target).orElse(null)))) {
case INSERTED:
{
{
                            if (target.sessionId().equals(state.selectedSession())
                                    && host.facts().surface() == GuidePresentationHost.Surface.GUIDE) {
                                host.focusComposerAfterVoiceDraft();
                            }
                            { $oaSwitch0_exit_result = VoiceRuntime.Insertion.INSERTED; break $oaSwitch0_exit; }
                        }
}
case PENDING:
{
$oaSwitch0_exit_result = VoiceRuntime.Insertion.PENDING; break $oaSwitch0_exit;
}
case REJECTED:
{
$oaSwitch0_exit_result = VoiceRuntime.Insertion.REJECTED; break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch0_exit_result;
}
                } finally { releaseVoiceObservation(target); }
            }
            @Override public java.util.concurrent.CompletableFuture<dev.openallay.tool.ToolResult<VoiceRuntime.DeliveryReceipt>> send(
                    VoiceRuntime.DraftTarget target, String text, java.util.function.BooleanSupplier admissionFence) {
                return sendVoice(target, text, admissionFence);
            }
            @Override public VoiceRuntime.Insertion retainPending(VoiceRuntime.DraftTarget target, String text) {
                try {
                    return validVoiceTarget(target) && state.retainPendingTranscript(voiceInsertion(target), text,
                            observationForVoice(target).orElse(null))
                            ? VoiceRuntime.Insertion.PENDING : VoiceRuntime.Insertion.REJECTED;
                } finally { releaseVoiceObservation(target); }
            }
        }), "voice");
        settingsBinding = settings == null ? () -> {} : settings.listen(ignored -> notifications.settingsChanged());
    }

    public GuidePresentationCoordinator withObservationInput(GuideObservationInputActions input) {
        observationActions = Objects.requireNonNull(input, "input");
        observationInput = new ClientObservationInputCoordinator(input, dispatcher);
        return this;
    }

    public GuidePresentationCoordinator withObservationSubmission(GuideObservationSubmission sender) {
        observationSubmission = Objects.requireNonNull(sender, "sender");
        return this;
    }

    public GuidePresentationCoordinator withObservationImages(
            java.util.function.Function<GuideService, BiFunction<UUID, String, List<ImageReference>>> images) {
        observationImages = Objects.requireNonNull(images, "images");
        return this;
    }

    /** Frozen at recording start, never recomputed from the currently selected session. */
    public Optional<GuideClientUiState.ObservationCapture> observationForVoice(VoiceRuntime.DraftTarget target) {
        GuideClientUiState.ObservationLease lease = voiceObservations.get(target);
        return lease == null ? Optional.empty() : Optional.of(lease.capture());
    }

    public void releaseVoiceObservation(VoiceRuntime.DraftTarget target) {
        GuideClientUiState.ObservationLease lease = voiceObservations.remove(target);
        if (lease != null) lease.close();
    }

    private void closeVoiceObservations() {
        dev.openallay.util.Java8Collections.listCopyOf(voiceObservations.values()).forEach(GuideClientUiState.ObservationLease::close);
        voiceObservations.clear();
    }

    /** Call before replacing the native screen. Menu capture finishes while that menu is still visible. */
    public void openGuide(GuideService service) {
        if (closed || service != bound || state == null || state.closed()) return;
        GuideClientUiState owner = state;
        String session = service.snapshot().selectedSession();
        state.selectSession(session);
        if (observationInput == null || state.observationInitialized(session)) {
            openCapturedGuide(service, owner);
            return;
        }
        BooleanSupplier viewFence = host.captureFence(GuidePresentationHost.FenceScope.VIEW);
        try {
            observationInput.beforeGuide(owner, session,
                    () -> valid(service, owner) && viewFence.getAsBoolean(),
                    () -> openCapturedGuide(service, owner)).exceptionally(failure -> {
                        dispatcher.execute(() -> {
                            if (valid(service, owner) && viewFence.getAsBoolean()) {
                                try { observationInput.seedFocus(owner, session); } catch (RuntimeException unavailable) { }
                                openCapturedGuide(service, owner);
                            }
                        });
                        return false;
                    });
        } catch (RuntimeException unavailable) {
            openCapturedGuide(service, owner);
        }
    }

    private void openCapturedGuide(GuideService service, GuideClientUiState owner) {
        if (!valid(service, owner)) return;
        BooleanSupplier ownerValid = () -> valid(service, owner);
        Runnable openSettings = settings == null ? null : () -> {
            if (!ownerValid.getAsBoolean()) return;
            host.showSettings(() -> openGuide(service), ownerValid, voice.settings(), config -> {
                if (ownerValid.getAsBoolean()) notifications.testNotification(config);
            });
        };
        host.showGuide(view(service, owner), openSettings);
    }

    private GuidePresentationHost.View view(GuideService service, GuideClientUiState owner) {
        return new GuidePresentationHost.View(service, owner, notifications, voice.input(), observationActions,
                observationSubmission, observationImages == null ? null : observationImages.apply(service));
    }

    private static boolean gameplay(GuidePresentationHost.Facts facts) {
        return facts.connected() && facts.surface() == GuidePresentationHost.Surface.GAMEPLAY && !facts.overlayPresent();
    }

    private static boolean feedback(GuidePresentationHost.Facts facts) {
        return !facts.overlayPresent() && !facts.hudHidden()
                && (facts.surface() == GuidePresentationHost.Surface.GAMEPLAY
                        || facts.surface() == GuidePresentationHost.Surface.GUIDE
                        || facts.surface() == GuidePresentationHost.Surface.HUD_INPUT);
    }

    /** Client tick only. Passive surfaces never bind an actor or capture context. */
    public void tick() {
        if (closed) return;
        if (state != null && bound != null) state.selectSession(bound.snapshot().selectedSession());
        hud.tick();
        notifications.tick();
        GuidePresentationHost.Facts facts = host.facts();
        GuidePresentationHost.Input input = host.pollInput();
        boolean gameplay = gameplay(facts);
        boolean feedback = feedback(facts);
        voice.input().setFeedbackVisible(feedback);
        for (int click = 0; click < input.toggleHudClicks(); click++) {
            if (gameplay && settings != null) {
                GuideDisplayConfig current = display.config();
                settings.saveDisplay(current.withUi(current.ui().withHud(
                        current.ui().hud().withEnabled(!current.ui().hud().enabled()))));
            }
        }
        for (int click = 0; click < input.editHudClicks(); click++) {
            if (gameplay && settings != null) {
                BooleanSupplier connectionFence = host.captureFence(GuidePresentationHost.FenceScope.CONNECTION);
                host.showHudEditor(display.config(), settings::saveDisplay, connectionFence);
            }
        }
        for (int click = 0; click < input.interactHudClicks(); click++) {
            if (gameplay) {
                GuideService service = services.forActor(facts.actorId());
                if (state != null) {
                    if (observationInput != null) observationInput.seedFocus(state, state.selectedSession());
                    host.showHudInput(view(service, state), () -> openGuide(service));
                }
            }
        }
        boolean physicalDown = input.pttDown();
        if (gameplay && physicalDown && !pttDown && feedback && voice.input().enabled()) {
            services.forActor(facts.actorId()); // Explicit PTT freezes actor/session and gameplay delivery choice.
            voice.input().pressPtt();
        } else if (pttDown && !physicalDown) {
            voice.input().release();
        }
        pttDown = physicalDown;
        GuidePresentationHost.Facts current = host.facts();
        voice.input().tick(current.windowActive(), current.connected(), physicalDown, feedback);
        if (!voice.input().status().active()) closeVoiceObservations();
    }

    public GuideHudController hud() { return hud; }
    public VoiceRuntime voiceInput() { return voice.input(); }
    public VoiceSettingsActions voiceSettings() { return voice.settings(); }
    public boolean closed() { return closed; }

    public void disconnect() {
        if (bound != null) notifications.invalidated(bound.presentationGeneration());
        closeState(); // Fence the original draft before asynchronous device cleanup.
        voice.input().cancel(VoiceRuntime.CancelReason.DISCONNECTED);
        pttDown = false;
        hud.disconnect();
    }

    private GuideClientUiState.Insertion voiceInsertion(VoiceRuntime.DraftTarget target) {
        return new GuideClientUiState.Insertion(target.uiOwnerId(), target.uiGeneration(),
                target.sessionId(), target.draftRevision());
    }

    private boolean validVoiceTarget(VoiceRuntime.DraftTarget target) {
        return !closed && target != null && state != null && !state.closed() && bound != null
                && host.facts().connected()
                && host.facts().actorId().equals(target.actorId())
                && bound.snapshot().actorId().equals(target.actorId())
                && state.ownerId().equals(target.uiOwnerId()) && state.generation() == target.uiGeneration()
                && bound.presentationGeneration().equals(target.connectionGeneration())
                && bound.presentationSessionOwner(target.sessionId()).filter(target.sessionOwner()::equals).isPresent();
    }

    private boolean voiceAdmissionAllowed(VoiceRuntime.DraftTarget target) {
        GuidePresentationHost.Facts facts = host.facts();
        return validVoiceTarget(target) && facts.windowActive() && feedback(facts);
    }

    private java.util.concurrent.CompletableFuture<dev.openallay.tool.ToolResult<VoiceRuntime.DeliveryReceipt>> sendVoice(
            VoiceRuntime.DraftTarget target, String text, java.util.function.BooleanSupplier admissionFence) {
        if (!voiceAdmissionAllowed(target) || !admissionFence.getAsBoolean()) {
            return java.util.concurrent.CompletableFuture.completedFuture(new dev.openallay.tool.ToolResult.Failure<>(
                    "voice_send_rejected", "The captured voice session is no longer available"));
        }
        GuideService service = bound;
        GuideClientUiState owner = state;
        GuideClientUiState.ObservationCapture observation = observationForVoice(target).orElse(null);
        java.util.function.BooleanSupplier fence = () -> service == bound && voiceAdmissionAllowed(target)
                && admissionFence.getAsBoolean();
        dev.openallay.model.ModelMessage input = dev.openallay.model.ModelMessage.userInput(text, dev.openallay.util.Java8Collections.listOf(),
                observation == null ? Optional.empty() : observation.anchor());
        return service.followUp(target.sessionId(), target.sessionOwner(), input, fence).thenApply(result -> {
            final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.guide.GuideService.InputReceipt> value; dev.openallay.tool.ToolResult.Success<GuideService.InputReceipt> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern0_holder.bound = (dev.openallay.tool.ToolResult.Success<GuideService.InputReceipt>) $oaPattern0_holder.value) != null))) {
                GuideService.InputReceipt receipt = $oaPattern0_holder.bound.value();
                dispatcher.execute(() -> {
                    if (owner == state && !owner.closed()) owner.acceptedObservation(observation);
                    releaseVoiceObservation(target);
                });
                return new dev.openallay.tool.ToolResult.Success<>(new VoiceRuntime.DeliveryReceipt(receipt.id(),
                        receipt.queued() ? VoiceRuntime.DeliveryKind.QUEUED : VoiceRuntime.DeliveryKind.SENT));
            }
            dev.openallay.tool.ToolResult.Failure<GuideService.InputReceipt> failure =
                    (dev.openallay.tool.ToolResult.Failure<GuideService.InputReceipt>) result;
            return new dev.openallay.tool.ToolResult.Failure<>(failure.code(), failure.message());
        });
    }

    private boolean valid(GuideService service, GuideClientUiState owner) {
        return !closed && service == bound && owner == state && !owner.closed()
                && host.facts().connected();
    }
    private void closeState() {
        closeVoiceObservations();
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
