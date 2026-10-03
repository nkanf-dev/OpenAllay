package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.GuideClientUiState;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Explicit compact native input surface. Gameplay keys/mouse are not forwarded while it is open. */
public final class GuideChatLiteScreen extends Screen {
    private final GuideService service;
    private final GuideClientUiState state;
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
    private Button latest;
    private Button back;
    private long presentationTicks;
    private boolean initialResults = true;
    private GuideClientUiState.ViewAttachment attachment;
    private MultiLineEditBox composer;
    private Button send;
    private Button stop;
    private Button mic;
    private Button intentAction;
    private GuideSnapshot projectedSnapshot;
    private GuideHudView view;
    private String session;
    private String notice = "";
    private boolean submitting;
    private boolean micHeld;
    private boolean pttHeld;
    private Card card;

    public GuideChatLiteScreen(GuideService service, GuideClientUiState state,
            GuideDisplayRuntime display, Runnable openFullscreen, VoiceRuntime voice) {
        super(Component.translatable("screen.openallay.hud.interact"));
        this.service = Objects.requireNonNull(service, "service");
        this.state = Objects.requireNonNull(state, "state");
        this.display = Objects.requireNonNull(display, "display");
        this.openFullscreen = Objects.requireNonNull(openFullscreen, "openFullscreen");
        this.voice = voice;
        session = service.snapshot().selectedSession();
        view = presenter.projectInteractive(service.snapshot(), display.config());
    }

    /** Shared current recipe preferences; no new service or model is created for HUD reading. */
    public GuideChatLiteScreen withRecipes(RecipeClientRuntime recipes) {
        this.recipes = Objects.requireNonNull(recipes, "recipes");
        return this;
    }
    public GuideHudResultRenderer.Receipt resultReceipt() { return results.receipt(); }

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
        readingLayout = GuideHudReadingLayout.calculate(card.x(), card.y(), card.width(), card.height());
        resultBounds = readingLayout.results();
        scrollbar = readingLayout.scrollbar();
        draggingScrollbar = false;
        results.invalidate();
        composer = addRenderableWidget(MultiLineEditBox.builder().setX(readingLayout.composer().x()).setY(readingLayout.composer().y())
                .setPlaceholder(Component.translatable("screen.openallay.composer.placeholder"))
                .build(font, inner, 38, Component.translatable("screen.openallay.composer.narration")));
        composer.setValue(state.readText(session), true);
        composer.setValueListener(value -> state.setText(session, value));
        int actionY = readingLayout.actions().y();
        int actionWidth = Math.max(1, (inner - 12) / (voice != null && voice.enabled() ? 4 : 3));
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
        if (voice != null && voice.enabled()) mic = addRenderableWidget(OpenAllayButton.create(
                Component.translatable("screen.openallay.voice.mic"), button -> { micHeld = true; voice.press(); })
                .bounds(card.x() + 20 + actionWidth * 3, actionY, actionWidth, 20).build());
        back = addRenderableWidget(OpenAllayButton.create(Component.translatable("screen.openallay.hud.back_results"), button -> {
            results.back(); project();
        }).bounds(card.x() + 8, readingLayout.navigation().y(), Math.min(112, inner / 2), 14).build());
        latest = addRenderableWidget(OpenAllayButton.create(Component.translatable("screen.openallay.hud.latest"), button -> results.scroll().latest())
                .bounds(card.x() + card.width() - 106, readingLayout.navigation().y(), 98, 14).build());
        if (!readingLayout.footerFits()) {
            composer.visible = false;
            send.visible = false;
            stop.visible = false;
            intentAction.visible = false;
            if (mic != null) mic.visible = false;
            // Native children still retain the draft. At physically impossible sizes none can
            // paint or take focus outside the card, and Escape still returns immediately.
            children().forEach(child -> { if (child instanceof Button button) button.visible = false; });
        }
        setFocused(null);
        project();
    }

    @Override public void added() {
        if (!state.closed()) attachment = state.attach(GuideClientUiState.Surface.HUD_INPUT, session);
    }

    @Override public void tick() {
        presentationTicks++;
        results.tick();
        if (state.closed() || minecraft.player == null || minecraft.level == null) { onClose(); return; }
        String selected = service.snapshot().selectedSession();
        if (!session.equals(selected)) {
            session = selected;
            results.back();
            results.scroll().latest();
            focusedResult = -1;
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
        GuideHudView next = presenter.projectInteractive(snapshot, display.config());
        if (snapshot != projectedSnapshot || !next.equals(view)) {
            projectedSnapshot = snapshot;
            view = next;

        }
        results.prepare(view, font, Math.max(1, resultBounds.width() - 6), resultBounds.height());
        if (initialResults) { results.scroll().latest(); initialResults = false; }
        if (latest != null) latest.visible = readingLayout.footerFits()
                && results.scroll().maximum() > 0 && !results.scroll().followingLatest();
        if (back != null) back.visible = readingLayout.footerFits() && results.detailOpen();
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
        GuideClientUiState.IntentCapture intentCapture = state.captureIntent(session);
        GuideClientUiState.DraftIntent intent = intentCapture.intent();
        if (text.isBlank() && (!intent.editing() || state.images().empty())) return;
        GuideClientUiState.Insertion captured = state.captureInsertion(session);
        SlashCommandDispatcher.Dispatch dispatch = dispatchDraft(text, intent,
                ordinaryText -> SlashCommandDispatcher.dispatch(ordinaryText, service, completion -> minecraft.execute(() -> {
                    if (state.closed()) return;
                    notice = Component.translatable("openallay.guide.slash." + completion.code()).getString();
                    if (completion.successful()) state.clearAcceptedText(captured, text);
                })));
        if (dispatch.handled()) return;
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

    /** A captured queued edit is literal message content, never a local composer command. */
    static SlashCommandDispatcher.Dispatch dispatchDraft(String text, GuideClientUiState.DraftIntent intent,
            java.util.function.Function<String, SlashCommandDispatcher.Dispatch> ordinaryDispatch) {
        if (intent.editing()) return new SlashCommandDispatcher.Dispatch(false, true, text);
        return ordinaryDispatch.apply(text);
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
        if (voice != null && voice.enabled() && (composer == null || !composer.isFocused())
                && !OpenAllayKeyMappings.VOICE_PTT.isUnbound() && OpenAllayKeyMappings.VOICE_PTT.matches(event)) {
            pttHeld = true;
            voice.press(); // Screen physical mappings are released natively; own release below.
            return true;
        }
        if (composer == null || !composer.isFocused()) {
            switch (event.key()) {
                case GLFW.GLFW_KEY_PAGE_UP -> { results.scroll().page(-1); return true; }
                case GLFW.GLFW_KEY_PAGE_DOWN -> { results.scroll().page(1); return true; }
                case GLFW.GLFW_KEY_HOME -> { results.scroll().first(); return true; }
                case GLFW.GLFW_KEY_END -> { results.scroll().latest(); return true; }
                case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                    List<GuideHudResultRenderer.Hit> hits = visibleResultHits();
                    if (!hits.isEmpty()) {
                        focusedResult = Math.floorMod(focusedResult + (event.key() == GLFW.GLFW_KEY_DOWN ? 1 : -1), hits.size());
                        if (minecraft.getNarrator().isActive()) minecraft.getNarrator().saySystemNow(Component.literal(hits.get(focusedResult).narration()));
                        return true;
                    }
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE -> {
                    List<GuideHudResultRenderer.Hit> hits = visibleResultHits();
                    if (focusedResult >= 0 && focusedResult < hits.size()) { resultAction(hits.get(focusedResult).action()); return true; }
                }
            }
        }
        return super.keyPressed(event);
    }

    private List<GuideHudResultRenderer.Hit> visibleResultHits() {
        return results.hits().stream().filter(hit -> hit.bounds().bottom() > resultBounds.y()
                && hit.bounds().y() < resultBounds.bottom()).toList();
    }
    @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (resultBounds.contains(x, y) || scrollbar.contains(x, y)) {
            results.scroll().wheel(scrollY);
            focusedResult = -1;
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Native controls still own dispatch, including the composer's external scrollbar.
        if (event.button() == 0 && !composerContains(event.x(), event.y())) clearFocus();
        if (event.button() == 0 && scrollbar.contains(event.x(), event.y()) && results.scroll().maximum() > 0) {
            draggingScrollbar = true;
            clearFocus();
            scrollAt(event.y());
            return true;
        }
        if (event.button() == 0 && resultBounds.contains(event.x(), event.y())) {
            clearFocus();
            for (var hit : visibleResultHits()) if (hit.bounds().contains(event.x(), event.y())) {
                resultAction(hit.action()); return true;
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
    private boolean composerContains(double x, double y) {
        if (composer == null) return false;
        GuideUiLayout.Rect input = new GuideUiLayout.Rect(
                composer.getX(), composer.getY(), composer.getWidth(), composer.getHeight());
        return input.contains(x, y) && composer.isMouseOver(x, y);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingScrollbar) { scrollAt(event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    private void scrollAt(double y) {
        int thumb = scrollbarThumbHeight();
        double range = Math.max(1, scrollbar.height() - thumb);
        results.scroll().move((y - scrollbar.y() - thumb / 2.0) / range * results.scroll().maximum());
    }
    private int scrollbarThumbHeight() {
        return Math.min(scrollbar.height(), Math.max(12, scrollbar.height() * resultBounds.height() / Math.max(1, results.scroll().totalHeight())));
    }
    private void resultAction(GuideHudResultRenderer.Action action) {
        focusedResult = -1;
        switch (action) {
            case GuideHudResultRenderer.Action.Tool tool -> results.openTool(tool.rowId());
            case GuideHudResultRenderer.Action.Sources sources -> results.openSources(sources.sources());
            case GuideHudResultRenderer.Action.Semantic semantic -> {
                switch (semantic.intent()) {
                    case dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.BrowseRecipes value -> navigate(recipes.openRecipes(value.itemId()));
                    case dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.BrowseUsages value -> navigate(recipes.openUsages(value.itemId()));
                    case dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.ExactRecipe value -> navigate(recipes.openExact(value.reference()));
                    case dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Source value -> openSources(value.originInvocationId());
                    case dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Evidence value -> openSources(value.originInvocationId());
                    case dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Choice value -> notice = Component.translatable("screen.openallay.choice.unavailable", value.choiceId()).getString();
                }
            }
        }
        project();
    }
    private void openSources(String invocation) {
        List<dev.openallay.guide.GuideSource> sources = view.rows().stream()
                .filter(dev.openallay.guide.ui.GuideUiRow.Tool.class::isInstance)
                .map(dev.openallay.guide.ui.GuideUiRow.Tool.class::cast)
                .filter(tool -> tool.activity().invocationId().equals(invocation))
                .flatMap(tool -> tool.activity().sources().stream()).toList();
        if (!sources.isEmpty()) results.openSources(sources);
    }
    private void navigate(RecipeNavigationResult result) {
        notice = Component.translatable(result.opened() ? "screen.openallay.recipe.viewer_opened"
                : "screen.openallay.recipe." + switch (result.code()) {
                    case "exact_unsupported", "preferred_viewer_unavailable", "viewer_unavailable", "unknown_item", "wrong_thread", "viewer_failure" -> result.code();
                    default -> "viewer_failure";
                }).getString();
    }

    @Override public boolean keyReleased(KeyEvent event) {
        if (pttHeld && OpenAllayKeyMappings.VOICE_PTT.matches(event)) { pttHeld = false; voice.release(); return true; }
        if (micHeld) { micHeld = false; voice.release(); }
        return super.keyReleased(event);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        draggingScrollbar = false;
        if (micHeld) { micHeld = false; voice.release(); }
        return super.mouseReleased(event);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        minecraft.gui.hud.extractDeferredSubtitles();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int background = view.presentation().theme() == GuideUiConfig.Theme.MINT ? 0xFF172A27 : OpenAllayWidgetTheme.CHARCOAL;
        graphics.fill(card.x(), card.y(), card.x() + card.width(), card.y() + card.height(), background);
        graphics.outline(card.x(), card.y(), card.width(), card.height(), OpenAllayWidgetTheme.SLATE_BORDER);
        graphics.text(font, font.plainSubstrByWidth(view.assistantName() + " · " + session, Math.max(1, card.width() - 138)),
                card.x() + 8, card.y() + 8, OpenAllayWidgetTheme.WHITE);
        Component status = view.progress() == null ? Component.translatable("screen.openallay.hud.idle")
                : Component.translatable(view.progress().activityTranslationKey());
        graphics.text(font, font.plainSubstrByWidth(status.getString(), card.width() - 16),
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
                    Component.translatable("screen.openallay.hud.scroll_tooltip"), mouseX, mouseY);
        }
        List<GuideHudResultRenderer.Hit> resultHits = visibleResultHits();
        if (focusedResult >= 0 && focusedResult < resultHits.size()) {
            var bounds = resultHits.get(focusedResult).bounds();
            graphics.outline(bounds.x(), Math.max(resultBounds.y(), bounds.y()), bounds.width(),
                    Math.min(bounds.bottom(), resultBounds.bottom()) - Math.max(resultBounds.y(), bounds.y()), OpenAllayWidgetTheme.MINT);
        }
        String message = state.intent(session).editInvalid() ? Component.translatable("screen.openallay.hud.edit_invalid").getString()
                : notice.isBlank() ? Component.translatable("screen.openallay.hud.input_controls").getString() : notice;
        if (readingLayout.footerFits() && readingLayout.notice().width() > 0 && readingLayout.notice().height() >= 10) {
            var strip = readingLayout.notice();
            graphics.enableScissor(strip.x(), strip.y(), strip.right(), strip.bottom());
            try {
                graphics.text(font, font.plainSubstrByWidth(message, strip.width()), strip.x(), strip.y(),
                        notice.isBlank() ? OpenAllayWidgetTheme.MUTED : OpenAllayWidgetTheme.AMBER);
            } finally { graphics.disableScissor(); }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        GuideVoiceIndicator.extract(graphics, minecraft, voice);
    }

    @Override public void removed() {
        if (attachment != null) attachment.close();
        attachment = null;
        if (voice != null && (micHeld || pttHeld || voice.status().active())) voice.cancel(VoiceRuntime.CancelReason.SCREEN_CLOSED);
        micHeld = false;
        pttHeld = false;
        presenter.clear();
        results.close();
        draggingScrollbar = false;
    }

    public record Card(int x, int y, int width, int height) {
        public static Card calculate(int viewportWidth, int viewportHeight) {
            int width = Math.max(1, Math.min(440, viewportWidth - 12));
            int height = Math.max(1, Math.min(400, viewportHeight - 12));
            return new Card(Math.max(0, (viewportWidth - width) / 2), Math.max(0, viewportHeight - height - 6), width, height);
        }
    }
}
