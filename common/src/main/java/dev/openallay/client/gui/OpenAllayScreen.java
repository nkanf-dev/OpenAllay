package dev.openallay.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import dev.openallay.client.gui.clipboard.ClipboardImageEncoder;
import dev.openallay.client.gui.clipboard.SystemImageClipboard;
import dev.openallay.guide.GuidePendingMessage;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.image.ImageInputCapability;
import net.minecraft.client.renderer.texture.DynamicTexture;
import dev.openallay.guide.GuideModelSelection;
import dev.openallay.guide.GuideFailure;
import dev.openallay.guide.GuideRequestSnapshot;
import dev.openallay.guide.GuideRequestStatus;
import dev.openallay.guide.GuideService;
import dev.openallay.guide.GuideSource;
import dev.openallay.guide.GuideSubscription;
import dev.openallay.guide.GuideSnapshot;
import dev.openallay.guide.GuideToolActivity;
import dev.openallay.guide.GuideToolMessage;
import dev.openallay.guide.GuideToolStatus;
import dev.openallay.guide.GuideSessionSnapshot;
import dev.openallay.guide.GuideHistoryPageState;
import dev.openallay.guide.history.GuideHistoryPageRequest;
import dev.openallay.guide.ui.GuideUiLayout;
import dev.openallay.guide.ui.GuideUiModelChoice;
import dev.openallay.guide.ui.GuideUiProgress;
import dev.openallay.guide.ui.GuideUiRow;
import dev.openallay.guide.ui.GuideUiSession;
import dev.openallay.guide.ui.GuideUiView;
import dev.openallay.guide.ui.GuideDetailCard;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideDisplayRuntime;
import dev.openallay.guide.ui.GuideItemView;
import dev.openallay.guide.ui.GuideRecipeCard;
import dev.openallay.guide.ui.GuideToolDetailView;
import dev.openallay.guide.ui.GuideUiClickRoute;
import dev.openallay.guide.ui.GuideTranscriptVirtualizer;
import dev.openallay.guide.ui.GuideViewportAnchor;
import dev.openallay.guide.ui.SemanticLayout;
import dev.openallay.guide.ui.SemanticLayoutCache;
import dev.openallay.guide.ui.SemanticLayoutEngine;
import dev.openallay.client.gui.nativeview.NativeDomainView;
import dev.openallay.client.gui.nativeview.NativeDomainViewBinding;
import dev.openallay.client.gui.nativeview.NativeDomainViewBindings;
import dev.openallay.client.gui.nativeview.NativeDomainViewRegistry;
import dev.openallay.client.gui.export.GuideSessionExporter;
import dev.openallay.recipe.RecipeNavigationResult;
import dev.openallay.recipe.config.RecipeClientRuntime;
import dev.openallay.tool.ToolResult;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import dev.openallay.guide.ui.GuideEvidencePresentation;
import dev.openallay.guide.ui.GuideToolDisplayStatus;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Full-screen, non-pausing projection and intent sender for GuideService. */
public final class OpenAllayScreen extends Screen {
    private static final int PANEL = OpenAllayWidgetTheme.PANEL;
    private static final int PANEL_ALT = OpenAllayWidgetTheme.PANEL_ALT;
    private static final int ACCENT = OpenAllayWidgetTheme.MINT;
    private static final int TEXT = OpenAllayWidgetTheme.TEXT;
    private static final int MUTED = OpenAllayWidgetTheme.MUTED_READABLE;
    private static final int ERROR = OpenAllayWidgetTheme.ERROR;
    private static final Gson DEBUG_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Executor EXPORT_EXECUTOR = command -> Thread.ofVirtual()
            .name("openallay-session-export")
            .start(command);
    private static final Executor IMAGE_EXECUTOR = command -> Thread.ofVirtual()
            .name("openallay-composer-image").start(command);
    private final GuideService service;
    private final ComposerImageDraft composerImages;
    private final GuideClientUiState uiState;
    private GuideClientUiState.ViewAttachment attachment;
    private AutoCloseable draftSubscription;
    private dev.openallay.client.voice.VoiceInputActions voice;
    private Button microphone;
    private boolean voiceKeyHeld;
    private dev.openallay.client.presentation.GuideNotificationController notifications;
    private boolean railVisible;
    private boolean overflowOpen;
    private int sessionScroll;
    private final dev.openallay.guide.ui.GuideToolStepFlowState toolStepFolds =
            new dev.openallay.guide.ui.GuideToolStepFlowState();
    private final Map<String, ToolStepBody> toolStepBodies = new LinkedHashMap<>();
    private ToolFlowOwner toolStepOwner;
    private record ToolFlowOwner(UUID actor, String session, Object world) {}
    private record ToolStepBody(GuideToolDetailView detail, String locale,
            dev.openallay.guide.ui.hud.GuideHudToolCards.Projection nativeBody,
            List<Component> messages) {}
    private final Map<String, GuideUiLayout.Rect> renderedRows = new LinkedHashMap<>();
    private final String imageDraftOwner = UUID.randomUUID().toString();
    private final Map<UUID, Identifier> imageTextures = new LinkedHashMap<>();
    private GuideUiLayout.ComposerExtras composerExtras;
    private boolean submittingDraft;
    private int pendingCursor;
    private int imageScroll;
    private String composerLayoutKey = "";
    private final RecipeClientRuntime recipeClient;
    private final Supplier<GuideDisplayConfig> display;
    private final Runnable settingsOpener;
    private final TickCoalescer<GuideSnapshot> pendingSnapshots = new TickCoalescer<>();
    private volatile GuideUiView view;
    private GuideSubscription subscription;
    private GuideUiLayout layout;
    private HeaderTitle headerTitleWidget;
    private GuideUiLayout.Rect renderedTelemetryBounds;
    private int renderedTelemetryRows;
    private MultiLineEditBox composer;
    private Button send;
    private Button stop;
    private Button retry;
    private Button model;
    private Button export;
    private GuideUiLayout.Rect modelSelectorButton;
    private String draft = "";
    private GuideUiNotice notice = GuideUiNotice.info("");
    private int scroll;
    private int detailScroll;
    private int detailContentHeight;
    private final Map<String, CodeLayout> detailCodeLayouts = new LinkedHashMap<>();
    private final Map<String, SourceDetailLayout> sourceDetailLayouts = new LinkedHashMap<>();
    private int activeProgressRenderFrames;
    private boolean sessionOverlay;
    private boolean modelSelectorOpen;
    private boolean exportRunning;
    private String lastExportFilename = "";
    private int lastExportRequestCount;
    private String exportNoticeKey = "";
    private GuideUiNotice exportNotice;
    private long renderedNativeFrame;
    private final List<String> renderedToolIds = new ArrayList<>();
    private final List<String> renderedResultCardIds = new ArrayList<>();
    private int modelSelectorScroll;
    private int modelSelectorCursor;
    private GuideUiRow.Tool selectedTool;
    private GuideEvidencePresentation.Group selectedSource;
    private String selectedSourceFocusId;
    private final HashSet<String> expandedDetails = new HashSet<>();
    private final Map<List<GuideSource>, List<GuideEvidencePresentation.Group>> sourceGroupCache =
            new java.util.IdentityHashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private final GuideTranscriptVirtualizer virtualizer = new GuideTranscriptVirtualizer();
    private final SemanticLayoutCache semanticLayouts = new SemanticLayoutCache();
    private final MinecraftSemanticRenderer semanticRenderer =
            new MinecraftSemanticRenderer(new MinecraftSemanticResolver());
    private NativeDomainViewRegistry nativeViews = new NativeDomainViewRegistry();
    private final Map<String, Integer> semanticHashes = new LinkedHashMap<>();
    private final StableRowHeights stableRowHeights = new StableRowHeights();
    private boolean followBottom = true;
    private String focusedContentId;
    private GuideDisplayConfig projectedDisplay;
    private long presentationTicks;
    private dev.openallay.guide.GuideTelemetrySnapshot telemetry;
    private Component telemetryContext = Component.empty();
    private Component telemetryInput = Component.empty();
    private Component telemetryOutput = Component.empty();
    private Component telemetryCost = Component.empty();
    private Component telemetryCompact = Component.empty();
    private Component telemetryTooltip = Component.empty();

    public OpenAllayScreen(GuideService service) {
        this(service, RecipeClientRuntime.defaults(), GuideDisplayConfig.defaults());
    }

    public OpenAllayScreen(GuideService service, RecipeClientRuntime recipeClient) {
        this(service, recipeClient, GuideDisplayConfig.defaults());
    }

    public OpenAllayScreen(
            GuideService service,
            RecipeClientRuntime recipeClient,
            GuideDisplayConfig displayConfig) {
        this(service, recipeClient, displayConfig, null);
    }

    public OpenAllayScreen(
            GuideService service,
            RecipeClientRuntime recipeClient,
            GuideDisplayConfig displayConfig,
            GuideFailure displayFailure) {
        this(service, recipeClient, displayConfig, displayFailure, null);
    }

    public OpenAllayScreen(
            GuideService service,
            RecipeClientRuntime recipeClient,
            GuideDisplayConfig displayConfig,
            GuideFailure displayFailure,
            Runnable settingsOpener) {
        this(service, recipeClient, () -> displayConfig, displayFailure, settingsOpener);
    }

    public OpenAllayScreen(
            GuideService service,
            RecipeClientRuntime recipeClient,
            GuideDisplayRuntime display,
            Runnable settingsOpener) {
        this(
                service,
                recipeClient,
                Objects.requireNonNull(display, "display")::config,
                display.failure(),
                settingsOpener);
    }

    private OpenAllayScreen(
            GuideService service,
            RecipeClientRuntime recipeClient,
            Supplier<GuideDisplayConfig> display,
            GuideFailure displayFailure,
            Runnable settingsOpener) {
        this(service, recipeClient, display, displayFailure, settingsOpener,
                GuideClientUiState.create(service, event -> net.minecraft.client.Minecraft.getInstance().execute(event)));
    }

    public OpenAllayScreen(GuideService service, RecipeClientRuntime recipeClient,
            GuideDisplayRuntime display, Runnable settingsOpener, GuideClientUiState uiState) {
        this(service, recipeClient, Objects.requireNonNull(display, "display")::config,
                display.failure(), settingsOpener, uiState);
    }

    private OpenAllayScreen(GuideService service, RecipeClientRuntime recipeClient,
            Supplier<GuideDisplayConfig> display, GuideFailure displayFailure,
            Runnable settingsOpener, GuideClientUiState uiState) {
        super(Component.translatable("screen.openallay.guide"));
        this.service = Objects.requireNonNull(service, "service");
        this.uiState = Objects.requireNonNull(uiState, "uiState");
        this.composerImages = uiState.images();
        this.recipeClient = java.util.Objects.requireNonNull(recipeClient, "recipeClient");
        this.display = java.util.Objects.requireNonNull(display, "display");
        this.settingsOpener = settingsOpener;
        this.projectedDisplay = currentDisplay();
        this.view = GuideUiView.from(service.snapshot(), projectedDisplay);
        List<String> startupNotices = new ArrayList<>();
        recipeClient.failure().ifPresent(failure -> startupNotices.add(Component.translatable(
                "screen.openallay.recipe.invalid_config", failure.code()).getString()));
        if (displayFailure != null) {
            startupNotices.add(Component.translatable(
                    "screen.openallay.debug.invalid_config").getString());
        }
        notice = GuideUiNotice.warning(String.join(" · ", startupNotices));
        uiState.selectSession(view.selectedSession());
        draft = uiState.readText(view.selectedSession());
        railVisible = projectedDisplay.ui().fullscreen().sessionRailVisible();
    }

    public OpenAllayScreen withVoice(dev.openallay.client.voice.VoiceInputActions voice) {
        this.voice = voice;
        return this;
    }

    public OpenAllayScreen withNotifications(dev.openallay.client.presentation.GuideNotificationController notifications) {
        this.notifications = notifications;
        return this;
    }

    @Override
    protected void init() {
        Component title = headerTitle();
        layout = GuideUiLayout.calculate(width, height, detailOpen(),
                font.width(title.getVisualOrderText()),
                font.width(Component.translatable("screen.openallay.action.sessions")) + 12,
                font.width(Component.translatable("screen.openallay.action.export")) + 12,
                font.width(Component.translatable("screen.openallay.action.refresh")) + 12,
                settingsOpener != null, hasComposerImagePreviews(), composerRequestActive(), pendingMessages().size(), railVisible);
        composerExtras = layout.composerExtras(hasComposerImagePreviews(), composerRequestActive(), pendingMessages().size());
        composerLayoutKey = currentComposerLayoutKey();
        GuideUiLayout.Header header = layout.header();
        headerTitleWidget = addRenderableWidget(new HeaderTitle(title, header.title()));
        renderedTelemetryBounds = null;
        renderedTelemetryRows = 0;
        renderedToolIds.clear();
        renderedResultCardIds.clear();
        Component sessionsLabel = Component.translatable("screen.openallay.action.sessions");
        Component sessionsText = font.width(sessionsLabel) + 8 <= header.sessions().width()
                ? sessionsLabel : Component.literal("≡");
        addRenderableWidget(OpenAllayButton.create(sessionsText, button -> toggleSessions())
                .bounds(header.sessions().x(), header.sessions().y(), header.sessions().width(), 20)
                .tooltip(Tooltip.create(sessionsLabel))
                .createNarration(ignored -> sessionsLabel.copy()).build());
        addRenderableWidget(OpenAllayButton.create(Component.literal("⋯"), button -> overflowOpen = !overflowOpen)
                .bounds(header.overflow().x(), header.overflow().y(), header.overflow().width(), 20)
                .tooltip(Tooltip.create(Component.translatable("screen.openallay.action.more")))
                .createNarration(ignored -> Component.translatable("screen.openallay.action.more")).build());
        modelSelectorButton = header.model();
        model = addRenderableWidget(OpenAllayButton.create(modelButtonLabel(), button -> {
                    modelSelectorOpen = !modelSelectorOpen;
                    if (modelSelectorOpen) revealSelectedModel();
                })
                .bounds(header.model().x(), header.model().y(), header.model().width(), 20)
                .createNarration(ignored -> modelButtonDescription()).build());
        model.setTooltip(Tooltip.create(modelButtonDescription()));
        if (settingsOpener != null) {
            addRenderableWidget(OpenAllayButton.create(
                            Component.translatable("screen.openallay.settings.short"), button -> settingsOpener.run())
                    .bounds(header.settings().x(), header.settings().y(), header.settings().width(), 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.openallay.settings.title")))
                    .createNarration(ignored -> Component.translatable("screen.openallay.settings.title")).build());
        }

        GuideUiLayout.ComposerControls controls = layout.composerControls();
        GuideUiLayout.Rect input = composerExtras.input();
        if (composer == null) {
            composer = MultiLineEditBox.builder()
                    .setX(input.x()).setY(input.y())
                    .setPlaceholder(Component.translatable("screen.openallay.composer.placeholder"))
                    .build(font, input.width(), input.height(),
                            Component.translatable("screen.openallay.composer.narration"));
        } else {
            GuideComposerGeometry.resize(composer, input);
        }
        if (!composer.getValue().equals(draft)) composer.setValue(draft, true);
        composer.setValueListener(value -> {
            draft = value;
            uiState.setText(view.selectedSession(), value);
        });
        addRenderableWidget(composer);
        send = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.action.send"), button -> submit())
                .bounds(controls.send().x(), controls.send().y(), controls.send().width(), 20).build());
        stop = controls.stop().height() == 0 ? null : addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.action.stop"), button -> cancel())
                .bounds(controls.stop().x(), controls.stop().y(), controls.stop().width(), 20).build());
        if (stop != null) stop.setTooltip(Tooltip.create(Component.translatable("screen.openallay.action.stop.description")));
        retry = null; // Retry belongs to its factual failed request row.
        microphone = null;
        if (voice != null && voice.enabled()) {
            GuideUiLayout.Rect action = controls.send();
            int micY = action.y() + (stop == null ? 24 : 44);
            if (micY + 18 <= layout.composer().bottom()) {
                microphone = addRenderableWidget(OpenAllayButton.create(
                                Component.translatable("screen.openallay.voice.mic_short"), button -> microphoneAction())
                        .bounds(action.x(), micY, action.width(), 18)
                        .tooltip(Tooltip.create(Component.translatable("screen.openallay.voice.mic"))).build());
            }
        }
        updateVirtualRows(Math.max(40, layout.transcript().width() - 18));
        scroll = followBottom
                ? virtualizer.maximumScroll(transcriptViewportHeight())
                : virtualizer.clampScroll(scroll, transcriptViewportHeight());
        refreshTelemetry();
        updateControls();
        if (detailOpen()) focusDetail();
        else setInitialFocus(composer);
    }

    @Override
    public void resize(int width, int height) {
        GuideViewportAnchor anchor = layout == null ? null : virtualizer.anchorAt(scroll);
        boolean shouldFollow = followBottom;
        draft = composer == null ? draft : composer.getValue();
        super.resize(width, height);
        restoreTranscript(anchor, shouldFollow);
    }

    @Override
    public void added() {
        // Minecraft may return to this same Screen instance from a native confirmation.
        // A removed screen releases every provider view, so each attachment gets a fresh owner.
        nativeViews = new NativeDomainViewRegistry();
        uiState.selectSession(service.snapshot().selectedSession());
        attachment = uiState.attach(GuideClientUiState.Surface.FULLSCREEN, service.snapshot().selectedSession());
        draftSubscription = uiState.subscribe(this::sharedDraftChanged);
        synchronizeComposerSession();
        sharedDraftChanged();
        syncComposerTextures();
        loadRestoredImagePreviews();
        subscription = service.subscribe(snapshot -> {
            if (!snapshot.selectedSession().equals(view.selectedSession())) submittingDraft = false;
            composerImages.observeSession(snapshot.selectedSession());
            pendingSnapshots.offer(snapshot);
        });
        // A newly-created Screen is also the return path from settings. Refresh after
        // subscribing so the latest profile/runtime projection cannot be missed.
        service.refreshCapabilities();
    }

    @Override
    public void removed() {
        draft = composer == null ? draft : composer.getValue();
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
        nativeViews.close();
        uiState.setText(view.selectedSession(), draft);
        if (attachment != null) { attachment.close(); attachment = null; }
        if (draftSubscription != null) {
            try { draftSubscription.close(); } catch (Exception ignored) { }
            draftSubscription = null;
        }
        if (notifications != null) notifications.clearVisibility(service);
        voiceKeyHeld = false;
        if (voice != null) voice.cancel(dev.openallay.client.voice.VoiceRuntime.CancelReason.SCREEN_CLOSED);
        releaseComposerTextures();
        submittingDraft = false;
    }

    @Override
    protected void repositionElements() {
        GuideViewportAnchor anchor = layout == null ? null : virtualizer.anchorAt(scroll);
        boolean shouldFollow = followBottom;
        draft = composer == null ? draft : composer.getValue();
        rebuildPresentationWidgets();
        restoreTranscript(anchor, shouldFollow);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        presentationTicks++;
        nativeViews.tick();
        applyPendingProjection();
        refreshTelemetry();
        validatePendingEditTarget();
        refreshComposerLayout();
        if (layout != null) requestViewportHistory(layout.transcript());
        updateControls();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (voice != null && voice.enabled()
                && voiceKeyAllowed(getFocused() == composer, sessionOverlay || overflowOpen || modelSelectorOpen)
                && OpenAllayKeyMappings.VOICE_PTT.matches(event)) {
            if (!voiceKeyHeld) {
                voiceKeyHeld = true;
                voice.press(); // Screen keys are not gameplay KeyMapping.isDown() PTT ownership.
            }
            return true;
        }
        if (overflowOpen && event.key() == GLFW.GLFW_KEY_ESCAPE) { overflowOpen = false; return true; }
        if (sessionOverlay && event.key() == GLFW.GLFW_KEY_ESCAPE) { sessionOverlay = false; return true; }
        if (sessionOverlay && scrollSessionsKey(event.key())) return true;
        if (getFocused() != composer && !detailOpen() && !modelSelectorOpen && !sessionOverlay && !overflowOpen && scrollTranscriptKey(event.key())) return true;
        if (modelSelectorOpen && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            modelSelectorOpen = false;
            return true;
        }
        if (modelSelectorOpen
                && (event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_DOWN)) {
            moveModelSelectorCursor(event.key() == GLFW.GLFW_KEY_UP ? -1 : 1);
            return true;
        }
        if (modelSelectorOpen && event.isConfirmation()) {
            int choices = view.modelChoices().size();
            if (choices == 0) {
                modelSelectorOpen = false;
            } else {
                modelSelectorCursor = Mth.clamp(modelSelectorCursor, 0, choices - 1);
                selectModel(view.modelChoices().get(modelSelectorCursor));
            }
            return true;
        }
        if (closesDetailFirst(detailOpen(), event.key() == GLFW.GLFW_KEY_ESCAPE)) {
            closeDetail();
            return true;
        }
        if (detailOpen() && getFocused() != composer && scrollDetailKey(event.key())) return true;
        if (composer != null && getFocused() == composer && event.isPaste()) {
            // Preserve Minecraft's text paste and selection semantics, including text+image clipboards.
            synchronizeComposerSession();
            super.keyPressed(event);
            composerImages.paste();
            return true;
        }
        ComposerKeyAction composerAction = composerKeyAction(
                composer != null && getFocused() == composer,
                event.isConfirmation(),
                event.hasShiftDown(),
                event.hasControlDownWithQuirk());
        if (composerAction == ComposerKeyAction.SUBMIT) {
            submit();
            return true;
        }
        if (composerAction == ComposerKeyAction.NEWLINE) {
            return super.keyPressed(event);
        }
        if (event.key() == GLFW.GLFW_KEY_F6) {
            List<Hit> focusable = hits.stream()
                    .filter(hit -> (sessionOverlay ? hit.kind() == HitKind.SESSION
                            : overflowOpen ? hit.kind() == HitKind.MENU
                            : isContentFocusTarget(detailOpen(), hit.kind() == HitKind.DETAIL,
                                    hit.kind() == HitKind.CONTENT || hit.kind() == HitKind.COMPOSER || hit.kind() == HitKind.SESSION))
                            && hit.focusId() != null)
                    .toList();
            if (!focusable.isEmpty()) {
                int current = -1;
                for (int index = 0; index < focusable.size(); index++) {
                    if (focusable.get(index).focusId().equals(focusedContentId)) current = index;
                }
                Hit next = focusable.get((current + 1) % focusable.size());
                focusedContentId = next.focusId();
                clearFocus();
                if (minecraft != null && minecraft.getNarrator().isActive()) {
                    minecraft.getNarrator().saySystemNow(next.narration());
                }
                return true;
            }
        }
        if (event.isConfirmation() && getFocused() == null && focusedContentId != null) {
            Hit focused = hits.stream()
                    .filter(hit -> sessionOverlay ? hit.kind() == HitKind.SESSION : overflowOpen ? hit.kind() == HitKind.MENU
                            : !detailOpen() || hit.kind() == HitKind.DETAIL)
                    .filter(hit -> focusedContentId.equals(hit.focusId()))
                    .findFirst().orElse(null);
            if (focused != null) {
                focused.action().run();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (voice != null && voiceKeyHeld && OpenAllayKeyMappings.VOICE_PTT.matches(event)) {
            voiceKeyHeld = false;
            voice.release();
            return true;
        }
        return super.keyReleased(event);
    }

    static boolean voiceKeyAllowed(boolean composerFocused, boolean modalOpen) {
        return !composerFocused && !modalOpen;
    }

    static void activateVoice(dev.openallay.client.voice.VoiceInputActions actions) {
        if (actions == null || !actions.enabled()) return;
        switch (actions.status().state()) {
            case STARTING, RECORDING -> actions.release();
            case TRANSCRIBING -> actions.cancel(dev.openallay.client.voice.VoiceRuntime.CancelReason.USER);
            default -> actions.press();
        }
    }

    static boolean closesDetailFirst(boolean detailOpen, boolean escape) {
        return detailOpen && escape;
    }

    static boolean isContentFocusTarget(boolean detailOpen, boolean detailAction, boolean contentAction) {
        return detailOpen ? detailAction : contentAction;
    }

    private boolean scrollDetailKey(int key) {
        int maximum = maximumDetailScroll();
        int page = Math.max(24, layout.detail().height() - 30);
        int target = switch (key) {
            case GLFW.GLFW_KEY_UP -> detailScroll - 24;
            case GLFW.GLFW_KEY_DOWN -> detailScroll + 24;
            case GLFW.GLFW_KEY_PAGE_UP -> detailScroll - page;
            case GLFW.GLFW_KEY_PAGE_DOWN -> detailScroll + page;
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> maximum;
            default -> Integer.MIN_VALUE;
        };
        if (target == Integer.MIN_VALUE) return false;
        detailScroll = Mth.clamp(target, 0, maximum);
        return true;
    }

    private int maximumDetailScroll() {
        return Math.max(0, detailContentHeight - layout.detail().height() + 34);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (sessionOverlay && sessionBounds().contains(x, y) || layout.sessionRail().contains(x, y)) {
            sessionScroll = Mth.clamp(sessionScroll - (int) Math.signum(scrollY), 0, maximumSessionScroll());
            return true;
        }
        if (sessionOverlay || overflowOpen) return true;
        if (composerExtras != null && composerExtras.images().contains(x, y)) {
            imageScroll = Math.max(0, imageScroll - (int) Math.signum(scrollY));
            return true;
        }
        if (composerExtras != null && composerExtras.footer().contains(x, y) && !pendingMessages().isEmpty()) {
            pendingCursor = Mth.clamp(pendingCursor - (int) Math.signum(scrollY), 0, pendingMessages().size() - 1);
            return true;
        }
        GuideUiLayout.Rect modelMenu = modelSelectorBounds();
        if (modelSelectorOpen && modelMenu != null && modelMenu.contains(x, y)) {
            int maximum = Math.max(0, view.modelChoices().size() - visibleModelChoiceCount());
            modelSelectorScroll = Mth.clamp(
                    modelSelectorScroll - (int) Math.signum(scrollY), 0, maximum);
            modelSelectorCursor = Mth.clamp(
                    modelSelectorCursor,
                    modelSelectorScroll,
                    Math.min(view.modelChoices().size() - 1,
                            modelSelectorScroll + visibleModelChoiceCount() - 1));
            return true;
        }
        if (detailOpen() && layout.detail().contains(x, y)) {
            detailScroll = Mth.clamp(detailScroll - (int) Math.round(scrollY * 24), 0, maximumDetailScroll());
            return true;
        }
        if (detailOpen() && layout.detailOverlay()) return true;
        if (layout.transcript().contains(x, y)) {
            int maximum = virtualizer.maximumScroll(Math.max(0, layout.transcript().height() - 14));
            scroll = Mth.clamp(scroll - (int) Math.round(scrollY * 24), 0, maximum);
            followBottom = virtualizer.atBottom(
                    scroll, Math.max(0, layout.transcript().height() - 14));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (sessionOverlay || overflowOpen) {
                HitKind topKind = sessionOverlay ? HitKind.SESSION : HitKind.MENU;
                for (Hit hit : List.copyOf(hits)) {
                    if (hit.kind() == topKind && hit.rect().contains(event.x(), event.y())) {
                        focusedContentId = hit.focusId();
                        clearFocus();
                        hit.action().run();
                        return true;
                    }
                }
                if (sessionOverlay && !sessionBounds().contains(event.x(), event.y())) sessionOverlay = false;
                if (overflowOpen && !overflowBounds().contains(event.x(), event.y())) overflowOpen = false;
                return true; // A modal's background never routes hidden transcript actions.
            }
            if (modelSelectorOpen
                    && (modelSelectorButton == null
                            || !modelSelectorButton.contains(event.x(), event.y()))
                    && (modelSelectorBounds() == null
                            || !modelSelectorBounds().contains(event.x(), event.y()))) {
                modelSelectorOpen = false;
            }
            if (modelSelectorOpen) {
                for (Hit hit : List.copyOf(hits)) {
                    if (hit.kind() == HitKind.MODEL
                            && hit.rect().contains(event.x(), event.y())) {
                        focusedContentId = hit.focusId();
                        clearFocus();
                        hit.action().run();
                        return true;
                    }
                }
            }
            if (detailOpen()) {
                List<Hit> detailHits = hits.stream()
                        .filter(hit -> hit.kind() == HitKind.DETAIL)
                        .toList();
                GuideUiClickRoute route = GuideUiClickRoute.resolveDetail(
                        layout.detail(),
                        detailCloseBounds(),
                        detailHits.stream().map(Hit::rect).toList(),
                        event.x(),
                        event.y());
                if (route.kind() == GuideUiClickRoute.Kind.ACTION) {
                    Hit selected = detailHits.get(route.actionIndex());
                    focusedContentId = selected.focusId();
                    clearFocus();
                    selected.action().run();
                    return true;
                }
                if (route.kind() == GuideUiClickRoute.Kind.DISMISS_DETAIL) {
                    closeDetail();
                    return true;
                }
                if (route.kind() == GuideUiClickRoute.Kind.INSIDE_DETAIL) return true;
                if (layout.detailOverlay() && !layout.composer().contains(event.x(), event.y())) return true;
            }
            if (sessionOverlay) {
                for (Hit hit : List.copyOf(hits)) {
                    if (hit.kind() == HitKind.SESSION && hit.rect().contains(event.x(), event.y())) {
                        clearFocus();
                        hit.action().run();
                        return true;
                    }
                }
            }
            for (Hit hit : List.copyOf(hits)) {
                if (hit.rect().contains(event.x(), event.y())) {
                    focusedContentId = hit.focusId();
                    clearFocus();
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (Boolean.getBoolean("openallay.e2e.enabled")) {
            renderedToolIds.clear();
            renderedResultCardIds.clear();
            renderedNativeFrame++;
        }
        graphics.fill(0, 0, width, height, 0xC00B0D12);
        renderTop(graphics, mouseX, mouseY);
        if (!sessionOverlay) renderSessions(graphics);
        renderTranscript(graphics, mouseX, mouseY);
        renderProgress(graphics);
        renderTelemetry(graphics, mouseX, mouseY);
        renderDetail(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        renderComposerExtras(graphics, mouseX, mouseY);
        renderModelSelector(graphics, mouseX, mouseY);
        renderLocalNotice(graphics, mouseX, mouseY);
        if (sessionOverlay) renderSessions(graphics);
        renderOverflow(graphics, mouseX, mouseY);
        reportVisibleReceipts();
    }

    private void renderTop(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        GuideUiLayout.Rect top = layout.topBar();
        graphics.fill(top.x(), top.y(), top.x() + top.width(), top.y() + top.height(), panelColor());
        graphics.fill(top.x(), top.y(), top.x() + 3, top.y() + top.height(), ACCENT);
        graphics.fill(top.x() + 3, top.y(), top.x() + 34, top.y() + 2, ACCENT);
        if (layout.header().status().height() > 0) {
            Component status = modelStatus();
            boundedHeaderText(graphics, status, layout.header().status(), MUTED);
            if (layout.header().status().contains(mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(font, status, mouseX, mouseY);
            }
        }
    }

    static Component headerTitle() {
        return Component.translatable("screen.openallay.guide").withStyle(ChatFormatting.BOLD);
    }

    /** A passive native label: Tab reveals and narrates its own complete name, not another button's. */
    private final class HeaderTitle extends AbstractWidget {
        private Component paintedTitle;

        private HeaderTitle(Component title, GuideUiLayout.Rect bounds) {
            super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), title);
            setTooltip(Tooltip.create(title));
        }

        @Override
        protected void extractWidgetRenderState(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            Component full = getMessage();
            Component visible = full;
            if (font.width(full.getVisualOrderText()) > getWidth()) {
                String prefix = font.getSplitter().plainHeadByWidth(full.getString(),
                        Math.max(0, getWidth() - font.width(Component.literal("…").withStyle(full.getStyle()))),
                        full.getStyle());
                visible = Component.literal(prefix + "…").withStyle(full.getStyle());
            }
            if (isFocused()) {
                graphics.outline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, ACCENT);
            }
            boundedHeaderText(graphics, visible,
                    new GuideUiLayout.Rect(getX(), getY(), getWidth(), getHeight()), TEXT);
            paintedTitle = visible;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }

    /** Read-only native title geometry and last extraction receipt; a screenshot is still required. */
    public Map<String, Object> e2eHeaderReceipt() {
        requireDevelopmentProbe();
        if (layout == null || headerTitleWidget == null) throw new IllegalStateException("header is not initialized");
        Component full = headerTitleWidget.getMessage();
        int styledWidth = font.width(full.getVisualOrderText());
        return Map.of("fullName", full.getString(),
                "plainWidth", font.width(full.copy().withStyle(style -> style.withBold(false)).getVisualOrderText()),
                "styleWidth", styledWidth,
                "titleWidth", headerTitleWidget.getWidth(),
                "headerHeight", layout.topBar().height(),
                "fullVisible", headerTitleWidget.paintedTitle != null
                        && headerTitleWidget.paintedTitle.getString().equals(full.getString())
                        && styledWidth <= headerTitleWidget.getWidth());
    }

    /** Reports extracted native rows, not a synthetic provider or a visual-acceptance verdict. */
    public Map<String, Object> e2eTelemetryReceipt() {
        requireDevelopmentProbe();
        if (layout == null) throw new IllegalStateException("telemetry is not initialized");
        GuideUiLayout.Rect area = layout.telemetry();
        return Map.of("card", layout.telemetryCard(),
                "rowCount", area.equals(renderedTelemetryBounds) ? renderedTelemetryRows : 0,
                "width", area.width(), "height", area.height(),
                "contextText", telemetryContext.getString(), "cacheText", telemetryInput.getString(),
                "costText", telemetryCost.getString(), "imageBarEligible", telemetryImageBarEligible());
    }

    /** Cached outcomes from the real export handler. Reading this receipt never captures or exports data. */
    public Map<String, Object> e2eExportReceipt() {
        requireDevelopmentProbe();
        return Map.of("running", exportRunning, "noticeSeverity", notice.severity().name(),
                "noticeMessage", notice.message(), "noticeKey", notice == exportNotice ? exportNoticeKey : "",
                "lastFilename", lastExportFilename, "lastRequestCount", lastExportRequestCount);
    }

    /** Explicit development intent uses the same handler as the player's Export menu action. */
    public void e2eExportSelectedSession() {
        requireDevelopmentProbe();
        exportSession();
    }

    /** Last native extraction identities only; clipped or retained-but-unpainted cards are not counted. */
    public Map<String, Object> e2eToolsReceipt() {
        requireDevelopmentProbe();
        List<GuideUiRow.Tool> tools = view.rows().stream().filter(GuideUiRow.Tool.class::isInstance)
                .map(GuideUiRow.Tool.class::cast).toList();
        long expanded = tools.stream().filter(this::toolExpanded).count();
        return Map.of("lastNativeFrame", renderedNativeFrame,
                "visibleToolIds", List.copyOf(renderedToolIds), "visibleToolCount", renderedToolIds.size(),
                "resultCardIds", List.copyOf(renderedResultCardIds), "resultCardCount", renderedResultCardIds.size(),
                "totalToolCount", tools.size(), "expandedToolCount", expanded,
                "toolsCollapsedDefault", projectedDisplay.ui().fullscreen().toolsCollapsed());
    }

    private void boundedHeaderText(
            GuiGraphicsExtractor graphics, Component text, GuideUiLayout.Rect bounds, int color) {
        graphics.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
        graphics.text(font, text, bounds.x(), bounds.y(), color, false);
        graphics.disableScissor();
    }

    private void toggleSessions() {
        overflowOpen = false;
        modelSelectorOpen = false;
        if (!layout.narrow()) {
            railVisible = !railVisible;
            sessionOverlay = false;
            rebuildForDetail();
        } else sessionOverlay = !sessionOverlay;
    }

    private GuideUiLayout.Rect sessionBounds() {
        if (layout.sessionRail().width() > 0) return layout.sessionRail();
        return new GuideUiLayout.Rect(layout.transcript().x(), layout.transcript().y(),
                Math.min(200, layout.transcript().width()), layout.transcript().height());
    }

    private int visibleSessionCount() { return Math.max(1, (sessionBounds().height() - 30) / 22); }
    private int maximumSessionScroll() { return Math.max(0, view.sessions().size() - visibleSessionCount()); }
    private boolean scrollSessionsKey(int key) {
        int target = switch (key) {
            case GLFW.GLFW_KEY_UP -> sessionScroll - 1;
            case GLFW.GLFW_KEY_DOWN -> sessionScroll + 1;
            case GLFW.GLFW_KEY_PAGE_UP -> sessionScroll - visibleSessionCount();
            case GLFW.GLFW_KEY_PAGE_DOWN -> sessionScroll + visibleSessionCount();
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> maximumSessionScroll();
            default -> Integer.MIN_VALUE;
        };
        if (target == Integer.MIN_VALUE) return false;
        sessionScroll = Mth.clamp(target, 0, maximumSessionScroll());
        return true;
    }

    private void renderSessions(GuiGraphicsExtractor graphics) {
        hits.removeIf(hit -> hit.kind() == HitKind.SESSION);
        if (layout.sessionRail().width() == 0 && !sessionOverlay) return;
        GuideUiLayout.Rect rail = sessionBounds();
        graphics.fill(rail.x(), rail.y(), rail.right(), rail.bottom(), panelAltColor());
        graphics.outline(rail.x(), rail.y(), rail.width(), rail.height(), OpenAllayWidgetTheme.SLATE_BORDER);
        graphics.text(font, Component.translatable("screen.openallay.session.title"), rail.x() + 8, rail.y() + 6, ACCENT, false);
        if (sessionOverlay) {
            GuideUiLayout.Rect close = new GuideUiLayout.Rect(rail.right() - 22, rail.y() + 2, 20, 18);
            graphics.text(font, "×", close.x() + 5, close.y() + 4, TEXT, false);
            hits.add(new Hit(close, HitKind.SESSION, () -> sessionOverlay = false,
                    "session:close", Component.translatable("screen.openallay.detail.close").getString()));
        }
        int y = rail.y() + 24;
        sessionScroll = Mth.clamp(sessionScroll, 0, maximumSessionScroll());
        graphics.enableScissor(rail.x(), y, rail.right(), rail.bottom());
        for (int index = sessionScroll; index < Math.min(view.sessions().size(), sessionScroll + visibleSessionCount()); index++) {
            GuideUiSession session = view.sessions().get(index);
            GuideUiLayout.Rect row = new GuideUiLayout.Rect(rail.x() + 4, y, rail.width() - 10, 20);
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(), session.selected() ? OpenAllayWidgetTheme.MINT_DARK : panelColor());
            boundedHeaderText(graphics, Component.literal(session.id() + (session.running() ? " ●" : "")),
                    new GuideUiLayout.Rect(row.x() + 4, row.y() + 6, row.width() - 8, 12), session.selected() ? TEXT : MUTED);
            String id = session.id();
            hits.add(new Hit(row, HitKind.SESSION, () -> {
                service.selectSession(id);
                sessionOverlay = false;
                scroll = 0;
            }, "session:" + id, session.id()));
            y += 22;
        }
        graphics.disableScissor();
        renderScrollMarker(graphics, rail, sessionScroll, maximumSessionScroll());
    }

    private boolean toolsExpanded(UUID request) {
        return taskTools(request).stream().allMatch(this::toolExpanded);
    }

    private boolean toolExpanded(GuideUiRow.Tool tool) {
        return toolStepFolds.expanded(tool);
    }

    private ToolFlowOwner toolFlowOwner() {
        return new ToolFlowOwner(service.snapshot().actorId(), view.selectedSession(), minecraft == null ? null : minecraft.level);
    }

    private List<GuideUiRow.Tool> taskTools(UUID request) {
        return view.rows().stream().filter(GuideUiRow.Tool.class::isInstance)
                .map(GuideUiRow.Tool.class::cast).filter(tool -> tool.requestId().equals(request)).toList();
    }

    private boolean firstTool(GuideUiRow.Tool tool) {
        return taskTools(tool.requestId()).getFirst().equals(tool);
    }

    private void toggleToolStep(GuideUiRow.Tool tool, boolean task) {
        GuideViewportAnchor anchor = virtualizer.anchorAt(scroll);
        if (task) toolStepFolds.toggleTask(taskTools(tool.requestId()));
        else toolStepFolds.toggle(tool);
        hits.removeIf(hit -> hit.kind() == HitKind.CONTENT);
        updateVirtualRows(Math.max(40, layout.transcript().width() - 18));
        // Folding is an explicit reading action. Keep the current step, not the last transcript row.
        followBottom = false;
        restoreTranscript(anchor, false);
    }

    private boolean scrollTranscriptKey(int key) {
        int maximum = virtualizer.maximumScroll(transcriptViewportHeight());
        int page = Math.max(20, transcriptViewportHeight() - 10);
        int next = switch (key) {
            case GLFW.GLFW_KEY_PAGE_UP -> scroll - page;
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll + page;
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> maximum;
            default -> Integer.MIN_VALUE;
        };
        if (next == Integer.MIN_VALUE) return false;
        scroll = Mth.clamp(next, 0, maximum);
        followBottom = scroll == maximum;
        return true;
    }
    private void renderScrollMarker(GuiGraphicsExtractor graphics, GuideUiLayout.Rect bounds, int position, int maximum) {
        if (maximum <= 0 || bounds.height() < 8) return;
        int track = bounds.height() - 4;
        int thumb = Math.max(6, track / 5);
        int top = bounds.y() + 2 + (track - thumb) * position / maximum;
        graphics.fill(bounds.right() - 3, bounds.y() + 2, bounds.right() - 1, bounds.bottom() - 2, OpenAllayWidgetTheme.SLATE_DISABLED);
        graphics.fill(bounds.right() - 3, top, bounds.right() - 1, top + thumb, ACCENT);
    }
    private GuideUiLayout.Rect overflowBounds() {
        return new GuideUiLayout.Rect(Math.max(4, layout.header().overflow().right() - 160),
                layout.topBar().bottom() + 2, 160, Math.min(156, height - layout.topBar().bottom() - 6));
    }
    private void renderOverflow(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        hits.removeIf(hit -> hit.kind() == HitKind.MENU);
        if (!overflowOpen) return;
        GuideUiLayout.Rect menu = overflowBounds();
        graphics.fill(menu.x(), menu.y(), menu.right(), menu.bottom(), panelAltColor());
        String[] labels = {"screen.openallay.session.new", "screen.openallay.session.delete", "screen.openallay.action.export",
                "screen.openallay.action.refresh", "screen.openallay.session.fork"};
        Runnable[] actions = {this::createSession, this::closeSession, this::exportSession,
                () -> service.refreshCapabilities(), this::forkSelectedSession};
        for (int index = 0; index < labels.length; index++) {
            int y = menu.y() + 4 + index * 24;
            if (y + 20 > menu.bottom()) break;
            GuideUiLayout.Rect row = new GuideUiLayout.Rect(menu.x() + 4, y, menu.width() - 8, 20);
            if (row.contains(mouseX, mouseY)) graphics.fill(row.x(), row.y(), row.right(), row.bottom(), OpenAllayWidgetTheme.CHARCOAL_HOVERED);
            Component label = Component.translatable(labels[index]);
            boundedHeaderText(graphics, label, new GuideUiLayout.Rect(row.x() + 4, row.y() + 5, row.width() - 8, 12), TEXT);
            Runnable action = actions[index];
            hits.add(new Hit(row, HitKind.MENU, () -> { overflowOpen = false; action.run(); }, "menu:" + index, label.getString()));
        }
    }
    private void microphoneAction() {
        activateVoice(voice);
    }

    private Component voiceFeedback() {
        var feedback = dev.openallay.client.voice.VoiceStatusPresentation.describe(voice.status());
        MutableComponent text = Component.translatable(feedback.translationKey()).copy();
        if (!feedback.actionTranslationKey().isBlank()) text.append(" · ").append(Component.translatable(feedback.actionTranslationKey()));
        return text;
    }

    private void renderLocalNotice(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        GuideUiLayout.Rect bounds = layout.composerNotice();
        if (voice != null && voice.enabled() && microphone == null) {
            int micWidth = voice.status().active() ? Math.min(70, bounds.width() / 2) : 30;
            GuideUiLayout.Rect mic = new GuideUiLayout.Rect(bounds.right() - micWidth, bounds.y(), micWidth, bounds.height());
            graphics.fill(mic.x(), mic.y(), mic.right(), mic.bottom(), panelAltColor());
            Component micLabel = voice.status().active()
                    ? Component.translatable("screen.openallay.voice.short." + voice.status().state().name().toLowerCase(java.util.Locale.ROOT), voice.status().elapsedMillis() / 1000)
                    : Component.translatable("screen.openallay.voice.mic_short");
            boundedHeaderText(graphics, micLabel, mic, voice.status().active() ? OpenAllayWidgetTheme.WARNING : ACCENT);
            hits.add(new Hit(mic, HitKind.COMPOSER, this::microphoneAction, "voice:mic", Component.translatable("screen.openallay.voice.mic").getString()));
            if (mic.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font, voiceFeedback(), mouseX, mouseY);
            bounds = new GuideUiLayout.Rect(bounds.x(), bounds.y(), bounds.width() - micWidth - 2, bounds.height());
        }
        if (!notice.empty()) {
            boundedHeaderText(graphics, Component.literal(notice.message()), bounds, notice.color());
            if (bounds.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font, Component.literal(notice.message()), mouseX, mouseY);
        } else if (voice != null && voice.status().indicatorVisible()) {
            Component status = voiceFeedback();
            var feedback = dev.openallay.client.voice.VoiceStatusPresentation.describe(voice.status());
            boundedHeaderText(graphics, status, bounds, feedback.error() ? ERROR : voice.status().active() ? OpenAllayWidgetTheme.WARNING : MUTED);
            if (bounds.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font, status, mouseX, mouseY);
        }
        if (draftIntent().editInvalid()) {
            GuideUiLayout.Rect reset = new GuideUiLayout.Rect(bounds.right() - Math.min(104, bounds.width()), bounds.y(), Math.min(104, bounds.width()), bounds.height());
            graphics.fill(reset.x(), reset.y(), reset.right(), reset.bottom(), panelAltColor());
            boundedHeaderText(graphics, Component.translatable("screen.openallay.pending.use_as_new"), reset, ACCENT);
            hits.add(new Hit(reset, HitKind.COMPOSER, () -> {
                uiState.resetIntent(view.selectedSession());
                notice = GuideUiNotice.info("");
            }, "composer:reset-edit", Component.translatable("screen.openallay.pending.use_as_new").getString()));
            return; // The missing pending-edit target requires an explicit player action.
        }
        List<GuideClientUiState.PendingInsertion> pending = uiState.pendingInsertions(view.selectedSession());
        if (!pending.isEmpty()) {
            GuideUiLayout.Rect action = new GuideUiLayout.Rect(bounds.right() - Math.min(100, bounds.width()), bounds.y(), Math.min(100, bounds.width()), bounds.height());
            graphics.fill(action.x(), action.y(), action.right(), action.bottom(), panelAltColor());
            boundedHeaderText(graphics, Component.translatable("screen.openallay.voice.pending", pending.size()), action, ACCENT);
            hits.add(new Hit(action, HitKind.COMPOSER, () -> uiState.applyPendingInsertion(pending.getFirst().id()),
                    "voice:pending", Component.translatable("screen.openallay.voice.pending", pending.size()).getString()));
        }
    }

    private void reportVisibleReceipts() {
        if (notifications == null) return;
        HashSet<dev.openallay.guide.GuidePresentationEvent.Key> visible = new HashSet<>();
        HashSet<dev.openallay.guide.GuidePresentationEvent.Key> seen = new HashSet<>();
        boolean uncovered = !sessionOverlay && !overflowOpen && !modelSelectorOpen && !(detailOpen() && layout.detailOverlay());
        if (uncovered) {
            GuideUiLayout.Rect panel = layout.transcript();
            GuideUiLayout.Rect viewport = new GuideUiLayout.Rect(panel.x(), panel.y() + 7, panel.width(), panel.height() - 14);
            for (var event : notifications.receipts(service, view.selectedSession())) {
                if (event.content().isEmpty()) continue;
                List<GuideUiLayout.Rect> content = event.content().stream().map(ref ->
                        view.rows().stream().filter(row -> receiptMatchesRow(event, ref, row)).findFirst()
                                .map(row -> renderedRows.get(ref.contentId().startsWith("node:")
                                        ? rowId(row) + ":" + ref.contentId() : rowId(row))).orElse(null)).toList();
                if (content.stream().filter(Objects::nonNull).anyMatch(bounds -> intersects(bounds, viewport))) visible.add(event.key());
                if (content.stream().allMatch(bounds -> bounds != null && bounds.y() >= viewport.y() && bounds.bottom() <= viewport.bottom())) seen.add(event.key());
            }
        }
        boolean active = minecraft != null && minecraft.isWindowActive();
        notifications.visible(service, view.selectedSession(), visible, active);
        if (active) notifications.markSeen(seen);
    }

    private static boolean receiptMatchesRow(dev.openallay.guide.GuidePresentationEvent event,
            dev.openallay.guide.GuidePresentationEvent.ContentRef ref, GuideUiRow row) {
        // Tool transcript rows show summaries, not the original tool cards. Never acknowledge those card refs.
        if (ref.contentId().startsWith("tool:")) return false;
        UUID request = switch (row) {
            case GuideUiRow.Assistant value -> value.requestId();
            case GuideUiRow.Tool value -> value.requestId();
            case GuideUiRow.Status value -> value.requestId();
            default -> null;
        };
        int ordinal = switch (row) {
            case GuideUiRow.Assistant value -> value.ordinal();
            case GuideUiRow.Tool value -> value.ordinal();
            case GuideUiRow.Status ignored -> -1;
            default -> Integer.MIN_VALUE;
        };
        if (ref.contentId().equals("reply") && !(row instanceof GuideUiRow.Assistant)) return false;
        if (ref.contentId().startsWith("node:") && !(row instanceof GuideUiRow.Assistant)) return false;
        if (ref.timelineOrdinal() == -1 && !(row instanceof GuideUiRow.Status)) return false;
        return event.key().requestId().equals(request) && ordinal == ref.timelineOrdinal();
    }

    private void refreshTelemetry() {
        var next = service.telemetry();
        if (Objects.equals(telemetry, next)) return;
        telemetry = next;
        String unknown = Component.translatable("screen.openallay.telemetry.unknown").getString();
        var context = telemetry.context();
        boolean contextKnown = context != null && context.budget() != null;
        boolean imageUnknown = contextKnown && context.imageAccounting()
                == dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting.UNKNOWN;
        String occupancy = contextKnown
                ? "~" + compactTokens(context.estimatedTokens()) + "/" + compactTokens(context.budget().contextWindowTokens())
                : unknown;
        if (imageUnknown) occupancy = Component.translatable(
                "screen.openallay.telemetry.text_estimate", occupancy).getString();
        telemetryContext = Component.literal(occupancy);
        var usage = telemetry.sessionUsage();
        var rate = usage.cacheHitRate();
        String cache = rate == null ? unknown : rate.movePointRight(2)
                .setScale(1, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
        telemetryInput = rate == null ? Component.translatable("screen.openallay.telemetry.cache_compact_unknown")
                : Component.translatable("screen.openallay.telemetry.cache", cache);
        String cost = usage.estimatedUsd() == null ? unknown : "~$" + usage.estimatedUsd()
                .setScale(5, java.math.RoundingMode.HALF_UP).toPlainString();
        if (usage.costIncomplete() && usage.estimatedUsd() != null) cost += "+";
        telemetryCost = Component.translatable("screen.openallay.telemetry.cost_compact", cost);
        telemetryCompact = Component.translatable("screen.openallay.telemetry.compact", occupancy, cache, cost);
        MutableComponent detail = Component.translatable("screen.openallay.telemetry.latest");
        detail.append("\n").append(contextKnown
                ? Component.translatable("screen.openallay.telemetry.budget",
                        context.estimatedTokens(), context.budget().inputTokens(),
                        context.budget().contextWindowTokens(), context.budget().reservedTokens(),
                        context.budget().maxOutputTokens())
                : Component.translatable("screen.openallay.telemetry.context_unknown"));
        if (imageUnknown) detail.append("\n").append(
                Component.translatable("screen.openallay.telemetry.image_unknown"));
        detail.append("\n").append(Component.translatable("screen.openallay.telemetry.session",
                usage.actualCalls(), cost));
        detail.append("\n").append(Component.translatable("screen.openallay.telemetry.cache_detail",
                cache, usage.cacheReadTokens(), usage.inputTokens()));
        if (usage.costIncomplete()) detail.append("\n").append(
                Component.translatable("screen.openallay.telemetry.partial"));
        if (usage.cacheIncomplete()) detail.append("\n").append(
                Component.translatable("screen.openallay.telemetry.cache_unknown"));
        var inherited = telemetry.inheritedUsage();
        if (inherited.actualCalls() > 0) {
            String reference = inherited.estimatedUsd() == null ? unknown : "~$" + inherited.estimatedUsd()
                    .setScale(5, java.math.RoundingMode.HALF_UP).toPlainString();
            if (inherited.costIncomplete() && inherited.estimatedUsd() != null) reference += "+";
            detail.append("\n").append(Component.translatable("screen.openallay.telemetry.inherited", reference));
        }
        detail.append("\n").append(Component.translatable("screen.openallay.telemetry.price_note"));
        telemetryTooltip = detail;
    }

    static String compactTokens(long count) {
        if (count < 1_000) return Long.toString(count);
        return String.format(java.util.Locale.ROOT, count < 1_000_000 ? "%.1fk" : "%.1fM",
                count / (count < 1_000_000 ? 1_000.0 : 1_000_000.0));
    }

    private void renderTelemetry(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        GuideUiLayout.Rect area = layout.telemetry();
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), panelAltColor());
        graphics.enableScissor(area.x() + 4, area.y(), area.right() - 4, area.bottom());
        if (!layout.telemetryCard()) {
            graphics.text(font, telemetryCompact, area.x() + 4, area.y() + 2, MUTED, false);
        } else {
            int x = area.x() + 7;
            int y = area.y() + 7;
            graphics.text(font, Component.translatable("screen.openallay.telemetry.context"), x, y, MUTED, false);
            graphics.text(font, telemetryContext, x, y + 12, TEXT, false);
            // Text-only estimates must not look like total image+text occupancy.
            if (telemetryImageBarEligible()) {
                int barWidth = Math.max(1, area.width() - 14);
                graphics.fill(x, y + 25, x + barWidth, y + 28, 0xFF3E4753);
                double ratio = Math.min(1, (double) telemetry.context().estimatedTokens()
                        / telemetry.context().budget().contextWindowTokens());
                graphics.fill(x, y + 25, x + (int) (barWidth * ratio), y + 28,
                        ratio >= 0.9 ? 0xFFFFD479 : ACCENT);
            }
            graphics.text(font, telemetryInput, x, y + 39, MUTED, false);
            graphics.text(font, telemetryCost, x, y + 55, MUTED, false);
        }
        graphics.disableScissor();
        renderedTelemetryBounds = area;
        renderedTelemetryRows = layout.telemetryCard() ? 3 : 1;
        if (area.contains(mouseX, mouseY) && !modelSelectorOpen && !sessionOverlay && !overflowOpen) {
            graphics.setTooltipForNextFrame(font, telemetryTooltip, mouseX, mouseY);
        }
    }

    private boolean telemetryImageBarEligible() {
        return telemetry != null && telemetry.context() != null && telemetry.context().budget() != null
                && telemetry.context().imageAccounting()
                != dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting.UNKNOWN;
    }

    private void renderProgress(GuiGraphicsExtractor graphics) {
        if (layout.progress().height() == 0) return;
        GuideUiProgress progress = view.progress();
        if (progress == null) {
            activeProgressRenderFrames = 0;
            return;
        }
        activeProgressRenderFrames++;
        GuideUiLayout.Rect area = layout.progress();
        graphics.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(), panelAltColor());
        graphics.enableScissor(area.x(), area.y(), area.x() + area.width(), area.y() + area.height());
        List<FormattedCharSequence> lines = font.split(
                progressMessage(progress, Instant.now(), projectedDisplay.debugMode()),
                Math.max(1, area.width() - 12));
        for (int index = 0; index < Math.min(2, lines.size()); index++) {
            graphics.text(font, lines.get(index), area.x() + 6, area.y() + 2 + index * 10,
                    progress.phase() == dev.openallay.guide.GuideRequestPhase.ENDPOINT_WAIT
                            ? 0xFFFFD479 : ACCENT,
                    false);
        }
        graphics.disableScissor();
    }

    static Component progressMessage(
            GuideUiProgress progress, Instant now, boolean debugMode) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(now, "now");
        MutableComponent message = Component.translatable(progress.activityTranslationKey());
        message.append(" · ").append(Component.translatable(
                "screen.openallay.progress.elapsed",
                formatDuration(Duration.between(progress.requestStartedAt(), now))));
        if (progress.retryAt() != null) {
            message.append(" · ").append(Component.translatable(
                    "screen.openallay.progress.retry_in",
                    formatDuration(Duration.between(now, progress.retryAt()))));
            if (progress.attempt() > 0) {
                message.append(" · ").append(Component.translatable(
                        "screen.openallay.progress.attempt", progress.attempt()));
            }
        } else if (progress.deadlineAt() != null) {
            message.append(" · ").append(Component.translatable(
                    "screen.openallay.progress.remaining",
                    formatDuration(Duration.between(now, progress.deadlineAt()))));
        } else if (progress.phase()
                == dev.openallay.guide.GuideRequestPhase.RESPONSE_STREAMING) {
            message.append(" · ").append(Component.translatable(
                    "screen.openallay.progress.last_update",
                    formatDuration(Duration.between(progress.lastProgressAt(), now))));
        }
        if (debugMode && progress.retryAt() == null && progress.attempt() > 0) {
            message.append(" · ").append(Component.translatable(
                    "screen.openallay.progress.attempt", progress.attempt()));
        }
        return message;
    }

    static String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long remainder = seconds % 60;
        return hours > 0
                ? "%d:%02d:%02d".formatted(hours, minutes, remainder)
                : "%d:%02d".formatted(minutes, remainder);
    }

    private void renderTranscript(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        hits.removeIf(hit -> hit.kind() == HitKind.CONTENT);
        renderedRows.clear();
        GuideUiLayout.Rect area = layout.transcript();
        graphics.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(), panelColor());
        graphics.enableScissor(area.x(), area.y(), area.x() + area.width(), area.y() + area.height());
        int textWidth = Math.max(40, area.width() - 18);
        if (!Objects.equals(toolStepOwner, toolFlowOwner())) updateVirtualRows(textWidth);
        int viewportHeight = Math.max(0, area.height() - 14);
        GuideTranscriptVirtualizer.Window window = virtualizer.visible(scroll, viewportHeight, 30);
        int contentTop = area.y() + 7;
        if (view.rows().isEmpty()) {
            graphics.text(font, Component.translatable(
                            "screen.openallay.transcript.empty",
                            projectedDisplay.assistantName()),
                    area.x() + 9, contentTop, MUTED, false);
        }
        nativeViews.beginFrame();
        try {
            for (int index = window.fromIndex(); index < window.toIndexExclusive(); index++) {
                int y = contentTop - scroll + virtualizer.offset(index);
                GuideUiRow row = view.rows().get(index);
                int bottom = renderRow(graphics, row, area.x() + 9, y, textWidth, mouseX, mouseY);
                // A collapsed step still has its own actual header and row identity.
                GuideUiLayout.Rect paintedRow = new GuideUiLayout.Rect(area.x() + 9, y, textWidth, bottom - y);
                renderedRows.put(rowId(row), paintedRow);
                if (Boolean.getBoolean("openallay.e2e.enabled") && row instanceof GuideUiRow.Tool paintedTool
                        && intersects(paintedRow, area)) renderedToolIds.add(toolFocusId(paintedTool));
            }
        } finally {
            nativeViews.endFrame();
        }
        hits.removeIf(hit -> hit.kind() == HitKind.CONTENT && !intersects(hit.rect(), area));
        hits.stream()
                .filter(hit -> hit.kind() == HitKind.CONTENT
                        && isFocused(focusedContentId, hit.focusId()))
                .findFirst()
                .ifPresent(hit -> graphics.outline(
                        hit.rect().x(), hit.rect().y(), hit.rect().width(), hit.rect().height(),
                        0xFFFFFFFF));
        graphics.disableScissor();
        renderScrollMarker(graphics, area, scroll, virtualizer.maximumScroll(transcriptViewportHeight()));
        if (!followBottom) {
            GuideUiLayout.Rect bottom = new GuideUiLayout.Rect(area.right() - Math.min(110, area.width()), area.bottom() - 14,
                    Math.min(108, area.width() - 2), 13);
            graphics.fill(bottom.x(), bottom.y(), bottom.right(), bottom.bottom(), panelAltColor());
            boundedHeaderText(graphics, Component.translatable("screen.openallay.scroll.bottom"), bottom, ACCENT);
            hits.add(new Hit(bottom, HitKind.CONTENT, () -> { followBottom = true; scroll = virtualizer.maximumScroll(transcriptViewportHeight()); },
                    "transcript:bottom", Component.translatable("screen.openallay.scroll.bottom").getString()));
        }
    }

    private int renderRow(
            GuiGraphicsExtractor graphics,
            GuideUiRow row,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY) {
        if (row instanceof GuideUiRow.User user) {
            graphics.text(font, Component.translatable("screen.openallay.speaker.user"),
                    x, y, ACCENT, false);
            renderCopyAction(graphics, row, user.text(), x, y, width);
            renderForkAction(graphics, user.requestId(), user.text(), x, y, width);
            y += 11;
            y = renderWrapped(graphics, GuideMarkup.paragraphs(user.text()), x + 6, y, width - 6, TEXT);
            return y + rowSpacing();
        }
        if (row instanceof GuideUiRow.Assistant assistant) {
            graphics.fill(x, y, x + 2, y + 9, ACCENT);
            graphics.text(font, assistantLabel(projectedDisplay, assistant.streaming()),
                    x + 6, y, ACCENT, false);
            renderCopyAction(graphics, row, assistant.text(), x, y, width);
            if (completedAssistantBoundary(service.snapshot(), view.selectedSession(), assistant)) {
                renderForkAction(graphics, assistant.requestId(), assistant.text(), x, y, width);
            }
            y += 11;
            if (assistant.text().isBlank()) {
                graphics.text(font, Component.translatable(
                                "screen.openallay.assistant.preparing"),
                        x + 6, y, MUTED, false);
                y += 10;
            } else {
                SemanticLayout semantic = semanticLayout(assistant, width - 6);
                MinecraftSemanticRenderer.Result rendered = semanticRenderer.render(
                        graphics, font, semantic, x + 6, y, width - 6,
                        mouseX, mouseY, projectedDisplay.animationsEnabled(),
                        presentationTicks,
                        (nativeGraphics, nativeFont, component, bounds,
                                nativeMouseX, nativeMouseY, ticks) -> renderNativeRecipe(
                                        assistant,
                                        nativeGraphics,
                                        nativeFont,
                                        component,
                                        bounds,
                                        nativeMouseX,
                                        nativeMouseY,
                                        ticks));
                int nodeY = y;
                for (SemanticLayout.Line line : semantic.lines()) {
                    String nodeKey = rowId(assistant) + ":node:" + line.nodeId();
                    GuideUiLayout.Rect previous = renderedRows.get(nodeKey);
                    int top = previous == null ? nodeY : previous.y();
                    renderedRows.put(nodeKey, new GuideUiLayout.Rect(x + 6, top, width - 6, nodeY + line.height() - top));
                    nodeY += line.height();
                }
                for (MinecraftSemanticRenderer.Hit hit : rendered.hits()) {
                    String focusId = "semantic:" + rowId(assistant) + ":" + hit.intent();
                    hits.add(new Hit(
                            hit.bounds(), HitKind.CONTENT,
                            () -> semanticIntent(hit.intent()), focusId,
                            semanticIntentNarration(hit.intent())));
                }
                y = rendered.bottom();
            }
            List<GuideEvidencePresentation.Group> sourceGroups = groupedSources(assistant.sources());
            for (int sourceIndex = 0; sourceIndex < sourceGroups.size(); sourceIndex++) {
                GuideEvidencePresentation.Group source = sourceGroups.get(sourceIndex);
                int sourceY = y;
                String label = sourceLabel(source, projectedDisplay.debugMode());
                String sourceFocusId = sourceFocusId(assistant, source, sourceIndex);
                boolean selected = isFocused(selectedSourceFocusId, sourceFocusId);
                if (selected) {
                    graphics.fill(x + 4, sourceY - 2, x + width - 4, sourceY + 10, 0xFF31453F);
                }
                graphics.text(font, label, x + 6, sourceY, ACCENT, false);
                hits.add(new Hit(new GuideUiLayout.Rect(x + 4, sourceY - 2, width - 8, 12),
                        HitKind.CONTENT, () -> open(source, sourceFocusId),
                        sourceFocusId, label));
                y += 12;
            }
            return y + rowSpacing();
        }
        if (row instanceof GuideUiRow.Tool tool) {
            return renderToolStepCard(graphics, tool, x, y, width, mouseX, mouseY);
        }
        int color = row instanceof GuideUiRow.Persistence persistence
                ? persistence.state() == dev.openallay.guide.GuidePersistenceSnapshot.State.UNAVAILABLE
                        ? 0xFFFFD479 : MUTED
                : ((GuideUiRow.Status) row).status() == GuideRequestStatus.RATE_LIMITED
                        ? 0xFFFFD479 : ERROR;
        List<FormattedCharSequence> lines = font.split(factualRowText(row), Math.max(1, width - 12));
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, x + 6, y, color, false);
            y += 10;
        }
        if (row instanceof GuideUiRow.Status status && (status.status() == GuideRequestStatus.FAILED
                || status.status() == GuideRequestStatus.CANCELLED || status.status() == GuideRequestStatus.INTERRUPTED)) {
            GuideUiLayout.Rect retryRow = new GuideUiLayout.Rect(x + 6, y, Math.min(90, width - 12), 14);
            boundedHeaderText(graphics, Component.translatable("screen.openallay.action.retry"), retryRow, ACCENT);
            hits.add(new Hit(retryRow, HitKind.CONTENT, () -> accept(service.retry(status.requestId()), ignored -> notice = GuideUiNotice.info("")),
                    "retry:" + status.requestId(), Component.translatable("screen.openallay.action.retry").getString()));
            y += 16;
        }
        return y + rowSpacing();
    }

    static Component factualRowText(GuideUiRow row) {
        if (row instanceof GuideUiRow.Persistence persistence) {
            Component message = Component.translatable(persistence.translationKey());
            return persistence.failure() == null ? message
                    : message.copy().append(" (" + persistence.failure().code() + ")");
        }
        GuideUiRow.Status status = (GuideUiRow.Status) row;
        return status.status() == GuideRequestStatus.INTERRUPTED
                ? Component.translatable("screen.openallay.history.interrupted")
                : Component.literal(status.text());
    }

    private void updateVirtualRows(int width) {
        List<GuideUiRow.Tool> tools = view.rows().stream().filter(GuideUiRow.Tool.class::isInstance)
                .map(GuideUiRow.Tool.class::cast).toList();
        ToolFlowOwner nextOwner = toolFlowOwner();
        boolean changedOwner = toolStepFolds.synchronize(
                nextOwner, tools, projectedDisplay.ui().fullscreen().toolsCollapsed());
        toolStepOwner = nextOwner;
        if (changedOwner) {
            toolStepBodies.keySet().forEach(semanticLayouts::invalidateRow);
            toolStepBodies.clear();
            nativeViews.clear();
        }
        ArrayList<GuideTranscriptVirtualizer.Row> measured = new ArrayList<>();
        Map<String, Integer> nextHashes = new LinkedHashMap<>();
        HashSet<String> retainedIds = new HashSet<>();
        stableRowHeights.begin(width);
        for (GuideUiRow row : view.rows()) {
            String id = rowId(row);
            retainedIds.add(id);
            if (row instanceof GuideUiRow.Assistant assistant) {
                int hash = assistant.semantic().hashCode();
                nextHashes.put(id, hash);
                if (!java.util.Objects.equals(semanticHashes.get(id), hash)) {
                    semanticLayouts.invalidateRow(id);
                }
            }
            boolean stabilize = row instanceof GuideUiRow.Assistant assistant
                    && assistant.streaming();
            measured.add(new GuideTranscriptVirtualizer.Row(
                    id, stableRowHeights.retain(id, measureRow(row, width), stabilize)));
        }
        stableRowHeights.retainOnly(retainedIds);
        toolStepBodies.keySet().removeIf(id -> {
            if (retainedIds.contains(id)) return false;
            semanticLayouts.invalidateRow(id);
            return true;
        });
        semanticHashes.clear();
        semanticHashes.putAll(nextHashes);
        virtualizer.update(measured);
    }

    private int measureRow(GuideUiRow row, int width) {
        if (row instanceof GuideUiRow.User user) {
            return 11 + wrappedHeight(GuideMarkup.paragraphs(user.text()), width - 6) + rowSpacing();
        }
        if (row instanceof GuideUiRow.Assistant assistant) {
            int body = assistant.text().isBlank()
                    ? 10 : semanticLayout(assistant, width - 6).height();
            return 11 + body + groupedSources(assistant.sources()).size() * 12 + rowSpacing();
        }
        if (row instanceof GuideUiRow.Tool tool) {
            return toolStepGeometry(tool, 0, 0, width).rowHeight();
        }
        int retryHeight = row instanceof GuideUiRow.Status status && (status.status() == GuideRequestStatus.FAILED
                || status.status() == GuideRequestStatus.CANCELLED || status.status() == GuideRequestStatus.INTERRUPTED) ? 16 : 0;
        return font.split(factualRowText(row), Math.max(1, width - 12)).size() * 10 + rowSpacing() + retryHeight;
    }

    private ToolStepBody toolStepBody(GuideUiRow.Tool tool) {
        String id = toolFocusId(tool);
        String locale = java.util.Locale.getDefault().toLanguageTag() + ":"
                + System.identityHashCode(net.minecraft.locale.Language.getInstance());
        ToolStepBody cached = toolStepBodies.get(id);
        if (cached != null && cached.detail().equals(tool.detail()) && cached.locale().equals(locale)) return cached;
        List<Component> messages = new ArrayList<>(toolFailureComponents(tool.detail(), tool.activity().toolId()));
        if (dev.openallay.guide.ui.GuideToolStepFlowPresenter.partial(tool)) {
            messages.add(Component.translatable("screen.openallay.tools.step.preview"));
        }
        dev.openallay.guide.ui.GuideToolStepFlowPresenter.messages(tool).stream()
                .map(OpenAllayScreen::toolMessage).forEach(messages::add);
        var nativeBody = dev.openallay.guide.ui.hud.GuideHudToolCards.project(
                tool, key -> Component.translatable(key).getString());
        ToolStepBody body = new ToolStepBody(tool.detail(), locale, nativeBody, List.copyOf(messages));
        toolStepBodies.put(id, body);
        semanticLayouts.invalidateRow(id);
        return body;
    }

    private SemanticLayout toolStepBodyLayout(GuideUiRow.Tool tool, int width) {
        return semanticLayouts.get(
                toolFocusId(tool), toolStepBody(tool).nativeBody().document(), Math.max(1, width),
                toolStepBody(tool).locale(), font.getClass().getName() + ":" + System.identityHashCode(font),
                new SemanticLayoutEngine.Measurer() {
                    @Override public int width(String text, SemanticLayout.Style style) {
                        return font.width(Component.literal(text).withStyle(switch (style) {
                            case EMPHASIS -> ChatFormatting.ITALIC;
                            case STRONG -> ChatFormatting.BOLD;
                            case CODE -> ChatFormatting.GRAY;
                            case REFERENCE -> ChatFormatting.AQUA;
                            case NORMAL -> ChatFormatting.WHITE;
                        }));
                    }
                    @Override public int lineHeight(SemanticLayout.Kind kind) {
                        return kind == SemanticLayout.Kind.HEADING ? 12 : 10;
                    }
                });
    }

    private dev.openallay.guide.ui.GuideToolStepFlowGeometry toolStepGeometry(
            GuideUiRow.Tool tool, int x, int y, int width) {
        int titleWidth = dev.openallay.guide.ui.GuideToolStepFlowGeometry.titleWidth(width);
        int titleHeight = Math.max(10, font.split(toolTitle(tool.activity()), titleWidth).size() * 10);
        int statusHeight = Math.max(10, font.split(toolCardStatus(tool.detail().displayStatus()), titleWidth).size() * 10);
        boolean expanded = toolExpanded(tool);
        int bodyWidth = dev.openallay.guide.ui.GuideToolStepFlowGeometry.bodyWidth(width);
        int bodyHeight = expanded ? wrappedHeight(toolStepBody(tool).messages(), bodyWidth)
                + toolStepBodyLayout(tool, bodyWidth).height() : 0;
        return dev.openallay.guide.ui.GuideToolStepFlowGeometry.measure(
                x, y, width, titleHeight, statusHeight, bodyHeight, expanded, rowSpacing());
    }

    private int renderToolStepCard(
            GuiGraphicsExtractor graphics, GuideUiRow.Tool tool, int x, int y,
            int width, int mouseX, int mouseY) {
        var geometry = toolStepGeometry(tool, x, y, width);
        GuideUiLayout.Rect card = geometry.card();
        boolean selected = selectedTool != null && toolFocusId(selectedTool).equals(toolFocusId(tool));
        int border = selected ? ACCENT : OpenAllayWidgetTheme.SLATE_BORDER;
        renderToolStepFrame(graphics, card, panelAltColor(), border);
        renderToolStepLines(graphics, toolTitle(tool.activity()), geometry.title(), TEXT);
        int statusColor = switch (tool.detail().displayStatus()) {
            case FAILED -> ERROR;
            case SUCCEEDED -> OpenAllayWidgetTheme.SUCCESS;
            case RUNNING -> ACCENT;
            case NO_RESULT_RECORDED -> MUTED;
        };
        Component status = toolCardStatus(tool.detail().displayStatus());
        if (tool.detail().displayStatus() == GuideToolDisplayStatus.RUNNING
                && projectedDisplay.animationsEnabled() && (presentationTicks / 8) % 2 == 0) {
            status = Component.literal("◍ ").append(Component.translatable(tool.detail().displayStatus().translationKey()));
        }
        renderToolStepLines(graphics, status, geometry.status(), statusColor);
        boolean expanded = toolExpanded(tool);
        String rowId = toolFocusId(tool);
        Component toggleLabel = Component.translatable(expanded
                ? "screen.openallay.tools.step.collapse" : "screen.openallay.tools.step.expand");
        renderToolStepControl(graphics, geometry.toggle(), expanded ? "▲" : "▼", toggleLabel,
                rowId + ":collapse", () -> toggleToolStep(tool, false), mouseX, mouseY);
        if (firstTool(tool) && taskTools(tool.requestId()).size() > 1) {
            boolean anyExpanded = taskTools(tool.requestId()).stream().anyMatch(this::toolExpanded);
            Component label = Component.translatable(anyExpanded
                    ? "screen.openallay.tools.task.collapse" : "screen.openallay.tools.task.expand");
            renderToolStepControl(graphics, geometry.batch(), anyExpanded ? "−" : "+", label,
                    rowId + ":task-collapse", () -> toggleToolStep(tool, true), mouseX, mouseY);
        }
        // The title opens complete detail. Neither the toggle nor the native body routes through it.
        toolStepHit(geometry.title(), () -> open(tool), rowId, toolTitle(tool.activity()).getString());
        if (expanded) {
            GuideUiLayout.Rect bodyBounds = geometry.body();
            ToolStepBody body = toolStepBody(tool);
            renderToolStepBody(graphics, tool, body.nativeBody(),
                    toolStepBodyLayout(tool, bodyBounds.width()), bodyBounds.x(), bodyBounds.y(),
                    bodyBounds.width(), mouseX, mouseY);
            renderToolStepLines(graphics, Component.translatable("screen.openallay.detail.title"),
                    geometry.detail(), ACCENT);
            toolStepHit(geometry.detail(), () -> open(tool), rowId + ":detail",
                    Component.translatable("screen.openallay.detail.title").getString());
        }
        // Consume inert slots and card padding after real native actions. Never click into the game.
        toolStepHit(card, () -> {}, null, "");
        return y + geometry.rowHeight();
    }

    private int renderToolStepBody(
            GuiGraphicsExtractor graphics, GuideUiRow.Tool tool,
            dev.openallay.guide.ui.hud.GuideHudToolCards.Projection body,
            SemanticLayout bodyLayout, int x, int y, int width, int mouseX, int mouseY) {
        for (Component message : toolStepBody(tool).messages()) {
            for (FormattedCharSequence line : font.split(message, Math.max(1, width))) {
                graphics.text(font, line, x, y, tool.detail().failure().isPresent() ? ERROR : MUTED, false);
                y += 10;
            }
        }
        int nativeBodyTop = y;
        MinecraftSemanticRenderer.Result rendered = semanticRenderer.render(
                graphics, font, bodyLayout, x, nativeBodyTop, width, mouseX, mouseY,
                projectedDisplay.animationsEnabled(), presentationTicks,
                (nativeGraphics, nativeFont, component, bounds, nativeMouseX, nativeMouseY, ticks) -> {
                    GuideRecipeCard recipe = body.recipes().get(component.nodeId());
                    if (recipe == null || !intersects(bounds, layout.transcript())) return false;
                    NativeDomainViewBinding.Recipe binding = new NativeDomainViewBinding.Recipe(
                            toolFocusId(tool) + ":card:" + component.nodeId(), component, recipe);
                    boolean painted = nativeViews.render(binding, new NativeDomainView.RenderContext(
                            nativeGraphics, nativeFont, bounds, nativeMouseX, nativeMouseY, ticks));
                    if (painted && Boolean.getBoolean("openallay.e2e.enabled")) {
                        renderedResultCardIds.add(binding.stableId());
                    }
                    return painted;
                });
        // Keep actual item icons inside restrained native slots, without copying the renderer.
        int lineY = nativeBodyTop;
        for (SemanticLayout.Line line : bodyLayout.lines()) {
            if (line.component() instanceof dev.openallay.guide.semantic.RichComponent.ItemRow items) {
                for (int item = 0; item < items.items().size(); item++) {
                    graphics.outline(x + line.indent() + 1, lineY + item * 22 - 1,
                            18, 18, OpenAllayWidgetTheme.SLATE_BORDER);
                }
            }
            if (Boolean.getBoolean("openallay.e2e.enabled")
                    && line.kind() != SemanticLayout.Kind.RULE
                    && !(line.component() instanceof dev.openallay.guide.semantic.RichComponent.RecipeGrid)) {
                int lineWidth = line.table() != null ? line.table().width() : Math.max(1, width - line.indent());
                GuideUiLayout.Rect paintedNode = new GuideUiLayout.Rect(
                        x + line.indent(), lineY, lineWidth, line.height());
                String cardId = toolFocusId(tool) + ":card:" + line.nodeId();
                if (intersects(paintedNode, layout.transcript()) && !renderedResultCardIds.contains(cardId)) {
                    renderedResultCardIds.add(cardId);
                }
            }
            lineY += line.height();
        }
        // Receipts include only the native typed result layout drawn above, never message narration.
        for (MinecraftSemanticRenderer.Hit hit : rendered.hits()) {
            toolStepHit(hit.bounds(), () -> semanticIntent(hit.intent()),
                    toolFocusId(tool) + ":native:" + hit.intent(), semanticIntentNarration(hit.intent()));
        }
        return rendered.bottom();
    }

    private void toolStepHit(GuideUiLayout.Rect bounds, Runnable action, String id, String narration) {
        GuideUiLayout.Rect viewport = layout.transcript();
        int left = Math.max(bounds.x(), viewport.x());
        int top = Math.max(bounds.y(), viewport.y());
        int right = Math.min(bounds.right(), viewport.right());
        int bottom = Math.min(bounds.bottom(), viewport.bottom());
        if (right > left && bottom > top) {
            hits.add(new Hit(new GuideUiLayout.Rect(left, top, right - left, bottom - top),
                    HitKind.CONTENT, action, id, narration));
        }
    }

    private void renderToolStepControl(
            GuiGraphicsExtractor graphics, GuideUiLayout.Rect bounds, String icon,
            Component label, String id, Runnable action, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY) || isFocused(focusedContentId, id);
        if (hovered) renderToolStepFrame(graphics, bounds, panelColor(), ACCENT);
        graphics.text(font, icon, bounds.x() + (bounds.width() - font.width(icon)) / 2,
                bounds.y() + 4, hovered ? ACCENT : MUTED, false);
        if (bounds.contains(mouseX, mouseY) && layout.transcript().contains(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font, label, mouseX, mouseY);
        }
        toolStepHit(bounds, action, id, label.getString());
    }

    private void renderToolStepLines(
            GuiGraphicsExtractor graphics, Component text, GuideUiLayout.Rect bounds, int color) {
        int y = bounds.y();
        for (FormattedCharSequence line : font.split(text, Math.max(1, bounds.width()))) {
            graphics.text(font, line, bounds.x(), y, color, false);
            y += 10;
        }
    }

    private static void renderToolStepFrame(
            GuiGraphicsExtractor graphics, GuideUiLayout.Rect bounds, int fill, int border) {
        int x = bounds.x(), y = bounds.y(), right = bounds.right(), bottom = bounds.bottom();
        // Two-pixel clipped corners keep the expert's rounded sub-card treatment native to Minecraft.
        graphics.fill(x + 2, y, right - 2, bottom, border);
        graphics.fill(x, y + 2, right, bottom - 2, border);
        graphics.fill(x + 2, y + 1, right - 2, bottom - 1, fill);
        graphics.fill(x + 1, y + 2, right - 1, bottom - 2, fill);
    }

    private List<FormattedCharSequence> toolSummaryLines(GuideUiRow.Tool tool, int width) {
        ArrayList<FormattedCharSequence> result = new ArrayList<>();
        for (Component message : toolSummaryComponents(tool.activity(), tool.detail())) {
            for (FormattedCharSequence wrapped : font.split(message, Math.max(1, width))) {
                result.add(wrapped);
                if (result.size() == 3) return List.copyOf(result);
            }
        }
        return List.copyOf(result);
    }

    static List<Component> toolSummaryComponents(GuideToolActivity activity) {
        return toolSummaryComponents(activity, GuideToolDisplayStatus.from(activity.status(), false));
    }

    static List<Component> toolSummaryComponents(
            GuideToolActivity activity, GuideToolDisplayStatus status) {
        GuideToolDetailView detail = dev.openallay.guide.ui.GuideToolDetailPresenter.project(activity, false);
        return toolSummaryComponents(activity, detail.forRequest(status == GuideToolDisplayStatus.NO_RESULT_RECORDED));
    }

    private static List<Component> toolSummaryComponents(GuideToolActivity activity, GuideToolDetailView detail) {
        ArrayList<Component> summary = new ArrayList<>();
        GuideToolDisplayStatus status = detail.displayStatus();
        boolean actualFailure = detail.failure().isPresent();
        if (actualFailure) summary.addAll(toolFailureComponents(detail, activity.toolId()));
        boolean javascript = activity.toolId().endsWith(":run_javascript");
        List<GuideToolMessage> messages = javascript && status == GuideToolDisplayStatus.SUCCEEDED
                && !actualFailure && activity.normalized() != null
                ? detail.narration() : activity.presentationMessages();
        for (GuideToolMessage message : messages) {
            if (actualFailure && message.key().name().startsWith("FAILURE_")) continue;
            if ((actualFailure || status == GuideToolDisplayStatus.FAILED)
                    && message.key().name().startsWith("ANALYSIS_")) continue;
            if (javascript && message.key() == GuideToolMessage.Key.INVOCATION_RUN_JAVASCRIPT) continue;
            if (status == GuideToolDisplayStatus.NO_RESULT_RECORDED
                    && message.key() == GuideToolMessage.Key.RESULT_PENDING) continue;
            summary.add(friendlyToolMessage(activity.toolId(), message));
        }
        if (javascript) summary.add(toolDescription(activity.intent()));
        return List.copyOf(summary);
    }

    static List<Component> toolFailureComponents(GuideToolDetailView detail, String toolId) {
        if (detail.failure().isPresent()) {
            GuideToolDetailView.Failure failure = detail.failure().orElseThrow();
            List<Component> lines = new ArrayList<>();
            if (!failure.code().isBlank()) lines.add(Component.literal(failure.code()));
            if (!failure.message().isBlank()) lines.add(Component.literal(failure.message()));
            if (!lines.isEmpty()) return List.copyOf(lines);
        }
        if (detail.displayStatus() != GuideToolDisplayStatus.FAILED) return List.of();
        return detail.narration().stream().map(message -> friendlyToolMessage(toolId, message)).toList();
    }

    private static Component friendlyToolMessage(String toolId, GuideToolMessage message) {
        if ("openallay:run_javascript".equals(toolId)
                && message.key() == GuideToolMessage.Key.FAILURE_GENERIC) {
            return Component.translatable("screen.openallay.tool.failure.javascript");
        }
        return toolMessage(message);
    }

    /** Preview scope must stay visible above every native result card, not only text fallbacks. */
    static List<GuideToolMessage> toolResultMessages(GuideToolDetailView detail) {
        return detail.failure().isPresent()
                || detail.displayStatus() == GuideToolDisplayStatus.FAILED
                || detail.displayStatus() == GuideToolDisplayStatus.NO_RESULT_RECORDED
                ? List.of() : detail.narration();
    }

    static List<GuideToolMessage> visibleToolSummaryMessages(
            List<GuideToolMessage> messages) {
        return messages.stream()
                .limit(3)
                .toList();
    }

    static Component toolMessage(GuideToolMessage message) {
        Object[] arguments = message.arguments().stream()
                .map(Component::literal)
                .toArray();
        return Component.translatable(message.key().translationKey(), arguments);
    }

    static int toolCardHeight(int visibleSummaryLines) {
        if (visibleSummaryLines < 0 || visibleSummaryLines > 3) {
            throw new IllegalArgumentException("visible Tool summary line count must be 0..3");
        }
        return 21 + visibleSummaryLines * 10;
    }

    private int wrappedHeight(List<Component> paragraphs, int width) {
        int height = 0;
        for (Component paragraph : paragraphs) {
            List<FormattedCharSequence> lines = font.split(paragraph, Math.max(1, width));
            height += Math.max(1, lines.size()) * 10;
        }
        return height;
    }

    private SemanticLayout semanticLayout(GuideUiRow.Assistant assistant, int width) {
        return semanticLayouts.get(
                rowId(assistant), assistant.semantic(), Math.max(1, width),
                java.util.Locale.getDefault().toLanguageTag(), font.getClass().getName(),
                new SemanticLayoutEngine.Measurer() {
                    @Override public int width(String text, SemanticLayout.Style style) {
                        return font.width(Component.literal(text).withStyle(switch (style) {
                            case EMPHASIS -> ChatFormatting.ITALIC;
                            case STRONG -> ChatFormatting.BOLD;
                            case CODE -> ChatFormatting.GRAY;
                            case REFERENCE -> ChatFormatting.AQUA;
                            case NORMAL -> ChatFormatting.WHITE;
                        }));
                    }
                    @Override public int lineHeight(SemanticLayout.Kind kind) {
                        return kind == SemanticLayout.Kind.HEADING ? 12 : 10;
                    }
                });
    }

    private static String rowId(GuideUiRow row) {
        return switch (row) {
            case GuideUiRow.Persistence value -> "persistence:" + value.state();
            case GuideUiRow.User value -> "user:" + value.requestId();
            case GuideUiRow.Assistant value ->
                    "assistant:" + value.requestId() + ":" + value.ordinal();
            case GuideUiRow.Tool value ->
                    "tool:" + value.requestId() + ":" + value.activity().invocationId();
            case GuideUiRow.Status value -> "status:" + value.requestId();
        };
    }

    private void requestViewportHistory(GuideUiLayout.Rect area) {
        GuideSessionSnapshot session = service.snapshot().sessions().stream()
                .filter(value -> value.sessionId().equals(view.selectedSession()))
                .findFirst().orElse(null);
        // Paging mutates the head of the transcript. Defer every paging direction while a
        // request is active so streaming deltas cannot race a history replacement and move the
        // visible conversation between the top and bottom of the viewport.
        if (!mayPageHistory(
                session != null,
                view.progress() != null || session != null && session.workingRequestId() != null,
                session == null ? null : session.historyWindow().state(),
                session == null ? 0 : session.historyWindow().totalRequests())) return;
        int count = Math.max(1, area.height() / 20 * 2);
        if (session.requests().isEmpty()) {
            service.requestHistoryWindow(
                    session.sessionId(), GuideHistoryPageRequest.Direction.NEWEST, null, count);
        } else if (scroll <= 32 && session.historyWindow().hasEarlier()
                && session.historyWindow().firstLoaded() != null) {
            service.requestHistoryWindow(
                    session.sessionId(), GuideHistoryPageRequest.Direction.BEFORE,
                    session.historyWindow().firstLoaded(), count);
        } else if (scroll >= virtualizer.maximumScroll(transcriptViewportHeight()) - 32
                && session.historyWindow().hasLater() && session.historyWindow().lastLoaded() != null) {
            service.requestHistoryWindow(session.sessionId(), GuideHistoryPageRequest.Direction.AFTER,
                    session.historyWindow().lastLoaded(), count);
        }
    }

    static boolean mayPageHistory(
            boolean sessionPresent,
            boolean requestRunning,
            GuideHistoryPageState state,
            long totalRequests) {
        return sessionPresent
                && !requestRunning
                && state == GuideHistoryPageState.IDLE
                && totalRequests > 0;
    }

    private void semanticIntent(MinecraftSemanticRenderer.Intent intent) {
        switch (intent) {
            case MinecraftSemanticRenderer.Intent.BrowseRecipes value ->
                    navigate(recipeClient.openRecipes(value.itemId()));
            case MinecraftSemanticRenderer.Intent.BrowseUsages value ->
                    navigate(recipeClient.openUsages(value.itemId()));
            case MinecraftSemanticRenderer.Intent.ExactRecipe value ->
                    navigate(recipeClient.openExact(value.reference()));
            case MinecraftSemanticRenderer.Intent.Source value -> openSemanticSource(
                    value.sourceId(), value.originInvocationId());
            case MinecraftSemanticRenderer.Intent.Evidence value -> openSemanticSource(
                    value.evidenceId(), value.originInvocationId());
            case MinecraftSemanticRenderer.Intent.Choice value -> notice = GuideUiNotice.info(
                    Component.translatable("screen.openallay.choice.unavailable", value.choiceId()).getString());
        }
    }

    private static String semanticIntentNarration(MinecraftSemanticRenderer.Intent intent) {
        return switch (intent) {
            case MinecraftSemanticRenderer.Intent.BrowseRecipes value ->
                    "查看 " + value.itemId() + " 的配方";
            case MinecraftSemanticRenderer.Intent.BrowseUsages value ->
                    "查看 " + value.itemId() + " 的用途";
            case MinecraftSemanticRenderer.Intent.ExactRecipe value ->
                    "打开配方 " + value.reference().recipeId();
            case MinecraftSemanticRenderer.Intent.Source value ->
                    "查看来源 " + value.sourceId();
            case MinecraftSemanticRenderer.Intent.Evidence value ->
                    "查看证据 " + value.evidenceId();
            case MinecraftSemanticRenderer.Intent.Choice value ->
                    "选择 " + value.choiceId();
        };
    }

    private static boolean intersects(GuideUiLayout.Rect first, GuideUiLayout.Rect second) {
        return first.x() < second.x() + second.width()
                && first.x() + first.width() > second.x()
                && first.y() < second.y() + second.height()
                && first.y() + first.height() > second.y();
    }

    private void openSemanticSource(String sourceId, String invocationId) {
        GuideSource source = view.rows().stream()
                .flatMap(row -> switch (row) {
                    case GuideUiRow.Assistant assistant -> assistant.sources().stream();
                    case GuideUiRow.Tool tool -> tool.activity().invocationId().equals(invocationId)
                            ? tool.activity().sources().stream() : java.util.stream.Stream.empty();
                    default -> java.util.stream.Stream.empty();
                })
                .filter(value -> value.evidence().sourceId().equals(sourceId))
                .findFirst().orElse(null);
        if (source != null) open(source);
    }

    private int renderWrapped(
            GuiGraphicsExtractor graphics, List<Component> paragraphs, int x, int y, int width, int color) {
        for (Component paragraph : paragraphs) {
            List<FormattedCharSequence> lines = font.split(paragraph, width);
            if (lines.isEmpty()) y += 9;
            for (FormattedCharSequence line : lines) {
                if (y >= layout.transcript().y() - 12
                        && y <= layout.transcript().y() + layout.transcript().height() + 12) {
                    graphics.text(font, line, x, y, color, false);
                }
                y += 10;
            }
        }
        return y;
    }

    static Component assistantLabel(GuideDisplayConfig display, boolean streaming) {
        Objects.requireNonNull(display, "display");
        return streaming
                ? Component.literal(display.assistantName())
                        .append(" · ")
                        .append(Component.translatable(
                                "screen.openallay.speaker.assistant_thinking"))
                : Component.literal(display.assistantName());
    }

    private void renderDetail(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!detailOpen()) return;
        hits.removeIf(hit -> hit.kind() == HitKind.DETAIL);
        GuideUiLayout.Rect detail = layout.detail();
        graphics.fill(detail.x(), detail.y(), detail.x() + detail.width(), detail.y() + detail.height(), 0xF02A303A);
        graphics.outline(detail.x(), detail.y(), detail.width(), detail.height(), ACCENT);
        graphics.text(font, Component.translatable("screen.openallay.detail.title"),
                detail.x() + 8, detail.y() + 8, ACCENT, false);
        GuideUiLayout.Rect close = detailCloseBounds();
        graphics.fill(close.x(), close.y(), close.right(), close.bottom(), panelAltColor());
        graphics.text(font, "×", close.x() + 5, close.y() + 4, TEXT, false);
        if (isFocused(focusedContentId, "detail:close")) {
            graphics.outline(close.x(), close.y(), close.width(), close.height(), ACCENT);
        }
        hits.add(new Hit(close, HitKind.DETAIL, this::closeDetail, "detail:close",
                Component.translatable("screen.openallay.detail.close").getString()));
        graphics.enableScissor(
                detail.x() + 1,
                detail.y() + 21,
                detail.x() + detail.width() - 1,
                detail.y() + detail.height() - 1);
        int y = detail.y() + 26 - detailScroll;
        if (selectedTool != null) {
            GuideToolDetailView toolDetail = selectedTool.detail();
            y = detailLine(graphics,
                    Component.translatable("screen.openallay.detail.tool.status").getString()
                            + ": " + Component.translatable(toolDetail.displayStatus().translationKey()).getString(),
                    detail, y);
            for (DetailSection section : toolDetailSections(toolDetail)) {
                switch (section) {
                    case RESULT -> {
                        for (Component reason : toolFailureComponents(toolDetail, selectedTool.activity().toolId())) {
                            y = detailLine(graphics, reason.copy().withStyle(ChatFormatting.RED), detail, y);
                        }
                        if (toolDetail.failure().isEmpty()) {
                            y = detailLine(graphics, Component.translatable("screen.openallay.detail.output"), detail, y + 4);
                        }
                        for (GuideToolMessage message : toolResultMessages(toolDetail)) {
                            y = detailLine(graphics, toolMessage(message), detail, y);
                        }
                        for (int cardIndex = 0; cardIndex < toolDetail.cards().size(); cardIndex++) {
                            GuideDetailCard card = toolDetail.cards().get(cardIndex);
                            int cardTop = y;
                            y = detailCard(graphics, card, detail, y, mouseX, mouseY);
                            if (Boolean.getBoolean("openallay.e2e.enabled") && visibleDetail(cardTop, y - cardTop, detail)) {
                                renderedResultCardIds.add(toolFocusId(selectedTool) + ":card:" + cardIndex
                                        + ":" + card.getClass().getSimpleName());
                            }
                        }
                    }
                    case PROGRAM -> {
                        y = detailDisclosure(graphics, Component.translatable("screen.openallay.detail.program"), detail, y + 4, "program");
                        if (expandedDetails.contains("program")) {
                            y = detailCode(graphics, toolProgram(toolDetail), detail, y, "javascript-source");
                        }
                    }
                    case INTENT -> {
                        y = detailLine(graphics, Component.translatable("screen.openallay.tool.intent.label"), detail, y + 4);
                        y = detailLine(graphics, intentTitle(toolDetail.intent(), toolDetail.titleKey()), detail, y);
                        if (selectedTool.activity().toolId().endsWith(":run_javascript")) {
                            y = detailLine(graphics, toolDescription(toolDetail.intent()), detail, y);
                        }
                    }
                    case SOURCES -> {
                        y = sourceGroups(graphics, selectedTool.activity().sources(), detail, y + 4);
                    }
                    case DEBUG -> {
                        y = detailDisclosure(graphics, Component.translatable("screen.openallay.debug.section"),
                                detail, y + 4, "debug-result");
                        if (expandedDetails.contains("debug-result")) {
                            GuideToolDetailView.Debug debug = toolDetail.debug().orElseThrow();
                            y = detailValues(graphics, "screen.openallay.detail.input.handles",
                                    toolDetail.invocation().handles(), detail, y);
                            y = detailValues(graphics, "screen.openallay.detail.input.modules",
                                    toolDetail.invocation().modules(), detail, y);
                            y = detailLine(graphics, "invocationId: " + debug.invocationId(), detail, y);
                            y = detailLine(graphics, "toolId: " + debug.toolId(), detail, y);
                            y = detailLine(graphics, "recordedToolStatus: " + toolDetail.status(), detail, y);
                            if (!debug.validationDiagnostic().isBlank()) {
                                y = detailLine(graphics, "validation: " + debug.validationDiagnostic(), detail, y);
                            }
                            if (debug.invocationArguments() != null) {
                                y = detailLine(graphics, Component.translatable("screen.openallay.detail.input"), detail, y);
                                y = detailCode(graphics, DEBUG_GSON.toJson(debug.invocationArguments()),
                                        detail, y, "invocation-arguments");
                            }
                            if (debug.normalized() != null) {
                                if (!selectedTool.activity().sources().isEmpty()) {
                                    y = detailLine(graphics, Component.translatable("screen.openallay.evidence.shown_separately"), detail, y);
                                }
                                y = detailLine(graphics, Component.translatable("screen.openallay.debug.normalized_result"), detail, y);
                                y = detailCode(graphics, DEBUG_GSON.toJson(debug.normalized()), detail, y, "normalized-result");
                            }
                        }
                    }
                }
            }
        } else if (selectedSource != null) {
            y = sourceGroup(graphics, selectedSource, detail, y, "selected-source");
        }
        detailContentHeight = Math.max(0, y + detailScroll - detail.y());
        graphics.disableScissor();
    }

    enum DetailSection { RESULT, PROGRAM, INTENT, SOURCES, DEBUG }

    /** Used by the renderer, so source metadata cannot precede the actual result or program. */
    static List<DetailSection> toolDetailSections(GuideToolDetailView detail) {
        List<DetailSection> sections = new ArrayList<>();
        sections.add(DetailSection.RESULT);
        if (!toolProgram(detail).isBlank()) sections.add(DetailSection.PROGRAM);
        sections.add(DetailSection.INTENT);
        if (detail.debug().isPresent()) sections.add(DetailSection.DEBUG);
        sections.add(DetailSection.SOURCES);
        return List.copyOf(sections);
    }

    static String toolProgram(GuideToolDetailView detail) {
        if (detail.debug().isEmpty()) return "";
        GuideToolDetailView.Debug debug = detail.debug().orElseThrow();
        JsonObject arguments = debug.invocationArguments();
        return debug.toolId().endsWith(":run_javascript") && arguments != null
                && arguments.has("source") && arguments.get("source").isJsonPrimitive()
                ? arguments.get("source").getAsString() : "";
    }

    private int detailDisclosure(
            GuiGraphicsExtractor graphics, Component label, GuideUiLayout.Rect detail, int y, String id) {
        Component text = Component.literal(expandedDetails.contains(id) ? "▼ " : "▶ ").append(label);
        int bottom = detailLine(graphics, text, detail, y);
        if (visibleDetail(y, bottom - y, detail)) {
            int top = Math.max(y, detail.y() + 21);
            int visibleBottom = Math.min(bottom, detail.bottom());
            GuideUiLayout.Rect bounds = new GuideUiLayout.Rect(detail.x() + 4, top,
                    detail.width() - 8, Math.max(0, visibleBottom - top));
            String focusId = "detail:" + id;
            if (isFocused(focusedContentId, focusId)) {
                graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), ACCENT);
            }
            hits.add(new Hit(bounds, HitKind.DETAIL, () -> {
                if (!expandedDetails.remove(id)) expandedDetails.add(id);
                hits.removeIf(hit -> hit.kind() == HitKind.DETAIL && !"detail:close".equals(hit.focusId()));
            }, focusId, text.getString()));
        }
        return bottom;
    }

    private int detailCard(
            GuiGraphicsExtractor graphics,
            GuideDetailCard card,
            GuideUiLayout.Rect detail,
            int y,
            int mouseX,
            int mouseY) {
        return switch (card) {
            case GuideDetailCard.Recipe recipe ->
                    recipeCard(graphics, recipe.recipe(), detail, y, mouseX, mouseY);
            case GuideDetailCard.ItemGrid grid ->
                    itemGridCard(graphics, grid, detail, y, mouseX, mouseY);
            case GuideDetailCard.Requirements requirements ->
                    requirementsCard(graphics, requirements, detail, y, mouseX, mouseY);
            case GuideDetailCard.Table table ->
                    tableCard(graphics, table, detail, y);
            case GuideDetailCard.KeyValue keyValue ->
                    keyValueCard(graphics, keyValue, detail, y);
            case GuideDetailCard.DataPreview preview ->
                    dataPreviewCard(graphics, preview, detail, y);
            case GuideDetailCard.Text text -> textCard(graphics, text, detail, y);
            case GuideDetailCard.Error error -> errorCard(graphics, error, detail, y);
        };
    }

    private int tableCard(
            GuiGraphicsExtractor graphics,
            GuideDetailCard.Table card,
            GuideUiLayout.Rect detail,
            int y) {
        int start = y;
        y = detailLine(graphics, Component.translatable(card.titleKey()).getString(), detail, y);
        String header = String.join("  │  ", card.columns());
        y = detailLine(graphics, header, detail, y);
        for (List<String> row : card.rows()) {
            y = detailLine(graphics, String.join("  │  ", row), detail, y);
        }
        return Math.max(y, start + 25);
    }

    private int keyValueCard(
            GuiGraphicsExtractor graphics,
            GuideDetailCard.KeyValue card,
            GuideUiLayout.Rect detail,
            int y) {
        int start = y;
        y = detailLine(graphics, Component.translatable(card.titleKey()).getString(), detail, y);
        for (GuideDetailCard.DataCell entry : card.entries()) {
            y = detailLine(graphics, entry.key() + ": " + entry.value(), detail, y);
        }
        return Math.max(y, start + 25);
    }

    private int dataPreviewCard(
            GuiGraphicsExtractor graphics,
            GuideDetailCard.DataPreview card,
            GuideUiLayout.Rect detail,
            int y) {
        int start = y;
        y = detailLine(graphics, Component.translatable(card.titleKey()).getString(), detail, y);
        for (GuideDetailCard.DataRow row : card.rows()) {
            String line = row.cells().stream()
                    .map(cell -> cell.key() + ": " + cell.value())
                    .collect(java.util.stream.Collectors.joining(" · "));
            y = detailLine(graphics, line, detail, y);
        }
        return Math.max(y, start + 25);
    }

    private int itemGridCard(
            GuiGraphicsExtractor graphics,
            GuideDetailCard.ItemGrid card,
            GuideUiLayout.Rect detail,
            int y,
            int mouseX,
            int mouseY) {
        int columns = Math.max(1, (detail.width() - 24) / 22);
        int rows = (card.items().size() + columns - 1) / columns;
        int height = 22 + rows * 22;
        int left = detail.x() + 6;
        if (visibleDetail(y, height, detail)) {
            graphics.fill(left, y, detail.x() + detail.width() - 6, y + height, panelAltColor());
            graphics.text(font, Component.translatable(card.titleKey()), left + 7, y + 6, TEXT, false);
            for (int index = 0; index < card.items().size(); index++) {
                int itemX = left + 7 + (index % columns) * 22;
                int itemY = y + 19 + (index / columns) * 22;
                renderItem(graphics, card.items().get(index), itemX, itemY, mouseX, mouseY);
            }
        }
        return y + height + 5;
    }

    private int requirementsCard(
            GuiGraphicsExtractor graphics,
            GuideDetailCard.Requirements card,
            GuideUiLayout.Rect detail,
            int y,
            int mouseX,
            int mouseY) {
        int height = 31 + Math.max(1, card.requirements().size()) * 31;
        int left = detail.x() + 6;
        if (visibleDetail(y, height, detail)) {
            graphics.fill(left, y, detail.x() + detail.width() - 6, y + height, panelAltColor());
            String state = card.craftable()
                    ? Component.translatable("screen.openallay.craftability.ready").getString()
                    : Component.translatable("screen.openallay.craftability.missing").getString();
            graphics.text(font, state, left + 7, y + 6, card.craftable() ? 0xFF7FC8A9 : 0xFFFFD479, false);
            graphics.text(font,
                    Component.translatable(
                            "screen.openallay.craftability.maximum", card.maximumCrafts()),
                    left + 7,
                    y + 17,
                    MUTED,
                    false);
            int rowY = y + 31;
            for (GuideDetailCard.Requirement requirement : card.requirements()) {
                graphics.text(font,
                        requirement.key() + "  " + requirement.allocated() + "/" + requirement.required(),
                        left + 7,
                        rowY + 2,
                        requirement.missing() == 0 ? TEXT : 0xFFFFD479,
                        false);
                int itemX = left + 7;
                for (GuideItemView item : requirement.allocatedItems()) {
                    renderItem(graphics, item, itemX, rowY + 12, mouseX, mouseY);
                    itemX += 20;
                }
                if (requirement.missing() > 0) {
                    graphics.text(font,
                            Component.translatable(
                                    "screen.openallay.craftability.need", requirement.missing()),
                            Math.max(itemX + 3, left + 98),
                            rowY + 17,
                            ERROR,
                            false);
                }
                rowY += 31;
            }
        }
        return y + height + 5;
    }

    private int textCard(
            GuiGraphicsExtractor graphics,
            GuideDetailCard.Text card,
            GuideUiLayout.Rect detail,
            int y) {
        int start = y;
        y = detailLine(graphics, Component.translatable(card.titleKey()).getString(), detail, y);
        for (String line : card.lines()) y = detailLine(graphics, line, detail, y);
        return Math.max(y, start + 25);
    }

    private int errorCard(
            GuiGraphicsExtractor graphics,
            GuideDetailCard.Error card,
            GuideUiLayout.Rect detail,
            int y) {
        return detailLine(graphics, card.message(), detail, y);
    }

    private int recipeCard(
            GuiGraphicsExtractor graphics,
            GuideRecipeCard card,
            GuideUiLayout.Rect detail,
            int y,
            int mouseX,
            int mouseY) {
        boolean canBrowse = recipeClient.canBrowse();
        int ingredientCount = card.ingredients().size() + card.catalysts().size();
        int materialRows = ingredientCount == 0 ? 0 : (ingredientCount + 7) / 8;
        int byproductRows = card.byproducts().isEmpty() ? 0 : (card.byproducts().size() + 7) / 8;
        int height = (canBrowse ? 62 : 74) + materialRows * 23 + byproductRows * 23;
        if (visibleDetail(y, height, detail)) {
            int left = detail.x() + 6;
            int right = detail.x() + detail.width() - 6;
            graphics.fill(left, y, right, y + height, panelAltColor());
            graphics.outline(left, y, right - left, height, 0xFF46515F);
            GuideRecipeCard.Output output = card.outputs().getFirst();
            ItemStack stack = itemStack(output);
            if (!stack.isEmpty()) {
                graphics.item(stack, left + 7, y + 7);
                graphics.itemDecorations(font, stack, left + 7, y + 7);
                if (mouseX >= left + 7 && mouseX < left + 23
                        && mouseY >= y + 7 && mouseY < y + 23) {
                    graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                }
            }
            graphics.text(font, output.displayName(), left + 29, y + 7, TEXT, false);
            if (!card.workstation().isBlank()) {
                graphics.text(font,
                        Component.translatable("screen.openallay.recipe.workstation", card.workstation()),
                        left + 29,
                        y + 20,
                        MUTED,
                        false);
            }
            int rowY = y + 34;
            int materialIndex = 0;
            for (GuideRecipeCard.Ingredient ingredient : card.ingredients()) {
                GuideItemView item = ingredientItem(ingredient);
                renderItem(graphics, item, left + 7 + (materialIndex % 8) * 22,
                        rowY + (materialIndex / 8) * 23, mouseX, mouseY);
                materialIndex++;
            }
            for (GuideRecipeCard.Ingredient catalyst : card.catalysts()) {
                GuideItemView item = ingredientItem(catalyst);
                renderItem(graphics, item, left + 7 + (materialIndex % 8) * 22,
                        rowY + (materialIndex / 8) * 23, mouseX, mouseY);
                materialIndex++;
            }
            rowY += materialRows * 23;
            for (int index = 0; index < card.byproducts().size(); index++) {
                GuideRecipeCard.Output outputView = card.byproducts().get(index);
                renderItem(graphics,
                        new GuideItemView(outputView.itemId(), outputView.displayName(), outputView.count()),
                        left + 7 + (index % 8) * 22,
                        rowY + (index / 8) * 23,
                        mouseX,
                        mouseY);
            }
            int actionY = y + 44 + materialRows * 23 + byproductRows * 23;
            int actionX = left + 7;
            actionX = recipeAction(
                    graphics,
                    Component.translatable("screen.openallay.recipe.recipes"),
                    actionX,
                    actionY,
                    canBrowse,
                    () -> navigate(recipeClient.openRecipes(output.itemId())));
            actionX = recipeAction(
                    graphics,
                    Component.translatable("screen.openallay.recipe.usages"),
                    actionX + 7,
                    actionY,
                    canBrowse,
                    () -> navigate(recipeClient.openUsages(output.itemId())));
            var exact = card.references().stream().filter(recipeClient::supportsExact).findFirst();
            recipeAction(
                    graphics,
                    Component.translatable(exact.isPresent()
                            ? "screen.openallay.recipe.open_exact"
                            : "screen.openallay.recipe.open_exact_unavailable"),
                    actionX + 7,
                    actionY,
                    exact.isPresent(),
                    () -> navigate(recipeClient.openExact(exact.orElseThrow())));
            if (!canBrowse) {
                graphics.text(
                        font,
                        Component.translatable("screen.openallay.recipe.viewer_unavailable"),
                        left + 7,
                        actionY + 14,
                        0xFFFFD479,
                        false);
            }
        }
        return y + height + 5;
    }

    private int recipeAction(
            GuiGraphicsExtractor graphics,
            Component label,
            int x,
            int y,
            boolean enabled,
            Runnable action) {
        int width = font.width(label) + 8;
        int color = enabled ? ACCENT : 0xFF6E7782;
        graphics.fill(x, y - 2, x + width, y + 10, enabled ? 0xFF29443F : 0xFF30343A);
        graphics.text(font, label, x + 4, y, color, false);
        GuideUiLayout.Rect detail = layout.detail();
        if (enabled && y - 2 >= detail.y() + 21 && y + 10 <= detail.bottom()) {
            GuideUiLayout.Rect bounds = new GuideUiLayout.Rect(x, y - 2, width, 12);
            String focusId = "detail:action:" + label.getString() + ":" + x + ":" + (y + detailScroll);
            if (isFocused(focusedContentId, focusId)) {
                graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), ACCENT);
            }
            hits.add(new Hit(bounds, HitKind.DETAIL, action, focusId, label.getString()));
        }
        return x + width;
    }

    private static ItemStack itemStack(GuideRecipeCard.Output output) {
        return itemStack(output.itemId(), output.count());
    }

    private static ItemStack itemStack(String itemId, long count) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.getValue(id),
                (int) Math.min(Integer.MAX_VALUE, Math.max(1, count)));
    }

    private static GuideItemView ingredientItem(GuideRecipeCard.Ingredient ingredient) {
        GuideRecipeCard.Alternative alternative = ingredient.alternatives().getFirst();
        String itemId = alternative.resolvedItems().isEmpty()
                ? alternative.id()
                : alternative.resolvedItems().getFirst();
        return new GuideItemView(itemId, itemId, ingredient.count());
    }

    private void renderItem(
            GuiGraphicsExtractor graphics,
            GuideItemView item,
            int x,
            int y,
            int mouseX,
            int mouseY) {
        ItemStack stack = itemStack(item.itemId(), item.count());
        if (stack.isEmpty()) {
            graphics.text(font, "?", x + 5, y + 4, MUTED, false);
            return;
        }
        graphics.item(stack, x, y);
        graphics.itemDecorations(font, stack, x, y);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
    }

    private static boolean visibleDetail(int y, int height, GuideUiLayout.Rect detail) {
        return y + height >= detail.y() + 21 && y <= detail.y() + detail.height();
    }

    private void navigate(RecipeNavigationResult result) {
        notice = GuideUiNotice.info(result.opened()
                ? Component.translatable("screen.openallay.recipe.viewer_opened").getString()
                : navigationFailure(result));
    }

    private static String navigationFailure(RecipeNavigationResult result) {
        String key = switch (result.code()) {
            case "exact_unsupported" -> "screen.openallay.recipe.exact_unsupported";
            case "preferred_viewer_unavailable" ->
                    "screen.openallay.recipe.preferred_viewer_unavailable";
            case "viewer_unavailable" -> "screen.openallay.recipe.viewer_unavailable";
            case "unknown_item" -> "screen.openallay.recipe.unknown_item";
            case "wrong_thread" -> "screen.openallay.recipe.wrong_thread";
            case "viewer_failure" -> "screen.openallay.recipe.viewer_failure";
            default -> null;
        };
        return key == null
                ? result.code() + ": " + result.message()
                : Component.translatable(key).getString();
    }

    private List<GuideEvidencePresentation.Group> groupedSources(List<GuideSource> sources) {
        return sourceGroupCache.computeIfAbsent(sources, GuideEvidencePresentation::groups);
    }

    private int sourceGroups(
            GuiGraphicsExtractor graphics, List<GuideSource> sources, GuideUiLayout.Rect detail, int y) {
        if (sources.isEmpty()) return y;
        List<GuideEvidencePresentation.Group> groups = groupedSources(sources);
        y = detailDisclosure(graphics, Component.translatable("screen.openallay.evidence.groups",
                groups.size()), detail, y, "sources");
        if (expandedDetails.contains("sources")) {
            for (int index = 0; index < groups.size(); index++) {
                y = sourceGroup(graphics, groups.get(index), detail, y, "source-group:" + index);
            }
        }
        return y;
    }

    private int sourceGroup(
            GuiGraphicsExtractor graphics, GuideEvidencePresentation.Group group,
            GuideUiLayout.Rect detail, int y, String id) {
        y = detailDisclosure(graphics, Component.literal(sourceLabel(group, projectedDisplay.debugMode())),
                detail, y, id);
        if (!expandedDetails.contains(id)) return y;
        int width = Math.max(1, detail.width() - 16);
        String locale = minecraft.getLanguageManager().getSelected();
        SourceDetailLayout cached = sourceDetailLayouts.get(id);
        if (cached == null || !cached.matches(group, width, locale)) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component component : sourceDetailComponents(group)) {
                lines.addAll(font.split(component, width));
            }
            cached = new SourceDetailLayout(group, width, locale, lines);
            sourceDetailLayouts.put(id, cached);
        }
        VisibleDetailLines visible = visibleDetailLines(detail, y, cached.lines().size());
        for (int index = visible.first(); index < visible.end(); index++) {
            graphics.text(font, cached.lines().get(index), detail.x() + 8, y + index * 10, TEXT, false);
        }
        return y + cached.lines().size() * 10 + 2;
    }

    /** Built only when a retained group, width, or locale changes; never clipped or capped. */
    static List<Component> sourceDetailComponents(GuideEvidencePresentation.Group group) {
        List<Component> lines = new ArrayList<>();
        GuideEvidencePresentation evidence = group.presentation();
        lines.add(Component.translatable(evidence.authorityKey()));
        lines.add(Component.translatable("screen.openallay.evidence.coverage",
                Component.translatable(evidence.coverageKey())));
        lines.add(Component.translatable("screen.openallay.evidence.capture_range",
                group.firstCapturedAt().toString(), group.lastCapturedAt().toString()));
        var identity = group.identity();
        lines.add(Component.literal("toolId: " + identity.toolId()));
        lines.add(Component.literal("sourceId: " + identity.sourceId()));
        lines.add(Component.literal("provenance: " + identity.provenance()));
        lines.add(Component.literal("gameVersion: " + identity.gameVersion()));
        lines.add(Component.literal("loader: " + identity.loader()));
        for (var entry : identity.scope().entrySet()) {
            lines.add(Component.literal(entry.getKey() + ": " + entry.getValue()));
        }
        // Shared scope is already above. Every retained observation-specific value stays available.
        for (GuideSource record : group.records()) {
            lines.add(Component.translatable("screen.openallay.evidence.capture_range",
                    new dev.openallay.context.SourceObservation(
                            record.evidence(), record.lastCapturedAt()).firstCapturedAt().toString(),
                    record.lastCapturedAt().toString()));
            for (var entry : record.evidence().details().entrySet()) {
                if (!identity.scope().containsKey(entry.getKey())) {
                    lines.add(Component.literal(entry.getKey() + ": " + entry.getValue()));
                }
            }
        }
        return List.copyOf(lines);
    }

    record SourceDetailLayout(
            GuideEvidencePresentation.Group group, int width, String locale, List<FormattedCharSequence> lines) {
        SourceDetailLayout { lines = List.copyOf(lines); }

        boolean matches(GuideEvidencePresentation.Group current, int currentWidth, String currentLocale) {
            // The groups are immutable. Identity avoids comparing thousands of retained records each frame.
            return group == current && width == currentWidth && locale.equals(currentLocale);
        }
    }

    record VisibleDetailLines(int first, int end) {}

    static VisibleDetailLines visibleDetailLines(GuideUiLayout.Rect detail, int y, int count) {
        int first = Math.clamp(Math.ceilDiv(detail.y() + 21 - y, 10), 0, count);
        int end = Math.clamp(Math.ceilDiv(detail.bottom() - 10 - y, 10), first, count);
        return new VisibleDetailLines(first, end);
    }

    static String formatCapturedAt(Instant capturedAt, java.util.Locale locale, java.time.ZoneId zone) {
        return java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT)
                .withLocale(locale).withZone(zone).format(capturedAt);
    }

    private int detailLine(GuiGraphicsExtractor graphics, String text, GuideUiLayout.Rect detail, int y) {
        return detailLine(graphics, Component.literal(text), detail, y);
    }

    private int detailLine(
            GuiGraphicsExtractor graphics, Component text, GuideUiLayout.Rect detail, int y) {
        for (FormattedCharSequence line : font.split(text, detail.width() - 16)) {
            if (y >= detail.y() + 21 && y < detail.y() + detail.height() - 10) {
                graphics.text(font, line, detail.x() + 8, y, TEXT, false);
            }
            y += 10;
        }
        return y + 2;
    }

    private int detailCode(
            GuiGraphicsExtractor graphics,
            String source,
            GuideUiLayout.Rect detail,
            int y,
            String cacheId) {
        int width = detail.width() - 16;
        CodeLayout cached = detailCodeLayouts.get(cacheId);
        if (cached == null || cached.width() != width || !cached.source().equals(source)) {
            String[] sourceLines = source.split("\\R", -1);
            int digits = Integer.toString(Math.max(1, sourceLines.length)).length();
            List<FormattedCharSequence> wrapped = new ArrayList<>();
            for (int index = 0; index < sourceLines.length; index++) {
                String prefix = String.format("%" + digits + "d │ ", index + 1);
                wrapped.addAll(font.split(Component.literal(prefix + sourceLines[index]), width));
            }
            cached = new CodeLayout(source, width, List.copyOf(wrapped));
            detailCodeLayouts.put(cacheId, cached);
        }
        int first = Math.max(0, (detail.y() + 21 - y) / 10);
        int last = Math.min(
                cached.lines().size(),
                Math.max(first, (detail.y() + detail.height() - y + 9) / 10));
        for (int index = first; index < last; index++) {
            int lineY = y + index * 10;
            if (lineY >= detail.y() + 21 && lineY < detail.y() + detail.height() - 10) {
                graphics.text(
                        font,
                        cached.lines().get(index),
                        detail.x() + 8,
                        lineY,
                        TEXT,
                        false);
            }
        }
        return y + cached.lines().size() * 10 + 2;
    }

    private int detailValues(
            GuiGraphicsExtractor graphics,
            String labelKey,
            List<String> values,
            GuideUiLayout.Rect detail,
            int y) {
        if (values.isEmpty()) {
            return y;
        }
        return detailLine(
                graphics,
                Component.translatable(labelKey).getString() + ": " + String.join(", ", values),
                detail,
                y);
    }

    private GuideUiLayout.Rect detailCloseBounds() {
        GuideUiLayout.Rect detail = layout.detail();
        return new GuideUiLayout.Rect(detail.right() - 24, detail.y() + 3, 20, 16);
    }

    private void closeDetail() {
        selectedTool = null;
        selectedSource = null;
        selectedSourceFocusId = null;
        detailScroll = 0;
        expandedDetails.clear();
        focusedContentId = null;
        detailCodeLayouts.clear();
        sourceDetailLayouts.clear();
        rebuildForDetail();
    }

    private void focusDetail() {
        focusedContentId = "detail:close";
        clearFocus();
    }

    private void open(GuideUiRow.Tool tool) {
        selectedTool = tool;
        selectedSource = null;
        selectedSourceFocusId = null;
        detailScroll = 0;
        expandedDetails.clear();
        detailCodeLayouts.clear();
        sourceDetailLayouts.clear();
        rebuildForDetail();
        focusDetail();
    }

    private void open(GuideSource source) {
        GuideEvidencePresentation.Identity identity = GuideEvidencePresentation.Identity.from(source);
        GuideEvidencePresentation.Group group = view.rows().stream()
                .filter(GuideUiRow.Tool.class::isInstance).map(GuideUiRow.Tool.class::cast)
                .filter(tool -> tool.activity().sources().contains(source))
                .flatMap(tool -> groupedSources(tool.activity().sources()).stream())
                .filter(value -> value.identity().equals(identity)).findFirst()
                .orElseGet(() -> GuideEvidencePresentation.groups(List.of(source)).getFirst());
        open(group, "source-detail:" + identity);
    }

    private void open(GuideEvidencePresentation.Group source, String focusId) {
        selectedSource = source;
        selectedSourceFocusId = Objects.requireNonNull(focusId, "focusId");
        selectedTool = null;
        detailScroll = 0;
        expandedDetails.clear();
        expandedDetails.add("selected-source");
        detailCodeLayouts.clear();
        sourceDetailLayouts.clear();
        rebuildForDetail();
        focusDetail();
    }

    private record CodeLayout(
            String source, int width, List<FormattedCharSequence> lines) {}

    private void rebuildPresentationWidgets() {
        boolean composerFocused = composer != null && getFocused() == composer;
        String contentFocus = focusedContentId;
        rebuildWidgets();
        focusedContentId = contentFocus;
        if (composerFocused) setFocused(composer);
    }

    private void rebuildForDetail() {
        GuideViewportAnchor anchor = layout == null ? null : virtualizer.anchorAt(scroll);
        boolean shouldFollow = followBottom;
        draft = composer == null ? draft : composer.getValue();
        rebuildPresentationWidgets();
        restoreTranscript(anchor, shouldFollow);
    }

    private boolean detailOpen() {
        return selectedTool != null || selectedSource != null;
    }

    private boolean refreshDetail(GuideUiView next) {
        boolean wasOpen = detailOpen();
        if (selectedTool != null) {
            GuideUiRow.Tool replacement = next.rows().stream()
                    .filter(GuideUiRow.Tool.class::isInstance)
                    .map(GuideUiRow.Tool.class::cast)
                            .filter(value -> toolFocusId(value).equals(toolFocusId(selectedTool)))
                    .findFirst().orElse(null);
            selectedTool = replacement;
        }
        if (selectedSource != null) {
            boolean retained = next.rows().stream().anyMatch(row -> switch (row) {
                case GuideUiRow.Assistant assistant -> groupedSources(assistant.sources()).contains(selectedSource);
                case GuideUiRow.Tool tool -> groupedSources(tool.activity().sources()).contains(selectedSource);
                default -> false;
            });
            if (!retained) {
                selectedSource = null;
                selectedSourceFocusId = null;
            }
        }
        if (!wasOpen) return false;
        if (!detailOpen()) return true;
        if (!next.selectedSession().equals(view.selectedSession())) {
            selectedTool = null;
            selectedSource = null;
            selectedSourceFocusId = null;
            detailScroll = 0;
            return true;
        }
        return false;
    }

    private void applyPendingProjection() {
        GuideSnapshot pending = pendingSnapshots.drain();
        GuideDisplayConfig nextDisplay = currentDisplay();
        if (pending == null && nextDisplay.equals(projectedDisplay)) return;
        GuideSnapshot snapshot = pending == null ? service.snapshot() : pending;
        applyProjection(GuideUiView.from(snapshot, nextDisplay), nextDisplay);
    }

    private void applyProjection(GuideUiView next, GuideDisplayConfig nextDisplay) {
        // Keep immutable retained groups across streaming updates; discard only lists no longer present.
        Map<List<GuideSource>, Boolean> retainedSources = new java.util.IdentityHashMap<>();
        for (GuideUiRow row : next.rows()) {
            switch (row) {
                case GuideUiRow.Assistant assistant -> retainedSources.put(assistant.sources(), true);
                case GuideUiRow.Tool tool -> retainedSources.put(tool.activity().sources(), true);
                default -> { }
            }
        }
        sourceGroupCache.keySet().removeIf(sources -> !retainedSources.containsKey(sources));
        boolean changedSession = !view.selectedSession().equals(next.selectedSession());
        if (changedSession) {
            uiState.setText(view.selectedSession(), composer == null ? draft : composer.getValue());
            uiState.selectSession(next.selectedSession());
            draft = uiState.readText(next.selectedSession());
            if (attachment != null) attachment.selectSession(next.selectedSession());
            pendingCursor = 0;
            imageScroll = 0;
            submittingDraft = false;
            syncComposerTextures();
        }
        GuideViewportAnchor anchor = layout == null ? null : virtualizer.anchorAt(scroll);
        boolean shouldFollow = followBottom;
        boolean closedDetail = refreshDetail(next);
        if (closedDetail || changedSession) sourceDetailLayouts.clear();
        boolean changedChrome = projectedDisplay.ui().fullscreen().sessionRailVisible() != nextDisplay.ui().fullscreen().sessionRailVisible();
        if (changedChrome) railVisible = nextDisplay.ui().fullscreen().sessionRailVisible();
        projectedDisplay = nextDisplay;
        view = next;
        if (view.modelChoices().isEmpty()) {
            modelSelectorOpen = false;
            modelSelectorCursor = 0;
            modelSelectorScroll = 0;
        } else {
            modelSelectorCursor = Mth.clamp(
                    modelSelectorCursor, 0, view.modelChoices().size() - 1);
        }
        if (changedSession) {
            expandedDetails.clear();
            scroll = 0;
            followBottom = true;
            semanticLayouts.clear();
            semanticHashes.clear();
            stableRowHeights.clear();
            nativeViews.clear();
        }
        if ((changedSession || closedDetail || changedChrome) && composer != null) {
            if (!changedSession) draft = composer.getValue();
            rebuildPresentationWidgets();
        } else if (layout != null) {
            updateVirtualRows(Math.max(40, layout.transcript().width() - 18));
        }
        restoreTranscript(changedSession ? null : anchor, changedSession || shouldFollow);
        updateControls();
    }

    private void restoreTranscript(GuideViewportAnchor anchor, boolean shouldFollow) {
        if (layout == null) return;
        int viewportHeight = transcriptViewportHeight();
        scroll = shouldFollow
                ? virtualizer.maximumScroll(viewportHeight)
                : virtualizer.restore(anchor, scroll, viewportHeight);
    }

    private int transcriptViewportHeight() {
        return layout == null ? 0 : Math.max(0, layout.transcript().height() - 14);
    }

    private int rowSpacing() {
        return projectedDisplay.ui().fullscreen().density() == dev.openallay.guide.ui.GuideUiConfig.Density.COMPACT ? 4 : 8;
    }
    private int panelColor() {
        return projectedDisplay.ui().fullscreen().theme() == dev.openallay.guide.ui.GuideUiConfig.Theme.MINT
                ? 0xFF162A27 : PANEL;
    }
    private int panelAltColor() {
        return projectedDisplay.ui().fullscreen().theme() == dev.openallay.guide.ui.GuideUiConfig.Theme.MINT
                ? 0xFF223D37 : PANEL_ALT;
    }

    private GuideDisplayConfig currentDisplay() {
        return Objects.requireNonNull(display.get(), "display config");
    }

    static GuideUiView project(
            GuideSnapshot snapshot, Supplier<GuideDisplayConfig> display) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(display, "display");
        return GuideUiView.from(
                snapshot, Objects.requireNonNull(display.get(), "display config"));
    }

    private void synchronizeComposerSession() {
        if (!composerImages.attached()) return;
        GuideSnapshot current = service.snapshot();
        if (!view.selectedSession().equals(current.selectedSession())) {
            applyProjection(GuideUiView.from(current, currentDisplay()), currentDisplay());
        }
    }

    private GuideClientUiState.DraftIntent draftIntent() { return uiState.intent(view.selectedSession()); }
    private UUID editingPending() { return draftIntent().pendingId(); }
    private boolean steerMode() { return draftIntent().steer(); }
    private void validatePendingEditTarget() {
        GuideClientUiState.DraftIntent intent = draftIntent();
        if (!intent.editing()) return;
        if (!intent.editInvalid() && !uiState.pendingEditSubmissionInFlight(view.selectedSession())
                && pendingMessages().stream().noneMatch(value -> value.id().equals(intent.pendingId()))) {
            uiState.invalidatePendingEdit(view.selectedSession(), intent.pendingId());
        }
        if (draftIntent().editInvalid()) notice = GuideUiNotice.warning(
                Component.translatable("screen.openallay.pending.edit_invalid").getString());
    }

    private boolean composerRequestActive() {
        return service.snapshot().sessions().stream()
                .filter(session -> session.sessionId().equals(view.selectedSession()))
                .anyMatch(session -> session.workingRequestId() != null);
    }

    private List<GuidePendingMessage> pendingMessages() {
        return service.pendingMessages(view.selectedSession());
    }

    private boolean hasComposerImagePreviews() {
        return composerImages.attachments().stream().anyMatch(image -> image.preview() != null || image.reference() != null);
    }

    private String currentComposerLayoutKey() {
        return hasComposerImagePreviews() + ":" + composerRequestActive() + ":" + pendingMessages().size()
                + ":" + (voice != null && voice.enabled());
    }

    private void refreshComposerLayout() {
        if (layout == null || composer == null || currentComposerLayoutKey().equals(composerLayoutKey)) return;
        draft = composer.getValue();
        rebuildForDetail();
    }

    private void sharedDraftChanged() {
        if (uiState.closed()) return;
        String sharedText = uiState.readText(view.selectedSession());
        if (!sharedText.equals(draft)) {
            draft = sharedText;
            if (composer != null && !composer.getValue().equals(sharedText)) composer.setValue(sharedText);
        }
        switch (view.selectedSession().equals(uiState.imageNoticeSession()) ? uiState.imageNotice() : ComposerImageDraft.Notice.NONE) {
            case CLIPBOARD_UNAVAILABLE -> notice = GuideUiNotice.error(Component.translatable("screen.openallay.image.clipboard_unavailable").getString());
            case IMPORT_FAILED -> notice = GuideUiNotice.error(Component.translatable("screen.openallay.image.import_failed").getString());
            case PROCESSING, READY, NONE -> { }
        }
        syncComposerTextures();
        refreshComposerLayout();
        updateControls();
    }

    private void syncComposerTextures() {
        if (minecraft == null || attachment == null || !composerImages.attached()) return;
        List<ComposerImageDraft.Attachment> images = composerImages.attachments();
        java.util.Set<UUID> retained = images.stream().map(ComposerImageDraft.Attachment::id)
                .collect(java.util.stream.Collectors.toSet());
        for (UUID id : new ArrayList<>(imageTextures.keySet())) {
            if (!retained.contains(id)) minecraft.getTextureManager().release(imageTextures.remove(id));
        }
        for (ComposerImageDraft.Attachment image : images) {
            if (image.preview() == null || imageTextures.containsKey(image.id())) continue;
            ClipboardImageEncoder.Preview preview = image.preview();
            NativeImage bitmap = new NativeImage(preview.width(), preview.height(), false);
            int[] pixels = preview.argb();
            for (int y = 0; y < preview.height(); y++) {
                for (int x = 0; x < preview.width(); x++) bitmap.setPixel(x, y, pixels[y * preview.width() + x]);
            }
            Identifier texture = Identifier.fromNamespaceAndPath("openallay", "composer/" + imageDraftOwner + "/" + image.id());
            minecraft.getTextureManager().register(texture, new DynamicTexture(() -> "OpenAllay draft image", bitmap));
            imageTextures.put(image.id(), texture);
        }
    }

    private void releaseComposerTextures() {
        if (minecraft != null) imageTextures.values().forEach(minecraft.getTextureManager()::release);
        imageTextures.clear();
    }

    private void renderComposerExtras(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        hits.removeIf(hit -> hit.kind() == HitKind.COMPOSER);
        if (composerExtras == null) return;
        GuideUiLayout.Rect strip = composerExtras.images();
        if (strip.height() > 0) {
            List<ComposerImageDraft.Attachment> images = composerImages.attachments();
            int cell = Math.min(46, strip.height());
            int visible = Math.max(1, strip.width() / (cell + 4));
            imageScroll = Mth.clamp(imageScroll, 0, Math.max(0, images.size() - visible));
            graphics.enableScissor(strip.x(), strip.y(), strip.right(), strip.bottom());
            for (int index = imageScroll; index < Math.min(images.size(), imageScroll + visible); index++) {
                ComposerImageDraft.Attachment image = images.get(index);
                int x = strip.x() + (index - imageScroll) * (cell + 4);
                graphics.fill(x, strip.y(), x + cell, strip.y() + cell, panelAltColor());
                Identifier texture = imageTextures.get(image.id());
                if (texture != null) {
                    double scale = Math.min((double) (cell - 2) / image.preview().width(),
                            (double) (cell - 2) / image.preview().height());
                    int imageWidth = Math.max(1, (int) (image.preview().width() * scale));
                    int imageHeight = Math.max(1, (int) (image.preview().height() * scale));
                    graphics.blit(texture, x + (cell - imageWidth) / 2, strip.y() + (cell - imageHeight) / 2,
                            imageWidth, imageHeight, 0, 1, 0, 1);
                } else graphics.text(font, image.pending() ? "…" : "▧", x + 4, strip.y() + 4, MUTED, false);
                GuideUiLayout.Rect remove = new GuideUiLayout.Rect(x + cell - 12, strip.y(), 12, 12);
                graphics.fill(remove.x(), remove.y(), remove.right(), remove.bottom(), 0xDD181B22);
                graphics.text(font, "×", remove.x() + 2, remove.y() + 1, TEXT, false);
                hits.add(new Hit(remove, HitKind.COMPOSER, () -> composerImages.remove(image.id()),
                        "remove-image:" + image.id(), Component.translatable("screen.openallay.image.remove").getString()));
                if (remove.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font,
                        Component.translatable("screen.openallay.image.remove"), mouseX, mouseY);
                else if (new GuideUiLayout.Rect(x, strip.y(), cell, cell).contains(mouseX, mouseY)) {
                    Component tooltip = image.pending() ? Component.translatable("screen.openallay.image.processing")
                            : Component.translatable("screen.openallay.image.attached", image.reference().width(), image.reference().height());
                    if (!image.pending()) tooltip = tooltip.copy().append(" · ").append(Component.translatable("screen.openallay.image.cost_unknown"));
                    graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                }
            }
            graphics.disableScissor();
        }
        renderPendingComposer(graphics, mouseX, mouseY);
    }

    private void renderPendingComposer(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        GuideUiLayout.Rect footer = composerExtras.footer();
        if (footer.height() == 0) return;
        List<GuidePendingMessage> pending = pendingMessages();
        boolean active = composerRequestActive();
        int modeWidth = active ? Math.min(64, footer.width() / 3) : 0;
        if (modeWidth > 0) {
            GuideUiLayout.Rect mode = new GuideUiLayout.Rect(footer.x(), footer.y(), modeWidth, footer.height());
            graphics.fill(mode.x(), mode.y(), mode.right() - 2, mode.bottom(), panelAltColor());
            boundedHeaderText(graphics, Component.translatable(steerMode() ? "screen.openallay.pending.steer" : "screen.openallay.pending.follow_up"), mode, ACCENT);
            hits.add(new Hit(mode, HitKind.COMPOSER, () -> uiState.setMode(view.selectedSession(), steerMode() ? GuideClientUiState.DraftMode.FOLLOW_UP : GuideClientUiState.DraftMode.STEER), "composer-mode",
                    Component.translatable("screen.openallay.pending.mode_description").getString()));
            if (mode.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font,
                    Component.translatable(steerMode() ? "screen.openallay.pending.steer_description" : "screen.openallay.pending.follow_up_description"), mouseX, mouseY);
        }
        if (pending.isEmpty()) return;
        pendingCursor = Mth.clamp(pendingCursor, 0, pending.size() - 1);
        GuideUiLayout.Rect first = new GuideUiLayout.Rect(footer.x() + modeWidth, footer.y(), footer.width() - modeWidth, footer.height());
        renderPendingRow(graphics, pending.get(pendingCursor), first, true, pending.size(), mouseX, mouseY);
        GuideUiLayout.Rect extra = composerExtras.pending();
        for (int row = 0; row < extra.height() / 20; row++) {
            int index = pendingCursor + row + 1;
            if (index >= pending.size()) break;
            renderPendingRow(graphics, pending.get(index), new GuideUiLayout.Rect(extra.x(), extra.y() + row * 20, extra.width(), 18),
                    false, pending.size(), mouseX, mouseY);
        }
    }

    private void renderPendingRow(GuiGraphicsExtractor graphics, GuidePendingMessage pending,
            GuideUiLayout.Rect area, boolean navigator, int count, int mouseX, int mouseY) {
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), panelAltColor());
        int textWidth = Math.max(0, area.width() - 42);
        GuideUiLayout.Rect text = new GuideUiLayout.Rect(area.x() + 2, area.y() + 3, textWidth, 12);
        Component label = Component.literal((navigator ? (pendingCursor + 1) + "/" + count + " " : "")
                + (pending.kind() == GuidePendingMessage.Kind.STEER ? "↪ " : "↳ ") + pending.text());
        boundedHeaderText(graphics, label, text, pending.failure() == null ? MUTED : ERROR);
        if (navigator) hits.add(new Hit(text, HitKind.COMPOSER, () -> pendingCursor = (pendingCursor + 1) % count,
                "pending-next", Component.translatable("screen.openallay.pending.next").getString()));
        GuideUiLayout.Rect edit = new GuideUiLayout.Rect(area.right() - 38, area.y(), 20, area.height());
        GuideUiLayout.Rect cancel = new GuideUiLayout.Rect(area.right() - 18, area.y(), 18, area.height());
        graphics.text(font, "✎", edit.x() + 4, edit.y() + 3, TEXT, false);
        graphics.text(font, "×", cancel.x() + 4, cancel.y() + 3, TEXT, false);
        hits.add(new Hit(edit, HitKind.COMPOSER, () -> editPending(pending), "pending-edit:" + pending.id(),
                Component.translatable("screen.openallay.pending.edit").getString()));
        hits.add(new Hit(cancel, HitKind.COMPOSER, () -> accept(service.cancelPending(pending.id()), ignored -> {
            uiState.invalidatePendingEdit(view.selectedSession(), pending.id());
            refreshComposerLayout();
        }), "pending-cancel:" + pending.id(), Component.translatable("screen.openallay.pending.cancel").getString()));
        if (area.contains(mouseX, mouseY)) {
            Component tooltip = Component.translatable(cancel.contains(mouseX, mouseY) ? "screen.openallay.pending.cancel"
                    : edit.contains(mouseX, mouseY) ? "screen.openallay.pending.edit" : "screen.openallay.pending.waiting", pending.text());
            if (pending.failure() != null) tooltip = tooltip.copy().append(" · ")
                    .append(pending.failure().code() + ": " + pending.failure().message());
            graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    private void editPending(GuidePendingMessage pending) {
        synchronizeComposerSession();
        if (!draft.isBlank() || !composerImages.empty()) {
            notice = GuideUiNotice.error(Component.translatable("screen.openallay.pending.draft_not_empty").getString());
            return;
        }
        uiState.beginPendingEdit(view.selectedSession(), pending.id(), pending.kind() == GuidePendingMessage.Kind.STEER
                ? GuideClientUiState.DraftMode.STEER : GuideClientUiState.DraftMode.FOLLOW_UP);
        draft = pending.message().content().stream().filter(ModelContent.Text.class::isInstance)
                .map(ModelContent.Text.class::cast).map(ModelContent.Text::text)
                .collect(java.util.stream.Collectors.joining("\n"));
        composer.setValue(draft);
        uiState.setText(view.selectedSession(), draft);
        composerImages.restore(pending.message().content().stream().filter(ModelContent.Image.class::isInstance)
                .map(ModelContent.Image.class::cast).map(ModelContent.Image::reference).toList());
        loadRestoredImagePreviews();
        setFocused(composer);
        updateControls();
    }

    private void loadRestoredImagePreviews() {
        String session = view.selectedSession();
        long generation = composerImages.generation();
        for (ComposerImageDraft.Attachment image : composerImages.attachments()) {
            if (image.reference() == null || image.preview() != null) continue;
            service.readImage(image.reference()).whenComplete((result, failure) -> {
                if (failure != null || !(result instanceof ToolResult.Success<byte[]> bytes)) return;
                IMAGE_EXECUTOR.execute(() -> {
                    try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new java.io.ByteArrayInputStream(bytes.value()))) {
                        var bitmap = javax.imageio.ImageIO.read(input);
                        if (bitmap == null) return;
                        ClipboardImageEncoder.Preview preview = ClipboardImageEncoder.encode(bitmap).preview();
                        minecraft.execute(() -> {
                            synchronizeComposerSession();
                            if (!composerImages.attached() || generation != composerImages.generation()
                                    || !session.equals(view.selectedSession())) return;
                            composerImages.preview(image.id(), preview);
                            syncComposerTextures();
                        });
                    } catch (java.io.IOException ignored) { }
                });
            });
        }
    }

    private void submit() {
        synchronizeComposerSession();
        if (composer == null || submittingDraft || uiState.intentSubmissionInFlight(view.selectedSession()) || minecraft.player == null) return;
        String commandText = composer.getValue();
        GuideClientUiState.Insertion commandRevision = uiState.captureInsertion(view.selectedSession());
        ComposerImageDraft.Submission commandScope = composerImages.captureSubmission();
        var dispatch = draftIntent().editing()
                ? new dev.openallay.guide.composer.SlashCommandDispatcher.Dispatch(false, true, commandText)
                : dev.openallay.guide.composer.SlashCommandDispatcher.dispatch(commandText, service,
                completion -> minecraft.execute(() -> {
                    if (uiState.closed()) return;
                    if (completion.successful()) uiState.clearAcceptedText(commandRevision, commandText);
                    if (attachment == null || !commandScope.session().equals(view.selectedSession())) return;
                    synchronizeComposerSession();
                    notice = completion.successful() ? GuideUiNotice.success(slashCompletionNotice(completion).getString())
                            : GuideUiNotice.error(slashCompletionNotice(completion).getString());
                    if (!composerImages.empty()) notice = GuideUiNotice.info(notice.message() + " · " + Component.translatable(
                            "openallay.guide.slash.attachments_retained").getString());
                    sharedDraftChanged();
                    updateControls();
                }));
        if (dispatch.handled()) return;
        if (composerImages.pending()) return;
        String question = dispatch.normalizedText().trim();
        if (question.isEmpty() && composerImages.empty()) return;
        if (!composerImages.empty() && view.selectedImageInputCapability() != ImageInputCapability.SUPPORTED) {
            notice = GuideUiNotice.error(Component.translatable("screen.openallay.image.model_unsupported").getString());
            return;
        }
        boolean active = composerRequestActive();
        validatePendingEditTarget();
        if (draftIntent().editInvalid()) return;
        if (editingPending() == null && !active && !view.canSend()) return;
        ModelMessage message = ModelMessage.userInput(question, composerImages.references());
        ComposerImageDraft.Submission images = composerImages.captureSubmission();
        GuideClientUiState.Insertion revision = uiState.captureInsertion(view.selectedSession());
        String capturedText = composer.getValue();
        UUID pendingId = editingPending();
        GuideClientUiState.IntentCapture capturedIntent = uiState.captureIntent(view.selectedSession());
        if (!uiState.beginIntentSubmission(capturedIntent)) return;
        submittingDraft = true;
        CompletableFuture<? extends ToolResult<?>> future;
        try {
            future = switch (GuideClientUiState.submissionRoute(capturedIntent.intent(), active)) {
            case EDIT_PENDING -> service.editPending(pendingId, message);
            case STEER -> service.steer(message);
            case FOLLOW_UP -> service.followUp(message);
            case ASK -> service.ask(message);
            case EDIT_INVALID -> throw new IllegalStateException("Invalid edit cannot be submitted");
            };
        } catch (RuntimeException dispatchFailed) {
            uiState.completeIntentSubmission(capturedIntent);
            submittingDraft = false;
            notice = GuideUiNotice.error(Component.translatable("screen.openallay.composer.submit_failed").getString());
            updateControls();
            return;
        }
        future.whenComplete((result, failure) -> minecraft.execute(() -> {
            try {
                if (uiState.closed()) return;
                boolean accepted = failure == null && submissionAccepted(pendingId != null, result);
                if (accepted) {
                    // Clear captured text before image removal increments the shared revision.
                    uiState.clearAcceptedText(revision, capturedText);
                    uiState.clearAcceptedIntent(capturedIntent);
                    composerImages.accepted(images);
                } else if (failure == null && pendingId != null && result instanceof ToolResult.Success<?>) {
                    uiState.invalidatePendingEdit(capturedIntent);
                }
                if (attachment == null || !images.session().equals(view.selectedSession())) return;
                synchronizeComposerSession();
                submittingDraft = false;
                if (failure != null) {
                    notice = GuideUiNotice.error(Component.translatable("screen.openallay.composer.submit_failed").getString());
                } else if (accepted) {
                    notice = GuideUiNotice.info("");
                } else if (pendingId != null && result instanceof ToolResult.Success<?>) {
                    notice = GuideUiNotice.warning(Component.translatable("screen.openallay.pending.already_consumed").getString());
                } else if (result instanceof ToolResult.Failure<?> rejected) {
                    notice = GuideUiNotice.error(rejected.code() + ": " + rejected.message());
                }
                sharedDraftChanged();
                refreshComposerLayout();
                updateControls();
            } finally {
                uiState.completeIntentSubmission(capturedIntent);
                if (images.session().equals(view.selectedSession())) submittingDraft = false;
                updateControls();
            }
        }));
        updateControls();
    }

    static Component slashCompletionNotice(dev.openallay.guide.composer.SlashCommandDispatcher.Completion completion) {
        return completion.successful() && "compact_completed".equals(completion.code()) && completion.result() != null
                ? Component.translatable("openallay.guide.slash.compact_completed", completion.result().beforeTokens(),
                        completion.result().afterTokens(), completion.result().inputBudget())
                : Component.translatable("openallay.guide.slash." + completion.code());
    }

    static boolean submissionAccepted(boolean editing, ToolResult<?> result) {
        return result instanceof ToolResult.Success<?> success
                && (!editing || Boolean.TRUE.equals(success.value()));
    }

    private void cancel() {
        String session = view.selectedSession();
        uiState.stopIntent(session);
        accept(service.cancel(), ignored -> notice = GuideUiNotice.info(""));
    }

    private void retry() {
        GuideRequestSnapshot request = selectedRequests().stream()
                .filter(value -> value.status() == GuideRequestStatus.FAILED
                        || value.status() == GuideRequestStatus.CANCELLED
                        || value.status() == GuideRequestStatus.INTERRUPTED)
                .reduce((first, second) -> second).orElse(null);
        if (request != null) accept(service.retry(request.requestId()), ignored -> notice = GuideUiNotice.error(""));
    }

    private void forkSelectedSession() {
        notice = GuideUiNotice.info(Component.translatable("screen.openallay.fork.running").getString());
        accept(service.forkSelectedSession(), id -> {
            notice = GuideUiNotice.success(Component.translatable("screen.openallay.fork.success", id).getString());
            sessionOverlay = false;
            scroll = 0;
        });
    }

    private void forkSession(String sourceSessionId, UUID completedRequestId) {
        notice = GuideUiNotice.info(Component.translatable("screen.openallay.fork.running").getString());
        accept(service.forkSession(sourceSessionId, completedRequestId), id -> {
            notice = GuideUiNotice.success(Component.translatable("screen.openallay.fork.success", id).getString());
            sessionOverlay = false;
            scroll = 0;
        });
    }

    private void renderForkAction(GuiGraphicsExtractor graphics, UUID requestId, String text,
            int x, int y, int width) {
        if (!forkableRequest(service.snapshot(), view.selectedSession(), requestId)) return;
        Component label = Component.translatable("screen.openallay.action.fork");
        int copyWidth = text == null || text.isBlank() ? 0
                : font.width(Component.translatable("screen.openallay.action.copy")) + 14;
        int actionWidth = font.width(label) + 8;
        int actionX = x + width - copyWidth - actionWidth;
        String focusId = "fork:" + requestId;
        if (isFocused(focusedContentId, focusId)) {
            graphics.fill(actionX - 2, y - 2, actionX + actionWidth, y + 10, 0xFF31453F);
        }
        graphics.text(font, label, actionX + 2, y, MUTED, false);
        String source = view.selectedSession();
        hits.add(new Hit(new GuideUiLayout.Rect(actionX - 2, y - 2, actionWidth + 2, 12),
                HitKind.CONTENT, () -> forkSession(source, requestId), focusId,
                Component.translatable("screen.openallay.action.fork.description").getString()));
    }

    static boolean completedAssistantBoundary(GuideSnapshot snapshot, String sessionId, GuideUiRow.Assistant row) {
        if (row.streaming()) return false;
        return snapshot.sessions().stream().filter(session -> session.sessionId().equals(sessionId))
                .flatMap(session -> session.requests().stream())
                .filter(request -> request.requestId().equals(row.requestId()) && request.terminal())
                .anyMatch(request -> !request.timeline().isEmpty()
                        && request.timeline().getLast() instanceof dev.openallay.guide.GuideTimelineEntry.Assistant assistant
                        && assistant.ordinal() == row.ordinal());
    }

    /** A selected user row denotes its whole terminal request, not a partial assistant/tool row. */
    static boolean forkableRequest(GuideSnapshot snapshot, String sessionId, UUID requestId) {
        return snapshot.sessions().stream().filter(session -> session.sessionId().equals(sessionId))
                .flatMap(session -> session.requests().stream())
                .anyMatch(request -> request.requestId().equals(requestId) && request.terminal());
    }

    private void createSession() {
        int index = 1;
        String id = "session-1";
        while (containsSession(id)) id = "session-" + ++index;
        accept(service.selectSession(id), ignored -> {
            notice = GuideUiNotice.info("");
            scroll = 0;
        });
    }

    private boolean containsSession(String id) {
        for (GuideUiSession session : view.sessions()) {
            if (session.id().equals(id)) return true;
        }
        return false;
    }

    private void closeSession() {
        String sessionId = view.selectedSession();
        minecraft.setScreenAndShow(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        confirmSessionDeletionAgain(sessionId);
                    } else {
                        minecraft.setScreenAndShow(this);
                    }
                },
                Component.translatable("screen.openallay.session.delete.first.title"),
                deleteConfirmationMessage(sessionId, false),
                Component.translatable("screen.openallay.session.delete.continue"),
                Component.translatable("screen.openallay.session.delete.cancel")));
    }

    private void confirmSessionDeletionAgain(String sessionId) {
        minecraft.setScreenAndShow(new ConfirmScreen(
                confirmed -> {
                    minecraft.setScreenAndShow(this);
                    if (confirmed) {
                        accept(service.closeSession(sessionId), deleted -> {
                            notice = GuideUiNotice.success(Component.translatable(
                                    deleted
                                            ? "screen.openallay.session.delete.success"
                                            : "screen.openallay.session.delete.missing",
                                    sessionId).getString());
                            if (deleted) scroll = 0;
                        });
                    }
                },
                Component.translatable("screen.openallay.session.delete.second.title"),
                deleteConfirmationMessage(sessionId, true),
                Component.translatable("screen.openallay.session.delete.confirm"),
                Component.translatable("screen.openallay.session.delete.cancel")));
    }

    private void exportSession() {
        if (exportRunning) return;
        java.nio.file.Path gameDirectory = minecraft.gameDirectory.toPath();
        exportRunning = true;
        notice = GuideUiNotice.info(Component.translatable("screen.openallay.session.export.running").getString());
        exportNotice = notice;
        exportNoticeKey = "screen.openallay.session.export.running";
        updateControls();
        service.captureSelectedSessionForExport().whenComplete((captured, captureFailure) -> {
            if (captureFailure != null || captured == null) {
                completeExportFailure();
                return;
            }
            if (captured instanceof ToolResult.Failure<?> failure) {
                completeExportFailure();
                return;
            }
            var snapshot = ((ToolResult.Success<
                    dev.openallay.guide.export.GuideSessionExportSnapshot>) captured).value();
            CompletableFuture.supplyAsync(
                            () -> new GuideSessionExporter(gameDirectory).export(snapshot),
                            EXPORT_EXECUTOR)
                    .whenComplete((exported, failure) -> minecraft.execute(() -> {
                        exportRunning = false;
                        if (failure == null) {
                            lastExportFilename = exported.filename();
                            lastExportRequestCount = exported.requestCount();
                        }
                        notice = new GuideUiNotice(failure == null ? GuideUiNotice.Severity.SUCCESS : GuideUiNotice.Severity.ERROR, GuideUiNotice.Placement.COMPOSER, failure == null
                                ? Component.translatable(
                                        "screen.openallay.session.export.success",
                                        exported.filename(),
                                        exported.requestCount()).getString()
                                : Component.translatable(
                                        "screen.openallay.session.export.failed").getString());
                        exportNotice = notice;
                        exportNoticeKey = failure == null ? "screen.openallay.session.export.success"
                                : "screen.openallay.session.export.failed";
                        updateControls();
                    }));
        });
    }

    private void completeExportFailure() {
        minecraft.execute(() -> {
            exportRunning = false;
            notice = GuideUiNotice.error(Component.translatable(
                    "screen.openallay.session.export.failed").getString());
            exportNotice = notice;
            exportNoticeKey = "screen.openallay.session.export.failed";
            updateControls();
        });
    }

    private void renderCopyAction(
            GuiGraphicsExtractor graphics,
            GuideUiRow row,
            String text,
            int x,
            int y,
            int width) {
        if (text == null || text.isBlank()) return;
        Component label = Component.translatable("screen.openallay.action.copy");
        int actionWidth = font.width(label) + 8;
        int actionX = x + width - actionWidth;
        String focusId = "copy:" + rowId(row);
        if (isFocused(focusedContentId, focusId)) {
            graphics.fill(actionX - 2, y - 2, actionX + actionWidth, y + 10, 0xFF31453F);
        }
        graphics.text(font, label, actionX + 2, y, MUTED, false);
        hits.add(new Hit(
                new GuideUiLayout.Rect(actionX - 2, y - 2, actionWidth + 2, 12),
                HitKind.CONTENT,
                () -> copyChatText(text),
                focusId,
                label.getString()));
    }

    private void copyChatText(String text) {
        try {
            minecraft.keyboardHandler.setClipboard(text);
            notice = GuideUiNotice.success(Component.translatable("screen.openallay.copy.success").getString());
        } catch (RuntimeException failure) {
            notice = GuideUiNotice.error(Component.translatable("screen.openallay.copy.failed").getString());
        }
    }

    static String copyableText(GuideUiRow row) {
        return switch (row) {
            case GuideUiRow.User value -> value.text();
            case GuideUiRow.Assistant value -> value.text();
            default -> null;
        };
    }

    static Component deleteConfirmationMessage(String sessionId, boolean finalConfirmation) {
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid session deletion target");
        }
        return Component.translatable(
                finalConfirmation
                        ? "screen.openallay.session.delete.second.message"
                        : "screen.openallay.session.delete.first.message",
                sessionId);
    }

    private List<GuideRequestSnapshot> selectedRequests() {
        GuideSnapshot snapshot = service.snapshot();
        return snapshot.sessions().stream()
                .filter(value -> value.sessionId().equals(snapshot.selectedSession()))
                .findFirst().orElseThrow().requests();
    }

    private <T> void accept(
            java.util.concurrent.CompletableFuture<ToolResult<T>> future, Consumer<T> success) {
        String capturedSession = view.selectedSession();
        long capturedGeneration = uiState.generation();
        future.thenAccept(result -> {
            if (uiState.closed() || capturedGeneration != uiState.generation() || attachment == null
                    || !capturedSession.equals(view.selectedSession())) return;
            if (result instanceof ToolResult.Success<T> value) success.accept(value.value());
            else {
                ToolResult.Failure<T> failure = (ToolResult.Failure<T>) result;
                notice = GuideUiNotice.error(failure.code() + ": " + failure.message());
            }
            updateControls();
        });
    }

    private void updateControls() {
        if (send == null) return;
        boolean inWorld = minecraft.player != null;
        boolean active = composerRequestActive();
        boolean content = !draft.trim().isEmpty() || !composerImages.empty();
        boolean localControl = !draftIntent().editing() && dev.openallay.guide.composer.SlashCommandParser.parse(draft).kind()
                != dev.openallay.guide.composer.SlashCommandParser.Kind.TEXT;
        boolean imageCapable = composerImages.references().isEmpty()
                || view.selectedImageInputCapability() == ImageInputCapability.SUPPORTED;
        send.active = inWorld && !submittingDraft && !uiState.intentSubmissionInFlight(view.selectedSession()) && (localControl
                || (editingPending() != null || active || view.canSend()) && content
                        && imageCapable && !composerImages.pending() && !draftIntent().editInvalid());
        send.setMessage(Component.translatable(localControl ? "screen.openallay.action.send"
                : editingPending() != null ? "screen.openallay.pending.save"
                : active ? steerMode() ? "screen.openallay.pending.steer" : "screen.openallay.pending.follow_up"
                : "screen.openallay.action.send"));
        String submitHelp = !localControl && !imageCapable ? "screen.openallay.image.model_unsupported"
                : !localControl && composerImages.pending() ? "screen.openallay.image.processing"
                : "screen.openallay.composer.submit_description";
        if (service.compactAvailable() && !dev.openallay.guide.composer.SlashCommandParser.suggestions(draft).isEmpty()) {
            submitHelp = "openallay.guide.slash.compact.help";
        }
        send.setTooltip(Tooltip.create(Component.translatable(submitHelp)));
        if (stop != null) stop.active = view.canCancel();
        model.setMessage(modelButtonLabel());
        model.setTooltip(Tooltip.create(modelButtonDescription()));

        model.active = !view.modelChoices().isEmpty();
        if (microphone != null && voice != null) {
            microphone.setMessage(voice.status().active()
                    ? Component.translatable("screen.openallay.voice.short." + voice.status().state().name().toLowerCase(java.util.Locale.ROOT), voice.status().elapsedMillis() / 1000)
                    : Component.translatable("screen.openallay.voice.mic_short"));
            microphone.setTooltip(Tooltip.create(voiceFeedback()));
        }
    }

    static String retryQuestion(GuideSnapshot snapshot) {
        return snapshot.sessions().stream()
                .filter(session -> session.sessionId().equals(snapshot.selectedSession()))
                .flatMap(session -> session.requests().stream())
                .filter(request -> request.status() == GuideRequestStatus.FAILED
                        || request.status() == GuideRequestStatus.CANCELLED
                        || request.status() == GuideRequestStatus.INTERRUPTED)
                .reduce((first, last) -> last).map(GuideRequestSnapshot::userMessage).orElse("");
    }

    private Component modelButtonLabel() {
        Component full = view.modelSwitchPending()
                ? Component.translatable("screen.openallay.model.next_short", view.selectedModel().displayName()) : modelLabel();
        int available = layout.header().model().width() - 12;
        if (available < 24) return Component.literal("▾");
        if (font.width(full) <= available) return full;
        String shortLabel = view.modelSwitchPending()
                ? Component.translatable("screen.openallay.model.next_short", view.selectedModel().displayName()).getString()
                : "▾ " + view.selectedModel().displayName();
        String text = font.plainSubstrByWidth(shortLabel,
                Math.max(1, available - font.width("…")));
        return Component.literal(text + "…");
    }

    private MutableComponent modelButtonDescription() {
        return Component.translatable("screen.openallay.action.models").append(" · ")
                .append(modelStatus()).append(" · ").append(modelLabel());
    }

    private Component modelLabel() {
        GuideUiModelChoice selected = view.selectedModel();
        Component label = choiceLabel(selected);
        Component value = selected.available()
                ? label
                : Component.translatable("screen.openallay.model.unavailable_short", label);
        return Component.literal("▾ ").append(value).append(" · ").append(imageInputLabel(selected));
    }

    private void revealSelectedModel() {
        int selected = Math.max(0, view.modelChoices().indexOf(view.selectedModel()));
        modelSelectorCursor = selected;
        int visible = visibleModelChoiceCount();
        modelSelectorScroll = Mth.clamp(
                selected - Math.max(0, visible - 1),
                0,
                Math.max(0, view.modelChoices().size() - visible));
    }

    private void moveModelSelectorCursor(int delta) {
        int last = view.modelChoices().size() - 1;
        if (last < 0) return;
        modelSelectorCursor = Mth.clamp(modelSelectorCursor + delta, 0, last);
        int visible = visibleModelChoiceCount();
        if (modelSelectorCursor < modelSelectorScroll) {
            modelSelectorScroll = modelSelectorCursor;
        } else if (modelSelectorCursor >= modelSelectorScroll + visible) {
            modelSelectorScroll = modelSelectorCursor - visible + 1;
        }
        GuideUiModelChoice choice = view.modelChoices().get(modelSelectorCursor);
        focusedContentId = modelFocusId(choice);
        if (minecraft != null && minecraft.getNarrator().isActive()) {
            minecraft.getNarrator().saySystemNow(choiceLabel(choice));
        }
    }

    private int visibleModelChoiceCount() {
        if (modelSelectorButton == null) return 1;
        return Math.max(1, Math.min(8,
                (height - modelSelectorButton.y() - modelSelectorButton.height() - 12) / 20));
    }

    private GuideUiLayout.Rect modelSelectorBounds() {
        if (!modelSelectorOpen || modelSelectorButton == null) return null;
        int visible = Math.min(visibleModelChoiceCount(), view.modelChoices().size());
        int wantedWidth = view.modelChoices().stream().mapToInt(choice -> font.width(choiceLabel(choice))
                        + font.width(imageInputLabel(choice)) + 48)
                .max().orElse(modelSelectorButton.width());
        int menuWidth = Math.min(width - 16, Math.max(modelSelectorButton.width(), wantedWidth));
        return new GuideUiLayout.Rect(
                Math.min(modelSelectorButton.x(), width - 8 - menuWidth),
                modelSelectorButton.y() + modelSelectorButton.height() + 2,
                menuWidth,
                Math.max(1, visible * 20));
    }

    private void renderModelSelector(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        hits.removeIf(hit -> hit.kind() == HitKind.MODEL);
        GuideUiLayout.Rect menu = modelSelectorBounds();
        if (menu == null) return;
        int visible = Math.min(visibleModelChoiceCount(), view.modelChoices().size());
        int maximum = Math.max(0, view.modelChoices().size() - visible);
        modelSelectorScroll = Mth.clamp(modelSelectorScroll, 0, maximum);
        graphics.fill(menu.x(), menu.y(), menu.x() + menu.width(), menu.y() + menu.height(), 0xFF181B22);
        graphics.outline(menu.x(), menu.y(), menu.width(), menu.height(), ACCENT);
        graphics.enableScissor(menu.x() + 1, menu.y() + 1,
                menu.x() + menu.width() - 1, menu.y() + menu.height() - 1);
        for (int offset = 0; offset < visible; offset++) {
            int choiceIndex = modelSelectorScroll + offset;
            GuideUiModelChoice choice = view.modelChoices().get(choiceIndex);
            int y = menu.y() + offset * 20;
            if (choice.selected()) {
                graphics.fill(menu.x() + 1, y + 1, menu.x() + menu.width() - 1, y + 19, 0xFF355F59);
            }
            if (choiceIndex == modelSelectorCursor) {
                graphics.outline(menu.x() + 2, y + 2, menu.width() - 4, 16, 0xFFFFFFFF);
            }
            Component label = Component.literal(choice.selected() ? "✓ " : "  ")
                    .append(choiceLabel(choice));
            if (!choice.available()) {
                label = label.copy().append(Component.translatable(
                        "screen.openallay.model.choice_unavailable"));
            }
            label = label.copy().append(" · ").append(imageInputLabel(choice));
            graphics.text(font, label, menu.x() + 5, y + 6,
                    choice.available() ? TEXT : MUTED, false);
            if (new GuideUiLayout.Rect(menu.x(), y, menu.width(), 20).contains(mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(font, label, mouseX, mouseY);
            }
            String focusId = modelFocusId(choice);
            hits.add(new Hit(
                    new GuideUiLayout.Rect(menu.x() + 1, y + 1, menu.width() - 2, 18),
                    HitKind.MODEL,
                    () -> selectModel(choice),
                    focusId,
                    label.getString()));
        }
        graphics.disableScissor();
    }

    private void selectModel(GuideUiModelChoice choice) {
        modelSelectorOpen = false;
        if (!choice.available() || choice.selected()) return;
        accept(service.setModelSelection(choice.selection()), ignored -> notice = GuideUiNotice.error(""));
    }

    private boolean renderNativeRecipe(
            GuideUiRow.Assistant assistant,
            GuiGraphicsExtractor graphics,
            net.minecraft.client.gui.Font font,
            dev.openallay.guide.semantic.RichComponent.RecipeGrid component,
            GuideUiLayout.Rect bounds,
            int mouseX,
            int mouseY,
            long ticks) {
        NativeDomainViewBinding.Recipe binding = NativeDomainViewBindings.recipe(
                view, assistant, component).orElse(null);
        boolean painted = binding != null && nativeViews.render(
                binding,
                new NativeDomainView.RenderContext(
                        graphics, font, bounds, mouseX, mouseY, ticks));
        if (painted && Boolean.getBoolean("openallay.e2e.enabled") && intersects(bounds, layout.transcript())) {
            renderedResultCardIds.add(binding.stableId());
        }
        return painted;
    }

    static String toolFocusId(GuideUiRow.Tool tool) {
        return "tool:" + tool.requestId() + ":" + tool.activity().invocationId();
    }

    static String sourceFocusId(
            GuideUiRow.Assistant assistant, GuideEvidencePresentation.Group source, int sourceIndex) {
        return "source:" + assistant.requestId() + ":" + assistant.ordinal() + ":"
                + sourceIndex + ":" + source.identity();
    }

    static String modelFocusId(GuideUiModelChoice choice) {
        String id = choice.selection().kind() == GuideModelSelection.Kind.SERVER
                ? "server" : choice.selection().profileId();
        return "model:" + choice.selection().kind() + ":" + id;
    }

    static boolean isFocused(String focusedId, String candidateId) {
        return focusedId != null && focusedId.equals(candidateId);
    }

    private Component modelStatus() {
        GuideUiModelChoice selected = view.selectedModel();
        Component selectedLabel = choiceLabel(selected);
        if (!selected.available()) {
            return Component.translatable(
                    "screen.openallay.model.selected_unavailable", selectedLabel);
        }
        if (view.modelSwitchPending()) {
            return Component.translatable(
                    "screen.openallay.model.running_next",
                    choiceLabel(view.runningModel()),
                    selectedLabel);
        }
        return Component.translatable("screen.openallay.model.using", selectedLabel);
    }

    static Component imageInputLabel(GuideUiModelChoice choice) {
        return Component.translatable("screen.openallay.settings.models.builtin.image_input."
                + choice.imageInput().encoded());
    }

    private static Component choiceLabel(GuideUiModelChoice choice) {
        return choice.selection().kind() == GuideModelSelection.Kind.SERVER
                ? Component.translatable("screen.openallay.model.server")
                        .copy()
                        .append(" · ")
                        .append(choice.displayName())
                : Component.translatable(
                        "screen.openallay.model.client", choice.displayName());
    }

    static String sourceLabel(GuideEvidencePresentation.Group group, boolean debugMode) {
        return sourceLabel(group.records().getFirst(), debugMode);
    }

    static String sourceLabel(GuideSource source, boolean debugMode) {
        if (!debugMode) {
            GuideEvidencePresentation evidence = GuideEvidencePresentation.from(source);
            return Component.translatable(evidence.sourceKey()).getString() + " · "
                    + Component.translatable(evidence.coverageKey()).getString();
        }
        return Component.translatable("screen.openallay.detail.tool.source").getString()
                + " · " + readableSource(source.evidence().sourceId()) + " · "
                + Component.translatable(coverageKey(source.evidence().completeness())).getString();
    }

    static String readableSource(String sourceId) {
        String translationKey = switch (sourceId) {
            case "minecraft:client_player" -> "screen.openallay.detail.tool.source.minecraft.client_player";
            case "minecraft:client_registry", "minecraft:registry" -> "screen.openallay.detail.tool.source.minecraft.client_registry";
            case "minecraft:recipe_manager" -> "screen.openallay.detail.tool.source.minecraft.recipe_manager";
            case "minecraft:client_recipe_book" -> "screen.openallay.detail.tool.source.minecraft.client_recipe_book";
            case "viewer:jei" -> "screen.openallay.detail.tool.source.viewer.jei";
            case "viewer:rei" -> "screen.openallay.detail.tool.source.viewer.rei";
            case "patchouli:resources" -> "screen.openallay.detail.tool.source.patchouli.resources";
            default -> null;
        };
        return translationKey == null ? sourceId : Component.translatable(translationKey).getString();
    }

    private static String coverageKey(dev.openallay.context.DataCompleteness completeness) {
        return switch (completeness) {
            case COMPLETE -> "screen.openallay.detail.tool.coverage.complete";
            case PARTIAL -> "screen.openallay.detail.tool.coverage.partial";
            case UNKNOWN -> "screen.openallay.detail.tool.coverage.unknown";
        };
    }

    static String toolStatusName(GuideToolStatus status) {
        return toolStatus(status).getString();
    }

    static Component toolStatus(GuideToolStatus status) {
        return Component.translatable(switch (status) {
            case RUNNING -> "screen.openallay.detail.tool.status.running";
            case SUCCEEDED -> "screen.openallay.detail.tool.status.succeeded";
            case FAILED -> "screen.openallay.detail.tool.status.failed";
        });
    }

    static Component toolTitle(GuideToolActivity activity) {
        return activity.intent().title().isEmpty()
                ? friendlyTool(activity.toolId())
                : Component.literal(activity.intent().title());
    }

    private static Component intentTitle(dev.openallay.guide.GuideToolIntent intent, String titleKey) {
        return intent.title().isEmpty()
                ? Component.translatable(titleKey) : Component.literal(intent.title());
    }

    static Component toolDescription(dev.openallay.guide.GuideToolIntent intent) {
        return intent.description().isEmpty()
                ? Component.translatable("screen.openallay.tool.intent.run_javascript.description")
                : Component.literal(intent.description());
    }

    static Component toolCardStatus(GuideToolDisplayStatus status) {
        String icon = switch (status) {
            case RUNNING -> "◌";
            case SUCCEEDED -> "✓";
            case FAILED -> "!";
            case NO_RESULT_RECORDED -> "—";
        };
        return Component.literal(icon + " ")
                .append(Component.translatable(status.translationKey()));
    }

    static Component toolCardTitle(GuideToolActivity activity) {
        var title = Component.empty();
        if (!activity.intent().empty()) {
            title.append(Component.translatable("screen.openallay.tool.intent.label")).append(": ");
        }
        return title.append(toolTitle(activity));
    }

    private static Component friendlyTool(String id) {
        int separator = id.indexOf(':');
        String name = separator >= 0 ? id.substring(separator + 1) : id;
        return switch (name) {
            case "load_skill" -> Component.translatable("screen.openallay.tool.load_skill");
            case "run_javascript" -> Component.translatable("screen.openallay.tool.run_javascript");
            default -> Component.literal(name);
        };
    }

    enum ComposerKeyAction { SUBMIT, NEWLINE, DELEGATE }

    static ComposerKeyAction composerKeyAction(
            boolean composerFocused,
            boolean confirmation,
            boolean shiftDown,
            boolean controlDown) {
        if (!composerFocused || !confirmation) return ComposerKeyAction.DELEGATE;
        if (controlDown) return ComposerKeyAction.SUBMIT;
        return shiftDown ? ComposerKeyAction.NEWLINE : ComposerKeyAction.SUBMIT;
    }

    static final class TickCoalescer<T> {
        private final java.util.concurrent.atomic.AtomicReference<T> pending =
                new java.util.concurrent.atomic.AtomicReference<>();

        void offer(T value) {
            pending.set(Objects.requireNonNull(value, "value"));
        }

        T drain() {
            return pending.getAndSet(null);
        }
    }

    /** Development-only positioning used by the retained real-client screenshot probe. */
    public void positionForDevelopmentProbe(double fraction) {
        if (!Boolean.getBoolean("openallay.e2e.enabled")) {
            throw new IllegalStateException("development probe is disabled");
        }
        if (!Double.isFinite(fraction) || fraction < 0.0D || fraction > 1.0D) {
            throw new IllegalArgumentException("scroll fraction must be 0..1");
        }
        int maximum = virtualizer.maximumScroll(transcriptViewportHeight());
        scroll = (int) Math.round(maximum * fraction);
        followBottom = fraction == 1.0D;
    }

    /** Development-only Tool detail selection used by screenshot acceptance. */
    public void selectToolForDevelopmentProbe(int index) {
        if (!Boolean.getBoolean("openallay.e2e.enabled")) {
            throw new IllegalStateException("development probe is disabled");
        }
        List<GuideUiRow.Tool> tools = view.rows().stream()
                .filter(GuideUiRow.Tool.class::isInstance)
                .map(GuideUiRow.Tool.class::cast)
                .toList();
        if (index < 0 || index >= tools.size()) {
            throw new IllegalArgumentException("tool index is unavailable");
        }
        open(tools.get(index));
    }

    /** Opens the latest actual JavaScript activity, including real failure or interruption. */
    public boolean selectLatestJavascriptForDevelopmentProbe() {
        requireDevelopmentProbe();
        List<GuideUiRow.Tool> tools = view.rows().stream().filter(GuideUiRow.Tool.class::isInstance)
                .map(GuideUiRow.Tool.class::cast)
                .filter(value -> value.activity().toolId().endsWith(":run_javascript")).toList();
        if (tools.isEmpty()) return false;
        open(tools.getLast());
        return true;
    }

    /** Opens only an actually retained source from the latest evidenced JavaScript call. */
    public boolean selectLatestSourceForDevelopmentProbe() {
        requireDevelopmentProbe();
        List<GuideUiRow.Tool> tools = view.rows().stream().filter(GuideUiRow.Tool.class::isInstance)
                .map(GuideUiRow.Tool.class::cast)
                .filter(value -> value.activity().toolId().endsWith(":run_javascript")
                        && !value.activity().sources().isEmpty()).toList();
        if (tools.isEmpty()) return false;
        open(tools.getLast().activity().sources().getFirst());
        return true;
    }

    public void scrollDetailToBottomForDevelopmentProbe() {
        requireDevelopmentProbe();
        if (layout == null || !detailOpen()) throw new IllegalStateException("E2E detail is not open");
        detailScroll = Math.max(0, detailContentHeight - layout.detail().height() + 34);
    }

    private static void requireDevelopmentProbe() {
        if (!Boolean.getBoolean("openallay.e2e.enabled"))
            throw new IllegalStateException("development probe is disabled");
    }

    /** Development-only count used to keep failed-report screenshots non-crashing. */
    public int toolCountForDevelopmentProbe() {
        if (!Boolean.getBoolean("openallay.e2e.enabled")) {
            throw new IllegalStateException("development probe is disabled");
        }
        return (int) view.rows().stream().filter(GuideUiRow.Tool.class::isInstance).count();
    }

    /** Development-only explicit-selector state used by retained screenshot acceptance. */
    public void openModelSelectorForDevelopmentProbe() {
        if (!Boolean.getBoolean("openallay.e2e.enabled")) {
            throw new IllegalStateException("development probe is disabled");
        }
        modelSelectorOpen = true;
        revealSelectedModel();
    }

    /** Development-only selector cleanup used before the narrow-layout screenshot. */
    public void closeModelSelectorForDevelopmentProbe() {
        if (!Boolean.getBoolean("openallay.e2e.enabled")) {
            throw new IllegalStateException("development probe is disabled");
        }
        modelSelectorOpen = false;
    }

    /** Development-only readiness check so retained screenshots prove the active strip rendered. */
    public boolean hasRenderedActiveProgressForDevelopmentProbe() {
        return activeProgressRenderFrames >= 2;
    }

    static final class StableRowHeights {
        private final Map<String, Integer> heights = new LinkedHashMap<>();
        private int width = -1;

        void begin(int replacementWidth) {
            if (replacementWidth <= 0) throw new IllegalArgumentException("row width must be positive");
            if (width != replacementWidth) {
                width = replacementWidth;
                heights.clear();
            }
        }

        int retain(String rowId, int measuredHeight, boolean stabilize) {
            if (rowId == null || rowId.isBlank() || measuredHeight <= 0) {
                throw new IllegalArgumentException("stable row measurement is invalid");
            }
            if (!stabilize) {
                heights.put(rowId, measuredHeight);
                return measuredHeight;
            }
            return heights.merge(rowId, measuredHeight, Math::max);
        }

        void retainOnly(java.util.Set<String> rowIds) {
            heights.keySet().retainAll(rowIds);
        }

        void clear() {
            heights.clear();
        }
    }

    private enum HitKind { MENU, SESSION, CONTENT, DETAIL, MODEL, COMPOSER }
    private record Hit(
            GuideUiLayout.Rect rect,
            HitKind kind,
            Runnable action,
            String focusId,
            String narration) {
        private Hit(GuideUiLayout.Rect rect, HitKind kind, Runnable action) {
            this(rect, kind, action, null, "");
        }
    }
}
