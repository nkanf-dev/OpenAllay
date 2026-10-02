package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.GuideClientUiState;
import dev.openallay.client.gui.OpenAllayButton;
import dev.openallay.client.gui.OpenAllayWidgetTheme;
import dev.openallay.guide.GuideService;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.composer.SlashCommandDispatcher;
import dev.openallay.guide.ui.GuideDisplayRuntime;
import dev.openallay.guide.ui.hud.GuideHudPresenter;
import dev.openallay.guide.ui.hud.GuideHudView;
import dev.openallay.tool.ToolResult;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** Explicit compact native input surface. Gameplay keys/mouse are not forwarded while it is open. */
public final class GuideChatLiteScreen extends Screen {
    private final GuideService service;
    private final GuideClientUiState state;
    private final GuideDisplayRuntime display;
    private final Runnable openFullscreen;
    private final GuideHudPresenter presenter = new GuideHudPresenter();
    private GuideClientUiState.ViewAttachment attachment;
    private MultiLineEditBox composer;
    private Button send;
    private Button stop;
    private Button intentAction;
    private GuideSnapshot projectedSnapshot;
    private GuideHudView view;
    private List<FormattedCharSequence> lines = List.of();
    private String session;
    private String notice = "";
    private boolean submitting;
    private Card card;

    public GuideChatLiteScreen(GuideService service, GuideClientUiState state,
            GuideDisplayRuntime display, Runnable openFullscreen) {
        super(Component.translatable("screen.openallay.hud.interact"));
        this.service = Objects.requireNonNull(service, "service");
        this.state = Objects.requireNonNull(state, "state");
        this.display = Objects.requireNonNull(display, "display");
        this.openFullscreen = Objects.requireNonNull(openFullscreen, "openFullscreen");
        session = service.snapshot().selectedSession();
        view = presenter.project(service.snapshot(), display.config());
    }

    @Override public void onClose() {
        if (minecraft.gui.canInterruptScreen()) super.onClose();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }
    @Override protected void setInitialFocus() {} // Text focus starts only from a click/explicit navigation.

    @Override protected void init() {
        projectedSnapshot = null; // GUI resize changes native wrap width even without a new snapshot.
        card = Card.calculate(width, height);
        int inner = Math.max(1, card.width() - 16);
        composer = addRenderableWidget(MultiLineEditBox.builder().setX(card.x() + 8).setY(card.y() + card.height() - 76)
                .setPlaceholder(Component.translatable("screen.openallay.composer.placeholder"))
                .build(font, inner, 38, Component.translatable("screen.openallay.composer.narration")));
        composer.setValue(state.readText(session), true);
        composer.setValueListener(value -> state.setText(session, value));
        int actionY = card.y() + card.height() - 30;
        int actionWidth = Math.max(1, (inner - 8) / 3);
        intentAction = addRenderableWidget(OpenAllayButton.create(Component.empty(), button -> {
            GuideClientUiState.DraftIntent intent = state.intent(session);
            if (intent.editing()) state.resetIntent(session); // Explicit conversion never deletes text/images.
            else state.setMode(session, intent.steer() ? GuideClientUiState.DraftMode.FOLLOW_UP : GuideClientUiState.DraftMode.STEER);
            project();
        }).bounds(card.x() + Math.max(8, card.width() - 120), card.y() + 5, Math.min(112, inner), 18).build());
        send = addRenderableWidget(OpenAllayButton.create(Component.translatable("screen.openallay.action.send"), button -> submit())
                .bounds(card.x() + 8, actionY, actionWidth, 20).build());
        stop = addRenderableWidget(OpenAllayButton.create(Component.translatable("screen.openallay.action.stop"), button -> {
            state.stopIntent(session);
            service.cancel();
        })
                .bounds(card.x() + 12 + actionWidth, actionY, actionWidth, 20).build());
        addRenderableWidget(OpenAllayButton.create(Component.translatable("screen.openallay.hud.fullscreen"), button -> openFullscreen.run())
                .bounds(card.x() + 16 + actionWidth * 2, actionY, actionWidth, 20).build());
        setFocused(null);
        project();
    }

    @Override public void added() {
        if (!state.closed()) attachment = state.attach(GuideClientUiState.Surface.HUD_INPUT, session);
    }

    @Override public void tick() {
        if (state.closed() || minecraft.player == null || minecraft.level == null) { onClose(); return; }
        String selected = service.snapshot().selectedSession();
        if (!session.equals(selected)) {
            session = selected;
            state.selectSession(session);
            if (attachment != null) attachment.selectSession(session);
            composer.setValue(state.readText(session), true);
        } else if (!composer.getValue().equals(state.readText(session))) {
            composer.setValue(state.readText(session), true);
        }
        project();
    }

    private void project() {
        GuideSnapshot snapshot = service.snapshot();
        GuideHudView next = presenter.project(snapshot, display.config());
        if (snapshot != projectedSnapshot || !next.equals(view)) {
            projectedSnapshot = snapshot;
            view = next;
            String preview = !view.streamingPreview().isBlank() ? view.streamingPreview() : view.latestReply();
            lines = font.split(Component.literal(preview), Math.max(1, card.width() - 16));
        }
        var selected = snapshot.sessions().stream().filter(value -> value.sessionId().equals(session)).findFirst().orElse(null);
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
                    && (!composer.getValue().isBlank() || intent.editing() && !state.images().empty())
                    && !intent.editInvalid() && !state.images().pending();
            send.setMessage(Component.translatable(intent.editing() ? "screen.openallay.pending.save"
                    : "screen.openallay.action.send"));
        }
        if (intentAction != null) {
            intentAction.active = !submitting && !state.intentSubmissionInFlight(session);
            intentAction.setMessage(Component.translatable(intent.editing() ? "screen.openallay.hud.new_draft"
                    : intent.steer() ? "screen.openallay.hud.mode.steer" : "screen.openallay.hud.mode.follow_up"));
        }
        if (stop != null) stop.active = active;
    }

    private void submit() {
        if (submitting || state.closed() || composer == null || state.intentSubmissionInFlight(session)) return;
        String text = state.readText(session);
        if (text.isBlank() && (!state.intent(session).editing() || state.images().empty())) return;
        GuideClientUiState.Insertion captured = state.captureInsertion(session);
        SlashCommandDispatcher.Dispatch dispatch = SlashCommandDispatcher.dispatch(text, service, completion -> minecraft.execute(() -> {
            if (state.closed()) return;
            notice = Component.translatable("openallay.guide.slash." + completion.code()).getString();
            if (completion.successful()) state.clearAcceptedText(captured, text);
        }));
        if (dispatch.handled()) return;
        GuideClientUiState.IntentCapture intentCapture = state.captureIntent(session);
        GuideClientUiState.DraftIntent intent = intentCapture.intent();
        var selected = service.snapshot().sessions().stream().filter(value -> value.sessionId().equals(session)).findFirst().orElse(null);
        boolean active = selected != null && (selected.workingRequestId() != null
                || selected.requests().stream().anyMatch(request -> !request.terminal()));
        boolean targetPresent = selected != null && selected.pendingMessages().stream()
                .anyMatch(pending -> pending.id().equals(intent.pendingId()));
        Route route = route(intent, active, targetPresent);
        if (state.images().pending()) return;
        if (route == Route.BLOCKED) {
            state.invalidatePendingEdit(intentCapture);
            notice = Component.translatable("screen.openallay.hud.edit_invalid").getString();
            project();
            return;
        }
        // Lite displays text only. A pending edit still preserves all of its captured image parts.
        var images = state.images().captureSubmission();
        var message = intent.editing()
                ? dev.openallay.model.ModelMessage.userInput(dispatch.normalizedText(), state.images().references())
                : dev.openallay.model.ModelMessage.userText(dispatch.normalizedText());
        if (!state.images().empty() && !intent.editing()) notice = Component.translatable("screen.openallay.hud.attachments_retained").getString();
        if (!state.beginIntentSubmission(intentCapture)) return;
        submitting = true;
        java.util.concurrent.CompletableFuture<? extends ToolResult<?>> future;
        try {
            future = switch (route) {
                case EDIT_PENDING -> service.editPending(intent.pendingId(), message);
                case STEER -> service.steer(message);
                case FOLLOW_UP -> service.followUp(message);
                case ASK -> service.ask(message);
                case BLOCKED -> throw new IllegalStateException("Blocked draft cannot be submitted");
            };
        } catch (RuntimeException failure) {
            state.completeIntentSubmission(intentCapture);
            submitting = false;
            notice = Component.translatable("screen.openallay.composer.submit_failed").getString();
            return;
        }
        future.whenComplete((result, failure) -> minecraft.execute(() -> {
            try {
                if (state.closed()) return;
                if (failure == null && submissionAccepted(intent.editing(), result)) {
                    state.clearAcceptedText(captured, text);
                    state.clearAcceptedIntent(intentCapture);
                    if (intent.editing()) state.images().accepted(images);
                } else if (failure == null && intent.editing() && result instanceof ToolResult.Success<?>) {
                    state.invalidatePendingEdit(intentCapture);
                    notice = Component.translatable("screen.openallay.hud.edit_invalid").getString();
                } else notice = Component.translatable("screen.openallay.composer.submit_failed").getString();
            } finally {
                state.completeIntentSubmission(intentCapture);
                submitting = false;
            }
        }));
    }

    enum Route { ASK, FOLLOW_UP, STEER, EDIT_PENDING, BLOCKED }
    static Route route(GuideClientUiState.DraftIntent intent, boolean active, boolean pendingPresent) {
        if (intent.editing()) return intent.editInvalid() || !pendingPresent ? Route.BLOCKED : Route.EDIT_PENDING;
        return !active ? Route.ASK : intent.steer() ? Route.STEER : Route.FOLLOW_UP;
    }
    static boolean submissionAccepted(boolean editing, ToolResult<?> result) {
        return result instanceof ToolResult.Success<?> success && (!editing || Boolean.TRUE.equals(success.value()));
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (composer != null && composer.isFocused() && event.key() == GLFW.GLFW_KEY_ENTER && !event.hasShiftDown()) {
            submit(); return true;
        }
        return super.keyPressed(event);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        minecraft.gui.hud.extractDeferredSubtitles();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(card.x(), card.y(), card.x() + card.width(), card.y() + card.height(), OpenAllayWidgetTheme.CHARCOAL);
        graphics.outline(card.x(), card.y(), card.width(), card.height(), OpenAllayWidgetTheme.SLATE_BORDER);
        graphics.text(font, font.plainSubstrByWidth(view.assistantName() + " · " + session, Math.max(1, card.width() - 138)),
                card.x() + 8, card.y() + 8, OpenAllayWidgetTheme.WHITE);
        Component status = view.progress() == null ? Component.translatable("screen.openallay.hud.idle")
                : Component.translatable(view.progress().activityTranslationKey());
        graphics.text(font, font.plainSubstrByWidth(status.getString(), card.width() - 16),
                card.x() + 8, card.y() + 22, OpenAllayWidgetTheme.MINT);
        graphics.enableScissor(card.x() + 8, card.y() + 36, card.x() + card.width() - 8, card.y() + card.height() - 92);
        try {
            for (int i = 0; i < Math.min(6, lines.size()); i++) graphics.text(font, lines.get(i), card.x() + 8, card.y() + 36 + i * 10, OpenAllayWidgetTheme.WHITE);
        } finally { graphics.disableScissor(); }
        String message = state.intent(session).editInvalid() ? Component.translatable("screen.openallay.hud.edit_invalid").getString()
                : notice.isBlank() ? Component.translatable("screen.openallay.hud.input_controls").getString() : notice;
        graphics.text(font, font.plainSubstrByWidth(message, card.width() - 16), card.x() + 8, card.y() + card.height() - 88,
                notice.isBlank() ? OpenAllayWidgetTheme.MUTED : OpenAllayWidgetTheme.AMBER);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void removed() {
        if (attachment != null) attachment.close();
        attachment = null;
        presenter.clear();
    }

    public record Card(int x, int y, int width, int height) {
        public static Card calculate(int viewportWidth, int viewportHeight) {
            int width = Math.max(1, Math.min(360, viewportWidth - 12));
            int height = Math.max(1, Math.min(210, viewportHeight - 12));
            return new Card(Math.max(0, (viewportWidth - width) / 2), Math.max(0, viewportHeight - height - 6), width, height);
        }
    }
}
