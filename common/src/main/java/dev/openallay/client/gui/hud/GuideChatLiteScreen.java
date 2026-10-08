package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.GuideNativeFont;

import dev.openallay.client.gui.MinecraftClientWindow;

import dev.openallay.client.gui.GuideClientUiState;
import dev.openallay.client.observation.ClientObservationInputCoordinator;
import dev.openallay.client.observation.GuideObservationInputActions;
import dev.openallay.client.observation.GuideObservationSubmission;
import dev.openallay.client.observation.ObservationAnchorPresentation;
import dev.openallay.model.image.ImageInputCapability;
import dev.openallay.client.gui.GuideComposerGeometry;
import dev.openallay.client.gui.GuideKeyInput;
import dev.openallay.client.gui.GuideKeyIntent;
import dev.openallay.client.gui.GuideNativeInput;
import dev.openallay.client.gui.GuideNativeFocus;
import dev.openallay.client.gui.GuideTextInputFocus;
import dev.openallay.client.gui.GuideUiNotice;
import dev.openallay.client.gui.OpenAllayButton;
import dev.openallay.client.gui.OpenAllayKeyMappings;
import dev.openallay.client.gui.OpenAllayWidgetTheme;
import dev.openallay.client.voice.VoiceRuntime;
import dev.openallay.guide.GuideService;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.composer.SlashCommandDispatcher;
import dev.openallay.guide.ui.GuideDisplayRuntime;
import dev.openallay.guide.ui.GuideUiLayout;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.recipe.config.RecipeClientRuntime;
import dev.openallay.recipe.RecipeNavigationResult;
import dev.openallay.guide.ui.hud.GuideHudPresenter;
import dev.openallay.guide.ui.hud.GuideHudView;
import dev.openallay.tool.ToolResult;
import java.util.List;
import java.util.Objects;
import dev.openallay.client.gui.GuideGraphics;
import dev.openallay.client.gui.GuideNativeButton;
import dev.openallay.client.gui.GuideMultilineEditor;
import dev.openallay.client.gui.GuideTooltip;

import dev.openallay.client.gui.GuideInputKey;
import dev.openallay.client.gui.GuideInputMouse;
import dev.openallay.platform.minecraft.MinecraftComponents;


/** Explicit compact native input surface. Gameplay keys/mouse are not forwarded while it is open. */
public final class GuideChatLiteScreen extends dev.openallay.client.gui.GuideNativeScreen {
    private final GuideService service;
    private final GuideClientUiState state;
    private GuideObservationInputActions observationActions;
    private GuideObservationSubmission observationSubmission;
    private boolean observationCapturing;
    private GuideNativeButton observationRefresh;
    private GuideNativeButton observationRemove;
    private GuideNativeButton observationAttach;
    private GuideNativeButton observationRemoveImage;
    private GuideUiLayout.Rect observationBounds;
    private final GuideDisplayRuntime display;
    private final Runnable openFullscreen;
    private final VoiceRuntime voice;
    private final GuideHudPresenter presenter = new GuideHudPresenter();
    private final GuideHudResultRenderer results = new GuideHudResultRenderer();
    private RecipeClientRuntime recipes = RecipeClientRuntime.defaults();
    private GuideHudReadingLayout readingLayout;
    private GuideUiLayout.Rect resultBounds;
    private GuideUiLayout.Rect scrollbar;
    private boolean draggingScrollbar;
    private int focusedResult = -1;
    private GuideNativeButton latest;
    private GuideNativeButton back;
    private GuideNativeButton voiceDrafts;
    private long presentationTicks;
    private boolean initialResults = true;
    private GuideClientUiState.ViewAttachment attachment;
    private GuideMultilineEditor composer;
    private GuideNativeButton send;
    private GuideNativeButton stop;
    private GuideNativeButton mic;
    private GuideNativeButton intentAction;
    private GuideSnapshot projectedSnapshot;
    private GuideHudView view;
    private String session;
    private GuideUiNotice notice = GuideUiNotice.info("");
    private boolean submitting;
    private boolean micHeld;
    private boolean pttHeld;
    private Card card;

    public GuideChatLiteScreen(GuideService service, GuideClientUiState state,
            GuideDisplayRuntime display, Runnable openFullscreen, VoiceRuntime voice) {
        super(MinecraftComponents.translatable("screen.openallay.hud.interact"));
        this.service = Objects.requireNonNull(service, "service");
        this.observationSubmission = GuideObservationSubmission.existing(service);
        this.state = Objects.requireNonNull(state, "state");
        this.display = Objects.requireNonNull(display, "display");
        this.openFullscreen = Objects.requireNonNull(openFullscreen, "openFullscreen");
        this.voice = voice;
        session = service.snapshot().selectedSession();
        view = presenter.projectInteractive(service.snapshot(), display.config());
    }

    public GuideChatLiteScreen withObservationInput(GuideObservationInputActions input) {
        observationActions = Objects.requireNonNull(input, "input");
        return this;
    }

    public GuideChatLiteScreen withObservationSubmission(GuideObservationSubmission sender) {
        observationSubmission = Objects.requireNonNull(sender, "sender");
        return this;
    }

    private boolean hasObservationStrip() {
        return observationActions != null || state.observation(session).isPresent();
    }

    private void refreshObservation() {
        if (observationActions == null || observationCapturing) return;
        try {
            new ClientObservationInputCoordinator(observationActions, action -> MinecraftClientWindow.execute(minecraft, action)).refresh(state, session);
        } catch (RuntimeException unavailable) {
            notice = GuideUiNotice.warning(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.observation.capture_failed")));
        }
        project();
    }

    private void attachObservationFrame() {
        if (observationActions == null || observationCapturing) return;
        observationCapturing = true;
        try {
            new ClientObservationInputCoordinator(observationActions, action -> MinecraftClientWindow.execute(minecraft, action)).attachCurrentFrame(state, session)
                    .whenComplete((applied, failure) -> MinecraftClientWindow.execute(minecraft, () -> {
                        observationCapturing = false;
                        if (attachment == null) return;
                        if (failure != null) notice = GuideUiNotice.warning(
                                MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.observation.capture_failed")));
                        project();
                    }));
        } catch (RuntimeException unavailable) {
            observationCapturing = false;
            notice = GuideUiNotice.warning(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.observation.capture_failed")));
        }
        project();
    }

    private void initObservationControls(GuideUiLayout.Rect strip) {
        observationBounds = hasObservationStrip() ? new GuideUiLayout.Rect(strip.x(), strip.y(), strip.width(), 12) : GuideUiLayout.Rect.EMPTY;
        observationRefresh = observationRemove = observationAttach = observationRemoveImage = null;
        if (observationBounds.height() == 0) return;
        int x = strip.right() - 64;
        observationRemoveImage = addGuideWidget(OpenAllayButton.create(MinecraftComponents.literal("×▧"), button -> {
            state.removeObservationImage(session); project();
        }).bounds(x, strip.y(), 16, 12).tooltip(GuideTooltip.create(MinecraftComponents.translatable("screen.openallay.observation.remove_frame"))).build());
        observationRemove = addGuideWidget(OpenAllayButton.create(MinecraftComponents.literal("×"), button -> {
            state.removeObservation(session); project();
        }).bounds(x + 16, strip.y(), 16, 12).tooltip(GuideTooltip.create(MinecraftComponents.translatable("screen.openallay.observation.remove"))).build());
        observationRefresh = addGuideWidget(OpenAllayButton.create(MinecraftComponents.literal("↻"), button -> refreshObservation())
                .bounds(x + 32, strip.y(), 16, 12).tooltip(GuideTooltip.create(MinecraftComponents.translatable("screen.openallay.observation.refresh"))).build());
        observationAttach = addGuideWidget(OpenAllayButton.create(MinecraftComponents.literal("▧"), button -> attachObservationFrame())
                .bounds(x + 48, strip.y(), 16, 12).tooltip(GuideTooltip.create(MinecraftComponents.translatable("screen.openallay.observation.attach_frame"))).build());
    }

    private void renderObservationStrip(GuideGraphics graphics, int mouseX, int mouseY) {
        if (observationBounds == null || observationBounds.height() == 0 || !readingLayout.footerFits()) return;
        net.minecraft.network.chat.Component label = MinecraftComponents.translatable("screen.openallay.observation.add_focus");
        java.util.Optional<dev.openallay.world.ClientObservationAnchor> anchor = state.observation(session);
        if (anchor.isPresent()) {
            label = MinecraftComponents.empty();
            for (dev.openallay.client.observation.ObservationAnchorPresentation.Chip chip : ObservationAnchorPresentation.chips(anchor.orElseThrow())) {
                if (!MinecraftComponents.getString(label).isEmpty()) label = MinecraftComponents.append(MinecraftComponents.copy(label), " · ");
                label = MinecraftComponents.append(MinecraftComponents.copy(label), MinecraftComponents.translatable(chip.key(), chip.value()));
            }
            if (anchor.orElseThrow().image().isPresent()) label = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(label), " · "),
                    MinecraftComponents.translatable("screen.openallay.observation.frame"));
        }
        int labelWidth = Math.max(0, observationBounds.width() - 66);
        graphics.text(font, GuideNativeFont.plainSubstrByWidth(font, MinecraftComponents.getString(label), labelWidth),
                observationBounds.x(), observationBounds.y() + 2, OpenAllayWidgetTheme.MUTED);
        if (new GuideUiLayout.Rect(observationBounds.x(), observationBounds.y(), labelWidth, 12).contains(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font, label, mouseX, mouseY);
        }
    }

    /** Shared current recipe preferences; no new service or model is created for HUD reading. */
    public GuideChatLiteScreen withRecipes(RecipeClientRuntime recipes) {
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        return this;
    }
    public GuideHudResultRenderer.Receipt resultReceipt() { return results.receipt(); }

    @Override public void onClose() {
        if (MinecraftClientWindow.canInterruptScreen(minecraft)) super.onClose();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
    @Override protected void guideInitialFocus() {} // Text focus starts only from a click/explicit navigation.

    @Override protected void initGuideScreen() {
        projectedSnapshot = null; // GUI resize changes native wrap width even without a new snapshot.
        card = Card.calculate(width, height);
        int inner = Math.max(1, card.width() - 16);
        readingLayout = GuideHudReadingLayout.calculate(card.x(), card.y(), card.width(), card.height());
        resultBounds = readingLayout.results();
        scrollbar = readingLayout.scrollbar();
        draggingScrollbar = false;
        results.invalidate();
        dev.openallay.guide.ui.GuideUiLayout.Rect strip = readingLayout.composer();
        initObservationControls(strip);
        int observationHeight = hasObservationStrip() ? 14 : 0;
        strip = new GuideUiLayout.Rect(strip.x(), strip.y() + observationHeight,
                strip.width(), Math.max(1, strip.height() - observationHeight));
        // Hidden tiny layouts still need a readable native text-field width for later reflow.
        dev.openallay.guide.ui.GuideUiLayout.Rect input = new GuideUiLayout.Rect(strip.x(), strip.y(),
                Math.max(dev.openallay.client.gui.GuideNativeMultilineText.defaultTotalPadding() + 1, strip.width()), Math.max(1, strip.height()));
        if (composer == null) {
            composer = dev.openallay.client.gui.GuideNativeMultilineText.create(
                    font, input.x(), input.y(), input.width(), input.height(),
                    MinecraftComponents.translatable("screen.openallay.composer.placeholder"),
                    MinecraftComponents.translatable("screen.openallay.composer.narration"));
        } else {
            GuideComposerGeometry.resize(composer, input);
        }
        if (!composer.getValue().equals(state.readText(session))) dev.openallay.client.gui.GuideNativeMultilineText.setValue(composer, state.readText(session), true);
        composer.setValueListener(value -> state.setText(session, value));
        composer.widget().guideVisible(readingLayout.footerFits());
        addGuideWidgetHandle(composer.widget());
        int actionY = readingLayout.actions().y();
        int actionWidth = Math.max(1, (inner - 12) / (voice != null && voice.enabled() ? 4 : 3));
        intentAction = addGuideWidget(OpenAllayButton.create(MinecraftComponents.empty(), button -> {
            GuideClientUiState.DraftIntent intent = state.intent(session);
            if (intent.editing()) state.resetIntent(session); // Explicit conversion never deletes text/images.
            else state.setMode(session, intent.steer() ? GuideClientUiState.DraftMode.FOLLOW_UP : GuideClientUiState.DraftMode.STEER);
            project();
        }).bounds(card.x() + Math.max(8, card.width() - 120), card.y() + 5, Math.min(112, inner), 18).build());
        send = addGuideWidget(OpenAllayButton.create(MinecraftComponents.translatable("screen.openallay.action.send"), button -> submit())
                .bounds(card.x() + 8, actionY, actionWidth, 20).build());
        stop = addGuideWidget(OpenAllayButton.create(MinecraftComponents.translatable("screen.openallay.action.stop"), button -> {
            state.stopIntent(session);
            service.cancel();
        })
                .bounds(card.x() + 12 + actionWidth, actionY, actionWidth, 20).build());
        addGuideWidget(OpenAllayButton.create(MinecraftComponents.translatable("screen.openallay.hud.fullscreen"), button -> openFullscreen.run())
                .bounds(card.x() + 16 + actionWidth * 2, actionY, actionWidth, 20).build());
        if (voice != null && voice.enabled()) mic = addGuideWidget(OpenAllayButton.create(
                MinecraftComponents.translatable("screen.openallay.voice.mic"), button -> { micHeld = true; voice.pressExternalPtt(); })
                .bounds(card.x() + 20 + actionWidth * 3, actionY, actionWidth, 20).build());
        back = addGuideWidget(OpenAllayButton.create(MinecraftComponents.translatable("screen.openallay.hud.back_results"), button -> {
            results.back(); project();
        }).bounds(card.x() + 8, readingLayout.navigation().y(), Math.min(112, inner / 2), 14).build());
        latest = addGuideWidget(OpenAllayButton.create(MinecraftComponents.translatable("screen.openallay.hud.latest"), button -> scrollResults(() -> results.scroll().latest()))
                .bounds(card.x() + card.width() - 106, readingLayout.navigation().y(), 98, 14).build());
        voiceDrafts = addGuideWidget(OpenAllayButton.create(MinecraftComponents.empty(), button -> openFullscreen.run())
                .bounds(card.x() + 8 + inner / 3, readingLayout.navigation().y(), Math.max(1, inner / 3 - 4), 14).build());
        voiceDrafts.visible = false;
        dev.openallay.client.gui.GuideNativeWidgetTooltips.set(voiceDrafts, GuideTooltip.create(MinecraftComponents.translatable("screen.openallay.hud.voice_drafts.description")));
        if (!readingLayout.footerFits()) {
            composer.widget().guideVisible(false);
            send.visible = false;
            stop.visible = false;
            intentAction.visible = false;
            if (mic != null) mic.visible = false;
            // Native children still retain the draft. At physically impossible sizes none can
            // paint or take focus outside the card, and Escape still returns immediately.
            guideWidgetChildren().forEach(child -> child.guideVisible(false));
        }
        clearGuideWidgetFocus();
        project();
    }

    @Override protected void repositionGuideElements() {
        boolean composerFocused = composer != null && guideWidgetFocused(composer.widget());
        guideRebuildWidgets();
        // Only the same composer survives the rebuild. Never reattach a discarded button.
        if (composerFocused && composer.widget().guideVisible() && composer.widget().guideActive()) setFocused(composer.widget());
    }

    @Override protected void guideAdded() {
        if (!state.closed()) attachment = state.attach(GuideClientUiState.Surface.HUD_INPUT, session);
    }

    @Override public void tick() {
        presentationTicks++;
        results.tick();
        if (state.closed() || !MinecraftClientWindow.playerPresent(minecraft) || !MinecraftClientWindow.worldPresent(minecraft)) { onClose(); return; }
        String selected = service.snapshot().selectedSession();
        if (!session.equals(selected)) {
            session = selected;
            notice = GuideUiNotice.info("");
            results.back();
            scrollResults(() -> results.scroll().latest());
            focusedResult = -1;
            state.selectSession(session);
            if (attachment != null) attachment.selectSession(session);
            if (!composer.getValue().equals(state.readText(session))) dev.openallay.client.gui.GuideNativeMultilineText.setValue(composer, state.readText(session), true);
        } else if (!composer.getValue().equals(state.readText(session))) {
            dev.openallay.client.gui.GuideNativeMultilineText.setValue(composer, state.readText(session), true);
        }
        project();
    }

    private void project() {
        GuideSnapshot snapshot = service.snapshot();
        GuideHudView next = presenter.projectInteractive(snapshot, display.config());
        if (snapshot != projectedSnapshot || !next.equals(view)) {
            projectedSnapshot = snapshot;
            view = next;

        }
        if (results.prepare(view, font, Math.max(1, resultBounds.width() - 6), resultBounds.height())) focusedResult = -1;
        if (initialResults) { scrollResults(() -> results.scroll().latest()); initialResults = false; }
        if (latest != null) latest.visible = readingLayout.footerFits()
                && results.scroll().maximum() > 0 && !results.scroll().followingLatest();
        if (back != null) back.visible = readingLayout.footerFits() && results.detailOpen();
        int pendingVoice = state.pendingInsertions(session).size();
        if (voiceDrafts != null) {
            boolean recoverable = pendingVoice > 0;
            voiceDrafts.visible = pendingVoiceRecoveryVisible(readingLayout.footerFits(), pendingVoice);
            voiceDrafts.setMessage(MinecraftComponents.translatable("screen.openallay.hud.voice_drafts", pendingVoice));
            // Recovery is explicit navigation only; it never inserts or sends a transcript.
            int inner = readingLayout.navigation().width();
            if (back != null) back.setWidth(Math.max(1, Math.min(112, recoverable ? inner / 3 - 4 : inner / 2)));
            if (latest != null) {
                int latestWidth = Math.max(1, Math.min(98, recoverable ? inner / 3 - 4 : inner));
                latest.setWidth(latestWidth);
                dev.openallay.client.gui.GuideNativeWidgetGeometry.x(latest, readingLayout.navigation().right() - latestWidth);
            }
        }
        dev.openallay.guide.GuideSessionSnapshot selected = snapshot.sessions().stream().filter(value -> value.sessionId().equals(session)).findFirst().orElse(null);
        boolean active = selected != null && (selected.workingRequestId() != null
                || selected.requests().stream().anyMatch(request -> !request.terminal()));
        GuideClientUiState.DraftIntent intent = state.intent(session);
        java.util.UUID pendingId = intent.pendingId();
        boolean targetPresent = selected != null && selected.pendingMessages().stream()
                .anyMatch(pending -> pending.id().equals(pendingId));
        if (intent.editing() && !targetPresent && !state.pendingEditSubmissionInFlight(session)) {
            state.invalidatePendingEdit(session, intent.pendingId());
            intent = state.intent(session);
        }
        if (send != null) {
            send.active = !submitting && !state.intentSubmissionInFlight(session)
                    && (!dev.openallay.util.Java8Strings.isBlank(composer.getValue()) || intent.editing() && !state.images().empty()
                            || state.observation(session).flatMap(value -> value.image()).isPresent())
                    && !intent.editInvalid() && !state.images().pending();
            send.setMessage(MinecraftComponents.translatable(intent.editing() ? "screen.openallay.pending.save"
                    : "screen.openallay.action.send"));
        }
        if (intentAction != null) {
            intentAction.active = !submitting && !state.intentSubmissionInFlight(session);
            intentAction.setMessage(MinecraftComponents.translatable(intent.editing() ? "screen.openallay.hud.new_draft"
                    : intent.steer() ? "screen.openallay.hud.mode.steer" : "screen.openallay.hud.mode.follow_up"));
        }
        if (stop != null) stop.active = active;
        if (observationRefresh != null) {
            boolean visible = readingLayout.footerFits();
            observationRefresh.visible = observationAttach.visible = visible && observationActions != null;
            observationRefresh.active = observationAttach.active = !observationCapturing;
            observationRemove.visible = visible && state.observation(session).isPresent();
            observationRemoveImage.visible = visible && state.observation(session).flatMap(value -> value.image()).isPresent();
            observationAttach.setMessage(MinecraftComponents.literal(observationCapturing ? "…" : "▧"));
        }
    }

    private void submit() {
        if (submitting || state.closed() || composer == null || state.intentSubmissionInFlight(session)) return;
        String text = state.readText(session);
        GuideClientUiState.IntentCapture intentCapture = state.captureIntent(session);
        GuideClientUiState.DraftIntent intent = intentCapture.intent();
        GuideClientUiState.ObservationCapture observation = state.captureObservation(session);
        List<dev.openallay.model.image.ImageReference> observedImages = dev.openallay.util.Java8Collections.toList(observation.anchor().stream()
                .flatMap(value -> value.image().stream()).map(value -> value.image()));
        if (dev.openallay.util.Java8Strings.isBlank(text) && observedImages.isEmpty() && (!intent.editing() || state.images().empty())) return;
        GuideClientUiState.Insertion captured = state.captureInsertion(session);
        SlashCommandDispatcher.Dispatch dispatch = dispatchDraft(text, intent,
                ordinaryText -> SlashCommandDispatcher.dispatch(ordinaryText, service, completion -> MinecraftClientWindow.execute(minecraft, () -> {
                    if (state.closed()) return;
                    if (completion.successful()) state.clearAcceptedText(captured, text);
                    if (attachment == null || !captured.session().equals(session)) return;
                    String feedback = MinecraftComponents.getString(MinecraftComponents.translatable("openallay.guide.slash." + completion.code()));
                    notice = completion.successful() ? GuideUiNotice.success(feedback) : GuideUiNotice.error(feedback);
                })));
        if (dispatch.handled()) return;
        dev.openallay.guide.GuideSessionSnapshot selected = service.snapshot().sessions().stream().filter(value -> value.sessionId().equals(session)).findFirst().orElse(null);
        boolean active = selected != null && (selected.workingRequestId() != null
                || selected.requests().stream().anyMatch(request -> !request.terminal()));
        boolean targetPresent = selected != null && selected.pendingMessages().stream()
                .anyMatch(pending -> pending.id().equals(intent.pendingId()));
        Route route = route(intent, active, targetPresent);
        if (state.images().pending()) return;
        if (route == Route.BLOCKED) {
            state.invalidatePendingEdit(intentCapture);
            notice = GuideUiNotice.warning(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.hud.edit_invalid")));
            project();
            return;
        }
        // Observation refs are visible here; explicit fullscreen paste drafts stay retained unless editing.
        dev.openallay.client.gui.ComposerImageDraft.Submission images = state.images().captureSubmission();
        java.util.List<dev.openallay.model.image.ImageReference> inputImages = intent.editing() ? state.inputImageReferences(session, observation) : observedImages;
        dev.openallay.guide.ui.GuideUiView inputView = dev.openallay.guide.ui.GuideUiView.from(service.snapshot(), display.config());
        if (!inputImages.isEmpty() && inputView.selectedImageInputCapability() != ImageInputCapability.SUPPORTED) {
            notice = GuideUiNotice.error(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.image.model_unsupported")));
            return;
        }
        dev.openallay.model.ModelMessage message = dev.openallay.model.ModelMessage.userInput(dispatch.normalizedText(),
                intent.editing() ? state.images().references() : dev.openallay.util.Java8Collections.listOf(), observation.anchor());
        boolean attachmentsRetained = !state.images().empty() && !intent.editing();
        if (attachmentsRetained) notice = GuideUiNotice.info(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.hud.attachments_retained")));
        if (!state.beginIntentSubmission(intentCapture)) return;
        GuideClientUiState.ObservationLease observationLease = state.leaseObservation(observation);
        submitting = true;
        java.util.concurrent.CompletableFuture<? extends ToolResult<?>> future;
        try {
            future = observationSubmission.send(service, submissionRoute(route), intent.pendingId(), message, observation);
        } catch (RuntimeException failure) {
            observationLease.close();
            state.completeIntentSubmission(intentCapture);
            submitting = false;
            notice = GuideUiNotice.error(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.composer.submit_failed")));
            if (attachmentsRetained) notice = new GuideUiNotice(notice.severity(), notice.placement(), notice.message()
                    + " · " + MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.hud.attachments_retained")));
            return;
        }
        future.whenComplete((result, failure) -> MinecraftClientWindow.execute(minecraft, () -> {
            try {
                if (state.closed()) return;
                boolean accepted = failure == null && submissionAccepted(intent.editing(), result);
                if (accepted) {
                    state.clearAcceptedText(captured, text);
                    state.clearAcceptedIntent(intentCapture);
                    if (intent.editing()) state.images().accepted(images);
                    state.acceptedObservation(observation);
                } else if (failure == null && intent.editing() && result instanceof ToolResult.Success<?>) {
                    state.invalidatePendingEdit(intentCapture);
                }
                if (attachment == null || !captured.session().equals(session)) return;
                if (failure != null) {
                    notice = GuideUiNotice.error(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.composer.submit_failed")));
                } else if (accepted) {
                    notice = GuideUiNotice.acceptedSubmission(submissionRoute(route), intent.pendingId(), result,
                            service.snapshot(), captured.session());
                } else if (intent.editing() && result instanceof ToolResult.Success<?>) {
                    notice = GuideUiNotice.warning(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.hud.edit_invalid")));
                } else {
final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<?> value; ToolResult.Failure<?> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = result) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern0_holder.bound = (ToolResult.Failure<?>) $oaPattern0_holder.value) != null))) {
                    notice = GuideUiNotice.error($oaPattern0_holder.bound.code() + ": " + $oaPattern0_holder.bound.message());
                } else {
                    notice = GuideUiNotice.error(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.composer.submit_failed")));
                }
}
                if (attachmentsRetained) notice = new GuideUiNotice(notice.severity(), notice.placement(), notice.message()
                        + " · " + MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.hud.attachments_retained")));
            } finally {
                observationLease.close();
                state.completeIntentSubmission(intentCapture);
                submitting = false;
            }
        }));
    }

    /** A captured queued edit is literal message content, never a local composer command. */
    static SlashCommandDispatcher.Dispatch dispatchDraft(String text, GuideClientUiState.DraftIntent intent,
            java.util.function.Function<String, SlashCommandDispatcher.Dispatch> ordinaryDispatch) {
        if (intent.editing()) return new SlashCommandDispatcher.Dispatch(false, true, text);
        return ordinaryDispatch.apply(text);
    }

    static boolean pendingVoiceRecoveryVisible(boolean footerFits, int pendingCount) {
        return footerFits && pendingCount > 0;
    }

    enum Route { ASK, FOLLOW_UP, STEER, EDIT_PENDING, BLOCKED }
    static Route route(GuideClientUiState.DraftIntent intent, boolean active, boolean pendingPresent) {
        if (intent.editing()) return intent.editInvalid() || !pendingPresent ? Route.BLOCKED : Route.EDIT_PENDING;
        return !active ? Route.ASK : intent.steer() ? Route.STEER : Route.FOLLOW_UP;
    }
    static GuideClientUiState.SubmissionRoute submissionRoute(Route route) {
        {
dev.openallay.client.gui.GuideClientUiState.SubmissionRoute $oaSwitch1_exit_result;
$oaSwitch1_exit: {
switch ((route)) {
case ASK:
{
$oaSwitch1_exit_result = GuideClientUiState.SubmissionRoute.ASK; break $oaSwitch1_exit;
}
case FOLLOW_UP:
{
$oaSwitch1_exit_result = GuideClientUiState.SubmissionRoute.FOLLOW_UP; break $oaSwitch1_exit;
}
case STEER:
{
$oaSwitch1_exit_result = GuideClientUiState.SubmissionRoute.STEER; break $oaSwitch1_exit;
}
case EDIT_PENDING:
{
$oaSwitch1_exit_result = GuideClientUiState.SubmissionRoute.EDIT_PENDING; break $oaSwitch1_exit;
}
case BLOCKED:
{
$oaSwitch1_exit_result = GuideClientUiState.SubmissionRoute.EDIT_INVALID; break $oaSwitch1_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch1_exit_result;
}
    }
    static boolean submissionAccepted(boolean editing, ToolResult<?> result) {
        final class $oaPattern1_Holder { dev.openallay.tool.ToolResult<?> value; ToolResult.Success<?> bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
return (($oaPattern1_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern1_holder.bound = (ToolResult.Success<?>) $oaPattern1_holder.value) != null))
                && (editing ? Boolean.TRUE.equals($oaPattern1_holder.bound.value()) : $oaPattern1_holder.bound.value() instanceof java.util.UUID);
    }

    @Override public boolean guideKeyPressed(GuideInputKey event) {
        GuideKeyInput input = GuideKeyInput.from(event);
        if (input.intent() == GuideKeyIntent.ESCAPE) { onClose(); return true; }
        if (composer != null && guideWidgetFocused(composer.widget()) && input.intent() == GuideKeyIntent.ENTER && !input.shift()) {
            submit(); return true;
        }
        if (voice != null && voice.enabled() && (composer == null || !guideWidgetFocused(composer.widget()))
                && !dev.openallay.client.gui.GuideNativeKeyMappings.unbound(OpenAllayKeyMappings.VOICE_PTT) && GuideNativeInput.matches(OpenAllayKeyMappings.VOICE_PTT, event)) {
            pttHeld = true;
            voice.pressExternalPtt(); // Screen physical mappings are released natively; own release below.
            return true;
        }
        if (composer == null || !guideWidgetFocused(composer.widget())) {
            switch ((input.intent())) {
case PAGE_UP:
{
{ scrollResults(() -> results.scroll().page(-1)); return true; }
}
case PAGE_DOWN:
{
{ scrollResults(() -> results.scroll().page(1)); return true; }
}
case HOME:
{
{ scrollResults(() -> results.scroll().first()); return true; }
}
case END:
{
{ scrollResults(() -> results.scroll().latest()); return true; }
}
case UP:
case DOWN:
{
{
                    List<GuideHudResultRenderer.Hit> hits = visibleResultHits();
                    if (!hits.isEmpty()) {
                        focusedResult = Math.floorMod(focusedResult + (input.intent() == GuideKeyIntent.DOWN ? 1 : -1), hits.size());
                        if (dev.openallay.client.gui.GuideNativeNarrator.isActive(minecraft)) dev.openallay.client.gui.GuideNativeNarrator.sayNow(minecraft, MinecraftComponents.literal(hits.get(focusedResult).narration()));
                        return true;
                    }
                }
break;
}
case ENTER:
case SPACE:
{
{
                    List<GuideHudResultRenderer.Hit> hits = visibleResultHits();
                    if (focusedResult >= 0 && focusedResult < hits.size()) { resultAction(hits.get(focusedResult).action()); return true; }
                }
break;
}
}

        }
        return super.guideKeyPressed(event);
    }

    private List<GuideHudResultRenderer.Hit> visibleResultHits() {
        return dev.openallay.util.Java8Collections.toList(results.hits(font, resultBounds).stream().filter(hit -> hit.bounds().bottom() > resultBounds.y()
                && hit.bounds().y() < resultBounds.bottom()));
    }
    private void scrollResults(Runnable navigation) {
        int previousOffset = results.scroll().offset();
        navigation.run();
        if (previousOffset != results.scroll().offset()) {
            results.invalidateHits();
            focusedResult = -1;
        }
    }
    @Override public boolean guideMouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (resultBounds.contains(x, y) || scrollbar.contains(x, y)) {
            scrollResults(() -> results.scroll().wheel(scrollY));
            focusedResult = -1;
            return true;
        }
        return super.guideMouseScrolled(x, y, scrollX, scrollY);
    }
    @Override public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) {
        // Native controls still own dispatch, including the composer's external scrollbar.
        if (GuideNativeInput.isLeftClick(event) && !composerContains(event.x(), event.y())) GuideNativeFocus.clear(this);
        if (GuideNativeInput.isLeftClick(event) && scrollbar.contains(event.x(), event.y()) && results.scroll().maximum() > 0) {
            draggingScrollbar = true;
            GuideNativeFocus.clear(this);
            scrollAt(event.y());
            return true;
        }
        if (GuideNativeInput.isLeftClick(event) && resultBounds.contains(event.x(), event.y())) {
            GuideNativeFocus.clear(this);
            for (dev.openallay.client.gui.hud.GuideHudResultRenderer.Hit hit : visibleResultHits()) if (hit.bounds().contains(event.x(), event.y())) {
                resultAction(hit.action()); return true;
            }
            return true;
        }
        return super.guideMouseClicked(event, doubleClick);
    }
    private boolean composerContains(double x, double y) {
        if (composer == null) return false;
        GuideUiLayout.Rect input = new GuideUiLayout.Rect(
                composer.widget().getX(), composer.widget().getY(), composer.widget().getWidth(), composer.widget().getHeight());
        return input.contains(x, y) && composer.widget().isMouseOver(x, y);
    }
    @Override public boolean guideMouseDragged(GuideInputMouse event, double dx, double dy) {
        if (draggingScrollbar) { scrollAt(event.y()); return true; }
        return super.guideMouseDragged(event, dx, dy);
    }
    private void scrollAt(double y) {
        int thumb = scrollbarThumbHeight();
        double range = Math.max(1, scrollbar.height() - thumb);
        scrollResults(() -> results.scroll().move((y - scrollbar.y() - thumb / 2.0) / range * results.scroll().maximum()));
    }
    private int scrollbarThumbHeight() {
        return Math.min(scrollbar.height(), Math.max(12, scrollbar.height() * resultBounds.height() / Math.max(1, results.scroll().totalHeight())));
    }
    private void resultAction(GuideHudResultRenderer.Action action) {
        focusedResult = -1;
        Objects.requireNonNull(action);
        final class $oaPattern2_Holder { dev.openallay.client.gui.hud.GuideHudResultRenderer.Action value; GuideHudResultRenderer.Action.Tool bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = action) instanceof dev.openallay.client.gui.hud.GuideHudResultRenderer.Action.Tool && (($oaPattern2_holder.bound = (GuideHudResultRenderer.Action.Tool) $oaPattern2_holder.value) != null))) {
            results.openTool($oaPattern2_holder.bound.rowId());
        } else {
final class $oaPattern3_Holder { dev.openallay.client.gui.hud.GuideHudResultRenderer.Action value; GuideHudResultRenderer.Action.Sources bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = action) instanceof dev.openallay.client.gui.hud.GuideHudResultRenderer.Action.Sources && (($oaPattern3_holder.bound = (GuideHudResultRenderer.Action.Sources) $oaPattern3_holder.value) != null))) {
            results.openSources($oaPattern3_holder.bound.sources());
        } else {
final class $oaPattern4_Holder { dev.openallay.client.gui.hud.GuideHudResultRenderer.Action value; GuideHudResultRenderer.Action.Semantic bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = action) instanceof dev.openallay.client.gui.hud.GuideHudResultRenderer.Action.Semantic && (($oaPattern4_holder.bound = (GuideHudResultRenderer.Action.Semantic) $oaPattern4_holder.value) != null))) {
            dev.openallay.client.gui.MinecraftSemanticRenderer.Intent intent = Objects.requireNonNull($oaPattern4_holder.bound.intent());
            final class $oaPattern5_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.BrowseRecipes bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.BrowseRecipes && (($oaPattern5_holder.bound = (dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.BrowseRecipes) $oaPattern5_holder.value) != null))) {
                navigate(recipes.openRecipes($oaPattern5_holder.bound.itemId()));
            } else {
final class $oaPattern6_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.BrowseUsages bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.BrowseUsages && (($oaPattern6_holder.bound = (dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.BrowseUsages) $oaPattern6_holder.value) != null))) {
                navigate(recipes.openUsages($oaPattern6_holder.bound.itemId()));
            } else {
final class $oaPattern7_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.ExactRecipe bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.ExactRecipe && (($oaPattern7_holder.bound = (dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.ExactRecipe) $oaPattern7_holder.value) != null))) {
                navigate(recipes.openExact($oaPattern7_holder.bound.reference()));
            } else {
final class $oaPattern8_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Source bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
if ((($oaPattern8_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Source && (($oaPattern8_holder.bound = (dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Source) $oaPattern8_holder.value) != null))) {
                openSources($oaPattern8_holder.bound.originInvocationId());
            } else {
final class $oaPattern9_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Evidence bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Evidence && (($oaPattern9_holder.bound = (dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Evidence) $oaPattern9_holder.value) != null))) {
                openSources($oaPattern9_holder.bound.originInvocationId());
            } else {
final class $oaPattern10_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Choice bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Choice && (($oaPattern10_holder.bound = (dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Choice) $oaPattern10_holder.value) != null))) {
                notice = GuideUiNotice.warning(
                        MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.choice.unavailable", $oaPattern10_holder.bound.choiceId())));
            }
}
}
}
}
}
        }
}
}
        project();
    }
    private void openSources(String invocation) {
        List<dev.openallay.guide.GuideSource> sources = dev.openallay.util.Java8Collections.toList(view.rows().stream()
                .filter(dev.openallay.guide.ui.GuideUiRow.Tool.class::isInstance)
                .map(dev.openallay.guide.ui.GuideUiRow.Tool.class::cast)
                .filter(tool -> tool.activity().invocationId().equals(invocation))
                .flatMap(tool -> tool.activity().sources().stream()));
        if (!sources.isEmpty()) results.openSources(sources);
    }
    private void navigate(RecipeNavigationResult result) {
        java.lang.String $oaSwitch0_exit_result_conditional1;
if (result.opened()) {
$oaSwitch0_exit_result_conditional1 = "screen.openallay.recipe.viewer_opened";
} else {
final java.lang.String $oaSwitch0_exit_result_prior0 = "screen.openallay.recipe.";
java.lang.String $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((result.code())) {
case "exact_unsupported":
case "preferred_viewer_unavailable":
case "viewer_unavailable":
case "unknown_item":
case "wrong_thread":
case "viewer_failure":
{
$oaSwitch0_exit_result = result.code(); break $oaSwitch0_exit;
}
default:
{
$oaSwitch0_exit_result = "viewer_failure"; break $oaSwitch0_exit;
}
}
}
$oaSwitch0_exit_result_conditional1 = $oaSwitch0_exit_result_prior0 + $oaSwitch0_exit_result;
}
String feedback = MinecraftComponents.getString(MinecraftComponents.translatable($oaSwitch0_exit_result_conditional1));
        notice = result.opened() ? GuideUiNotice.info(feedback) : GuideUiNotice.warning(feedback);
    }

    @Override public boolean guideKeyReleased(GuideInputKey event) {
        if (pttHeld && GuideNativeInput.matches(OpenAllayKeyMappings.VOICE_PTT, event)) { pttHeld = false; voice.release(); return true; }
        if (micHeld) { micHeld = false; voice.release(); }
        return super.guideKeyReleased(event);
    }
    @Override public boolean guideMouseReleased(GuideInputMouse event) {
        draggingScrollbar = false;
        if (micHeld) { micHeld = false; voice.release(); }
        return super.guideMouseReleased(event);
    }

    @Override protected void paintGuideBackground(GuideGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MinecraftClientWindow.extractDeferredSubtitles(minecraft, graphics);
    }
    @Override protected void paintGuideScreen(GuideGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int background = view.presentation().theme() == GuideUiConfig.Theme.MINT ? 0xFF172A27 : OpenAllayWidgetTheme.CHARCOAL;
        graphics.fill(card.x(), card.y(), card.x() + card.width(), card.y() + card.height(), background);
        graphics.outline(card.x(), card.y(), card.width(), card.height(), OpenAllayWidgetTheme.SLATE_BORDER);
        graphics.text(font, GuideNativeFont.plainSubstrByWidth(font, view.assistantName() + " · " + session, Math.max(1, card.width() - 138)),
                card.x() + 8, card.y() + 8, OpenAllayWidgetTheme.WHITE);
        net.minecraft.network.chat.Component status = view.progress() == null ? MinecraftComponents.translatable("screen.openallay.hud.idle")
                : MinecraftComponents.translatable(view.progress().activityTranslationKey());
        graphics.text(font, GuideNativeFont.plainSubstrByWidth(font, MinecraftComponents.getString(status), card.width() - 16),
                card.x() + 8, card.y() + 22, OpenAllayWidgetTheme.MINT);
        results.prepare(view, font, Math.max(1, resultBounds.width() - 6), resultBounds.height());
        results.render(graphics, font, view, resultBounds, results.scroll().offset(), mouseX, mouseY, true, presentationTicks);
        if (results.scroll().maximum() > 0) {
            int thumb = scrollbarThumbHeight();
            int top = scrollbar.y() + (int) Math.round((scrollbar.height() - thumb)
                    * results.scroll().offset() / (double) results.scroll().maximum());
            graphics.fill(scrollbar.x(), scrollbar.y(), scrollbar.right(), scrollbar.bottom(), OpenAllayWidgetTheme.SLATE_BORDER);
            graphics.fill(scrollbar.x(), top, scrollbar.right(), top + thumb, OpenAllayWidgetTheme.MINT);
            if (scrollbar.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font,
                    MinecraftComponents.translatable("screen.openallay.hud.scroll_tooltip"), mouseX, mouseY);
        }
        List<GuideHudResultRenderer.Hit> resultHits = visibleResultHits();
        if (focusedResult >= 0 && focusedResult < resultHits.size()) {
            dev.openallay.guide.ui.GuideUiLayout.Rect bounds = resultHits.get(focusedResult).bounds();
            graphics.outline(bounds.x(), Math.max(resultBounds.y(), bounds.y()), bounds.width(),
                    Math.min(bounds.bottom(), resultBounds.bottom()) - Math.max(resultBounds.y(), bounds.y()), OpenAllayWidgetTheme.MINT);
        }
        boolean invalidEdit = state.intent(session).editInvalid();
        String message = invalidEdit ? MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.hud.edit_invalid"))
                : notice.empty() ? MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.hud.input_controls")) : notice.message();
        if (readingLayout.footerFits() && readingLayout.notice().width() > 0 && readingLayout.notice().height() >= 10) {
            dev.openallay.guide.ui.GuideUiLayout.Rect strip = readingLayout.notice();
            graphics.enableScissor(strip.x(), strip.y(), strip.right(), strip.bottom());
            try {
                graphics.text(font, GuideNativeFont.plainSubstrByWidth(font, message, strip.width()), strip.x(), strip.y(),
                        invalidEdit ? OpenAllayWidgetTheme.WARNING : notice.empty() ? OpenAllayWidgetTheme.MUTED : notice.color());
            } finally { graphics.disableScissor(); }
            if (strip.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font, MinecraftComponents.literal(message), mouseX, mouseY);
        }
        renderObservationStrip(graphics, mouseX, mouseY);
        renderGuideWidgets(graphics, mouseX, mouseY, partialTick);
        GuideVoiceIndicator.extract(graphics, minecraft, voice);
    }

    @Override protected void guideRemoved() {
        GuideTextInputFocus.release(this);
        if (attachment != null) attachment.close();
        attachment = null;
        if (voice != null && (micHeld || pttHeld || voice.status().active())) voice.cancel(VoiceRuntime.CancelReason.SCREEN_CLOSED);
        micHeld = false;
        pttHeld = false;
        presenter.clear();
        results.close();
        draggingScrollbar = false;
    }

    @dev.openallay.value.ValueType(Card.ValueSchemaProvider.class)
public static final class Card {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    public Card(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
public static Card calculate(int viewportWidth, int viewportHeight) {
            int width = Math.max(1, Math.min(440, viewportWidth - 12));
            int height = Math.max(1, Math.min(400, viewportHeight - 12));
            return new Card(Math.max(0, (viewportWidth - width) / 2), Math.max(0, viewportHeight - height - 6), width, height);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Card)) return false;
        Card that = (Card) other;
        return x == that.x && y == that.y && width == that.width && height == that.height;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(x);
        hash = 31 * hash + Integer.hashCode(y);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        return hash;
    }
    @Override public String toString() { return "Card[x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Card> schema() {
            return new dev.openallay.value.ValueSchema<>(Card.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Card>>asList(new dev.openallay.value.ValueSchema.Component<>(Card.class, "x", Card::x), new dev.openallay.value.ValueSchema.Component<>(Card.class, "y", Card::y), new dev.openallay.value.ValueSchema.Component<>(Card.class, "width", Card::width), new dev.openallay.value.ValueSchema.Component<>(Card.class, "height", Card::height)), arguments -> new Card((Integer) arguments[0], (Integer) arguments[1], (Integer) arguments[2], (Integer) arguments[3]));
        }
    }
}
}
