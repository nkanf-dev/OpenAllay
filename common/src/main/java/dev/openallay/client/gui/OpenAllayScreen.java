package dev.openallay.client.gui;

import dev.openallay.client.observation.MinecraftImageTextures;

import dev.openallay.platform.minecraft.MinecraftResourceIds;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import dev.openallay.client.gui.clipboard.ClipboardImageEncoder;
import dev.openallay.client.observation.ClientObservationInputCoordinator;
import dev.openallay.client.observation.GuideObservationInputActions;
import dev.openallay.client.observation.GuideObservationSubmission;
import dev.openallay.client.observation.ObservationAnchorPresentation;
import dev.openallay.client.observation.ObservationComposerLayout;
import dev.openallay.client.observation.ObservationImageTextures;
import dev.openallay.model.image.ImageReference;
import java.util.function.BiFunction;
import dev.openallay.client.gui.clipboard.SystemImageClipboard;
import dev.openallay.guide.GuidePendingMessage;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.image.ImageInputCapability;
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
import dev.openallay.client.gui.GuideGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import dev.openallay.client.gui.GuideNativeButton;
import dev.openallay.client.gui.GuideMultilineEditor;
import dev.openallay.client.gui.GuideTooltip;
import dev.openallay.guide.ui.GuideEvidencePresentation;
import dev.openallay.guide.ui.GuideToolDisplayStatus;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import dev.openallay.client.gui.GuideInputKey;
import dev.openallay.client.gui.GuideInputMouse;
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import dev.openallay.platform.minecraft.MinecraftNativeRegistries;
import net.minecraft.world.item.ItemStack;
import dev.openallay.client.gui.GuideTextLine;
import dev.openallay.client.gui.GuideNativeFont;
import net.minecraft.util.Mth;

/** Full-screen, non-pausing projection and intent sender for GuideService. */
public final class OpenAllayScreen extends dev.openallay.client.gui.GuideNativeScreen {
    private static final int PANEL = OpenAllayWidgetTheme.PANEL;
    private static final int PANEL_ALT = OpenAllayWidgetTheme.PANEL_ALT;
    private static final int ACCENT = OpenAllayWidgetTheme.MINT;
    private static final int TEXT = OpenAllayWidgetTheme.TEXT;
    private static final int MUTED = OpenAllayWidgetTheme.MUTED_READABLE;
    private static final int ERROR = OpenAllayWidgetTheme.ERROR;
    private static final Gson DEBUG_GSON = dev.openallay.json.EngineJson.create(builder -> builder.setPrettyPrinting());
    private static final Executor EXPORT_EXECUTOR = command -> dev.openallay.concurrent.NamedThreads.startDaemon("openallay-session-export", command);
    private static final Executor IMAGE_EXECUTOR = command -> dev.openallay.concurrent.NamedThreads.startDaemon("openallay-composer-image", command);
    private final GuideService service;
    private final ComposerImageDraft composerImages;
    private final GuideClientUiState uiState;
    private GuideObservationInputActions observationActions;
    private GuideObservationSubmission observationSubmission;
    private BiFunction<UUID, String, List<ImageReference>> observationImages;
    private ObservationImageTextures observationTextures;
    private ImageReference selectedObservationImage;
    private boolean observationCapturing;
    private GuideUiLayout.Rect observationComposerBounds = GuideUiLayout.Rect.EMPTY;
    private GuideClientUiState.ViewAttachment attachment;
    private AutoCloseable draftSubscription;
    private dev.openallay.client.voice.VoiceInputActions voice;
    private GuideNativeButton microphone;
    private boolean voiceKeyHeld;
    private dev.openallay.client.presentation.GuideNotificationController notifications;
    private boolean railVisible;
    private boolean overflowOpen;
    private int sessionScroll;
    private ToolFlowOwner toolSummaryOwner;
    private record ToolFlowOwner(UUID actor, String session, Object world) {}
    private final Map<String, GuideUiLayout.Rect> renderedRows = new LinkedHashMap<>();
    private final String imageDraftOwner = UUID.randomUUID().toString();
    private final Map<UUID, String> imageTextures = new LinkedHashMap<>();
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
    private GuideMultilineEditor composer;
    private boolean presentationInitialized;
    private GuideNativeButton send;
    private GuideNativeButton stop;
    private GuideNativeButton retry;
    private GuideNativeButton model;
    private GuideNativeButton export;
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
    private final List<Map<String, Object>> renderedToolSummaries = new ArrayList<>();
    private final List<String> renderedSummaryCapsuleIds = new ArrayList<>();
    private final List<String> renderedDetailCardIds = new ArrayList<>();
    private final List<String> renderedDetailNativeRecipeIds = new ArrayList<>();
    private String renderedDetailToolId = "";
    private long detailCardPaintSerial;
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
    private NativeDomainViewRegistry detailNativeViews = new NativeDomainViewRegistry();
    private final Map<String, Integer> semanticHashes = new LinkedHashMap<>();
    private final StableRowHeights stableRowHeights = new StableRowHeights();
    private boolean followBottom = true;
    private String focusedContentId;
    private GuideDisplayConfig projectedDisplay;
    private long presentationTicks;
    private dev.openallay.guide.GuideTelemetrySnapshot telemetry;
    private Component telemetryContext = MinecraftComponents.empty();
    private Component telemetryInput = MinecraftComponents.empty();
    private Component telemetryOutput = MinecraftComponents.empty();
    private Component telemetryCost = MinecraftComponents.empty();
    private Component telemetryCompact = MinecraftComponents.empty();
    private List<Component> telemetryTooltip = List.of();
    private List<GuideTextLine> telemetryTooltipWrapped = List.of();
    private List<String> telemetryTooltipTexts = List.of();
    private net.minecraft.client.gui.Font telemetryTooltipFont;
    private net.minecraft.locale.Language telemetryTooltipLanguage;
    private int telemetryTooltipWidth;
    private int requestedTelemetryTooltipWidth;
    private int requestedTelemetryTooltipLines;
    private long requestedTelemetryTooltipFrame;

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
                GuideClientUiStates.create(service, event -> net.minecraft.client.Minecraft.getInstance().execute(event)));
    }

    public OpenAllayScreen(GuideService service, RecipeClientRuntime recipeClient,
            GuideDisplayRuntime display, Runnable settingsOpener, GuideClientUiState uiState) {
        this(service, recipeClient, Objects.requireNonNull(display, "display")::config,
                display.failure(), settingsOpener, uiState);
    }

    private OpenAllayScreen(GuideService service, RecipeClientRuntime recipeClient,
            Supplier<GuideDisplayConfig> display, GuideFailure displayFailure,
            Runnable settingsOpener, GuideClientUiState uiState) {
        super(MinecraftComponents.translatable("screen.openallay.guide"));
        this.service = Objects.requireNonNull(service, "service");
        this.observationSubmission = GuideObservationSubmission.existing(service);
        this.uiState = Objects.requireNonNull(uiState, "uiState");
        this.composerImages = uiState.images();
        this.recipeClient = java.util.Objects.requireNonNull(recipeClient, "recipeClient");
        this.display = java.util.Objects.requireNonNull(display, "display");
        this.settingsOpener = settingsOpener;
        this.projectedDisplay = currentDisplay();
        this.view = GuideUiView.from(service.snapshot(), projectedDisplay);
        List<String> startupNotices = new ArrayList<>();
        recipeClient.failure().ifPresent(failure -> startupNotices.add(MinecraftComponents.translatable(
                "screen.openallay.recipe.invalid_config", failure.code()).getString()));
        if (displayFailure != null) {
            startupNotices.add(MinecraftComponents.translatable(
                    "screen.openallay.debug.invalid_config").getString());
        }
        notice = GuideUiNotice.warning(String.join(" · ", startupNotices));
        uiState.selectSession(view.selectedSession());
        draft = uiState.readText(view.selectedSession());
        railVisible = projectedDisplay.ui().fullscreen().sessionRailVisible();
    }

    public OpenAllayScreen withObservationInput(GuideObservationInputActions input) {
        observationActions = Objects.requireNonNull(input, "input");
        return this;
    }

    public OpenAllayScreen withObservationSubmission(GuideObservationSubmission sender) {
        observationSubmission = Objects.requireNonNull(sender, "sender");
        return this;
    }

    public OpenAllayScreen withObservationImages(BiFunction<UUID, String, List<ImageReference>> images) {
        observationImages = Objects.requireNonNull(images, "images");
        return this;
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
    protected void initGuideScreen() {
        Component title = headerTitle();
        layout = GuideUiLayout.calculate(width, height, detailOpen(),
                GuideNativeFont.width(font, GuideNativeFont.visual(title)),
                font.width(MinecraftComponents.translatable("screen.openallay.action.sessions")) + 12,
                font.width(MinecraftComponents.translatable("screen.openallay.action.export")) + 12,
                font.width(MinecraftComponents.translatable("screen.openallay.action.refresh")) + 12,
                settingsOpener != null, hasComposerImagePreviews(), composerRequestActive(), pendingMessages().size(), railVisible);
        composerExtras = layout.composerExtras(hasComposerImagePreviews(), composerRequestActive(), pendingMessages().size());
        composerLayoutKey = currentComposerLayoutKey();
        GuideUiLayout.Header header = layout.header();
        headerTitleWidget = addGuideWidget(new HeaderTitle(title, header.title()));
        renderedTelemetryBounds = null;
        renderedTelemetryRows = 0;
        clearToolPaintReceipts();
        Component sessionsLabel = MinecraftComponents.translatable("screen.openallay.action.sessions");
        Component sessionsText = font.width(sessionsLabel) + 8 <= header.sessions().width()
                ? sessionsLabel : MinecraftComponents.literal("≡");
        addGuideWidget(OpenAllayButton.create(sessionsText, button -> toggleSessions())
                .bounds(header.sessions().x(), header.sessions().y(), header.sessions().width(), 20)
                .tooltip(GuideTooltip.create(sessionsLabel))
                .createNarration(ignored -> sessionsLabel.copy()).build());
        addGuideWidget(OpenAllayButton.create(MinecraftComponents.literal("⋯"), button -> overflowOpen = !overflowOpen)
                .bounds(header.overflow().x(), header.overflow().y(), header.overflow().width(), 20)
                .tooltip(GuideTooltip.create(MinecraftComponents.translatable("screen.openallay.action.more")))
                .createNarration(ignored -> MinecraftComponents.translatable("screen.openallay.action.more")).build());
        modelSelectorButton = header.model();
        model = addGuideWidget(OpenAllayButton.create(modelButtonLabel(), button -> {
                    modelSelectorOpen = !modelSelectorOpen;
                    if (modelSelectorOpen) revealSelectedModel();
                })
                .bounds(header.model().x(), header.model().y(), header.model().width(), 20)
                .createNarration(ignored -> modelButtonDescription()).build());
        dev.openallay.client.gui.GuideNativeWidgetTooltips.set(model, GuideTooltip.create(modelButtonDescription()));
        if (settingsOpener != null) {
            addGuideWidget(OpenAllayButton.create(
                            MinecraftComponents.translatable("screen.openallay.settings.short"), button -> settingsOpener.run())
                    .bounds(header.settings().x(), header.settings().y(), header.settings().width(), 20)
                    .tooltip(GuideTooltip.create(MinecraftComponents.translatable("screen.openallay.settings.title")))
                    .createNarration(ignored -> MinecraftComponents.translatable("screen.openallay.settings.title")).build());
        }

        GuideUiLayout.ComposerControls controls = layout.composerControls();
        GuideUiLayout.Rect input = composerExtras.input();
        if (composer == null) {
            composer = GuideNativeMultilineText.create(font, input.x(), input.y(), input.width(), input.height(),
                    MinecraftComponents.translatable("screen.openallay.composer.placeholder"),
                    MinecraftComponents.translatable("screen.openallay.composer.narration"));
        } else {
            GuideComposerGeometry.resize(composer, input);
        }
        if (!composer.getValue().equals(draft)) dev.openallay.client.gui.GuideNativeMultilineText.setValue(composer, draft, true);
        composer.setValueListener(value -> {
            draft = value;
            uiState.setText(view.selectedSession(), value);
        });
        addGuideWidget(composer.widget());
        send = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable("screen.openallay.action.send"), button -> submit())
                .bounds(controls.send().x(), controls.send().y(), controls.send().width(), 20).build());
        stop = controls.stop().height() == 0 ? null : addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable("screen.openallay.action.stop"), button -> cancel())
                .bounds(controls.stop().x(), controls.stop().y(), controls.stop().width(), 20).build());
        if (stop != null) dev.openallay.client.gui.GuideNativeWidgetTooltips.set(stop, GuideTooltip.create(MinecraftComponents.translatable("screen.openallay.action.stop.description")));
        retry = null; // Retry belongs to its factual failed request row.
        microphone = null;
        if (voice != null && voice.enabled()) {
            GuideUiLayout.Rect action = controls.send();
            int micY = action.y() + (stop == null ? 24 : 44);
            if (micY + 18 <= layout.composer().bottom()) {
                microphone = addGuideWidget(OpenAllayButton.create(
                                MinecraftComponents.translatable("screen.openallay.voice.mic_short"), button -> microphoneAction())
                        .bounds(action.x(), micY, action.width(), 18)
                        .tooltip(GuideTooltip.create(MinecraftComponents.translatable("screen.openallay.voice.mic"))).build());
            }
        }
        updateVirtualRows(Math.max(40, layout.transcript().width() - 18));
        scroll = followBottom
                ? virtualizer.maximumScroll(transcriptViewportHeight())
                : virtualizer.clampScroll(scroll, transcriptViewportHeight());
        refreshTelemetry();
        updateControls();
        if (detailOpen()) focusDetail();
        else if (!presentationInitialized) setInitialFocus(composer.widget());
        presentationInitialized = true;
    }

    @Override
    protected void guideInitialFocus() {
        // init owns first input focus; a native rebuild must not choose a new focused widget.
    }

    @Override
    protected void resizeGuide(int width, int height) {
        invalidateContentHits();
        GuideViewportAnchor anchor = layout == null ? null : virtualizer.anchorAt(scroll);
        boolean shouldFollow = followBottom;
        draft = composer == null ? draft : composer.getValue();
        resizeGuideWidgets(width, height);
        restoreTranscript(anchor, shouldFollow);
    }

    @Override
    protected void guideAdded() {
        // Minecraft may return to this same Screen instance from a native confirmation.
        // A removed screen releases every provider view, so each attachment gets a fresh owner.
        nativeViews = new NativeDomainViewRegistry();
        detailNativeViews = new NativeDomainViewRegistry();
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
    protected void guideRemoved() {
        GuideTextInputFocus.release(this);
        draft = composer == null ? draft : composer.getValue();
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
        nativeViews.close();
        detailNativeViews.close();
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
        if (observationTextures != null) { observationTextures.close(); observationTextures = null; }
        submittingDraft = false;
    }

    @Override
    protected void repositionGuideElements() {
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
        tickGuideWidgets();
        presentationTicks++;
        nativeViews.tick();
        detailNativeViews.tick();
        applyPendingProjection();
        refreshTelemetry();
        validatePendingEditTarget();
        refreshComposerLayout();
        if (layout != null) requestViewportHistory(layout.transcript());
        updateControls();
    }

    @Override
    public boolean guideKeyPressed(GuideInputKey event) {
        GuideKeyInput input = GuideKeyInput.from(event);
        if (voice != null && voice.enabled()
                && voiceKeyAllowed(composer != null && getFocused() == composer.widget(), sessionOverlay || overflowOpen || modelSelectorOpen)
                && GuideNativeInput.matches(OpenAllayKeyMappings.VOICE_PTT, event)) {
            if (!voiceKeyHeld) {
                voiceKeyHeld = true;
                voice.press(); // Screen keys are not gameplay KeyMapping.isDown() PTT ownership.
            }
            return true;
        }
        if (overflowOpen && input.intent() == GuideKeyIntent.ESCAPE) { overflowOpen = false; return true; }
        if (sessionOverlay && input.intent() == GuideKeyIntent.ESCAPE) { sessionOverlay = false; return true; }
        if (sessionOverlay && scrollSessionsKey(input.intent())) return true;
        if ((composer == null || getFocused() != composer.widget()) && !detailOpen() && !modelSelectorOpen && !sessionOverlay && !overflowOpen && scrollTranscriptKey(input.intent())) return true;
        if (modelSelectorOpen && input.intent() == GuideKeyIntent.ESCAPE) {
            modelSelectorOpen = false;
            return true;
        }
        if (modelSelectorOpen
                && (input.intent() == GuideKeyIntent.UP || input.intent() == GuideKeyIntent.DOWN)) {
            moveModelSelectorCursor(input.intent() == GuideKeyIntent.UP ? -1 : 1);
            return true;
        }
        if (modelSelectorOpen && input.confirmation()) {
            int choices = view.modelChoices().size();
            if (choices == 0) {
                modelSelectorOpen = false;
            } else {
                modelSelectorCursor = Mth.clamp(modelSelectorCursor, 0, choices - 1);
                selectModel(view.modelChoices().get(modelSelectorCursor));
            }
            return true;
        }
        if (closesDetailFirst(detailOpen(), input.intent() == GuideKeyIntent.ESCAPE)) {
            closeDetail();
            return true;
        }
        if (detailOpen() && (composer == null || getFocused() != composer.widget()) && scrollDetailKey(input.intent())) return true;
        if (composer != null && getFocused() == composer.widget() && input.paste()) {
            // Preserve Minecraft's text paste and selection semantics, including text+image clipboards.
            synchronizeComposerSession();
            super.guideKeyPressed(event);
            composerImages.paste();
            return true;
        }
        // Native confirmation also includes Space, which must remain text/IME input here.
        ComposerKeyAction composerAction = composerKeyAction(
                composer != null && getFocused() == composer.widget(),
                input.intent() == GuideKeyIntent.ENTER,
                input.shift(),
                input.control());
        if (composerAction == ComposerKeyAction.SUBMIT) {
            submit();
            return true;
        }
        if (composerAction == ComposerKeyAction.NEWLINE) {
            return super.guideKeyPressed(event);
        }
        if (input.intent() == GuideKeyIntent.NEXT_CONTENT) {
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
                GuideNativeFocus.clear(this);
                if (minecraft != null && dev.openallay.client.gui.GuideNativeNarrator.isActive(minecraft)) {
                    dev.openallay.client.gui.GuideNativeNarrator.sayNow(minecraft, next.narration());
                }
                return true;
            }
        }
        if (input.confirmation() && getFocused() == null && focusedContentId != null) {
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
        return super.guideKeyPressed(event);
    }

    @Override
    public boolean guideKeyReleased(GuideInputKey event) {
        if (voice != null && voiceKeyHeld && GuideNativeInput.matches(OpenAllayKeyMappings.VOICE_PTT, event)) {
            voiceKeyHeld = false;
            voice.release();
            return true;
        }
        return super.guideKeyReleased(event);
    }

    static boolean voiceKeyAllowed(boolean composerFocused, boolean modalOpen) {
        return !composerFocused && !modalOpen;
    }

    static void activateVoice(dev.openallay.client.voice.VoiceInputActions actions) {
        if (actions == null || !actions.enabled()) return;
        switch (actions.status().state()) {
            case STARTING, RECORDING -> actions.release();
            case TRANSCRIBING, DELIVERING -> actions.cancel(dev.openallay.client.voice.VoiceRuntime.CancelReason.USER);
            default -> actions.press();
        }
    }

    static boolean closesDetailFirst(boolean detailOpen, boolean escape) {
        return detailOpen && escape;
    }

    static boolean isContentFocusTarget(boolean detailOpen, boolean detailAction, boolean contentAction) {
        return detailOpen ? detailAction : contentAction;
    }

    private boolean scrollDetailKey(GuideKeyIntent key) {
        int maximum = maximumDetailScroll();
        int page = Math.max(24, layout.detail().height() - 30);
        int target = switch (key) {
            case UP -> detailScroll - 24;
            case DOWN -> detailScroll + 24;
            case PAGE_UP -> detailScroll - page;
            case PAGE_DOWN -> detailScroll + page;
            case HOME -> 0;
            case END -> maximum;
            default -> Integer.MIN_VALUE;
        };
        if (target == Integer.MIN_VALUE) return false;
        invalidateContentHits();
        detailScroll = Mth.clamp(target, 0, maximum);
        return true;
    }

    private int maximumDetailScroll() {
        return Math.max(0, detailContentHeight - layout.detail().height() + 34);
    }

    @Override
    public boolean guideMouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollX != 0 || scrollY != 0) invalidateContentHits();
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
        return super.guideMouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) {
        if (GuideNativeInput.isLeftClick(event)) {
            // Clear before routing. Native buttons and the input scrollbar may take focus below.
            if (!composerContains(event.x(), event.y())) GuideNativeFocus.clear(this);
            if (sessionOverlay || overflowOpen) {
                HitKind topKind = sessionOverlay ? HitKind.SESSION : HitKind.MENU;
                for (Hit hit : List.copyOf(hits)) {
                    if (hit.kind() == topKind && hit.rect().contains(event.x(), event.y())) {
                        focusedContentId = hit.focusId();
                        GuideNativeFocus.clear(this);
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
                        GuideNativeFocus.clear(this);
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
                    GuideNativeFocus.clear(this);
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
                        GuideNativeFocus.clear(this);
                        hit.action().run();
                        return true;
                    }
                }
            }
            for (Hit hit : List.copyOf(hits)) {
                if (hit.rect().contains(event.x(), event.y())) {
                    focusedContentId = hit.focusId();
                    GuideNativeFocus.clear(this);
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.guideMouseClicked(event, doubleClick);
    }

    /** Old render closures cannot act after scrolling, projection or geometry changes. */
    private void invalidateContentHits() {
        hits.removeIf(hit -> hit.kind() == HitKind.CONTENT || hit.kind() == HitKind.DETAIL
                || hit.kind() == HitKind.COMPOSER);
    }

    private boolean composerContains(double x, double y) {
        if (composer == null) return false;
        GuideUiLayout.Rect input = new GuideUiLayout.Rect(
                dev.openallay.client.gui.GuideNativeWidgetGeometry.x(composer.widget()), dev.openallay.client.gui.GuideNativeWidgetGeometry.y(composer.widget()), composer.widget().getWidth(), composer.widget().getHeight());
        return input.contains(x, y) && composer.widget().isMouseOver(x, y);
    }

    /** Only a successful voice draft insertion may request this; never displace a player's focus. */
    public boolean focusComposerAfterVoiceDraft() {
        if (minecraft == null || MinecraftClientWindow.screen(minecraft) != this || composer == null
                || !composer.widget().active || !composer.widget().visible || getFocused() != null
                || sessionOverlay || overflowOpen || modelSelectorOpen || detailOpen()
                || draftIntent().editing()) return false;
        setFocused(composer.widget());
        return getFocused() == composer.widget();
    }

    @Override
    protected void paintGuideScreen(GuideGraphics graphics, int mouseX, int mouseY, float a) {
        if (Boolean.getBoolean("openallay.e2e.enabled")) {
            clearToolPaintReceipts();
            renderedNativeFrame++;
        }
        graphics.fill(0, 0, width, height, 0xC00B0D12);
        renderTop(graphics, mouseX, mouseY);
        if (!sessionOverlay) renderSessions(graphics);
        renderTranscript(graphics, mouseX, mouseY);
        renderProgress(graphics);
        renderTelemetry(graphics, mouseX, mouseY);
        renderDetail(graphics, mouseX, mouseY);
        renderGuideWidgets(graphics, mouseX, mouseY, a);
        renderComposerExtras(graphics, mouseX, mouseY);
        renderModelSelector(graphics, mouseX, mouseY);
        renderLocalNotice(graphics, mouseX, mouseY);
        if (sessionOverlay) renderSessions(graphics);
        renderOverflow(graphics, mouseX, mouseY);
        reportVisibleReceipts();
    }

    private void renderTop(GuideGraphics graphics, int mouseX, int mouseY) {
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
        return MinecraftComponents.translatable("screen.openallay.guide").withStyle(ChatFormatting.BOLD);
    }

    /** A passive native label: Tab reveals and narrates its own complete name, not another button's. */
    private final class HeaderTitle extends GuideNativeWidget {
        private Component paintedTitle;

        private HeaderTitle(Component title, GuideUiLayout.Rect bounds) {
            super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), title);
            setTooltip(GuideTooltip.create(title));
        }

        @Override
        protected void paintGuideWidget(
                GuideGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Component full = getMessage();
            Component visible = full;
            if (GuideNativeFont.width(font, GuideNativeFont.visual(full)) > getWidth()) {
                String prefix = font.getSplitter().plainHeadByWidth(full.getString(),
                        Math.max(0, getWidth() - font.width(MinecraftComponents.literal("…").withStyle(full.getStyle()))),
                        full.getStyle());
                visible = MinecraftComponents.literal(prefix + "…").withStyle(full.getStyle());
            }
            if (isFocused()) {
                graphics.outline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, ACCENT);
            }
            boundedHeaderText(graphics, visible,
                    new GuideUiLayout.Rect(getX(), getY(), getWidth(), getHeight()), TEXT);
            paintedTitle = visible;
        }

        @Override
        protected void narrateGuideWidget(GuideNarration output) {
            output.add(GuideNarration.Part.TITLE, getMessage());
        }
    }

    /** Read-only native title geometry and last extraction receipt; a screenshot is still required. */
    public Map<String, Object> e2eHeaderReceipt() {
        requireDevelopmentProbe();
        if (layout == null || headerTitleWidget == null) throw new IllegalStateException("header is not initialized");
        Component full = headerTitleWidget.getMessage();
        int styledWidth = GuideNativeFont.width(font, GuideNativeFont.visual(full));
        return Map.of("fullName", full.getString(),
                "plainWidth", GuideNativeFont.width(font, GuideNativeFont.visual(full.copy().withStyle(style -> style.withBold(false)))),
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

    /** Cached aggregate telemetry only. A tooltip request is not a screenshot or visual acceptance. */
    public Map<String, Object> e2eTelemetryTooltipReceipt() {
        requireDevelopmentProbe();
        return Map.of("logicalLineCount", telemetryTooltip.size(),
                "wrappedLineCount", telemetryTooltipWrapped.size(), "wrapWidth", telemetryTooltipWidth,
                "logicalTexts", telemetryTooltipTexts,
                "requestedWidth", requestedTelemetryTooltipWidth, "requestedLineCount", requestedTelemetryTooltipLines,
                "requestedNativeFrame", requestedTelemetryTooltipFrame);
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

    /** Last real summary and detail extraction only. No retained or expected card is counted as paint. */
    public Map<String, Object> e2eToolsReceipt() {
        requireDevelopmentProbe();
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("lastNativeFrame", renderedNativeFrame);
        receipt.put("visibleToolIds", List.copyOf(renderedToolIds));
        receipt.put("visibleToolCount", renderedToolIds.size());
        receipt.put("toolSummaries", List.copyOf(renderedToolSummaries));
        receipt.put("summaryCapsuleIds", List.copyOf(renderedSummaryCapsuleIds));
        receipt.put("summaryCapsuleCount", renderedSummaryCapsuleIds.size());
        receipt.put("resultCardIds", List.copyOf(renderedResultCardIds));
        receipt.put("resultCardCount", renderedResultCardIds.size());
        receipt.put("detailToolId", renderedDetailToolId);
        receipt.put("detailCardIds", List.copyOf(renderedDetailCardIds));
        receipt.put("detailCardCount", renderedDetailCardIds.size());
        receipt.put("detailNativeRecipeIds", List.copyOf(renderedDetailNativeRecipeIds));
        receipt.put("detailNativeRecipeCount", renderedDetailNativeRecipeIds.size());
        receipt.put("totalToolCount", view.rows().stream().filter(GuideUiRow.Tool.class::isInstance).count());
        return Map.copyOf(receipt);
    }

    private void clearToolPaintReceipts() {
        renderedToolIds.clear();
        renderedResultCardIds.clear();
        renderedToolSummaries.clear();
        renderedSummaryCapsuleIds.clear();
        renderedDetailCardIds.clear();
        renderedDetailNativeRecipeIds.clear();
        renderedDetailToolId = "";
    }

    private void boundedHeaderText(
            GuideGraphics graphics, Component text, GuideUiLayout.Rect bounds, int color) {
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
    private boolean scrollSessionsKey(GuideKeyIntent key) {
        int target = switch (key) {
            case UP -> sessionScroll - 1;
            case DOWN -> sessionScroll + 1;
            case PAGE_UP -> sessionScroll - visibleSessionCount();
            case PAGE_DOWN -> sessionScroll + visibleSessionCount();
            case HOME -> 0;
            case END -> maximumSessionScroll();
            default -> Integer.MIN_VALUE;
        };
        if (target == Integer.MIN_VALUE) return false;
        sessionScroll = Mth.clamp(target, 0, maximumSessionScroll());
        return true;
    }

    private void renderSessions(GuideGraphics graphics) {
        hits.removeIf(hit -> hit.kind() == HitKind.SESSION);
        if (layout.sessionRail().width() == 0 && !sessionOverlay) return;
        GuideUiLayout.Rect rail = sessionBounds();
        graphics.fill(rail.x(), rail.y(), rail.right(), rail.bottom(), panelAltColor());
        graphics.outline(rail.x(), rail.y(), rail.width(), rail.height(), OpenAllayWidgetTheme.SLATE_BORDER);
        graphics.text(font, MinecraftComponents.translatable("screen.openallay.session.title"), rail.x() + 8, rail.y() + 6, ACCENT, false);
        if (sessionOverlay) {
            GuideUiLayout.Rect close = new GuideUiLayout.Rect(rail.right() - 22, rail.y() + 2, 20, 18);
            graphics.text(font, "×", close.x() + 5, close.y() + 4, TEXT, false);
            hits.add(new Hit(close, HitKind.SESSION, () -> sessionOverlay = false,
                    "session:close", MinecraftComponents.translatable("screen.openallay.detail.close").getString()));
        }
        int y = rail.y() + 24;
        sessionScroll = Mth.clamp(sessionScroll, 0, maximumSessionScroll());
        graphics.enableScissor(rail.x(), y, rail.right(), rail.bottom());
        for (int index = sessionScroll; index < Math.min(view.sessions().size(), sessionScroll + visibleSessionCount()); index++) {
            GuideUiSession session = view.sessions().get(index);
            GuideUiLayout.Rect row = new GuideUiLayout.Rect(rail.x() + 4, y, rail.width() - 10, 20);
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(), session.selected() ? OpenAllayWidgetTheme.MINT_DARK : panelColor());
            boundedHeaderText(graphics, MinecraftComponents.literal(session.id() + (session.running() ? " ●" : "")),
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

    private ToolFlowOwner toolFlowOwner() {
        return new ToolFlowOwner(service.snapshot().actorId(), view.selectedSession(), minecraft == null ? null : minecraft.level);
    }

    private boolean scrollTranscriptKey(GuideKeyIntent key) {
        int maximum = virtualizer.maximumScroll(transcriptViewportHeight());
        int page = Math.max(20, transcriptViewportHeight() - 10);
        int next = switch (key) {
            case PAGE_UP -> scroll - page;
            case PAGE_DOWN -> scroll + page;
            case HOME -> 0;
            case END -> maximum;
            default -> Integer.MIN_VALUE;
        };
        if (next == Integer.MIN_VALUE) return false;
        invalidateContentHits();
        scroll = Mth.clamp(next, 0, maximum);
        followBottom = scroll == maximum;
        return true;
    }
    private void renderScrollMarker(GuideGraphics graphics, GuideUiLayout.Rect bounds, int position, int maximum) {
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
    private void renderOverflow(GuideGraphics graphics, int mouseX, int mouseY) {
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
            Component label = MinecraftComponents.translatable(labels[index]);
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
        MutableComponent text = MinecraftComponents.translatable(feedback.translationKey()).copy();
        if (!feedback.actionTranslationKey().isBlank()) text.append(" · ").append(MinecraftComponents.translatable(feedback.actionTranslationKey()));
        return text;
    }

    private void renderLocalNotice(GuideGraphics graphics, int mouseX, int mouseY) {
        GuideUiLayout.Rect bounds = layout.composerNotice();
        if (voice != null && voice.enabled() && microphone == null) {
            int micWidth = voice.status().active() ? Math.min(70, bounds.width() / 2) : 30;
            GuideUiLayout.Rect mic = new GuideUiLayout.Rect(bounds.right() - micWidth, bounds.y(), micWidth, bounds.height());
            graphics.fill(mic.x(), mic.y(), mic.right(), mic.bottom(), panelAltColor());
            Component micLabel = voice.status().active()
                    ? MinecraftComponents.translatable("screen.openallay.voice.short." + voice.status().state().name().toLowerCase(java.util.Locale.ROOT), voice.status().elapsedMillis() / 1000)
                    : MinecraftComponents.translatable("screen.openallay.voice.mic_short");
            boundedHeaderText(graphics, micLabel, mic, voice.status().active() ? OpenAllayWidgetTheme.WARNING : ACCENT);
            hits.add(new Hit(mic, HitKind.COMPOSER, this::microphoneAction, "voice:mic", MinecraftComponents.translatable("screen.openallay.voice.mic").getString()));
            if (mic.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font, voiceFeedback(), mouseX, mouseY);
            bounds = new GuideUiLayout.Rect(bounds.x(), bounds.y(), bounds.width() - micWidth - 2, bounds.height());
        }
        if (!notice.empty()) {
            boundedHeaderText(graphics, MinecraftComponents.literal(notice.message()), bounds, notice.color());
            if (bounds.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font, MinecraftComponents.literal(notice.message()), mouseX, mouseY);
        } else if (voice != null && voice.status().indicatorVisible()) {
            Component status = voiceFeedback();
            var feedback = dev.openallay.client.voice.VoiceStatusPresentation.describe(voice.status());
            boundedHeaderText(graphics, status, bounds, feedback.error() ? ERROR : voice.status().active() ? OpenAllayWidgetTheme.WARNING : MUTED);
            if (bounds.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font, status, mouseX, mouseY);
        }
        if (draftIntent().editInvalid()) {
            GuideUiLayout.Rect reset = new GuideUiLayout.Rect(bounds.right() - Math.min(104, bounds.width()), bounds.y(), Math.min(104, bounds.width()), bounds.height());
            graphics.fill(reset.x(), reset.y(), reset.right(), reset.bottom(), panelAltColor());
            boundedHeaderText(graphics, MinecraftComponents.translatable("screen.openallay.pending.use_as_new"), reset, ACCENT);
            hits.add(new Hit(reset, HitKind.COMPOSER, () -> {
                uiState.resetIntent(view.selectedSession());
                notice = GuideUiNotice.info("");
            }, "composer:reset-edit", MinecraftComponents.translatable("screen.openallay.pending.use_as_new").getString()));
            return; // The missing pending-edit target requires an explicit player action.
        }
        List<GuideClientUiState.PendingInsertion> pending = uiState.pendingInsertions(view.selectedSession());
        if (!pending.isEmpty()) {
            GuideUiLayout.Rect action = new GuideUiLayout.Rect(bounds.right() - Math.min(100, bounds.width()), bounds.y(), Math.min(100, bounds.width()), bounds.height());
            graphics.fill(action.x(), action.y(), action.right(), action.bottom(), panelAltColor());
            boundedHeaderText(graphics, MinecraftComponents.translatable("screen.openallay.voice.pending", pending.size()), action, ACCENT);
            hits.add(new Hit(action, HitKind.COMPOSER, () -> uiState.applyPendingInsertion(pending.get(0).id()),
                    "voice:pending", MinecraftComponents.translatable("screen.openallay.voice.pending", pending.size()).getString()));
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
        Objects.requireNonNull(row);
        UUID request;
        if (row instanceof GuideUiRow.Assistant value) {
            request = value.requestId();
        } else if (row instanceof GuideUiRow.Tool value) {
            request = value.requestId();
        } else if (row instanceof GuideUiRow.Status value) {
            request = value.requestId();
        } else {
            request = null;
        }
        int ordinal;
        if (row instanceof GuideUiRow.Assistant value) {
            ordinal = value.ordinal();
        } else if (row instanceof GuideUiRow.Tool value) {
            ordinal = value.ordinal();
        } else if (row instanceof GuideUiRow.Status) {
            ordinal = -1;
        } else {
            ordinal = Integer.MIN_VALUE;
        }
        if (ref.contentId().equals("reply") && !(row instanceof GuideUiRow.Assistant)) return false;
        if (ref.contentId().startsWith("node:") && !(row instanceof GuideUiRow.Assistant)) return false;
        if (ref.timelineOrdinal() == -1 && !(row instanceof GuideUiRow.Status)) return false;
        return event.key().requestId().equals(request) && ordinal == ref.timelineOrdinal();
    }

    private void refreshTelemetry() {
        var next = service.telemetry();
        var language = net.minecraft.locale.Language.getInstance();
        int wrapWidth = nativeTooltipWidth(width);
        if (telemetry == next && telemetryTooltipFont == font
                && telemetryTooltipLanguage == language && telemetryTooltipWidth == wrapWidth) return;
        telemetry = next;
        String unknown = MinecraftComponents.translatable("screen.openallay.telemetry.unknown").getString();
        var context = telemetry.context();
        boolean contextKnown = context != null && context.budget() != null;
        boolean imageUnknown = contextKnown && context.imageAccounting()
                == dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting.UNKNOWN;
        String occupancy = contextKnown
                ? "~" + compactTokens(context.estimatedTokens()) + "/" + compactTokens(context.budget().contextWindowTokens())
                : unknown;
        if (imageUnknown) occupancy = MinecraftComponents.translatable(
                "screen.openallay.telemetry.text_estimate", occupancy).getString();
        telemetryContext = MinecraftComponents.literal(occupancy);
        var usage = telemetry.sessionUsage();
        var rate = usage.cacheHitRate();
        String cache = telemetryCacheText(usage, unknown);
        telemetryInput = rate == null ? MinecraftComponents.translatable("screen.openallay.telemetry.cache_compact_unknown")
                : MinecraftComponents.translatable("screen.openallay.telemetry.cache", cache);
        String cost = telemetryCostText(usage, unknown);
        telemetryCost = MinecraftComponents.translatable("screen.openallay.telemetry.cost_compact", cost);
        telemetryCompact = MinecraftComponents.translatable("screen.openallay.telemetry.compact", occupancy, cache, cost);
        telemetryTooltip = telemetryTooltipComponents(telemetry, unknown, cost, cache);
        telemetryTooltipWrapped = wrapNativeTooltip(telemetryTooltip, wrapWidth, (text, width) -> GuideNativeFont.split(font, text, width));
        telemetryTooltipTexts = telemetryTooltip.stream().map(Component::getString).toList();
        telemetryTooltipFont = font;
        telemetryTooltipLanguage = language;
        telemetryTooltipWidth = wrapWidth;
    }

    /** Logical native lines remain separate before the font performs bounded wrapping. */
    static List<Component> telemetryTooltipComponents(
            dev.openallay.guide.GuideTelemetrySnapshot telemetry, String unknown, String cost, String cache) {
        List<Component> detail = new ArrayList<>();
        detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.latest").withStyle(ChatFormatting.BOLD));
        var context = telemetry.context();
        boolean contextKnown = context != null && context.budget() != null;
        if (contextKnown) {
            detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.budget.input",
                    context.estimatedTokens(), context.budget().inputTokens()));
            detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.budget.window",
                    context.budget().contextWindowTokens(), context.budget().reservedTokens(),
                    context.budget().maxOutputTokens()));
        } else {
            detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.context_unknown"));
        }
        if (contextKnown && context.imageAccounting()
                == dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting.UNKNOWN) {
            detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.image_unknown"));
        }
        var usage = telemetry.sessionUsage();
        detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.session.calls", usage.actualCalls()));
        detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.session.cost", cost));
        detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.cache_detail",
                cache, usage.cacheReadTokens(), usage.inputTokens()));
        if (usage.costIncomplete()) detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.partial"));
        if (usage.cacheIncomplete()) detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.cache_unknown"));
        var inherited = telemetry.inheritedUsage();
        if (inherited.actualCalls() > 0) {
            String reference = telemetryCostText(inherited, unknown);
            detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.inherited", reference));
        }
        detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.price_note"));
        return List.copyOf(detail);
    }

    static String telemetryCacheText(dev.openallay.guide.GuideUsageSnapshot usage, String unknown) {
        var rate = usage.cacheHitRate();
        return rate == null ? unknown : rate.movePointRight(2)
                .setScale(1, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
    }

    static String telemetryCostText(dev.openallay.guide.GuideUsageSnapshot usage, String unknown) {
        String cost = usage.estimatedUsd() == null ? unknown : "~$" + usage.estimatedUsd()
                .setScale(5, java.math.RoundingMode.HALF_UP).toPlainString();
        if (usage.costIncomplete() && usage.estimatedUsd() != null) cost += "+";
        return cost;
    }

    static int nativeTooltipWidth(int screenWidth) {
        return Math.max(1, Math.min(260, screenWidth - 24));
    }

    static <T> List<T> wrapNativeTooltip(List<Component> logicalLines, int wrapWidth,
            java.util.function.BiFunction<Component, Integer, List<T>> splitter) {
        return logicalLines.stream().flatMap(line -> splitter.apply(line, wrapWidth).stream()).toList();
    }

    static String compactTokens(long count) {
        if (count < 1_000) return Long.toString(count);
        return String.format(java.util.Locale.ROOT, count < 1_000_000 ? "%.1fk" : "%.1fM",
                count / (count < 1_000_000 ? 1_000.0 : 1_000_000.0));
    }

    private void renderTelemetry(GuideGraphics graphics, int mouseX, int mouseY) {
        GuideUiLayout.Rect area = layout.telemetry();
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), panelAltColor());
        graphics.enableScissor(area.x() + 4, area.y(), area.right() - 4, area.bottom());
        if (!layout.telemetryCard()) {
            graphics.text(font, telemetryCompact, area.x() + 4, area.y() + 2, MUTED, false);
        } else {
            int x = area.x() + 7;
            int y = area.y() + 7;
            graphics.text(font, MinecraftComponents.translatable("screen.openallay.telemetry.context"), x, y, MUTED, false);
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
            graphics.setTooltipForNextFrame(font, telemetryTooltipWrapped,
                    GuideTooltipPlacement.DEFAULT,
                    mouseX, mouseY, false);
            requestedTelemetryTooltipWidth = telemetryTooltipWidth;
            requestedTelemetryTooltipLines = telemetryTooltipWrapped.size();
            requestedTelemetryTooltipFrame = renderedNativeFrame;
        }
    }

    private boolean telemetryImageBarEligible() {
        return telemetry != null && telemetry.context() != null && telemetry.context().budget() != null
                && telemetry.context().imageAccounting()
                != dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting.UNKNOWN;
    }

    private void renderProgress(GuideGraphics graphics) {
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
        List<GuideTextLine> lines = GuideNativeFont.split(font,
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
        MutableComponent message = MinecraftComponents.translatable(progress.activityTranslationKey());
        message.append(" · ").append(MinecraftComponents.translatable(
                "screen.openallay.progress.elapsed",
                formatDuration(Duration.between(progress.requestStartedAt(), now))));
        if (progress.retryAt() != null) {
            message.append(" · ").append(MinecraftComponents.translatable(
                    "screen.openallay.progress.retry_in",
                    formatDuration(Duration.between(now, progress.retryAt()))));
            if (progress.attempt() > 0) {
                message.append(" · ").append(MinecraftComponents.translatable(
                        "screen.openallay.progress.attempt", progress.attempt()));
            }
        } else if (progress.deadlineAt() != null) {
            message.append(" · ").append(MinecraftComponents.translatable(
                    "screen.openallay.progress.remaining",
                    formatDuration(Duration.between(now, progress.deadlineAt()))));
        } else if (progress.phase()
                == dev.openallay.guide.GuideRequestPhase.RESPONSE_STREAMING) {
            message.append(" · ").append(MinecraftComponents.translatable(
                    "screen.openallay.progress.last_update",
                    formatDuration(Duration.between(progress.lastProgressAt(), now))));
        }
        if (debugMode && progress.retryAt() == null && progress.attempt() > 0) {
            message.append(" · ").append(MinecraftComponents.translatable(
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

    private void renderTranscript(GuideGraphics graphics, int mouseX, int mouseY) {
        hits.removeIf(hit -> hit.kind() == HitKind.CONTENT);
        renderedRows.clear();
        GuideUiLayout.Rect area = layout.transcript();
        graphics.fill(area.x(), area.y(), area.x() + area.width(), area.y() + area.height(), panelColor());
        graphics.enableScissor(area.x(), area.y(), area.x() + area.width(), area.y() + area.height());
        int textWidth = Math.max(40, area.width() - 18);
        if (!Objects.equals(toolSummaryOwner, toolFlowOwner())) updateVirtualRows(textWidth);
        int viewportHeight = Math.max(0, area.height() - 14);
        GuideTranscriptVirtualizer.Window window = virtualizer.visible(scroll, viewportHeight, 30);
        int contentTop = area.y() + 7;
        if (view.rows().isEmpty()) {
            graphics.text(font, MinecraftComponents.translatable(
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
                // Every compact Tool summary retains its own measured row identity.
                GuideUiLayout.Rect paintedRow = new GuideUiLayout.Rect(area.x() + 9, y, textWidth, bottom - y);
                renderedRows.put(rowId(row), paintedRow);

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
            boundedHeaderText(graphics, MinecraftComponents.translatable("screen.openallay.scroll.bottom"), bottom, ACCENT);
            hits.add(new Hit(bottom, HitKind.CONTENT, () -> { followBottom = true; scroll = virtualizer.maximumScroll(transcriptViewportHeight()); },
                    "transcript:bottom", MinecraftComponents.translatable("screen.openallay.scroll.bottom").getString()));
        }
    }

    private int renderRow(
            GuideGraphics graphics,
            GuideUiRow row,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY) {
        if (row instanceof GuideUiRow.User user) {
            graphics.text(font, MinecraftComponents.translatable("screen.openallay.speaker.user"),
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
                graphics.text(font, MinecraftComponents.translatable(
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
            return renderToolSummaryCard(graphics, tool, x, y, width, mouseX, mouseY);
        }
        int color = row instanceof GuideUiRow.Persistence persistence
                ? persistence.state() == dev.openallay.guide.GuidePersistenceSnapshot.State.UNAVAILABLE
                        ? 0xFFFFD479 : MUTED
                : ((GuideUiRow.Status) row).status() == GuideRequestStatus.RATE_LIMITED
                        ? 0xFFFFD479 : ERROR;
        List<GuideTextLine> lines = GuideNativeFont.split(font, factualRowText(row), Math.max(1, width - 12));
        for (GuideTextLine line : lines) {
            graphics.text(font, line, x + 6, y, color, false);
            y += 10;
        }
        if (row instanceof GuideUiRow.Status status && (status.status() == GuideRequestStatus.FAILED
                || status.status() == GuideRequestStatus.CANCELLED || status.status() == GuideRequestStatus.INTERRUPTED)) {
            GuideUiLayout.Rect retryRow = new GuideUiLayout.Rect(x + 6, y, Math.min(90, width - 12), 14);
            boundedHeaderText(graphics, MinecraftComponents.translatable("screen.openallay.action.retry"), retryRow, ACCENT);
            hits.add(new Hit(retryRow, HitKind.CONTENT, () -> accept(service.retry(status.requestId()), ignored -> notice = GuideUiNotice.info("")),
                    "retry:" + status.requestId(), MinecraftComponents.translatable("screen.openallay.action.retry").getString()));
            y += 16;
        }
        return y + rowSpacing();
    }

    static Component factualRowText(GuideUiRow row) {
        if (row instanceof GuideUiRow.Persistence persistence) {
            Component message = MinecraftComponents.translatable(persistence.translationKey());
            return persistence.failure() == null ? message
                    : message.copy().append(" (" + persistence.failure().code() + ")");
        }
        GuideUiRow.Status status = (GuideUiRow.Status) row;
        return status.status() == GuideRequestStatus.INTERRUPTED
                ? MinecraftComponents.translatable("screen.openallay.history.interrupted")
                : MinecraftComponents.literal(status.text());
    }

    private void updateVirtualRows(int width) {
        ToolFlowOwner nextOwner = toolFlowOwner();
        if (!Objects.equals(toolSummaryOwner, nextOwner)) {
            nativeViews.clear();
            detailNativeViews.clear();
        }
        toolSummaryOwner = nextOwner;
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
            return toolSummaryGeometry(tool, 0, 0, width).rowHeight();
        }
        int retryHeight = row instanceof GuideUiRow.Status status && (status.status() == GuideRequestStatus.FAILED
                || status.status() == GuideRequestStatus.CANCELLED || status.status() == GuideRequestStatus.INTERRUPTED) ? 16 : 0;
        return GuideNativeFont.split(font, factualRowText(row), Math.max(1, width - 12)).size() * 10 + rowSpacing() + retryHeight;
    }

    private dev.openallay.guide.ui.GuideToolSummaryGeometry toolSummaryGeometry(
            GuideUiRow.Tool tool, int x, int y, int width) {
        var summary = dev.openallay.guide.ui.GuideToolSummaryPresenter.project(tool);
        List<Integer> capsules = summary.capsules().stream().map(capsule ->
                Math.max(22, Math.min(110, 22 + font.width(capsuleLabel(capsule))))).toList();
        return dev.openallay.guide.ui.GuideToolSummaryGeometry.measure(x, y, width,
                font.width(MinecraftComponents.translatable(summary.status().translationKey())),
                capsules, summary.hasDescription(), rowSpacing());
    }

    private int renderToolSummaryCard(
            GuideGraphics graphics, GuideUiRow.Tool tool, int x, int y,
            int width, int mouseX, int mouseY) {
        var summary = dev.openallay.guide.ui.GuideToolSummaryPresenter.project(tool);
        var geometry = toolSummaryGeometry(tool, x, y, width);
        GuideUiLayout.Rect card = geometry.card();
        boolean selected = selectedTool != null && toolFocusId(selectedTool).equals(summary.id());
        boolean hovered = card.contains(mouseX, mouseY) && layout.transcript().contains(mouseX, mouseY);
        renderToolSummaryFrame(graphics, card, panelAltColor(),
                selected || hovered ? ACCENT : OpenAllayWidgetTheme.SLATE_BORDER);
        int statusColor = switch (summary.status()) {
            case FAILED -> ERROR;
            case SUCCEEDED -> OpenAllayWidgetTheme.SUCCESS;
            case RUNNING -> ACCENT;
            case NO_RESULT_RECORDED -> MUTED;
        };
        String marker = switch (summary.status()) {
            case FAILED -> "!";
            case SUCCEEDED -> "✓";
            case RUNNING -> projectedDisplay.animationsEnabled() && (presentationTicks / 8) % 2 == 0 ? "◍" : "◌";
            case NO_RESULT_RECORDED -> "—";
        };
        graphics.text(font, marker, geometry.icon().x(), geometry.icon().y(), statusColor, false);
        Component title = intentTitle(tool.detail().intent(), summary.titleKey());
        renderToolSummaryText(graphics, title, geometry.title(), TEXT, mouseX, mouseY, true);
        renderToolSummaryText(graphics, MinecraftComponents.translatable(summary.status().translationKey()),
                geometry.status(), statusColor, mouseX, mouseY, false);
        if (summary.hasDescription()) {
            renderToolSummaryText(graphics, MinecraftComponents.literal(summary.description()), geometry.description(),
                    MUTED, mouseX, mouseY, false);
        }
        List<String> paintedCapsules = new ArrayList<>();
        List<Map<String, Object>> capsuleReceipts = new ArrayList<>();
        for (int index = 0; index < geometry.capsules().size(); index++) {
            var capsule = summary.capsules().get(index);
            GuideUiLayout.Rect capsuleBounds = geometry.capsules().get(index);
            if (renderToolSummaryCapsule(graphics, capsule, capsuleBounds, mouseX, mouseY)) {
                paintedCapsules.add(capsule.id());
                if (Boolean.getBoolean("openallay.e2e.enabled")) {
                    capsuleReceipts.add(toolSummaryCapsuleReceipt(capsule, capsuleBounds));
                }
            }
        }
        // Child semantic actions are inserted first. The complete card body then opens real detail.
        toolSummaryHit(card, () -> open(tool), summary.id(), title.getString() + " · "
                + MinecraftComponents.translatable("screen.openallay.tool.view_details").getString());
        if (Boolean.getBoolean("openallay.e2e.enabled") && intersects(card, layout.transcript())) {
            renderedToolIds.add(summary.id());
            GuideUiLayout.Rect viewport = layout.transcript();
            int visibleTop = Math.max(card.y(), viewport.y());
            int visibleBottom = Math.min(card.bottom(), viewport.bottom());
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("id", summary.id());
            receipt.put("title", title.getString());
            receipt.put("description", summary.description());
            receipt.put("status", summary.status().name());
            receipt.put("rowHeight", geometry.rowHeight());
            receipt.put("capsuleIds", List.copyOf(paintedCapsules));
            receipt.put("capsules", List.copyOf(capsuleReceipts));
            receipt.put("bounds", toolPaintBounds(card));
            receipt.put("titleBounds", toolPaintBounds(geometry.title()));
            receipt.put("blankClickX", card.x() + 2);
            receipt.put("blankClickY", visibleTop + (visibleBottom - visibleTop) / 2);
            renderedToolSummaries.add(Map.copyOf(receipt));
        }
        return y + geometry.rowHeight();
    }

    private boolean renderToolSummaryCapsule(
            GuideGraphics graphics, dev.openallay.guide.ui.GuideToolSummaryPresenter.Capsule capsule,
            GuideUiLayout.Rect bounds, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY) || isFocused(focusedContentId, capsule.id());
        renderToolSummaryFrame(graphics, bounds, panelColor(), hovered ? ACCENT : OpenAllayWidgetTheme.SLATE_BORDER);
        GuideItemView item = capsule.item();
        ItemStack stack = itemStack(item.itemId(), item.count());
        if (!stack.isEmpty()) {
            graphics.item(stack, bounds.x() + 1, bounds.y());
            graphics.itemDecorations(font, stack, bounds.x() + 1, bounds.y());
        }
        renderToolSummaryText(graphics, MinecraftComponents.literal(capsuleLabel(capsule)),
                new GuideUiLayout.Rect(bounds.x() + 20, bounds.y() + 3, Math.max(1, bounds.width() - 22), 10),
                TEXT, mouseX, mouseY, false);
        if (bounds.contains(mouseX, mouseY) && layout.transcript().contains(mouseX, mouseY) && !stack.isEmpty()) {
            graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
        MinecraftSemanticRenderer.Intent intent = toolSummaryCapsuleIntent(capsule);
        toolSummaryHit(bounds, () -> semanticIntent(intent), capsule.id(), semanticIntentNarration(intent));
        boolean painted = !stack.isEmpty() && intersects(bounds, layout.transcript());
        if (painted && Boolean.getBoolean("openallay.e2e.enabled")) renderedSummaryCapsuleIds.add(capsule.id());
        return painted;
    }

    private MinecraftSemanticRenderer.Intent toolSummaryCapsuleIntent(
            dev.openallay.guide.ui.GuideToolSummaryPresenter.Capsule capsule) {
        java.util.Objects.requireNonNull(capsule);
        if (capsule instanceof dev.openallay.guide.ui.GuideToolSummaryPresenter.Item value) {
            return new MinecraftSemanticRenderer.Intent.BrowseRecipes(value.item().itemId());
        } else if (capsule instanceof dev.openallay.guide.ui.GuideToolSummaryPresenter.Recipe value) {
            return new MinecraftSemanticRenderer.Intent.ExactRecipe(value.recipe().references().stream()
                    .filter(recipeClient::supportsExact).findFirst().orElse(value.recipe().reference()));
        }
        throw new IncompatibleClassChangeError();
    }

    private Map<String, Object> toolSummaryCapsuleReceipt(
            dev.openallay.guide.ui.GuideToolSummaryPresenter.Capsule capsule, GuideUiLayout.Rect bounds) {
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("id", capsule.id());
        receipt.put("originInvocationId", capsule.originInvocationId());
        receipt.put("bounds", toolPaintBounds(bounds));
        receipt.put("itemId", capsule.item().itemId());
        MinecraftSemanticRenderer.Intent intent = toolSummaryCapsuleIntent(capsule);
        if (intent instanceof MinecraftSemanticRenderer.Intent.ExactRecipe exact) {
            receipt.put("action", "ExactRecipe");
            receipt.put("reference", exact.reference());
        } else receipt.put("action", "BrowseRecipes");
        return Map.copyOf(receipt);
    }

    private static Map<String, Integer> toolPaintBounds(GuideUiLayout.Rect bounds) {
        return Map.of("x", bounds.x(), "y", bounds.y(), "width", bounds.width(), "height", bounds.height());
    }

    private static String capsuleLabel(dev.openallay.guide.ui.GuideToolSummaryPresenter.Capsule capsule) {
        GuideItemView item = capsule.item();
        return item.displayName() + (item.count() > 1 ? " ×" + item.count() : "");
    }

    private void toolSummaryHit(GuideUiLayout.Rect bounds, Runnable action, String id, String narration) {
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

    private void renderToolSummaryText(
            GuideGraphics graphics, Component text, GuideUiLayout.Rect bounds, int color,
            int mouseX, int mouseY, boolean detailHint) {
        String full = text.getString();
        int available = Math.max(1, bounds.width());
        String visible = font.width(text) <= available ? full
                : font.plainSubstrByWidth(full, Math.max(0, available - font.width("…")))
                        + (available >= font.width("…") ? "…" : "");
        graphics.text(font, visible, bounds.x(), bounds.y(), color, false);
        if (bounds.contains(mouseX, mouseY) && layout.transcript().contains(mouseX, mouseY)) {
            Component tooltip = detailHint ? text.copy().append("\n")
                    .append(MinecraftComponents.translatable("screen.openallay.tool.view_details")) : text;
            graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    private static void renderToolSummaryFrame(
            GuideGraphics graphics, GuideUiLayout.Rect bounds, int fill, int border) {
        int x = bounds.x(), y = bounds.y(), right = bounds.right(), bottom = bounds.bottom();
        graphics.fill(x + 2, y, right - 2, bottom, border);
        graphics.fill(x, y + 2, right, bottom - 2, border);
        graphics.fill(x + 2, y + 1, right - 2, bottom - 1, fill);
        graphics.fill(x + 1, y + 2, right - 1, bottom - 2, fill);
    }

    static List<Component> toolFailureComponents(GuideToolDetailView detail, String toolId) {
        if (detail.failure().isPresent()) {
            GuideToolDetailView.Failure failure = detail.failure().orElseThrow();
            List<Component> lines = new ArrayList<>();
            if (!failure.code().isBlank()) lines.add(MinecraftComponents.literal(failure.code()));
            if (!failure.message().isBlank()) lines.add(MinecraftComponents.literal(failure.message()));
            if (!lines.isEmpty()) return List.copyOf(lines);
        }
        if (detail.displayStatus() != GuideToolDisplayStatus.FAILED) return List.of();
        return detail.narration().stream().map(message -> friendlyToolMessage(toolId, message)).toList();
    }

    private static Component friendlyToolMessage(String toolId, GuideToolMessage message) {
        if ("openallay:run_javascript".equals(toolId)
                && message.key() == GuideToolMessage.Key.FAILURE_GENERIC) {
            return MinecraftComponents.translatable("screen.openallay.tool.failure.javascript");
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

    static Component toolMessage(GuideToolMessage message) {
        Object[] arguments = message.arguments().stream()
                .map(MinecraftComponents::literal)
                .toArray();
        return MinecraftComponents.translatable(message.key().translationKey(), arguments);
    }

    private int wrappedHeight(List<Component> paragraphs, int width) {
        int height = 0;
        for (Component paragraph : paragraphs) {
            List<GuideTextLine> lines = GuideNativeFont.split(font, paragraph, Math.max(1, width));
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
                        return font.width(MinecraftComponents.literal(text).withStyle(switch (style) {
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
        java.util.Objects.requireNonNull(row);
        if (row instanceof GuideUiRow.Persistence value) {
            return "persistence:" + value.state();
        } else if (row instanceof GuideUiRow.User value) {
            return "user:" + value.requestId();
        } else if (row instanceof GuideUiRow.Assistant value) {
            return "assistant:" + value.requestId() + ":" + value.ordinal();
        } else if (row instanceof GuideUiRow.Tool value) {
            return "tool:" + value.requestId() + ":" + value.activity().invocationId();
        } else if (row instanceof GuideUiRow.Status value) {
            return "status:" + value.requestId();
        }
        throw new IncompatibleClassChangeError();
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
        java.util.Objects.requireNonNull(intent);
        if (intent instanceof MinecraftSemanticRenderer.Intent.BrowseRecipes value) {
            navigate(recipeClient.openRecipes(value.itemId()));
        } else if (intent instanceof MinecraftSemanticRenderer.Intent.BrowseUsages value) {
            navigate(recipeClient.openUsages(value.itemId()));
        } else if (intent instanceof MinecraftSemanticRenderer.Intent.ExactRecipe value) {
            navigate(recipeClient.openExact(value.reference()));
        } else if (intent instanceof MinecraftSemanticRenderer.Intent.Source value) {
            openSemanticSource(value.sourceId(), value.originInvocationId());
        } else if (intent instanceof MinecraftSemanticRenderer.Intent.Evidence value) {
            openSemanticSource(value.evidenceId(), value.originInvocationId());
        } else if (intent instanceof MinecraftSemanticRenderer.Intent.Choice value) {
            notice = GuideUiNotice.info(
                    MinecraftComponents.translatable("screen.openallay.choice.unavailable", value.choiceId()).getString());
        }
    }

    private static String semanticIntentNarration(MinecraftSemanticRenderer.Intent intent) {
        java.util.Objects.requireNonNull(intent);
        if (intent instanceof MinecraftSemanticRenderer.Intent.BrowseRecipes value) {
            return "查看 " + value.itemId() + " 的配方";
        } else if (intent instanceof MinecraftSemanticRenderer.Intent.BrowseUsages value) {
            return "查看 " + value.itemId() + " 的用途";
        } else if (intent instanceof MinecraftSemanticRenderer.Intent.ExactRecipe value) {
            return "打开配方 " + value.reference().recipeId();
        } else if (intent instanceof MinecraftSemanticRenderer.Intent.Source value) {
            return "查看来源 " + value.sourceId();
        } else if (intent instanceof MinecraftSemanticRenderer.Intent.Evidence value) {
            return "查看证据 " + value.evidenceId();
        } else if (intent instanceof MinecraftSemanticRenderer.Intent.Choice value) {
            return "选择 " + value.choiceId();
        }
        throw new IncompatibleClassChangeError();
    }

    private static boolean intersects(GuideUiLayout.Rect first, GuideUiLayout.Rect second) {
        return first.x() < second.x() + second.width()
                && first.x() + first.width() > second.x()
                && first.y() < second.y() + second.height()
                && first.y() + first.height() > second.y();
    }

    private void openSemanticSource(String sourceId, String invocationId) {
        GuideSource source = view.rows().stream()
                .flatMap(row -> {
                    java.util.Objects.requireNonNull(row);
                    if (row instanceof GuideUiRow.Assistant assistant) {
                        return assistant.sources().stream();
                    } else if (row instanceof GuideUiRow.Tool tool) {
                        return tool.activity().invocationId().equals(invocationId)
                                ? tool.activity().sources().stream() : java.util.stream.Stream.empty();
                    } else {
                        return java.util.stream.Stream.empty();
                    }
                })
                .filter(value -> value.evidence().sourceId().equals(sourceId))
                .findFirst().orElse(null);
        if (source != null) open(source);
    }

    private int renderWrapped(
            GuideGraphics graphics, List<Component> paragraphs, int x, int y, int width, int color) {
        for (Component paragraph : paragraphs) {
            List<GuideTextLine> lines = GuideNativeFont.split(font, paragraph, width);
            if (lines.isEmpty()) y += 9;
            for (GuideTextLine line : lines) {
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
                ? MinecraftComponents.literal(display.assistantName())
                        .append(" · ")
                        .append(MinecraftComponents.translatable(
                                "screen.openallay.speaker.assistant_thinking"))
                : MinecraftComponents.literal(display.assistantName());
    }

    private void renderDetail(GuideGraphics graphics, int mouseX, int mouseY) {
        detailNativeViews.beginFrame();
        try {
            renderDetailContent(graphics, mouseX, mouseY);
        } finally {
            detailNativeViews.endFrame();
        }
    }

    private void renderDetailContent(GuideGraphics graphics, int mouseX, int mouseY) {
        if (!detailOpen()) return;
        hits.removeIf(hit -> hit.kind() == HitKind.DETAIL);
        GuideUiLayout.Rect detail = layout.detail();
        graphics.fill(detail.x(), detail.y(), detail.x() + detail.width(), detail.y() + detail.height(), 0xF02A303A);
        graphics.outline(detail.x(), detail.y(), detail.width(), detail.height(), ACCENT);
        graphics.text(font, MinecraftComponents.translatable("screen.openallay.detail.title"),
                detail.x() + 8, detail.y() + 8, ACCENT, false);
        GuideUiLayout.Rect close = detailCloseBounds();
        graphics.fill(close.x(), close.y(), close.right(), close.bottom(), panelAltColor());
        graphics.text(font, "×", close.x() + 5, close.y() + 4, TEXT, false);
        if (isFocused(focusedContentId, "detail:close")) {
            graphics.outline(close.x(), close.y(), close.width(), close.height(), ACCENT);
        }
        hits.add(new Hit(close, HitKind.DETAIL, this::closeDetail, "detail:close",
                MinecraftComponents.translatable("screen.openallay.detail.close").getString()));
        graphics.enableScissor(
                detail.x() + 1,
                detail.y() + 21,
                detail.x() + detail.width() - 1,
                detail.y() + detail.height() - 1);
        int y = detail.y() + 26 - detailScroll;
        if (selectedObservationImage != null) {
            y = observationImageDetail(graphics, selectedObservationImage, detail, y, mouseX, mouseY, true);
        } else if (selectedTool != null) {
            GuideToolDetailView toolDetail = selectedTool.detail();
            if (Boolean.getBoolean("openallay.e2e.enabled")) renderedDetailToolId = toolFocusId(selectedTool);
            y = detailLine(graphics,
                    MinecraftComponents.translatable("screen.openallay.detail.tool.status").getString()
                            + ": " + MinecraftComponents.translatable(toolDetail.displayStatus().translationKey()).getString(),
                    detail, y);
            for (DetailSection section : toolDetailSections(toolDetail)) {
                switch (section) {
                    case RESULT -> {
                        for (Component reason : toolFailureComponents(toolDetail, selectedTool.activity().toolId())) {
                            y = detailLine(graphics, reason.copy().withStyle(ChatFormatting.RED), detail, y);
                        }
                        if (toolDetail.failure().isEmpty()) {
                            y = detailLine(graphics, MinecraftComponents.translatable("screen.openallay.detail.output"), detail, y + 4);
                        }
                        for (GuideToolMessage message : toolResultMessages(toolDetail)) {
                            y = detailLine(graphics, toolMessage(message), detail, y);
                        }
                        if (observationImages != null) {
                            for (ImageReference reference : List.copyOf(observationImages.apply(
                                    selectedTool.requestId(), selectedTool.activity().invocationId()))) {
                                y = observationImageDetail(graphics, reference, detail, y, mouseX, mouseY, false);
                            }
                        }
                        for (int cardIndex = 0; cardIndex < toolDetail.cards().size(); cardIndex++) {
                            GuideDetailCard card = toolDetail.cards().get(cardIndex);
                            long paintBefore = detailCardPaintSerial;
                            String cardId = toolFocusId(selectedTool) + ":card:" + cardIndex
                                    + ":" + card.getClass().getSimpleName();
                            y = detailCard(graphics, card, cardId, detail, y, mouseX, mouseY);
                            if (Boolean.getBoolean("openallay.e2e.enabled") && detailCardPaintSerial > paintBefore) {
                                renderedDetailCardIds.add(cardId);
                            }
                        }
                    }
                    case PROGRAM -> {
                        y = detailDisclosure(graphics, MinecraftComponents.translatable("screen.openallay.detail.program"), detail, y + 4, "program");
                        if (expandedDetails.contains("program")) {
                            y = detailCode(graphics, toolProgram(toolDetail), detail, y, "javascript-source");
                        }
                    }
                    case INTENT -> {
                        y = detailLine(graphics, MinecraftComponents.translatable("screen.openallay.tool.intent.label"), detail, y + 4);
                        y = detailLine(graphics, intentTitle(toolDetail.intent(), toolDetail.titleKey()), detail, y);
                        if (!toolDetail.intent().description().isBlank()) {
                            y = detailLine(graphics, toolDescription(toolDetail.intent()), detail, y);
                        }
                    }
                    case SOURCES -> {
                        y = sourceGroups(graphics, selectedTool.activity().sources(), detail, y + 4);
                    }
                    case DEBUG -> {
                        y = detailDisclosure(graphics, MinecraftComponents.translatable("screen.openallay.debug.section"),
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
                                y = detailLine(graphics, MinecraftComponents.translatable("screen.openallay.detail.input"), detail, y);
                                y = detailCode(graphics, DEBUG_GSON.toJson(debug.invocationArguments()),
                                        detail, y, "invocation-arguments");
                            }
                            if (debug.normalized() != null) {
                                if (!selectedTool.activity().sources().isEmpty()) {
                                    y = detailLine(graphics, MinecraftComponents.translatable("screen.openallay.evidence.shown_separately"), detail, y);
                                }
                                y = detailLine(graphics, MinecraftComponents.translatable("screen.openallay.debug.normalized_result"), detail, y);
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
        int clampedScroll = Mth.clamp(detailScroll, 0, maximumDetailScroll());
        if (clampedScroll != detailScroll) {
            detailScroll = clampedScroll;
            hits.removeIf(hit -> hit.kind() == HitKind.DETAIL && !"detail:close".equals(hit.focusId()));
        }
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
            GuideGraphics graphics, Component label, GuideUiLayout.Rect detail, int y, String id) {
        Component text = MinecraftComponents.literal(expandedDetails.contains(id) ? "▼ " : "▶ ").append(label);
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
            GuideGraphics graphics,
            GuideDetailCard card,
            String cardId,
            GuideUiLayout.Rect detail,
            int y,
            int mouseX,
            int mouseY) {
        java.util.Objects.requireNonNull(card);
        if (card instanceof GuideDetailCard.Recipe recipe) {
            return recipeCard(graphics, recipe.recipe(), cardId, detail, y, mouseX, mouseY);
        } else if (card instanceof GuideDetailCard.ItemGrid grid) {
            return itemGridCard(graphics, grid, detail, y, mouseX, mouseY);
        } else if (card instanceof GuideDetailCard.Requirements requirements) {
            return requirementsCard(graphics, requirements, detail, y, mouseX, mouseY);
        } else if (card instanceof GuideDetailCard.Table table) {
            return tableCard(graphics, table, detail, y);
        } else if (card instanceof GuideDetailCard.KeyValue keyValue) {
            return keyValueCard(graphics, keyValue, detail, y);
        } else if (card instanceof GuideDetailCard.DataPreview preview) {
            return dataPreviewCard(graphics, preview, detail, y);
        } else if (card instanceof GuideDetailCard.Text text) {
            return textCard(graphics, text, detail, y);
        } else if (card instanceof GuideDetailCard.Error error) {
            return errorCard(graphics, error, detail, y);
        }
        throw new IncompatibleClassChangeError();
    }

    private int tableCard(
            GuideGraphics graphics,
            GuideDetailCard.Table card,
            GuideUiLayout.Rect detail,
            int y) {
        int start = y;
        y = detailLine(graphics, MinecraftComponents.translatable(card.titleKey()).getString(), detail, y);
        String header = String.join("  │  ", card.columns());
        y = detailLine(graphics, header, detail, y);
        for (List<String> row : card.rows()) {
            y = detailLine(graphics, String.join("  │  ", row), detail, y);
        }
        return Math.max(y, start + 25);
    }

    private int keyValueCard(
            GuideGraphics graphics,
            GuideDetailCard.KeyValue card,
            GuideUiLayout.Rect detail,
            int y) {
        int start = y;
        y = detailLine(graphics, MinecraftComponents.translatable(card.titleKey()).getString(), detail, y);
        for (GuideDetailCard.DataCell entry : card.entries()) {
            y = detailLine(graphics, entry.key() + ": " + entry.value(), detail, y);
        }
        return Math.max(y, start + 25);
    }

    private int dataPreviewCard(
            GuideGraphics graphics,
            GuideDetailCard.DataPreview card,
            GuideUiLayout.Rect detail,
            int y) {
        int start = y;
        y = detailLine(graphics, MinecraftComponents.translatable(card.titleKey()).getString(), detail, y);
        for (GuideDetailCard.DataRow row : card.rows()) {
            String line = row.cells().stream()
                    .map(cell -> cell.key() + ": " + cell.value())
                    .collect(java.util.stream.Collectors.joining(" · "));
            y = detailLine(graphics, line, detail, y);
        }
        return Math.max(y, start + 25);
    }

    private int itemGridCard(
            GuideGraphics graphics,
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
            detailCardPaintSerial++;
            graphics.fill(left, y, detail.x() + detail.width() - 6, y + height, panelAltColor());
            graphics.text(font, MinecraftComponents.translatable(card.titleKey()), left + 7, y + 6, TEXT, false);
            for (int index = 0; index < card.items().size(); index++) {
                int itemX = left + 7 + (index % columns) * 22;
                int itemY = y + 19 + (index / columns) * 22;
                renderItem(graphics, card.items().get(index), itemX, itemY, mouseX, mouseY);
            }
        }
        return y + height + 5;
    }

    private int requirementsCard(
            GuideGraphics graphics,
            GuideDetailCard.Requirements card,
            GuideUiLayout.Rect detail,
            int y,
            int mouseX,
            int mouseY) {
        int height = 31 + Math.max(1, card.requirements().size()) * 31;
        int left = detail.x() + 6;
        if (visibleDetail(y, height, detail)) {
            detailCardPaintSerial++;
            graphics.fill(left, y, detail.x() + detail.width() - 6, y + height, panelAltColor());
            String state = card.craftable()
                    ? MinecraftComponents.translatable("screen.openallay.craftability.ready").getString()
                    : MinecraftComponents.translatable("screen.openallay.craftability.missing").getString();
            graphics.text(font, state, left + 7, y + 6, card.craftable() ? 0xFF7FC8A9 : 0xFFFFD479, false);
            graphics.text(font,
                    MinecraftComponents.translatable(
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
                            MinecraftComponents.translatable(
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
            GuideGraphics graphics,
            GuideDetailCard.Text card,
            GuideUiLayout.Rect detail,
            int y) {
        int start = y;
        y = detailLine(graphics, MinecraftComponents.translatable(card.titleKey()).getString(), detail, y);
        for (String line : card.lines()) y = detailLine(graphics, line, detail, y);
        return Math.max(y, start + 25);
    }

    private int errorCard(
            GuideGraphics graphics,
            GuideDetailCard.Error card,
            GuideUiLayout.Rect detail,
            int y) {
        return detailLine(graphics, card.message(), detail, y);
    }

    private int recipeCard(
            GuideGraphics graphics, GuideRecipeCard card, String cardId,
            GuideUiLayout.Rect detail, int y, int mouseX, int mouseY) {
        int left = detail.x() + 6;
        int canvasWidth = Math.max(1, detail.width() - 12);
        int canvasHeight = 126;
        if (visibleDetail(y, canvasHeight, detail)) {
            String label = card.outputs().isEmpty() ? card.id() : card.outputs().get(0).displayName();
            var component = new dev.openallay.guide.semantic.RichComponent.RecipeGrid(
                    toolDetailRecipeNodeId(cardId), card.reference(), selectedTool.activity().invocationId(), label, label, label);
            NativeDomainViewBinding.Recipe binding = new NativeDomainViewBinding.Recipe(cardId, component, card);
            boolean painted = detailNativeViews.render(binding, new NativeDomainView.RenderContext(
                    graphics, font, new GuideUiLayout.Rect(left, y, canvasWidth, canvasHeight),
                    mouseX, mouseY, presentationTicks));
            if (painted) detailCardPaintSerial++;
            if (painted && Boolean.getBoolean("openallay.e2e.enabled")) {
                renderedDetailNativeRecipeIds.add(binding.stableId());
                renderedResultCardIds.add(binding.stableId());
            }
        }
        y += canvasHeight + 5;
        // The native canvas is bounded. These scrollable lines preserve every stored player fact.
        for (var line : dev.openallay.guide.ui.GuideRecipeDetailFacts.project(card)) {
            Object[] arguments = line.arguments().stream().map(MinecraftComponents::literal).toArray();
            y = detailLine(graphics, MinecraftComponents.translatable(line.key(), arguments), detail, y);
        }
        for (GuideRecipeCard.Output output : card.outputs()) {
            y = detailLine(graphics, MinecraftComponents.literal(output.displayName() + " ×" + output.count()), detail, y + 3);
            int actionX = left + 7;
            actionX = recipeAction(graphics, MinecraftComponents.translatable("screen.openallay.recipe.recipes"),
                    actionX, y + 2, recipeClient.canBrowse(), () -> navigate(recipeClient.openRecipes(output.itemId())));
            recipeAction(graphics, MinecraftComponents.translatable("screen.openallay.recipe.usages"),
                    actionX + 7, y + 2, recipeClient.canBrowse(), () -> navigate(recipeClient.openUsages(output.itemId())));
            y += 16;
        }
        var exact = card.references().stream().filter(recipeClient::supportsExact).findFirst();
        recipeAction(graphics, MinecraftComponents.translatable(exact.isPresent()
                        ? "screen.openallay.recipe.open_exact" : "screen.openallay.recipe.open_exact_unavailable"),
                left + 7, y + 2, exact.isPresent(), () -> navigate(recipeClient.openExact(exact.orElseThrow())));
        y += 16;
        if (!recipeClient.canBrowse()) {
            y = detailLine(graphics, MinecraftComponents.translatable("screen.openallay.recipe.viewer_unavailable")
                    .withStyle(ChatFormatting.YELLOW), detail, y);
        }
        return y + 5;
    }

    private static String toolDetailRecipeNodeId(String cardId) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(cardId.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private int recipeAction(
            GuideGraphics graphics,
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

    private static ItemStack itemStack(String itemId, long count) {
        var id = MinecraftResourceIds.tryParse(itemId);
        if (id == null || !MinecraftNativeRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(dev.openallay.client.gui.GuideNativeItemLookup.item(id.toString()),
                (int) Math.min(Integer.MAX_VALUE, Math.max(1, count)));
    }

    private void renderItem(
            GuideGraphics graphics,
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
        return y + height > detail.y() + 21 && y < detail.bottom();
    }

    private void navigate(RecipeNavigationResult result) {
        notice = GuideUiNotice.info(result.opened()
                ? MinecraftComponents.translatable("screen.openallay.recipe.viewer_opened").getString()
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
                : MinecraftComponents.translatable(key).getString();
    }

    private List<GuideEvidencePresentation.Group> groupedSources(List<GuideSource> sources) {
        return sourceGroupCache.computeIfAbsent(sources, GuideEvidencePresentation::groups);
    }

    private int sourceGroups(
            GuideGraphics graphics, List<GuideSource> sources, GuideUiLayout.Rect detail, int y) {
        if (sources.isEmpty()) return y;
        List<GuideEvidencePresentation.Group> groups = groupedSources(sources);
        y = detailDisclosure(graphics, MinecraftComponents.translatable("screen.openallay.evidence.groups",
                groups.size()), detail, y, "sources");
        if (expandedDetails.contains("sources")) {
            for (int index = 0; index < groups.size(); index++) {
                y = sourceGroup(graphics, groups.get(index), detail, y, "source-group:" + index);
            }
        }
        return y;
    }

    private int sourceGroup(
            GuideGraphics graphics, GuideEvidencePresentation.Group group,
            GuideUiLayout.Rect detail, int y, String id) {
        y = detailDisclosure(graphics, MinecraftComponents.literal(sourceLabel(group, projectedDisplay.debugMode())),
                detail, y, id);
        if (!expandedDetails.contains(id)) return y;
        int width = Math.max(1, detail.width() - 16);
        String locale = dev.openallay.client.MinecraftNativeClientFacts.selectedLanguage(minecraft);
        SourceDetailLayout cached = sourceDetailLayouts.get(id);
        if (cached == null || !cached.matches(group, width, locale)) {
            List<GuideTextLine> lines = new ArrayList<>();
            for (Component component : sourceDetailComponents(group)) {
                lines.addAll(GuideNativeFont.split(font, component, width));
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
        lines.add(MinecraftComponents.translatable(evidence.authorityKey()));
        lines.add(MinecraftComponents.translatable("screen.openallay.evidence.coverage",
                MinecraftComponents.translatable(evidence.coverageKey())));
        lines.add(MinecraftComponents.translatable("screen.openallay.evidence.capture_range",
                group.firstCapturedAt().toString(), group.lastCapturedAt().toString()));
        var identity = group.identity();
        lines.add(MinecraftComponents.literal("toolId: " + identity.toolId()));
        lines.add(MinecraftComponents.literal("sourceId: " + identity.sourceId()));
        lines.add(MinecraftComponents.literal("provenance: " + identity.provenance()));
        lines.add(MinecraftComponents.literal("gameVersion: " + identity.gameVersion()));
        lines.add(MinecraftComponents.literal("loader: " + identity.loader()));
        for (var entry : identity.scope().entrySet()) {
            lines.add(MinecraftComponents.literal(entry.getKey() + ": " + entry.getValue()));
        }
        // Shared scope is already above. Every retained observation-specific value stays available.
        for (GuideSource record : group.records()) {
            lines.add(MinecraftComponents.translatable("screen.openallay.evidence.capture_range",
                    new dev.openallay.context.SourceObservation(
                            record.evidence(), record.lastCapturedAt()).firstCapturedAt().toString(),
                    record.lastCapturedAt().toString()));
            for (var entry : record.evidence().details().entrySet()) {
                if (!identity.scope().containsKey(entry.getKey())) {
                    lines.add(MinecraftComponents.literal(entry.getKey() + ": " + entry.getValue()));
                }
            }
        }
        return List.copyOf(lines);
    }

    record SourceDetailLayout(
            GuideEvidencePresentation.Group group, int width, String locale, List<GuideTextLine> lines) {
        SourceDetailLayout { lines = List.copyOf(lines); }

        boolean matches(GuideEvidencePresentation.Group current, int currentWidth, String currentLocale) {
            // The groups are immutable. Identity avoids comparing thousands of retained records each frame.
            return group == current && width == currentWidth && locale.equals(currentLocale);
        }
    }

    record VisibleDetailLines(int first, int end) {}

    static VisibleDetailLines visibleDetailLines(GuideUiLayout.Rect detail, int y, int count) {
        // Keep native int geometry arithmetic; negate the divisor, never the possibly MIN_VALUE numerator.
        int firstLine = -Math.floorDiv(detail.y() + 21 - y, -10);
        if (count < 0) throw new IllegalArgumentException("count must not be negative");
        int first = Math.max(0, Math.min(firstLine, count));
        int end = Math.max(first, Math.min(-Math.floorDiv(detail.bottom() - 10 - y, -10), count));
        return new VisibleDetailLines(first, end);
    }

    static String formatCapturedAt(Instant capturedAt, java.util.Locale locale, java.time.ZoneId zone) {
        return java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT)
                .withLocale(locale).withZone(zone).format(capturedAt);
    }

    private int detailLine(GuideGraphics graphics, String text, GuideUiLayout.Rect detail, int y) {
        return detailLine(graphics, MinecraftComponents.literal(text), detail, y);
    }

    private int detailLine(
            GuideGraphics graphics, Component text, GuideUiLayout.Rect detail, int y) {
        for (GuideTextLine line : GuideNativeFont.split(font, text, detail.width() - 16)) {
            if (y >= detail.y() + 21 && y < detail.y() + detail.height() - 10) {
                graphics.text(font, line, detail.x() + 8, y, TEXT, false);
                detailCardPaintSerial++;
            }
            y += 10;
        }
        return y + 2;
    }

    private int detailCode(
            GuideGraphics graphics,
            String source,
            GuideUiLayout.Rect detail,
            int y,
            String cacheId) {
        int width = detail.width() - 16;
        CodeLayout cached = detailCodeLayouts.get(cacheId);
        if (cached == null || cached.width() != width || !cached.source().equals(source)) {
            String[] sourceLines = source.split("\\R", -1);
            int digits = Integer.toString(Math.max(1, sourceLines.length)).length();
            List<GuideTextLine> wrapped = new ArrayList<>();
            for (int index = 0; index < sourceLines.length; index++) {
                String prefix = String.format("%" + digits + "d │ ", index + 1);
                wrapped.addAll(GuideNativeFont.split(font, MinecraftComponents.literal(prefix + sourceLines[index]), width));
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
            GuideGraphics graphics,
            String labelKey,
            List<String> values,
            GuideUiLayout.Rect detail,
            int y) {
        if (values.isEmpty()) {
            return y;
        }
        return detailLine(
                graphics,
                MinecraftComponents.translatable(labelKey).getString() + ": " + String.join(", ", values),
                detail,
                y);
    }

    private GuideUiLayout.Rect detailCloseBounds() {
        GuideUiLayout.Rect detail = layout.detail();
        return new GuideUiLayout.Rect(detail.right() - 24, detail.y() + 3, 20, 16);
    }

    private void closeDetail() {
        hits.removeIf(hit -> hit.kind() == HitKind.CONTENT || hit.kind() == HitKind.DETAIL);
        detailNativeViews.clear();
        selectedTool = null;
        selectedSource = null;
        selectedObservationImage = null;
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
        GuideNativeFocus.clear(this);
    }

    private void open(GuideUiRow.Tool tool) {
        hits.removeIf(hit -> hit.kind() == HitKind.CONTENT || hit.kind() == HitKind.DETAIL);
        selectedTool = tool;
        selectedObservationImage = null;
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
                .orElseGet(() -> GuideEvidencePresentation.groups(List.of(source)).get(0));
        open(group, "source-detail:" + identity);
    }

    private void open(GuideEvidencePresentation.Group source, String focusId) {
        hits.removeIf(hit -> hit.kind() == HitKind.CONTENT || hit.kind() == HitKind.DETAIL);
        selectedSource = source;
        selectedObservationImage = null;
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
            String source, int width, List<GuideTextLine> lines) {}

    private void rebuildPresentationWidgets() {
        invalidateContentHits();
        boolean composerFocused = composer != null && getFocused() == composer.widget();
        AbstractWidget previous = getFocused() instanceof AbstractWidget widget ? widget : null;
        String contentFocus = focusedContentId;
        guideRebuildWidgets();
        focusedContentId = contentFocus;
        GuideNativeFocus.clear(this);
        if (composerFocused) setFocused(composer.widget());
        else if (previous != null) {
            // Recreated controls may retain focus by native type and label; never reattach an old widget.
            children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                    .filter(widget -> widget.getClass() == previous.getClass()
                            && widget.getMessage().equals(previous.getMessage()) && widget.active && widget.visible)
                    .findFirst().ifPresent(this::setFocused);
        }
    }

    private void rebuildForDetail() {
        GuideViewportAnchor anchor = layout == null ? null : virtualizer.anchorAt(scroll);
        boolean shouldFollow = followBottom;
        draft = composer == null ? draft : composer.getValue();
        rebuildPresentationWidgets();
        restoreTranscript(anchor, shouldFollow);
    }

    private boolean detailOpen() {
        return selectedTool != null || selectedSource != null || selectedObservationImage != null;
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
            boolean retained = next.rows().stream().anyMatch(row -> {
                java.util.Objects.requireNonNull(row);
                if (row instanceof GuideUiRow.Assistant assistant) {
                    return groupedSources(assistant.sources()).contains(selectedSource);
                } else if (row instanceof GuideUiRow.Tool tool) {
                    return groupedSources(tool.activity().sources()).contains(selectedSource);
                } else {
                    return false;
                }
            });
            if (!retained) {
                selectedSource = null;
                selectedSourceFocusId = null;
            }
        }
        if (!wasOpen) return false;
        if (!detailOpen()) return true;
        if (!next.selectedSession().equals(view.selectedSession())) {
            selectedObservationImage = null;
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
        invalidateContentHits();
        // Keep immutable retained groups across streaming updates; discard only lists no longer present.
        Map<List<GuideSource>, Boolean> retainedSources = new java.util.IdentityHashMap<>();
        for (GuideUiRow row : next.rows()) {
            java.util.Objects.requireNonNull(row);
            if (row instanceof GuideUiRow.Assistant assistant) {
                retainedSources.put(assistant.sources(), true);
            } else if (row instanceof GuideUiRow.Tool tool) {
                retainedSources.put(tool.activity().sources(), true);
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
            detailNativeViews.clear();
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
                MinecraftComponents.translatable("screen.openallay.pending.edit_invalid").getString());
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
        return observationActions != null || uiState.observation(view.selectedSession()).isPresent()
                || composerImages.attachments().stream().anyMatch(image -> image.preview() != null || image.reference() != null);
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
            case CLIPBOARD_UNAVAILABLE -> notice = GuideUiNotice.error(MinecraftComponents.translatable("screen.openallay.image.clipboard_unavailable").getString());
            case IMPORT_FAILED -> notice = GuideUiNotice.error(MinecraftComponents.translatable("screen.openallay.image.import_failed").getString());
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
            if (!retained.contains(id)) MinecraftImageTextures.release(minecraft.getTextureManager(), imageTextures.remove(id));
        }
        for (ComposerImageDraft.Attachment image : images) {
            if (image.preview() == null || imageTextures.containsKey(image.id())) continue;
            ClipboardImageEncoder.Preview preview = image.preview();
            var bitmap = MinecraftImageTextures.create(preview.width(), preview.height());
            int[] pixels = preview.argb();
            for (int y = 0; y < preview.height(); y++) {
                for (int x = 0; x < preview.width(); x++) MinecraftImageTextures.setArgb(bitmap, x, y, pixels[y * preview.width() + x]);
            }
            String texture = "openallay:composer/" + imageDraftOwner + "/" + image.id();
            MinecraftImageTextures.register(minecraft.getTextureManager(), texture, () -> "OpenAllay draft image", bitmap);
            imageTextures.put(image.id(), texture);
        }
    }

    private void releaseComposerTextures() {
        if (minecraft != null) imageTextures.values().forEach(
                texture -> MinecraftImageTextures.release(minecraft.getTextureManager(), texture));
        imageTextures.clear();
    }

    private ObservationImageTextures observationTextures() {
        if (observationTextures == null) observationTextures = new ObservationImageTextures(minecraft, service);
        return observationTextures;
    }

    private void refreshObservation() {
        if (observationActions == null || observationCapturing) return;
        try {
            new ClientObservationInputCoordinator(observationActions, minecraft::execute)
                    .refresh(uiState, view.selectedSession());
            notice = GuideUiNotice.info("");
        } catch (RuntimeException unavailable) {
            notice = GuideUiNotice.warning(MinecraftComponents.translatable("screen.openallay.observation.capture_failed").getString());
        }
    }

    private void attachObservationFrame() {
        if (observationActions == null || observationCapturing) return;
        observationCapturing = true;
        try {
            new ClientObservationInputCoordinator(observationActions, minecraft::execute)
                    .attachCurrentFrame(uiState, view.selectedSession()).whenComplete((applied, failure) -> minecraft.execute(() -> {
                        observationCapturing = false;
                        if (failure != null && attachment != null) notice = GuideUiNotice.warning(
                                MinecraftComponents.translatable("screen.openallay.observation.capture_failed").getString());
                        if (attachment != null) sharedDraftChanged();
                    }));
        } catch (RuntimeException unavailable) {
            observationCapturing = false;
            notice = GuideUiNotice.warning(MinecraftComponents.translatable("screen.openallay.observation.capture_failed").getString());
        }
    }

    /** Leaves the existing image/pending strips and text cursor in their measured bounds. */
    private GuideUiLayout.Rect renderObservationComposer(GuideGraphics graphics,
            GuideUiLayout.Rect strip, int mouseX, int mouseY) {
        var anchor = uiState.observation(view.selectedSession());
        if (anchor.isEmpty() && observationActions == null) return strip;
        ObservationComposerLayout observationLayout = ObservationComposerLayout.calculate(strip, !composerImages.empty());
        GuideUiLayout.Rect row = observationLayout.row();
        int rowHeight = row.height();
        observationComposerBounds = row;
        graphics.fill(row.x(), row.y(), row.right(), row.bottom(), panelAltColor());
        int controls = observationActions == null ? 0 : 32;
        if (anchor.isPresent()) controls += 16;
        int imageControls = anchor.flatMap(value -> value.image()).isPresent() ? 48 : 0;
        GuideUiLayout.Rect label = new GuideUiLayout.Rect(row.x() + 2, row.y(),
                Math.max(0, row.width() - controls - imageControls - 2), rowHeight);
        Component text = MinecraftComponents.empty();
        if (anchor.isPresent()) {
            for (var chip : ObservationAnchorPresentation.chips(anchor.orElseThrow())) {
                if (!text.getString().isEmpty()) text = text.copy().append(" · ");
                text = text.copy().append(MinecraftComponents.translatable(chip.key(), chip.value()));
            }
        } else text = MinecraftComponents.translatable("screen.openallay.observation.add_focus");
        boundedHeaderText(graphics, text, label, MUTED);
        if (label.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font, text, mouseX, mouseY);
        int x = Math.max(row.x(), row.right() - controls - imageControls);
        if (imageControls > 0) {
            ImageReference reference = anchor.orElseThrow().image().orElseThrow().image();
            GuideUiLayout.Rect image = new GuideUiLayout.Rect(x, row.y(), 32, rowHeight);
            observationComposerAction(graphics, image, MinecraftComponents.translatable("screen.openallay.observation.frame"),
                    () -> openObservationImage(reference), "observation:frame", mouseX, mouseY);
            observationComposerAction(graphics, new GuideUiLayout.Rect(x + 32, row.y(), 16, rowHeight),
                    MinecraftComponents.literal("×"), () -> uiState.removeObservationImage(view.selectedSession()),
                    "observation:remove-frame", mouseX, mouseY);
            x += 48;
        }
        if (anchor.isPresent()) {
            observationComposerAction(graphics, new GuideUiLayout.Rect(x, row.y(), 16, rowHeight), MinecraftComponents.literal("×"),
                    () -> uiState.removeObservation(view.selectedSession()), "observation:remove", mouseX, mouseY);
            x += 16;
        }
        if (observationActions != null) {
            observationComposerAction(graphics, new GuideUiLayout.Rect(x, row.y(), 16, rowHeight), MinecraftComponents.literal("↻"),
                    this::refreshObservation, "observation:refresh", mouseX, mouseY);
            observationComposerAction(graphics, new GuideUiLayout.Rect(x + 16, row.y(), 16, rowHeight),
                    MinecraftComponents.literal(observationCapturing ? "…" : "▧"), this::attachObservationFrame,
                    "observation:attach", mouseX, mouseY);
        }
        return observationLayout.remaining();
    }

    private void observationComposerAction(GuideGraphics graphics, GuideUiLayout.Rect bounds,
            Component label, Runnable action, String id, int mouseX, int mouseY) {
        int left = Math.max(bounds.x(), observationComposerBounds.x());
        int right = Math.min(bounds.right(), observationComposerBounds.right());
        if (right <= left || bounds.height() <= 0) return;
        bounds = new GuideUiLayout.Rect(left, bounds.y(), right - left, bounds.height());
        boundedHeaderText(graphics, label, bounds, observationCapturing ? MUTED : ACCENT);
        String key = switch (id) {
            case "observation:refresh" -> "screen.openallay.observation.refresh";
            case "observation:attach" -> "screen.openallay.observation.attach_frame";
            case "observation:remove-frame" -> "screen.openallay.observation.remove_frame";
            case "observation:frame" -> "screen.openallay.observation.open_frame";
            default -> "screen.openallay.observation.remove";
        };
        Component description = MinecraftComponents.translatable(key);
        hits.add(new Hit(bounds, HitKind.COMPOSER, action, id, description.getString()));
        if (bounds.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font, description, mouseX, mouseY);
    }

    private void openObservationImage(ImageReference reference) {
        selectedObservationImage = Objects.requireNonNull(reference, "reference");
        selectedTool = null;
        selectedSource = null;
        detailScroll = 0;
        expandedDetails.clear();
        rebuildForDetail();
    }

    private int observationImageDetail(GuideGraphics graphics, ImageReference reference,
            GuideUiLayout.Rect detail, int y, int mouseX, int mouseY, boolean expanded) {
        y = detailLine(graphics, MinecraftComponents.translatable("screen.openallay.observation.frame_size",
                reference.width(), reference.height()), detail, y);
        int canvasWidth = Math.max(1, detail.width() - 16);
        int canvasHeight = Math.min(expanded ? 320 : 144,
                Math.max(30, (int) Math.round(canvasWidth * reference.height() / (double) reference.width())));
        GuideUiLayout.Rect canvas = new GuideUiLayout.Rect(detail.x() + 8, y, canvasWidth, canvasHeight);
        if (visibleDetail(y, canvasHeight, detail)) {
            graphics.fill(canvas.x(), canvas.y(), canvas.right(), canvas.bottom(), panelAltColor());
            String texture = observationTextures().texture(reference);
            if (texture != null) {
                double scale = Math.min(canvas.width() / (double) reference.width(), canvas.height() / (double) reference.height());
                int imageWidth = Math.max(1, (int) Math.round(reference.width() * scale));
                int imageHeight = Math.max(1, (int) Math.round(reference.height() * scale));
                graphics.blitTexture(texture, canvas.x() + (canvas.width() - imageWidth) / 2,
                        canvas.y() + (canvas.height() - imageHeight) / 2, imageWidth, imageHeight, 0, 1, 0, 1);
            } else boundedHeaderText(graphics, MinecraftComponents.translatable(observationTextures().failed(reference)
                    ? "screen.openallay.observation.image_unavailable" : "screen.openallay.observation.image_loading"), canvas, MUTED);
            int clippedTop = Math.max(canvas.y(), detail.y() + 21);
            int clippedBottom = Math.min(canvas.bottom(), detail.bottom() - 1);
            if (!expanded && clippedBottom > clippedTop) {
                GuideUiLayout.Rect hit = new GuideUiLayout.Rect(canvas.x(), clippedTop, canvas.width(), clippedBottom - clippedTop);
                hits.add(new Hit(hit, HitKind.DETAIL, () -> openObservationImage(reference),
                        "observation:image:" + reference.sha256(), MinecraftComponents.translatable("screen.openallay.observation.open_frame").getString()));
                if (hit.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font,
                        MinecraftComponents.translatable("screen.openallay.observation.open_frame"), mouseX, mouseY);
            }
        }
        return y + canvasHeight + 5;
    }

    private void renderComposerExtras(GuideGraphics graphics, int mouseX, int mouseY) {
        hits.removeIf(hit -> hit.kind() == HitKind.COMPOSER);
        if (composerExtras == null) return;
        GuideUiLayout.Rect strip = composerExtras.images();
        if (strip.height() > 0) strip = renderObservationComposer(graphics, strip, mouseX, mouseY);
        if (strip.height() > 0 && strip.width() > 0) {
            List<ComposerImageDraft.Attachment> images = composerImages.attachments();
            int cell = Math.min(46, strip.height());
            int visible = Math.max(1, strip.width() / (cell + 4));
            imageScroll = Mth.clamp(imageScroll, 0, Math.max(0, images.size() - visible));
            graphics.enableScissor(strip.x(), strip.y(), strip.right(), strip.bottom());
            for (int index = imageScroll; index < Math.min(images.size(), imageScroll + visible); index++) {
                ComposerImageDraft.Attachment image = images.get(index);
                int x = strip.x() + (index - imageScroll) * (cell + 4);
                graphics.fill(x, strip.y(), x + cell, strip.y() + cell, panelAltColor());
                String texture = imageTextures.get(image.id());
                if (texture != null) {
                    double scale = Math.min((double) (cell - 2) / image.preview().width(),
                            (double) (cell - 2) / image.preview().height());
                    int imageWidth = Math.max(1, (int) (image.preview().width() * scale));
                    int imageHeight = Math.max(1, (int) (image.preview().height() * scale));
                    graphics.blitTexture(texture, x + (cell - imageWidth) / 2, strip.y() + (cell - imageHeight) / 2,
                            imageWidth, imageHeight, 0, 1, 0, 1);
                } else graphics.text(font, image.pending() ? "…" : "▧", x + 4, strip.y() + 4, MUTED, false);
                GuideUiLayout.Rect remove = new GuideUiLayout.Rect(x + cell - 12, strip.y(), 12, 12);
                graphics.fill(remove.x(), remove.y(), remove.right(), remove.bottom(), 0xDD181B22);
                graphics.text(font, "×", remove.x() + 2, remove.y() + 1, TEXT, false);
                hits.add(new Hit(remove, HitKind.COMPOSER, () -> composerImages.remove(image.id()),
                        "remove-image:" + image.id(), MinecraftComponents.translatable("screen.openallay.image.remove").getString()));
                if (remove.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font,
                        MinecraftComponents.translatable("screen.openallay.image.remove"), mouseX, mouseY);
                else if (new GuideUiLayout.Rect(x, strip.y(), cell, cell).contains(mouseX, mouseY)) {
                    Component tooltip = image.pending() ? MinecraftComponents.translatable("screen.openallay.image.processing")
                            : MinecraftComponents.translatable("screen.openallay.image.attached", image.reference().width(), image.reference().height());
                    if (!image.pending()) tooltip = tooltip.copy().append(" · ").append(MinecraftComponents.translatable("screen.openallay.image.cost_unknown"));
                    graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
                }
            }
            graphics.disableScissor();
        }
        renderPendingComposer(graphics, mouseX, mouseY);
    }

    private void renderPendingComposer(GuideGraphics graphics, int mouseX, int mouseY) {
        GuideUiLayout.Rect footer = composerExtras.footer();
        if (footer.height() == 0) return;
        List<GuidePendingMessage> pending = pendingMessages();
        boolean active = composerRequestActive();
        int modeWidth = active ? Math.min(64, footer.width() / 3) : 0;
        if (modeWidth > 0) {
            GuideUiLayout.Rect mode = new GuideUiLayout.Rect(footer.x(), footer.y(), modeWidth, footer.height());
            graphics.fill(mode.x(), mode.y(), mode.right() - 2, mode.bottom(), panelAltColor());
            boundedHeaderText(graphics, MinecraftComponents.translatable(steerMode() ? "screen.openallay.pending.steer" : "screen.openallay.pending.follow_up"), mode, ACCENT);
            hits.add(new Hit(mode, HitKind.COMPOSER, () -> uiState.setMode(view.selectedSession(), steerMode() ? GuideClientUiState.DraftMode.FOLLOW_UP : GuideClientUiState.DraftMode.STEER), "composer-mode",
                    MinecraftComponents.translatable("screen.openallay.pending.mode_description").getString()));
            if (mode.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font,
                    MinecraftComponents.translatable(steerMode() ? "screen.openallay.pending.steer_description" : "screen.openallay.pending.follow_up_description"), mouseX, mouseY);
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

    private void renderPendingRow(GuideGraphics graphics, GuidePendingMessage pending,
            GuideUiLayout.Rect area, boolean navigator, int count, int mouseX, int mouseY) {
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), panelAltColor());
        int textWidth = Math.max(0, area.width() - 42);
        GuideUiLayout.Rect text = new GuideUiLayout.Rect(area.x() + 2, area.y() + 3, textWidth, 12);
        Component label = MinecraftComponents.literal((navigator ? (pendingCursor + 1) + "/" + count + " " : "")
                + (pending.kind() == GuidePendingMessage.Kind.STEER ? "↪ " : "↳ ") + pending.text());
        boundedHeaderText(graphics, label, text, pending.failure() == null ? MUTED : ERROR);
        if (navigator) hits.add(new Hit(text, HitKind.COMPOSER, () -> pendingCursor = (pendingCursor + 1) % count,
                "pending-next", MinecraftComponents.translatable("screen.openallay.pending.next").getString()));
        GuideUiLayout.Rect edit = new GuideUiLayout.Rect(area.right() - 38, area.y(), 20, area.height());
        GuideUiLayout.Rect cancel = new GuideUiLayout.Rect(area.right() - 18, area.y(), 18, area.height());
        graphics.text(font, "✎", edit.x() + 4, edit.y() + 3, TEXT, false);
        graphics.text(font, "×", cancel.x() + 4, cancel.y() + 3, TEXT, false);
        hits.add(new Hit(edit, HitKind.COMPOSER, () -> editPending(pending), "pending-edit:" + pending.id(),
                MinecraftComponents.translatable("screen.openallay.pending.edit").getString()));
        hits.add(new Hit(cancel, HitKind.COMPOSER, () -> accept(service.cancelPending(pending.id()), ignored -> {
            uiState.invalidatePendingEdit(view.selectedSession(), pending.id());
            refreshComposerLayout();
        }), "pending-cancel:" + pending.id(), MinecraftComponents.translatable("screen.openallay.pending.cancel").getString()));
        if (area.contains(mouseX, mouseY)) {
            Component tooltip = MinecraftComponents.translatable(cancel.contains(mouseX, mouseY) ? "screen.openallay.pending.cancel"
                    : edit.contains(mouseX, mouseY) ? "screen.openallay.pending.edit" : "screen.openallay.pending.waiting", pending.text());
            if (pending.failure() != null) tooltip = tooltip.copy().append(" · ")
                    .append(pending.failure().code() + ": " + pending.failure().message());
            graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
        }
    }

    static GuidePendingMessage currentPending(List<GuidePendingMessage> current, UUID id) {
        return current.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }

    private void editPending(GuidePendingMessage pending) {
        synchronizeComposerSession();
        // The rendered closure may belong to a prior session or an already changed queue entry.
        String currentSession = service.snapshot().selectedSession();
        pending = view.selectedSession().equals(currentSession)
                ? currentPending(service.pendingMessages(currentSession), pending.id()) : null;
        if (pending == null) {
            notice = GuideUiNotice.warning(MinecraftComponents.translatable("screen.openallay.pending.already_consumed").getString());
            return;
        }
        if (!draft.isBlank() || !composerImages.empty()) {
            notice = GuideUiNotice.error(MinecraftComponents.translatable("screen.openallay.pending.draft_not_empty").getString());
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
        setFocused(composer.widget());
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
                    if (!composerImages.empty()) notice = GuideUiNotice.info(notice.message() + " · " + MinecraftComponents.translatable(
                            "openallay.guide.slash.attachments_retained").getString());
                    sharedDraftChanged();
                    updateControls();
                }));
        if (dispatch.handled()) return;
        if (composerImages.pending()) return;
        String question = dispatch.normalizedText().trim();
        GuideClientUiState.ObservationCapture observation = uiState.captureObservation(view.selectedSession());
        List<ImageReference> inputImages = uiState.inputImageReferences(view.selectedSession(), observation);
        if (question.isEmpty() && inputImages.isEmpty()) return;
        if (!inputImages.isEmpty() && view.selectedImageInputCapability() != ImageInputCapability.SUPPORTED) {
            notice = GuideUiNotice.error(MinecraftComponents.translatable("screen.openallay.image.model_unsupported").getString());
            return;
        }
        boolean active = composerRequestActive();
        validatePendingEditTarget();
        if (draftIntent().editInvalid()) return;
        if (editingPending() == null && !active && !view.canSend()) return;
        ModelMessage message = ModelMessage.userInput(question, composerImages.references(), observation.anchor());
        ComposerImageDraft.Submission images = composerImages.captureSubmission();
        GuideClientUiState.Insertion revision = uiState.captureInsertion(view.selectedSession());
        String capturedText = composer.getValue();
        UUID pendingId = editingPending();
        GuideClientUiState.IntentCapture capturedIntent = uiState.captureIntent(view.selectedSession());
        if (!uiState.beginIntentSubmission(capturedIntent)) return;
        GuideClientUiState.ObservationLease observationLease = uiState.leaseObservation(observation);
        submittingDraft = true;
        GuideClientUiState.SubmissionRoute capturedRoute = GuideClientUiState.submissionRoute(capturedIntent.intent(), active);
        CompletableFuture<? extends ToolResult<?>> future;
        try {
            future = observationSubmission.send(service, capturedRoute, pendingId, message, observation);
        } catch (RuntimeException dispatchFailed) {
            observationLease.close();
            uiState.completeIntentSubmission(capturedIntent);
            submittingDraft = false;
            notice = GuideUiNotice.error(MinecraftComponents.translatable("screen.openallay.composer.submit_failed").getString());
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
                    uiState.acceptedObservation(observation);
                } else if (failure == null && pendingId != null && result instanceof ToolResult.Success<?>) {
                    uiState.invalidatePendingEdit(capturedIntent);
                }
                if (attachment == null || !images.session().equals(view.selectedSession())) return;
                synchronizeComposerSession();
                submittingDraft = false;
                if (failure != null) {
                    notice = GuideUiNotice.error(MinecraftComponents.translatable("screen.openallay.composer.submit_failed").getString());
                } else if (accepted) {
                    notice = GuideUiNotice.acceptedSubmission(capturedRoute, pendingId, result,
                            service.snapshot(), images.session());
                } else if (pendingId != null && result instanceof ToolResult.Success<?>) {
                    notice = GuideUiNotice.warning(MinecraftComponents.translatable("screen.openallay.pending.already_consumed").getString());
                } else if (result instanceof ToolResult.Failure<?> rejected) {
                    notice = GuideUiNotice.error(rejected.code() + ": " + rejected.message());
                } else {
                    notice = GuideUiNotice.error(MinecraftComponents.translatable("screen.openallay.composer.submit_failed").getString());
                }
                sharedDraftChanged();
                refreshComposerLayout();
                updateControls();
            } finally {
                observationLease.close();
                uiState.completeIntentSubmission(capturedIntent);
                if (images.session().equals(view.selectedSession())) submittingDraft = false;
                updateControls();
            }
        }));
        updateControls();
    }

    static Component slashCompletionNotice(dev.openallay.guide.composer.SlashCommandDispatcher.Completion completion) {
        return completion.successful() && "compact_completed".equals(completion.code()) && completion.result() != null
                ? MinecraftComponents.translatable("openallay.guide.slash.compact_completed", completion.result().beforeTokens(),
                        completion.result().afterTokens(), completion.result().inputBudget())
                : MinecraftComponents.translatable("openallay.guide.slash." + completion.code());
    }

    static boolean submissionAccepted(boolean editing, ToolResult<?> result) {
        return GuideClientUiState.submissionAccepted(editing, result);
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
        notice = GuideUiNotice.info(MinecraftComponents.translatable("screen.openallay.fork.running").getString());
        accept(service.forkSelectedSession(), id -> {
            notice = GuideUiNotice.success(MinecraftComponents.translatable("screen.openallay.fork.success", id).getString());
            sessionOverlay = false;
            scroll = 0;
        });
    }

    private void forkSession(String sourceSessionId, UUID completedRequestId) {
        notice = GuideUiNotice.info(MinecraftComponents.translatable("screen.openallay.fork.running").getString());
        accept(service.forkSession(sourceSessionId, completedRequestId), id -> {
            notice = GuideUiNotice.success(MinecraftComponents.translatable("screen.openallay.fork.success", id).getString());
            sessionOverlay = false;
            scroll = 0;
        });
    }

    private void renderForkAction(GuideGraphics graphics, UUID requestId, String text,
            int x, int y, int width) {
        if (!forkableRequest(service.snapshot(), view.selectedSession(), requestId)) return;
        Component label = MinecraftComponents.translatable("screen.openallay.action.fork");
        int copyWidth = text == null || text.isBlank() ? 0
                : font.width(MinecraftComponents.translatable("screen.openallay.action.copy")) + 14;
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
                MinecraftComponents.translatable("screen.openallay.action.fork.description").getString()));
    }

    static boolean completedAssistantBoundary(GuideSnapshot snapshot, String sessionId, GuideUiRow.Assistant row) {
        if (row.streaming()) return false;
        return snapshot.sessions().stream().filter(session -> session.sessionId().equals(sessionId))
                .flatMap(session -> session.requests().stream())
                .filter(request -> request.requestId().equals(row.requestId()) && request.terminal())
                .anyMatch(request -> !request.timeline().isEmpty()
                        && request.timeline().get(request.timeline().size() - 1) instanceof dev.openallay.guide.GuideTimelineEntry.Assistant assistant
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
        dev.openallay.client.gui.MinecraftClientWindow.showScreen(minecraft, new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        confirmSessionDeletionAgain(sessionId);
                    } else {
                        dev.openallay.client.gui.MinecraftClientWindow.showScreen(minecraft, this);
                    }
                },
                MinecraftComponents.translatable("screen.openallay.session.delete.first.title"),
                deleteConfirmationMessage(sessionId, false),
                MinecraftComponents.translatable("screen.openallay.session.delete.continue"),
                MinecraftComponents.translatable("screen.openallay.session.delete.cancel")));
    }

    private void confirmSessionDeletionAgain(String sessionId) {
        dev.openallay.client.gui.MinecraftClientWindow.showScreen(minecraft, new ConfirmScreen(
                confirmed -> {
                    dev.openallay.client.gui.MinecraftClientWindow.showScreen(minecraft, this);
                    if (confirmed) {
                        accept(service.closeSession(sessionId), deleted -> {
                            notice = GuideUiNotice.success(MinecraftComponents.translatable(
                                    deleted
                                            ? "screen.openallay.session.delete.success"
                                            : "screen.openallay.session.delete.missing",
                                    sessionId).getString());
                            if (deleted) scroll = 0;
                        });
                    }
                },
                MinecraftComponents.translatable("screen.openallay.session.delete.second.title"),
                deleteConfirmationMessage(sessionId, true),
                MinecraftComponents.translatable("screen.openallay.session.delete.confirm"),
                MinecraftComponents.translatable("screen.openallay.session.delete.cancel")));
    }

    private void exportSession() {
        if (exportRunning) return;
        java.nio.file.Path gameDirectory = minecraft.gameDirectory.toPath();
        exportRunning = true;
        notice = GuideUiNotice.info(MinecraftComponents.translatable("screen.openallay.session.export.running").getString());
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
                                ? MinecraftComponents.translatable(
                                        "screen.openallay.session.export.success",
                                        exported.filename(),
                                        exported.requestCount()).getString()
                                : MinecraftComponents.translatable(
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
            notice = GuideUiNotice.error(MinecraftComponents.translatable(
                    "screen.openallay.session.export.failed").getString());
            exportNotice = notice;
            exportNoticeKey = "screen.openallay.session.export.failed";
            updateControls();
        });
    }

    private void renderCopyAction(
            GuideGraphics graphics,
            GuideUiRow row,
            String text,
            int x,
            int y,
            int width) {
        if (text == null || text.isBlank()) return;
        Component label = MinecraftComponents.translatable("screen.openallay.action.copy");
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
            notice = GuideUiNotice.success(MinecraftComponents.translatable("screen.openallay.copy.success").getString());
        } catch (RuntimeException failure) {
            notice = GuideUiNotice.error(MinecraftComponents.translatable("screen.openallay.copy.failed").getString());
        }
    }

    static String copyableText(GuideUiRow row) {
        java.util.Objects.requireNonNull(row);
        if (row instanceof GuideUiRow.User value) {
            return value.text();
        } else if (row instanceof GuideUiRow.Assistant value) {
            return value.text();
        } else {
            return null;
        }
    }

    static Component deleteConfirmationMessage(String sessionId, boolean finalConfirmation) {
        if (sessionId == null || !sessionId.matches("[a-zA-Z0-9_.-]+")) {
            throw new IllegalArgumentException("invalid session deletion target");
        }
        return MinecraftComponents.translatable(
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
        List<ImageReference> inputImages = uiState.inputImageReferences(view.selectedSession(),
                uiState.captureObservation(view.selectedSession()));
        boolean content = !draft.trim().isEmpty() || !composerImages.empty() || !inputImages.isEmpty();
        boolean localControl = !draftIntent().editing() && dev.openallay.guide.composer.SlashCommandParser.parse(draft).kind()
                != dev.openallay.guide.composer.SlashCommandParser.Kind.TEXT;
        boolean imageCapable = inputImages.isEmpty()
                || view.selectedImageInputCapability() == ImageInputCapability.SUPPORTED;
        send.active = inWorld && !submittingDraft && !uiState.intentSubmissionInFlight(view.selectedSession()) && (localControl
                || (editingPending() != null || active || view.canSend()) && content
                        && imageCapable && !composerImages.pending() && !draftIntent().editInvalid());
        send.setMessage(MinecraftComponents.translatable(localControl ? "screen.openallay.action.send"
                : editingPending() != null ? "screen.openallay.pending.save"
                : active ? steerMode() ? "screen.openallay.pending.steer" : "screen.openallay.pending.follow_up"
                : "screen.openallay.action.send"));
        String submitHelp = !localControl && !imageCapable ? "screen.openallay.image.model_unsupported"
                : !localControl && composerImages.pending() ? "screen.openallay.image.processing"
                : "screen.openallay.composer.submit_description";
        if (service.compactAvailable() && !dev.openallay.guide.composer.SlashCommandParser.suggestions(draft).isEmpty()) {
            submitHelp = "openallay.guide.slash.compact.help";
        }
        dev.openallay.client.gui.GuideNativeWidgetTooltips.set(send, GuideTooltip.create(MinecraftComponents.translatable(submitHelp)));
        if (stop != null) stop.active = view.canCancel();
        model.setMessage(modelButtonLabel());
        dev.openallay.client.gui.GuideNativeWidgetTooltips.set(model, GuideTooltip.create(modelButtonDescription()));

        model.active = !view.modelChoices().isEmpty();
        if (microphone != null && voice != null) {
            microphone.setMessage(voice.status().active()
                    ? MinecraftComponents.translatable("screen.openallay.voice.short." + voice.status().state().name().toLowerCase(java.util.Locale.ROOT), voice.status().elapsedMillis() / 1000)
                    : MinecraftComponents.translatable("screen.openallay.voice.mic_short"));
            dev.openallay.client.gui.GuideNativeWidgetTooltips.set(microphone, GuideTooltip.create(voiceFeedback()));
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
                ? MinecraftComponents.translatable("screen.openallay.model.next_short", view.selectedModel().displayName()) : modelLabel();
        int available = layout.header().model().width() - 12;
        if (available < 24) return MinecraftComponents.literal("▾");
        if (font.width(full) <= available) return full;
        String shortLabel = view.modelSwitchPending()
                ? MinecraftComponents.translatable("screen.openallay.model.next_short", view.selectedModel().displayName()).getString()
                : "▾ " + view.selectedModel().displayName();
        String text = font.plainSubstrByWidth(shortLabel,
                Math.max(1, available - font.width("…")));
        return MinecraftComponents.literal(text + "…");
    }

    private MutableComponent modelButtonDescription() {
        return MinecraftComponents.translatable("screen.openallay.action.models").append(" · ")
                .append(modelStatus()).append(" · ").append(modelLabel());
    }

    private Component modelLabel() {
        GuideUiModelChoice selected = view.selectedModel();
        Component label = choiceLabel(selected);
        Component value = selected.available()
                ? label
                : MinecraftComponents.translatable("screen.openallay.model.unavailable_short", label);
        return MinecraftComponents.literal("▾ ").append(value).append(" · ").append(imageInputLabel(selected));
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
        if (minecraft != null && dev.openallay.client.gui.GuideNativeNarrator.isActive(minecraft)) {
            dev.openallay.client.gui.GuideNativeNarrator.sayNow(minecraft, choiceLabel(choice));
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

    private void renderModelSelector(GuideGraphics graphics, int mouseX, int mouseY) {
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
            Component label = MinecraftComponents.literal(choice.selected() ? "✓ " : "  ")
                    .append(choiceLabel(choice));
            if (!choice.available()) {
                label = label.copy().append(MinecraftComponents.translatable(
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
            GuideGraphics graphics,
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
            return MinecraftComponents.translatable(
                    "screen.openallay.model.selected_unavailable", selectedLabel);
        }
        if (view.modelSwitchPending()) {
            return MinecraftComponents.translatable(
                    "screen.openallay.model.running_next",
                    choiceLabel(view.runningModel()),
                    selectedLabel);
        }
        return MinecraftComponents.translatable("screen.openallay.model.using", selectedLabel);
    }

    static Component imageInputLabel(GuideUiModelChoice choice) {
        return MinecraftComponents.translatable("screen.openallay.settings.models.builtin.image_input."
                + choice.imageInput().encoded());
    }

    private static Component choiceLabel(GuideUiModelChoice choice) {
        return choice.selection().kind() == GuideModelSelection.Kind.SERVER
                ? MinecraftComponents.translatable("screen.openallay.model.server")
                        .copy()
                        .append(" · ")
                        .append(choice.displayName())
                : MinecraftComponents.translatable(
                        "screen.openallay.model.client", choice.displayName());
    }

    static String sourceLabel(GuideEvidencePresentation.Group group, boolean debugMode) {
        return sourceLabel(group.records().get(0), debugMode);
    }

    static String sourceLabel(GuideSource source, boolean debugMode) {
        if (!debugMode) {
            GuideEvidencePresentation evidence = GuideEvidencePresentation.from(source);
            return MinecraftComponents.translatable(evidence.sourceKey()).getString() + " · "
                    + MinecraftComponents.translatable(evidence.coverageKey()).getString();
        }
        return MinecraftComponents.translatable("screen.openallay.detail.tool.source").getString()
                + " · " + readableSource(source.evidence().sourceId()) + " · "
                + MinecraftComponents.translatable(coverageKey(source.evidence().completeness())).getString();
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
        return translationKey == null ? sourceId : MinecraftComponents.translatable(translationKey).getString();
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
        return MinecraftComponents.translatable(switch (status) {
            case RUNNING -> "screen.openallay.detail.tool.status.running";
            case SUCCEEDED -> "screen.openallay.detail.tool.status.succeeded";
            case FAILED -> "screen.openallay.detail.tool.status.failed";
        });
    }

    static Component toolTitle(GuideToolActivity activity) {
        return activity.intent().title().isEmpty()
                ? friendlyTool(activity.toolId())
                : MinecraftComponents.literal(activity.intent().title());
    }

    private static Component intentTitle(dev.openallay.guide.GuideToolIntent intent, String titleKey) {
        return intent.title().isEmpty()
                ? MinecraftComponents.translatable(titleKey) : MinecraftComponents.literal(intent.title());
    }

    static Component toolDescription(dev.openallay.guide.GuideToolIntent intent) {
        return intent.description().isEmpty() ? MinecraftComponents.empty() : MinecraftComponents.literal(intent.description());
    }

    static Component toolCardStatus(GuideToolDisplayStatus status) {
        String icon = switch (status) {
            case RUNNING -> "◌";
            case SUCCEEDED -> "✓";
            case FAILED -> "!";
            case NO_RESULT_RECORDED -> "—";
        };
        return MinecraftComponents.literal(icon + " ")
                .append(MinecraftComponents.translatable(status.translationKey()));
    }

    private static Component friendlyTool(String id) {
        int separator = id.indexOf(':');
        String name = separator >= 0 ? id.substring(separator + 1) : id;
        return switch (name) {
            case "load_skill" -> MinecraftComponents.translatable("screen.openallay.tool.load_skill");
            case "run_javascript" -> MinecraftComponents.translatable("screen.openallay.tool.run_javascript");
            default -> MinecraftComponents.literal(name);
        };
    }

    enum ComposerKeyAction { SUBMIT, NEWLINE, DELEGATE }

    static ComposerKeyAction composerKeyAction(
            boolean composerFocused,
            boolean enterPressed,
            boolean shiftDown,
            boolean controlDown) {
        if (!composerFocused || !enterPressed) return ComposerKeyAction.DELEGATE;
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
        open(tools.get(tools.size() - 1));
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
        open(tools.get(tools.size() - 1).activity().sources().get(0));
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
