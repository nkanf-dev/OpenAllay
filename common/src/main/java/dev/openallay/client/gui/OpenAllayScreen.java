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

import dev.openallay.client.gui.GuideGraphics;
import dev.openallay.client.gui.GuideWidget;
import dev.openallay.client.gui.GuideNativeButton;
import dev.openallay.client.gui.GuideMultilineEditor;
import dev.openallay.client.gui.GuideTooltip;
import dev.openallay.guide.ui.GuideEvidencePresentation;
import dev.openallay.guide.ui.GuideToolDisplayStatus;

import dev.openallay.client.gui.GuideInputKey;
import dev.openallay.client.gui.GuideInputMouse;
import dev.openallay.platform.minecraft.MinecraftComponents;

import dev.openallay.platform.minecraft.MinecraftNativeRegistries;

import dev.openallay.client.gui.GuideTextLine;
import dev.openallay.client.gui.GuideNativeFont;


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
    @dev.openallay.value.ValueType(ToolFlowOwner.ValueSchemaProvider.class)
private static final class ToolFlowOwner {
    private final UUID actor;
    private final String session;
    private final Object world;
    private ToolFlowOwner(UUID actor, String session, Object world) {
        this.actor = actor;
        this.session = session;
        this.world = world;
    }
    public UUID actor() { return actor; }
    public String session() { return session; }
    public Object world() { return world; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ToolFlowOwner)) return false;
        ToolFlowOwner that = (ToolFlowOwner) other;
        return java.util.Objects.equals(actor, that.actor) && java.util.Objects.equals(session, that.session) && java.util.Objects.equals(world, that.world);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(actor);
        hash = 31 * hash + java.util.Objects.hashCode(session);
        hash = 31 * hash + java.util.Objects.hashCode(world);
        return hash;
    }
    @Override public String toString() { return "ToolFlowOwner[actor=" + actor + ", session=" + session + ", world=" + world + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ToolFlowOwner> schema() {
            return new dev.openallay.value.ValueSchema<>(ToolFlowOwner.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ToolFlowOwner>>asList(new dev.openallay.value.ValueSchema.Component<>(ToolFlowOwner.class, "actor", ToolFlowOwner::actor), new dev.openallay.value.ValueSchema.Component<>(ToolFlowOwner.class, "session", ToolFlowOwner::session), new dev.openallay.value.ValueSchema.Component<>(ToolFlowOwner.class, "world", ToolFlowOwner::world)), arguments -> new ToolFlowOwner((UUID) arguments[0], (String) arguments[1], (Object) arguments[2]));
        }
    }
}
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
    private net.minecraft.network.chat.Component telemetryContext = MinecraftComponents.empty();
    private net.minecraft.network.chat.Component telemetryInput = MinecraftComponents.empty();
    private net.minecraft.network.chat.Component telemetryOutput = MinecraftComponents.empty();
    private net.minecraft.network.chat.Component telemetryCost = MinecraftComponents.empty();
    private net.minecraft.network.chat.Component telemetryCompact = MinecraftComponents.empty();
    private List<net.minecraft.network.chat.Component> telemetryTooltip = dev.openallay.util.Java8Collections.listOf();
    private List<GuideTextLine> telemetryTooltipWrapped = dev.openallay.util.Java8Collections.listOf();
    private List<String> telemetryTooltipTexts = dev.openallay.util.Java8Collections.listOf();
    private net.minecraft.client.gui.Font telemetryTooltipFont;
    private Object telemetryTooltipLanguage;
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
                GuideClientUiStates.create(service, event -> MinecraftClientWindow.execute(MinecraftClientWindow.instance(), event)));
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
        recipeClient.failure().ifPresent(failure -> startupNotices.add(MinecraftComponents.getString(MinecraftComponents.translatable(
                "screen.openallay.recipe.invalid_config", failure.code()))));
        if (displayFailure != null) {
            startupNotices.add(MinecraftComponents.getString(MinecraftComponents.translatable(
                    "screen.openallay.debug.invalid_config")));
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
        net.minecraft.network.chat.Component title = headerTitle();
        layout = GuideUiLayout.calculate(width, height, detailOpen(),
                GuideNativeFont.width(font, GuideNativeFont.visual(title)),
                GuideNativeFont.width(font, MinecraftComponents.translatable("screen.openallay.action.sessions")) + 12,
                GuideNativeFont.width(font, MinecraftComponents.translatable("screen.openallay.action.export")) + 12,
                GuideNativeFont.width(font, MinecraftComponents.translatable("screen.openallay.action.refresh")) + 12,
                settingsOpener != null, hasComposerImagePreviews(), composerRequestActive(), pendingMessages().size(), railVisible);
        composerExtras = layout.composerExtras(hasComposerImagePreviews(), composerRequestActive(), pendingMessages().size());
        composerLayoutKey = currentComposerLayoutKey();
        GuideUiLayout.Header header = layout.header();
        headerTitleWidget = addGuideWidget(new HeaderTitle(title, header.title()));
        renderedTelemetryBounds = null;
        renderedTelemetryRows = 0;
        clearToolPaintReceipts();
        net.minecraft.network.chat.Component sessionsLabel = MinecraftComponents.translatable("screen.openallay.action.sessions");
        net.minecraft.network.chat.Component sessionsText = GuideNativeFont.width(font, sessionsLabel) + 8 <= header.sessions().width()
                ? sessionsLabel : MinecraftComponents.literal("≡");
        addGuideWidget(OpenAllayButton.create(sessionsText, button -> toggleSessions())
                .bounds(header.sessions().x(), header.sessions().y(), header.sessions().width(), 20)
                .tooltip(GuideTooltip.create(sessionsLabel))
                .createNarration(ignored -> MinecraftComponents.copy(sessionsLabel)).build());
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
        addGuideWidgetHandle(composer.widget());
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
                && voiceKeyAllowed(composer != null && guideWidgetFocused(composer.widget()), sessionOverlay || overflowOpen || modelSelectorOpen)
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
        if ((composer == null || !guideWidgetFocused(composer.widget())) && !detailOpen() && !modelSelectorOpen && !sessionOverlay && !overflowOpen && scrollTranscriptKey(input.intent())) return true;
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
                modelSelectorCursor = net.minecraft.util.Mth.clamp(modelSelectorCursor, 0, choices - 1);
                selectModel(view.modelChoices().get(modelSelectorCursor));
            }
            return true;
        }
        if (closesDetailFirst(detailOpen(), input.intent() == GuideKeyIntent.ESCAPE)) {
            closeDetail();
            return true;
        }
        if (detailOpen() && (composer == null || !guideWidgetFocused(composer.widget())) && scrollDetailKey(input.intent())) return true;
        if (composer != null && guideWidgetFocused(composer.widget()) && input.paste()) {
            // Preserve Minecraft's text paste and selection semantics, including text+image clipboards.
            synchronizeComposerSession();
            super.guideKeyPressed(event);
            composerImages.paste();
            return true;
        }
        // Native confirmation also includes Space, which must remain text/IME input here.
        ComposerKeyAction composerAction = composerKeyAction(
                composer != null && guideWidgetFocused(composer.widget()),
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
            List<Hit> focusable = dev.openallay.util.Java8Collections.toList(hits.stream()
                    .filter(hit -> (sessionOverlay ? hit.kind() == HitKind.SESSION
                            : overflowOpen ? hit.kind() == HitKind.MENU
                            : isContentFocusTarget(detailOpen(), hit.kind() == HitKind.DETAIL,
                                    hit.kind() == HitKind.CONTENT || hit.kind() == HitKind.COMPOSER || hit.kind() == HitKind.SESSION))
                            && hit.focusId() != null));
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
        switch ((actions.status().state())) {
case STARTING:
case RECORDING:
{
actions.release();
break;
}
case TRANSCRIBING:
case DELIVERING:
{
actions.cancel(dev.openallay.client.voice.VoiceRuntime.CancelReason.USER);
break;
}
default:
{
actions.press();
break;
}
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
        int $oaSwitch8_exit_result;
$oaSwitch8_exit: {
switch ((key)) {
case UP:
{
$oaSwitch8_exit_result = detailScroll - 24; break $oaSwitch8_exit;
}
case DOWN:
{
$oaSwitch8_exit_result = detailScroll + 24; break $oaSwitch8_exit;
}
case PAGE_UP:
{
$oaSwitch8_exit_result = detailScroll - page; break $oaSwitch8_exit;
}
case PAGE_DOWN:
{
$oaSwitch8_exit_result = detailScroll + page; break $oaSwitch8_exit;
}
case HOME:
{
$oaSwitch8_exit_result = 0; break $oaSwitch8_exit;
}
case END:
{
$oaSwitch8_exit_result = maximum; break $oaSwitch8_exit;
}
default:
{
$oaSwitch8_exit_result = Integer.MIN_VALUE; break $oaSwitch8_exit;
}
}
}
int target = $oaSwitch8_exit_result;
        if (target == Integer.MIN_VALUE) return false;
        invalidateContentHits();
        detailScroll = net.minecraft.util.Mth.clamp(target, 0, maximum);
        return true;
    }

    private int maximumDetailScroll() {
        return Math.max(0, detailContentHeight - layout.detail().height() + 34);
    }

    @Override
    public boolean guideMouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollX != 0 || scrollY != 0) invalidateContentHits();
        if (sessionOverlay && sessionBounds().contains(x, y) || layout.sessionRail().contains(x, y)) {
            sessionScroll = net.minecraft.util.Mth.clamp(sessionScroll - (int) Math.signum(scrollY), 0, maximumSessionScroll());
            return true;
        }
        if (sessionOverlay || overflowOpen) return true;
        if (composerExtras != null && composerExtras.images().contains(x, y)) {
            imageScroll = Math.max(0, imageScroll - (int) Math.signum(scrollY));
            return true;
        }
        if (composerExtras != null && composerExtras.footer().contains(x, y) && !pendingMessages().isEmpty()) {
            pendingCursor = net.minecraft.util.Mth.clamp(pendingCursor - (int) Math.signum(scrollY), 0, pendingMessages().size() - 1);
            return true;
        }
        GuideUiLayout.Rect modelMenu = modelSelectorBounds();
        if (modelSelectorOpen && modelMenu != null && modelMenu.contains(x, y)) {
            int maximum = Math.max(0, view.modelChoices().size() - visibleModelChoiceCount());
            modelSelectorScroll = net.minecraft.util.Mth.clamp(
                    modelSelectorScroll - (int) Math.signum(scrollY), 0, maximum);
            modelSelectorCursor = net.minecraft.util.Mth.clamp(
                    modelSelectorCursor,
                    modelSelectorScroll,
                    Math.min(view.modelChoices().size() - 1,
                            modelSelectorScroll + visibleModelChoiceCount() - 1));
            return true;
        }
        if (detailOpen() && layout.detail().contains(x, y)) {
            detailScroll = net.minecraft.util.Mth.clamp(detailScroll - (int) Math.round(scrollY * 24), 0, maximumDetailScroll());
            return true;
        }
        if (detailOpen() && layout.detailOverlay()) return true;
        if (layout.transcript().contains(x, y)) {
            int maximum = virtualizer.maximumScroll(Math.max(0, layout.transcript().height() - 14));
            scroll = net.minecraft.util.Mth.clamp(scroll - (int) Math.round(scrollY * 24), 0, maximum);
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
                for (Hit hit : dev.openallay.util.Java8Collections.listCopyOf(hits)) {
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
                for (Hit hit : dev.openallay.util.Java8Collections.listCopyOf(hits)) {
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
                List<Hit> detailHits = dev.openallay.util.Java8Collections.toList(hits.stream()
                        .filter(hit -> hit.kind() == HitKind.DETAIL));
                GuideUiClickRoute route = GuideUiClickRoute.resolveDetail(
                        layout.detail(),
                        detailCloseBounds(),
                        dev.openallay.util.Java8Collections.toList(detailHits.stream().map(Hit::rect)),
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
                for (Hit hit : dev.openallay.util.Java8Collections.listCopyOf(hits)) {
                    if (hit.kind() == HitKind.SESSION && hit.rect().contains(event.x(), event.y())) {
                        GuideNativeFocus.clear(this);
                        hit.action().run();
                        return true;
                    }
                }
            }
            for (Hit hit : dev.openallay.util.Java8Collections.listCopyOf(hits)) {
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
                || !composer.widget().guideActive() || !composer.widget().guideVisible() || getFocused() != null
                || sessionOverlay || overflowOpen || modelSelectorOpen || detailOpen()
                || draftIntent().editing()) return false;
        setFocused(composer.widget());
        return guideWidgetFocused(composer.widget());
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
            net.minecraft.network.chat.Component status = modelStatus();
            boundedHeaderText(graphics, status, layout.header().status(), MUTED);
            if (layout.header().status().contains(mouseX, mouseY)) {
                graphics.setTooltipForNextFrame(font, status, mouseX, mouseY);
            }
        }
    }

    static net.minecraft.network.chat.Component headerTitle() {
        return MinecraftComponents.style(MinecraftComponents.translatable("screen.openallay.guide"), net.minecraft.ChatFormatting.BOLD);
    }

    /** A passive native label: Tab reveals and narrates its own complete name, not another button's. */
    private final class HeaderTitle extends GuideNativeWidget {
        private net.minecraft.network.chat.Component paintedTitle;

        private HeaderTitle(net.minecraft.network.chat.Component title, GuideUiLayout.Rect bounds) {
            super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), title);
            setTooltip(GuideTooltip.create(title));
        }

        @Override
        protected void paintGuideWidget(
                GuideGraphics graphics, int mouseX, int mouseY, float partialTick) {
            net.minecraft.network.chat.Component full = getMessage();
            net.minecraft.network.chat.Component visible = full;
            if (GuideNativeFont.width(font, GuideNativeFont.visual(full)) > getWidth()) {
                String prefix = GuideNativeFont.plainSubstrByWidth(font, MinecraftComponents.getString(full),
                        Math.max(0, getWidth() - GuideNativeFont.width(font, MinecraftComponents.style(MinecraftComponents.literal("…"), full.getStyle()))),
                        full.getStyle());
                visible = MinecraftComponents.style(MinecraftComponents.literal(prefix + "…"), full.getStyle());
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
        net.minecraft.network.chat.Component full = headerTitleWidget.getMessage();
        int styledWidth = GuideNativeFont.width(font, GuideNativeFont.visual(full));
        return dev.openallay.util.Java8Collections.mapOf("fullName", MinecraftComponents.getString(full), "plainWidth", GuideNativeFont.width(font, GuideNativeFont.visual(MinecraftComponents.style(MinecraftComponents.copy(full), style -> GuideNativeTextStyle.bold(style, false)))), "styleWidth", styledWidth, "titleWidth", headerTitleWidget.getWidth(), "headerHeight", layout.topBar().height(), "fullVisible", headerTitleWidget.paintedTitle != null
                        && MinecraftComponents.getString(headerTitleWidget.paintedTitle).equals(MinecraftComponents.getString(full))
                        && styledWidth <= headerTitleWidget.getWidth());
    }

    /** Reports extracted native rows, not a synthetic provider or a visual-acceptance verdict. */
    public Map<String, Object> e2eTelemetryReceipt() {
        requireDevelopmentProbe();
        if (layout == null) throw new IllegalStateException("telemetry is not initialized");
        GuideUiLayout.Rect area = layout.telemetry();
        return dev.openallay.util.Java8Collections.mapOf("card", layout.telemetryCard(), "rowCount", area.equals(renderedTelemetryBounds) ? renderedTelemetryRows : 0, "width", area.width(), "height", area.height(), "contextText", MinecraftComponents.getString(telemetryContext), "cacheText", MinecraftComponents.getString(telemetryInput), "costText", MinecraftComponents.getString(telemetryCost), "imageBarEligible", telemetryImageBarEligible());
    }

    /** Cached aggregate telemetry only. A tooltip request is not a screenshot or visual acceptance. */
    public Map<String, Object> e2eTelemetryTooltipReceipt() {
        requireDevelopmentProbe();
        return dev.openallay.util.Java8Collections.mapOf("logicalLineCount", telemetryTooltip.size(), "wrappedLineCount", telemetryTooltipWrapped.size(), "wrapWidth", telemetryTooltipWidth, "logicalTexts", telemetryTooltipTexts, "requestedWidth", requestedTelemetryTooltipWidth, "requestedLineCount", requestedTelemetryTooltipLines, "requestedNativeFrame", requestedTelemetryTooltipFrame);
    }

    /** Cached outcomes from the real export handler. Reading this receipt never captures or exports data. */
    public Map<String, Object> e2eExportReceipt() {
        requireDevelopmentProbe();
        return dev.openallay.util.Java8Collections.mapOf("running", exportRunning, "noticeSeverity", notice.severity().name(), "noticeMessage", notice.message(), "noticeKey", notice == exportNotice ? exportNoticeKey : "", "lastFilename", lastExportFilename, "lastRequestCount", lastExportRequestCount);
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
        receipt.put("visibleToolIds", dev.openallay.util.Java8Collections.listCopyOf(renderedToolIds));
        receipt.put("visibleToolCount", renderedToolIds.size());
        receipt.put("toolSummaries", dev.openallay.util.Java8Collections.listCopyOf(renderedToolSummaries));
        receipt.put("summaryCapsuleIds", dev.openallay.util.Java8Collections.listCopyOf(renderedSummaryCapsuleIds));
        receipt.put("summaryCapsuleCount", renderedSummaryCapsuleIds.size());
        receipt.put("resultCardIds", dev.openallay.util.Java8Collections.listCopyOf(renderedResultCardIds));
        receipt.put("resultCardCount", renderedResultCardIds.size());
        receipt.put("detailToolId", renderedDetailToolId);
        receipt.put("detailCardIds", dev.openallay.util.Java8Collections.listCopyOf(renderedDetailCardIds));
        receipt.put("detailCardCount", renderedDetailCardIds.size());
        receipt.put("detailNativeRecipeIds", dev.openallay.util.Java8Collections.listCopyOf(renderedDetailNativeRecipeIds));
        receipt.put("detailNativeRecipeCount", renderedDetailNativeRecipeIds.size());
        receipt.put("totalToolCount", view.rows().stream().filter(GuideUiRow.Tool.class::isInstance).count());
        return dev.openallay.util.Java8Collections.mapCopyOf(receipt);
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
            GuideGraphics graphics, net.minecraft.network.chat.Component text, GuideUiLayout.Rect bounds, int color) {
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
        int $oaSwitch9_exit_result;
$oaSwitch9_exit: {
switch ((key)) {
case UP:
{
$oaSwitch9_exit_result = sessionScroll - 1; break $oaSwitch9_exit;
}
case DOWN:
{
$oaSwitch9_exit_result = sessionScroll + 1; break $oaSwitch9_exit;
}
case PAGE_UP:
{
$oaSwitch9_exit_result = sessionScroll - visibleSessionCount(); break $oaSwitch9_exit;
}
case PAGE_DOWN:
{
$oaSwitch9_exit_result = sessionScroll + visibleSessionCount(); break $oaSwitch9_exit;
}
case HOME:
{
$oaSwitch9_exit_result = 0; break $oaSwitch9_exit;
}
case END:
{
$oaSwitch9_exit_result = maximumSessionScroll(); break $oaSwitch9_exit;
}
default:
{
$oaSwitch9_exit_result = Integer.MIN_VALUE; break $oaSwitch9_exit;
}
}
}
int target = $oaSwitch9_exit_result;
        if (target == Integer.MIN_VALUE) return false;
        sessionScroll = net.minecraft.util.Mth.clamp(target, 0, maximumSessionScroll());
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
                    "session:close", MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.detail.close"))));
        }
        int y = rail.y() + 24;
        sessionScroll = net.minecraft.util.Mth.clamp(sessionScroll, 0, maximumSessionScroll());
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
        return new ToolFlowOwner(service.snapshot().actorId(), view.selectedSession(), minecraft == null ? null : MinecraftClientWindow.world(minecraft));
    }

    private boolean scrollTranscriptKey(GuideKeyIntent key) {
        int maximum = virtualizer.maximumScroll(transcriptViewportHeight());
        int page = Math.max(20, transcriptViewportHeight() - 10);
        int $oaSwitch2_exit_result;
$oaSwitch2_exit: {
switch ((key)) {
case PAGE_UP:
{
$oaSwitch2_exit_result = scroll - page; break $oaSwitch2_exit;
}
case PAGE_DOWN:
{
$oaSwitch2_exit_result = scroll + page; break $oaSwitch2_exit;
}
case HOME:
{
$oaSwitch2_exit_result = 0; break $oaSwitch2_exit;
}
case END:
{
$oaSwitch2_exit_result = maximum; break $oaSwitch2_exit;
}
default:
{
$oaSwitch2_exit_result = Integer.MIN_VALUE; break $oaSwitch2_exit;
}
}
}
int next = $oaSwitch2_exit_result;
        if (next == Integer.MIN_VALUE) return false;
        invalidateContentHits();
        scroll = net.minecraft.util.Mth.clamp(next, 0, maximum);
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
            net.minecraft.network.chat.Component label = MinecraftComponents.translatable(labels[index]);
            boundedHeaderText(graphics, label, new GuideUiLayout.Rect(row.x() + 4, row.y() + 5, row.width() - 8, 12), TEXT);
            Runnable action = actions[index];
            hits.add(new Hit(row, HitKind.MENU, () -> { overflowOpen = false; action.run(); }, "menu:" + index, MinecraftComponents.getString(label)));
        }
    }
    private void microphoneAction() {
        activateVoice(voice);
    }

    private net.minecraft.network.chat.Component voiceFeedback() {
        dev.openallay.client.voice.VoiceStatusPresentation.Notice feedback = dev.openallay.client.voice.VoiceStatusPresentation.describe(voice.status());
        net.minecraft.network.chat.Component text = MinecraftComponents.copy(MinecraftComponents.translatable(feedback.translationKey()));
        if (!dev.openallay.util.Java8Strings.isBlank(feedback.actionTranslationKey())) MinecraftComponents.append(MinecraftComponents.append(text, " · "), MinecraftComponents.translatable(feedback.actionTranslationKey()));
        return text;
    }

    private void renderLocalNotice(GuideGraphics graphics, int mouseX, int mouseY) {
        GuideUiLayout.Rect bounds = layout.composerNotice();
        if (voice != null && voice.enabled() && microphone == null) {
            int micWidth = voice.status().active() ? Math.min(70, bounds.width() / 2) : 30;
            GuideUiLayout.Rect mic = new GuideUiLayout.Rect(bounds.right() - micWidth, bounds.y(), micWidth, bounds.height());
            graphics.fill(mic.x(), mic.y(), mic.right(), mic.bottom(), panelAltColor());
            net.minecraft.network.chat.Component micLabel = voice.status().active()
                    ? MinecraftComponents.translatable("screen.openallay.voice.short." + voice.status().state().name().toLowerCase(java.util.Locale.ROOT), voice.status().elapsedMillis() / 1000)
                    : MinecraftComponents.translatable("screen.openallay.voice.mic_short");
            boundedHeaderText(graphics, micLabel, mic, voice.status().active() ? OpenAllayWidgetTheme.WARNING : ACCENT);
            hits.add(new Hit(mic, HitKind.COMPOSER, this::microphoneAction, "voice:mic", MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.voice.mic"))));
            if (mic.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font, voiceFeedback(), mouseX, mouseY);
            bounds = new GuideUiLayout.Rect(bounds.x(), bounds.y(), bounds.width() - micWidth - 2, bounds.height());
        }
        if (!notice.empty()) {
            boundedHeaderText(graphics, MinecraftComponents.literal(notice.message()), bounds, notice.color());
            if (bounds.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font, MinecraftComponents.literal(notice.message()), mouseX, mouseY);
        } else if (voice != null && voice.status().indicatorVisible()) {
            net.minecraft.network.chat.Component status = voiceFeedback();
            dev.openallay.client.voice.VoiceStatusPresentation.Notice feedback = dev.openallay.client.voice.VoiceStatusPresentation.describe(voice.status());
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
            }, "composer:reset-edit", MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.pending.use_as_new"))));
            return; // The missing pending-edit target requires an explicit player action.
        }
        List<GuideClientUiState.PendingInsertion> pending = uiState.pendingInsertions(view.selectedSession());
        if (!pending.isEmpty()) {
            GuideUiLayout.Rect action = new GuideUiLayout.Rect(bounds.right() - Math.min(100, bounds.width()), bounds.y(), Math.min(100, bounds.width()), bounds.height());
            graphics.fill(action.x(), action.y(), action.right(), action.bottom(), panelAltColor());
            boundedHeaderText(graphics, MinecraftComponents.translatable("screen.openallay.voice.pending", pending.size()), action, ACCENT);
            hits.add(new Hit(action, HitKind.COMPOSER, () -> uiState.applyPendingInsertion(pending.get(0).id()),
                    "voice:pending", MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.voice.pending", pending.size()))));
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
            for (dev.openallay.guide.GuidePresentationEvent event : notifications.receipts(service, view.selectedSession())) {
                if (event.content().isEmpty()) continue;
                List<GuideUiLayout.Rect> content = dev.openallay.util.Java8Collections.toList(event.content().stream().map(ref ->
                        view.rows().stream().filter(row -> receiptMatchesRow(event, ref, row)).findFirst()
                                .map(row -> renderedRows.get(ref.contentId().startsWith("node:")
                                        ? rowId(row) + ":" + ref.contentId() : rowId(row))).orElse(null)));
                if (content.stream().filter(Objects::nonNull).anyMatch(bounds -> intersects(bounds, viewport))) visible.add(event.key());
                if (content.stream().allMatch(bounds -> bounds != null && bounds.y() >= viewport.y() && bounds.bottom() <= viewport.bottom())) seen.add(event.key());
            }
        }
        boolean active = minecraft != null && MinecraftClientWindow.focused(minecraft);
        notifications.visible(service, view.selectedSession(), visible, active);
        if (active) notifications.markSeen(seen);
    }

    private static boolean receiptMatchesRow(dev.openallay.guide.GuidePresentationEvent event,
            dev.openallay.guide.GuidePresentationEvent.ContentRef ref, GuideUiRow row) {
        // Tool transcript rows show summaries, not the original tool cards. Never acknowledge those card refs.
        if (ref.contentId().startsWith("tool:")) return false;
        Objects.requireNonNull(row);
        UUID request;
        final class $oaPattern0_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Assistant bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Assistant && (($oaPattern0_holder.bound = (GuideUiRow.Assistant) $oaPattern0_holder.value) != null))) {
            request = $oaPattern0_holder.bound.requestId();
        } else {
final class $oaPattern1_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Tool bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Tool && (($oaPattern1_holder.bound = (GuideUiRow.Tool) $oaPattern1_holder.value) != null))) {
            request = $oaPattern1_holder.bound.requestId();
        } else {
final class $oaPattern2_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Status bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Status && (($oaPattern2_holder.bound = (GuideUiRow.Status) $oaPattern2_holder.value) != null))) {
            request = $oaPattern2_holder.bound.requestId();
        } else {
            request = null;
        }
}
}
        int ordinal;
        final class $oaPattern3_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Assistant bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Assistant && (($oaPattern3_holder.bound = (GuideUiRow.Assistant) $oaPattern3_holder.value) != null))) {
            ordinal = $oaPattern3_holder.bound.ordinal();
        } else {
final class $oaPattern4_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Tool bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Tool && (($oaPattern4_holder.bound = (GuideUiRow.Tool) $oaPattern4_holder.value) != null))) {
            ordinal = $oaPattern4_holder.bound.ordinal();
        } else if (row instanceof GuideUiRow.Status) {
            ordinal = -1;
        } else {
            ordinal = Integer.MIN_VALUE;
        }
}
        if (ref.contentId().equals("reply") && !(row instanceof GuideUiRow.Assistant)) return false;
        if (ref.contentId().startsWith("node:") && !(row instanceof GuideUiRow.Assistant)) return false;
        if (ref.timelineOrdinal() == -1 && !(row instanceof GuideUiRow.Status)) return false;
        return event.key().requestId().equals(request) && ordinal == ref.timelineOrdinal();
    }

    private void refreshTelemetry() {
        dev.openallay.guide.GuideTelemetrySnapshot next = service.telemetry();
        java.lang.Object language = GuideNativeFont.languageIdentity();
        int wrapWidth = nativeTooltipWidth(width);
        if (telemetry == next && telemetryTooltipFont == font
                && telemetryTooltipLanguage == language && telemetryTooltipWidth == wrapWidth) return;
        telemetry = next;
        String unknown = MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.telemetry.unknown"));
        dev.openallay.guide.GuideContextEstimate context = telemetry.context();
        boolean contextKnown = context != null && context.budget() != null;
        boolean imageUnknown = contextKnown && context.imageAccounting()
                == dev.openallay.model.tokenizer.TokenizerMetadata.ImageAccounting.UNKNOWN;
        String occupancy = contextKnown
                ? "~" + compactTokens(context.estimatedTokens()) + "/" + compactTokens(context.budget().contextWindowTokens())
                : unknown;
        if (imageUnknown) occupancy = MinecraftComponents.getString(MinecraftComponents.translatable(
                "screen.openallay.telemetry.text_estimate", occupancy));
        telemetryContext = MinecraftComponents.literal(occupancy);
        dev.openallay.guide.GuideUsageSnapshot usage = telemetry.sessionUsage();
        java.math.BigDecimal rate = usage.cacheHitRate();
        String cache = telemetryCacheText(usage, unknown);
        telemetryInput = rate == null ? MinecraftComponents.translatable("screen.openallay.telemetry.cache_compact_unknown")
                : MinecraftComponents.translatable("screen.openallay.telemetry.cache", cache);
        String cost = telemetryCostText(usage, unknown);
        telemetryCost = MinecraftComponents.translatable("screen.openallay.telemetry.cost_compact", cost);
        telemetryCompact = MinecraftComponents.translatable("screen.openallay.telemetry.compact", occupancy, cache, cost);
        telemetryTooltip = telemetryTooltipComponents(telemetry, unknown, cost, cache);
        telemetryTooltipWrapped = wrapNativeTooltip(telemetryTooltip, wrapWidth, (text, width) -> GuideNativeFont.split(font, text, width));
        telemetryTooltipTexts = dev.openallay.util.Java8Collections.toList(telemetryTooltip.stream().map(MinecraftComponents::getString));
        telemetryTooltipFont = font;
        telemetryTooltipLanguage = language;
        telemetryTooltipWidth = wrapWidth;
    }

    /** Logical native lines remain separate before the font performs bounded wrapping. */
    static List<net.minecraft.network.chat.Component> telemetryTooltipComponents(
            dev.openallay.guide.GuideTelemetrySnapshot telemetry, String unknown, String cost, String cache) {
        List<net.minecraft.network.chat.Component> detail = new ArrayList<>();
        detail.add(MinecraftComponents.style(MinecraftComponents.translatable("screen.openallay.telemetry.latest"), net.minecraft.ChatFormatting.BOLD));
        dev.openallay.guide.GuideContextEstimate context = telemetry.context();
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
        dev.openallay.guide.GuideUsageSnapshot usage = telemetry.sessionUsage();
        detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.session.calls", usage.actualCalls()));
        detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.session.cost", cost));
        detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.cache_detail",
                cache, usage.cacheReadTokens(), usage.inputTokens()));
        if (usage.costIncomplete()) detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.partial"));
        if (usage.cacheIncomplete()) detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.cache_unknown"));
        dev.openallay.guide.GuideUsageSnapshot inherited = telemetry.inheritedUsage();
        if (inherited.actualCalls() > 0) {
            String reference = telemetryCostText(inherited, unknown);
            detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.inherited", reference));
        }
        detail.add(MinecraftComponents.translatable("screen.openallay.telemetry.price_note"));
        return dev.openallay.util.Java8Collections.listCopyOf(detail);
    }

    static String telemetryCacheText(dev.openallay.guide.GuideUsageSnapshot usage, String unknown) {
        java.math.BigDecimal rate = usage.cacheHitRate();
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

    static <T> List<T> wrapNativeTooltip(List<net.minecraft.network.chat.Component> logicalLines, int wrapWidth,
            java.util.function.BiFunction<net.minecraft.network.chat.Component, Integer, List<T>> splitter) {
        return dev.openallay.util.Java8Collections.toList(logicalLines.stream().flatMap(line -> splitter.apply(line, wrapWidth).stream()));
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

    static net.minecraft.network.chat.Component progressMessage(
            GuideUiProgress progress, Instant now, boolean debugMode) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(now, "now");
        net.minecraft.network.chat.Component message = MinecraftComponents.translatable(progress.activityTranslationKey());
        MinecraftComponents.append(MinecraftComponents.append(message, " · "), MinecraftComponents.translatable(
                "screen.openallay.progress.elapsed",
                formatDuration(Duration.between(progress.requestStartedAt(), now))));
        if (progress.retryAt() != null) {
            MinecraftComponents.append(MinecraftComponents.append(message, " · "), MinecraftComponents.translatable(
                    "screen.openallay.progress.retry_in",
                    formatDuration(Duration.between(now, progress.retryAt()))));
            if (progress.attempt() > 0) {
                MinecraftComponents.append(MinecraftComponents.append(message, " · "), MinecraftComponents.translatable(
                        "screen.openallay.progress.attempt", progress.attempt()));
            }
        } else if (progress.deadlineAt() != null) {
            MinecraftComponents.append(MinecraftComponents.append(message, " · "), MinecraftComponents.translatable(
                    "screen.openallay.progress.remaining",
                    formatDuration(Duration.between(now, progress.deadlineAt()))));
        } else if (progress.phase()
                == dev.openallay.guide.GuideRequestPhase.RESPONSE_STREAMING) {
            MinecraftComponents.append(MinecraftComponents.append(message, " · "), MinecraftComponents.translatable(
                    "screen.openallay.progress.last_update",
                    formatDuration(Duration.between(progress.lastProgressAt(), now))));
        }
        if (debugMode && progress.retryAt() == null && progress.attempt() > 0) {
            MinecraftComponents.append(MinecraftComponents.append(message, " · "), MinecraftComponents.translatable(
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
                    "transcript:bottom", MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.scroll.bottom"))));
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
        final class $oaPattern5_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.User bound; }
final $oaPattern5_Holder $oaPattern5_holder = new $oaPattern5_Holder();
if ((($oaPattern5_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.User && (($oaPattern5_holder.bound = (GuideUiRow.User) $oaPattern5_holder.value) != null))) {
            graphics.text(font, MinecraftComponents.translatable("screen.openallay.speaker.user"),
                    x, y, ACCENT, false);
            renderCopyAction(graphics, row, $oaPattern5_holder.bound.text(), x, y, width);
            renderForkAction(graphics, $oaPattern5_holder.bound.requestId(), $oaPattern5_holder.bound.text(), x, y, width);
            y += 11;
            y = renderWrapped(graphics, GuideMarkup.paragraphs($oaPattern5_holder.bound.text()), x + 6, y, width - 6, TEXT);
            return y + rowSpacing();
        }
        final class $oaPattern6_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Assistant bound; }
final $oaPattern6_Holder $oaPattern6_holder = new $oaPattern6_Holder();
if ((($oaPattern6_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Assistant && (($oaPattern6_holder.bound = (GuideUiRow.Assistant) $oaPattern6_holder.value) != null))) {
            graphics.fill(x, y, x + 2, y + 9, ACCENT);
            graphics.text(font, assistantLabel(projectedDisplay, $oaPattern6_holder.bound.streaming()),
                    x + 6, y, ACCENT, false);
            renderCopyAction(graphics, row, $oaPattern6_holder.bound.text(), x, y, width);
            if (completedAssistantBoundary(service.snapshot(), view.selectedSession(), $oaPattern6_holder.bound)) {
                renderForkAction(graphics, $oaPattern6_holder.bound.requestId(), $oaPattern6_holder.bound.text(), x, y, width);
            }
            y += 11;
            if (dev.openallay.util.Java8Strings.isBlank($oaPattern6_holder.bound.text())) {
                graphics.text(font, MinecraftComponents.translatable(
                                "screen.openallay.assistant.preparing"),
                        x + 6, y, MUTED, false);
                y += 10;
            } else {
                SemanticLayout semantic = semanticLayout($oaPattern6_holder.bound, width - 6);
                MinecraftSemanticRenderer.Result rendered = semanticRenderer.render(
                        graphics, font, semantic, x + 6, y, width - 6,
                        mouseX, mouseY, projectedDisplay.animationsEnabled(),
                        presentationTicks,
                        (nativeGraphics, nativeFont, component, bounds,
                                nativeMouseX, nativeMouseY, ticks) -> renderNativeRecipe(
                                        $oaPattern6_holder.bound,
                                        nativeGraphics,
                                        nativeFont,
                                        component,
                                        bounds,
                                        nativeMouseX,
                                        nativeMouseY,
                                        ticks));
                int nodeY = y;
                for (SemanticLayout.Line line : semantic.lines()) {
                    String nodeKey = rowId($oaPattern6_holder.bound) + ":node:" + line.nodeId();
                    GuideUiLayout.Rect previous = renderedRows.get(nodeKey);
                    int top = previous == null ? nodeY : previous.y();
                    renderedRows.put(nodeKey, new GuideUiLayout.Rect(x + 6, top, width - 6, nodeY + line.height() - top));
                    nodeY += line.height();
                }
                for (MinecraftSemanticRenderer.Hit hit : rendered.hits()) {
                    String focusId = "semantic:" + rowId($oaPattern6_holder.bound) + ":" + hit.intent();
                    hits.add(new Hit(
                            hit.bounds(), HitKind.CONTENT,
                            () -> semanticIntent(hit.intent()), focusId,
                            semanticIntentNarration(hit.intent())));
                }
                y = rendered.bottom();
            }
            List<GuideEvidencePresentation.Group> sourceGroups = groupedSources($oaPattern6_holder.bound.sources());
            for (int sourceIndex = 0; sourceIndex < sourceGroups.size(); sourceIndex++) {
                GuideEvidencePresentation.Group source = sourceGroups.get(sourceIndex);
                int sourceY = y;
                String label = sourceLabel(source, projectedDisplay.debugMode());
                String sourceFocusId = sourceFocusId($oaPattern6_holder.bound, source, sourceIndex);
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
        final class $oaPattern7_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Tool bound; }
final $oaPattern7_Holder $oaPattern7_holder = new $oaPattern7_Holder();
if ((($oaPattern7_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Tool && (($oaPattern7_holder.bound = (GuideUiRow.Tool) $oaPattern7_holder.value) != null))) {
            return renderToolSummaryCard(graphics, $oaPattern7_holder.bound, x, y, width, mouseX, mouseY);
        }
        final class $oaPattern8_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Persistence bound; }
final $oaPattern8_Holder $oaPattern8_holder = new $oaPattern8_Holder();
int color = (($oaPattern8_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Persistence && (($oaPattern8_holder.bound = (GuideUiRow.Persistence) $oaPattern8_holder.value) != null))
                ? $oaPattern8_holder.bound.state() == dev.openallay.guide.GuidePersistenceSnapshot.State.UNAVAILABLE
                        ? 0xFFFFD479 : MUTED
                : ((GuideUiRow.Status) row).status() == GuideRequestStatus.RATE_LIMITED
                        ? 0xFFFFD479 : ERROR;
        List<GuideTextLine> lines = GuideNativeFont.split(font, factualRowText(row), Math.max(1, width - 12));
        for (GuideTextLine line : lines) {
            graphics.text(font, line, x + 6, y, color, false);
            y += 10;
        }
        final class $oaPattern9_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Status bound; }
final $oaPattern9_Holder $oaPattern9_holder = new $oaPattern9_Holder();
if ((($oaPattern9_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Status && (($oaPattern9_holder.bound = (GuideUiRow.Status) $oaPattern9_holder.value) != null)) && ($oaPattern9_holder.bound.status() == GuideRequestStatus.FAILED
                || $oaPattern9_holder.bound.status() == GuideRequestStatus.CANCELLED || $oaPattern9_holder.bound.status() == GuideRequestStatus.INTERRUPTED)) {
            GuideUiLayout.Rect retryRow = new GuideUiLayout.Rect(x + 6, y, Math.min(90, width - 12), 14);
            boundedHeaderText(graphics, MinecraftComponents.translatable("screen.openallay.action.retry"), retryRow, ACCENT);
            hits.add(new Hit(retryRow, HitKind.CONTENT, () -> accept(service.retry($oaPattern9_holder.bound.requestId()), ignored -> notice = GuideUiNotice.info("")),
                    "retry:" + $oaPattern9_holder.bound.requestId(), MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.action.retry"))));
            y += 16;
        }
        return y + rowSpacing();
    }

    static net.minecraft.network.chat.Component factualRowText(GuideUiRow row) {
        final class $oaPattern10_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Persistence bound; }
final $oaPattern10_Holder $oaPattern10_holder = new $oaPattern10_Holder();
if ((($oaPattern10_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Persistence && (($oaPattern10_holder.bound = (GuideUiRow.Persistence) $oaPattern10_holder.value) != null))) {
            net.minecraft.network.chat.Component message = MinecraftComponents.translatable($oaPattern10_holder.bound.translationKey());
            return $oaPattern10_holder.bound.failure() == null ? message
                    : MinecraftComponents.append(MinecraftComponents.copy(message), " (" + $oaPattern10_holder.bound.failure().code() + ")");
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
            final class $oaPattern11_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Assistant bound; }
final $oaPattern11_Holder $oaPattern11_holder = new $oaPattern11_Holder();
if ((($oaPattern11_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Assistant && (($oaPattern11_holder.bound = (GuideUiRow.Assistant) $oaPattern11_holder.value) != null))) {
                int hash = $oaPattern11_holder.bound.semantic().hashCode();
                nextHashes.put(id, hash);
                if (!java.util.Objects.equals(semanticHashes.get(id), hash)) {
                    semanticLayouts.invalidateRow(id);
                }
            }
            final class $oaPattern12_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Assistant bound; }
final $oaPattern12_Holder $oaPattern12_holder = new $oaPattern12_Holder();
boolean stabilize = (($oaPattern12_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Assistant && (($oaPattern12_holder.bound = (GuideUiRow.Assistant) $oaPattern12_holder.value) != null))
                    && $oaPattern12_holder.bound.streaming();
            measured.add(new GuideTranscriptVirtualizer.Row(
                    id, stableRowHeights.retain(id, measureRow(row, width), stabilize)));
        }
        stableRowHeights.retainOnly(retainedIds);
        semanticHashes.clear();
        semanticHashes.putAll(nextHashes);
        virtualizer.update(measured);
    }

    private int measureRow(GuideUiRow row, int width) {
        final class $oaPattern13_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.User bound; }
final $oaPattern13_Holder $oaPattern13_holder = new $oaPattern13_Holder();
if ((($oaPattern13_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.User && (($oaPattern13_holder.bound = (GuideUiRow.User) $oaPattern13_holder.value) != null))) {
            return 11 + wrappedHeight(GuideMarkup.paragraphs($oaPattern13_holder.bound.text()), width - 6) + rowSpacing();
        }
        final class $oaPattern14_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Assistant bound; }
final $oaPattern14_Holder $oaPattern14_holder = new $oaPattern14_Holder();
if ((($oaPattern14_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Assistant && (($oaPattern14_holder.bound = (GuideUiRow.Assistant) $oaPattern14_holder.value) != null))) {
            int body = dev.openallay.util.Java8Strings.isBlank($oaPattern14_holder.bound.text())
                    ? 10 : semanticLayout($oaPattern14_holder.bound, width - 6).height();
            return 11 + body + groupedSources($oaPattern14_holder.bound.sources()).size() * 12 + rowSpacing();
        }
        final class $oaPattern15_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Tool bound; }
final $oaPattern15_Holder $oaPattern15_holder = new $oaPattern15_Holder();
if ((($oaPattern15_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Tool && (($oaPattern15_holder.bound = (GuideUiRow.Tool) $oaPattern15_holder.value) != null))) {
            return toolSummaryGeometry($oaPattern15_holder.bound, 0, 0, width).rowHeight();
        }
        final class $oaPattern16_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Status bound; }
final $oaPattern16_Holder $oaPattern16_holder = new $oaPattern16_Holder();
int retryHeight = (($oaPattern16_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Status && (($oaPattern16_holder.bound = (GuideUiRow.Status) $oaPattern16_holder.value) != null)) && ($oaPattern16_holder.bound.status() == GuideRequestStatus.FAILED
                || $oaPattern16_holder.bound.status() == GuideRequestStatus.CANCELLED || $oaPattern16_holder.bound.status() == GuideRequestStatus.INTERRUPTED) ? 16 : 0;
        return GuideNativeFont.split(font, factualRowText(row), Math.max(1, width - 12)).size() * 10 + rowSpacing() + retryHeight;
    }

    private dev.openallay.guide.ui.GuideToolSummaryGeometry toolSummaryGeometry(
            GuideUiRow.Tool tool, int x, int y, int width) {
        dev.openallay.guide.ui.GuideToolSummaryPresenter.Summary summary = dev.openallay.guide.ui.GuideToolSummaryPresenter.project(tool);
        List<Integer> capsules = dev.openallay.util.Java8Collections.toList(summary.capsules().stream().map(capsule ->
                Math.max(22, Math.min(110, 22 + GuideNativeFont.width(font, capsuleLabel(capsule))))));
        return dev.openallay.guide.ui.GuideToolSummaryGeometry.measure(x, y, width,
                GuideNativeFont.width(font, MinecraftComponents.translatable(summary.status().translationKey())),
                capsules, summary.hasDescription(), rowSpacing());
    }

    private int renderToolSummaryCard(
            GuideGraphics graphics, GuideUiRow.Tool tool, int x, int y,
            int width, int mouseX, int mouseY) {
        dev.openallay.guide.ui.GuideToolSummaryPresenter.Summary summary = dev.openallay.guide.ui.GuideToolSummaryPresenter.project(tool);
        dev.openallay.guide.ui.GuideToolSummaryGeometry geometry = toolSummaryGeometry(tool, x, y, width);
        GuideUiLayout.Rect card = geometry.card();
        boolean selected = selectedTool != null && toolFocusId(selectedTool).equals(summary.id());
        boolean hovered = card.contains(mouseX, mouseY) && layout.transcript().contains(mouseX, mouseY);
        renderToolSummaryFrame(graphics, card, panelAltColor(),
                selected || hovered ? ACCENT : OpenAllayWidgetTheme.SLATE_BORDER);
        int $oaSwitch1_exit_result;
$oaSwitch1_exit: {
switch ((summary.status())) {
case FAILED:
{
$oaSwitch1_exit_result = ERROR; break $oaSwitch1_exit;
}
case SUCCEEDED:
{
$oaSwitch1_exit_result = OpenAllayWidgetTheme.SUCCESS; break $oaSwitch1_exit;
}
case RUNNING:
{
$oaSwitch1_exit_result = ACCENT; break $oaSwitch1_exit;
}
case NO_RESULT_RECORDED:
{
$oaSwitch1_exit_result = MUTED; break $oaSwitch1_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
int statusColor = $oaSwitch1_exit_result;
        java.lang.String $oaSwitch4_exit_result;
$oaSwitch4_exit: {
switch ((summary.status())) {
case FAILED:
{
$oaSwitch4_exit_result = "!"; break $oaSwitch4_exit;
}
case SUCCEEDED:
{
$oaSwitch4_exit_result = "✓"; break $oaSwitch4_exit;
}
case RUNNING:
{
$oaSwitch4_exit_result = projectedDisplay.animationsEnabled() && (presentationTicks / 8) % 2 == 0 ? "◍" : "◌"; break $oaSwitch4_exit;
}
case NO_RESULT_RECORDED:
{
$oaSwitch4_exit_result = "—"; break $oaSwitch4_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
String marker = $oaSwitch4_exit_result;
        graphics.text(font, marker, geometry.icon().x(), geometry.icon().y(), statusColor, false);
        net.minecraft.network.chat.Component title = intentTitle(tool.detail().intent(), summary.titleKey());
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
            dev.openallay.guide.ui.GuideToolSummaryPresenter.Capsule capsule = summary.capsules().get(index);
            GuideUiLayout.Rect capsuleBounds = geometry.capsules().get(index);
            if (renderToolSummaryCapsule(graphics, capsule, capsuleBounds, mouseX, mouseY)) {
                paintedCapsules.add(capsule.id());
                if (Boolean.getBoolean("openallay.e2e.enabled")) {
                    capsuleReceipts.add(toolSummaryCapsuleReceipt(capsule, capsuleBounds));
                }
            }
        }
        // Child semantic actions are inserted first. The complete card body then opens real detail.
        toolSummaryHit(card, () -> open(tool), summary.id(), MinecraftComponents.getString(title) + " · "
                + MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.tool.view_details")));
        if (Boolean.getBoolean("openallay.e2e.enabled") && intersects(card, layout.transcript())) {
            renderedToolIds.add(summary.id());
            GuideUiLayout.Rect viewport = layout.transcript();
            int visibleTop = Math.max(card.y(), viewport.y());
            int visibleBottom = Math.min(card.bottom(), viewport.bottom());
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("id", summary.id());
            receipt.put("title", MinecraftComponents.getString(title));
            receipt.put("description", summary.description());
            receipt.put("status", summary.status().name());
            receipt.put("rowHeight", geometry.rowHeight());
            receipt.put("capsuleIds", dev.openallay.util.Java8Collections.listCopyOf(paintedCapsules));
            receipt.put("capsules", dev.openallay.util.Java8Collections.listCopyOf(capsuleReceipts));
            receipt.put("bounds", toolPaintBounds(card));
            receipt.put("titleBounds", toolPaintBounds(geometry.title()));
            receipt.put("blankClickX", card.x() + 2);
            receipt.put("blankClickY", visibleTop + (visibleBottom - visibleTop) / 2);
            renderedToolSummaries.add(dev.openallay.util.Java8Collections.mapCopyOf(receipt));
        }
        return y + geometry.rowHeight();
    }

    private boolean renderToolSummaryCapsule(
            GuideGraphics graphics, dev.openallay.guide.ui.GuideToolSummaryPresenter.Capsule capsule,
            GuideUiLayout.Rect bounds, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY) || isFocused(focusedContentId, capsule.id());
        renderToolSummaryFrame(graphics, bounds, panelColor(), hovered ? ACCENT : OpenAllayWidgetTheme.SLATE_BORDER);
        GuideItemView item = capsule.item();
        net.minecraft.world.item.ItemStack stack = itemStack(item.itemId(), item.count());
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
        final class $oaPattern17_Holder { dev.openallay.guide.ui.GuideToolSummaryPresenter.Capsule value; dev.openallay.guide.ui.GuideToolSummaryPresenter.Item bound; }
final $oaPattern17_Holder $oaPattern17_holder = new $oaPattern17_Holder();
if ((($oaPattern17_holder.value = capsule) instanceof dev.openallay.guide.ui.GuideToolSummaryPresenter.Item && (($oaPattern17_holder.bound = (dev.openallay.guide.ui.GuideToolSummaryPresenter.Item) $oaPattern17_holder.value) != null))) {
            return new MinecraftSemanticRenderer.Intent.BrowseRecipes($oaPattern17_holder.bound.item().itemId());
        } else {
final class $oaPattern18_Holder { dev.openallay.guide.ui.GuideToolSummaryPresenter.Capsule value; dev.openallay.guide.ui.GuideToolSummaryPresenter.Recipe bound; }
final $oaPattern18_Holder $oaPattern18_holder = new $oaPattern18_Holder();
if ((($oaPattern18_holder.value = capsule) instanceof dev.openallay.guide.ui.GuideToolSummaryPresenter.Recipe && (($oaPattern18_holder.bound = (dev.openallay.guide.ui.GuideToolSummaryPresenter.Recipe) $oaPattern18_holder.value) != null))) {
            return new MinecraftSemanticRenderer.Intent.ExactRecipe($oaPattern18_holder.bound.recipe().references().stream()
                    .filter(recipeClient::supportsExact).findFirst().orElse($oaPattern18_holder.bound.recipe().reference()));
        }
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
        final class $oaPattern19_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; MinecraftSemanticRenderer.Intent.ExactRecipe bound; }
final $oaPattern19_Holder $oaPattern19_holder = new $oaPattern19_Holder();
if ((($oaPattern19_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.ExactRecipe && (($oaPattern19_holder.bound = (MinecraftSemanticRenderer.Intent.ExactRecipe) $oaPattern19_holder.value) != null))) {
            receipt.put("action", "ExactRecipe");
            receipt.put("reference", $oaPattern19_holder.bound.reference());
        } else receipt.put("action", "BrowseRecipes");
        return dev.openallay.util.Java8Collections.mapCopyOf(receipt);
    }

    private static Map<String, Integer> toolPaintBounds(GuideUiLayout.Rect bounds) {
        return dev.openallay.util.Java8Collections.mapOf("x", bounds.x(), "y", bounds.y(), "width", bounds.width(), "height", bounds.height());
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
            GuideGraphics graphics, net.minecraft.network.chat.Component text, GuideUiLayout.Rect bounds, int color,
            int mouseX, int mouseY, boolean detailHint) {
        String full = MinecraftComponents.getString(text);
        int available = Math.max(1, bounds.width());
        String visible = GuideNativeFont.width(font, text) <= available ? full
                : GuideNativeFont.plainSubstrByWidth(font, full, Math.max(0, available - GuideNativeFont.width(font, "…")))
                        + (available >= GuideNativeFont.width(font, "…") ? "…" : "");
        graphics.text(font, visible, bounds.x(), bounds.y(), color, false);
        if (bounds.contains(mouseX, mouseY) && layout.transcript().contains(mouseX, mouseY)) {
            net.minecraft.network.chat.Component tooltip = detailHint ? MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(text), "\n"), MinecraftComponents.translatable("screen.openallay.tool.view_details")) : text;
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

    static List<net.minecraft.network.chat.Component> toolFailureComponents(GuideToolDetailView detail, String toolId) {
        if (detail.failure().isPresent()) {
            GuideToolDetailView.Failure failure = detail.failure().orElseThrow();
            List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
            if (!dev.openallay.util.Java8Strings.isBlank(failure.code())) lines.add(MinecraftComponents.literal(failure.code()));
            if (!dev.openallay.util.Java8Strings.isBlank(failure.message())) lines.add(MinecraftComponents.literal(failure.message()));
            if (!lines.isEmpty()) return dev.openallay.util.Java8Collections.listCopyOf(lines);
        }
        if (detail.displayStatus() != GuideToolDisplayStatus.FAILED) return dev.openallay.util.Java8Collections.listOf();
        return dev.openallay.util.Java8Collections.toList(detail.narration().stream().map(message -> friendlyToolMessage(toolId, message)));
    }

    private static net.minecraft.network.chat.Component friendlyToolMessage(String toolId, GuideToolMessage message) {
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
                ? dev.openallay.util.Java8Collections.listOf() : detail.narration();
    }

    static net.minecraft.network.chat.Component toolMessage(GuideToolMessage message) {
        Object[] arguments = message.arguments().stream()
                .map(MinecraftComponents::literal)
                .toArray();
        return MinecraftComponents.translatable(message.key().translationKey(), arguments);
    }

    private int wrappedHeight(List<net.minecraft.network.chat.Component> paragraphs, int width) {
        int height = 0;
        for (net.minecraft.network.chat.Component paragraph : paragraphs) {
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
                        {
final net.minecraft.client.gui.Font $oaSwitch11_exit_result_prior1 = font;
final net.minecraft.network.chat.Component $oaSwitch11_exit_result_prior0 = MinecraftComponents.literal(text);
net.minecraft.ChatFormatting $oaSwitch11_exit_result;
$oaSwitch11_exit: {
switch ((style)) {
case EMPHASIS:
{
$oaSwitch11_exit_result = net.minecraft.ChatFormatting.ITALIC; break $oaSwitch11_exit;
}
case STRONG:
{
$oaSwitch11_exit_result = net.minecraft.ChatFormatting.BOLD; break $oaSwitch11_exit;
}
case CODE:
{
$oaSwitch11_exit_result = net.minecraft.ChatFormatting.GRAY; break $oaSwitch11_exit;
}
case REFERENCE:
{
$oaSwitch11_exit_result = net.minecraft.ChatFormatting.AQUA; break $oaSwitch11_exit;
}
case NORMAL:
{
$oaSwitch11_exit_result = net.minecraft.ChatFormatting.WHITE; break $oaSwitch11_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return GuideNativeFont.width($oaSwitch11_exit_result_prior1, MinecraftComponents.style($oaSwitch11_exit_result_prior0, $oaSwitch11_exit_result));
}
                    }
                    @Override public int lineHeight(SemanticLayout.Kind kind) {
                        return kind == SemanticLayout.Kind.HEADING ? 12 : 10;
                    }
                });
    }

    private static String rowId(GuideUiRow row) {
        java.util.Objects.requireNonNull(row);
        final class $oaPattern20_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Persistence bound; }
final $oaPattern20_Holder $oaPattern20_holder = new $oaPattern20_Holder();
if ((($oaPattern20_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Persistence && (($oaPattern20_holder.bound = (GuideUiRow.Persistence) $oaPattern20_holder.value) != null))) {
            return "persistence:" + $oaPattern20_holder.bound.state();
        } else {
final class $oaPattern21_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.User bound; }
final $oaPattern21_Holder $oaPattern21_holder = new $oaPattern21_Holder();
if ((($oaPattern21_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.User && (($oaPattern21_holder.bound = (GuideUiRow.User) $oaPattern21_holder.value) != null))) {
            return "user:" + $oaPattern21_holder.bound.requestId();
        } else {
final class $oaPattern22_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Assistant bound; }
final $oaPattern22_Holder $oaPattern22_holder = new $oaPattern22_Holder();
if ((($oaPattern22_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Assistant && (($oaPattern22_holder.bound = (GuideUiRow.Assistant) $oaPattern22_holder.value) != null))) {
            return "assistant:" + $oaPattern22_holder.bound.requestId() + ":" + $oaPattern22_holder.bound.ordinal();
        } else {
final class $oaPattern23_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Tool bound; }
final $oaPattern23_Holder $oaPattern23_holder = new $oaPattern23_Holder();
if ((($oaPattern23_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Tool && (($oaPattern23_holder.bound = (GuideUiRow.Tool) $oaPattern23_holder.value) != null))) {
            return "tool:" + $oaPattern23_holder.bound.requestId() + ":" + $oaPattern23_holder.bound.activity().invocationId();
        } else {
final class $oaPattern24_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Status bound; }
final $oaPattern24_Holder $oaPattern24_holder = new $oaPattern24_Holder();
if ((($oaPattern24_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Status && (($oaPattern24_holder.bound = (GuideUiRow.Status) $oaPattern24_holder.value) != null))) {
            return "status:" + $oaPattern24_holder.bound.requestId();
        }
}
}
}
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
        final class $oaPattern25_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; MinecraftSemanticRenderer.Intent.BrowseRecipes bound; }
final $oaPattern25_Holder $oaPattern25_holder = new $oaPattern25_Holder();
if ((($oaPattern25_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.BrowseRecipes && (($oaPattern25_holder.bound = (MinecraftSemanticRenderer.Intent.BrowseRecipes) $oaPattern25_holder.value) != null))) {
            navigate(recipeClient.openRecipes($oaPattern25_holder.bound.itemId()));
        } else {
final class $oaPattern26_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; MinecraftSemanticRenderer.Intent.BrowseUsages bound; }
final $oaPattern26_Holder $oaPattern26_holder = new $oaPattern26_Holder();
if ((($oaPattern26_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.BrowseUsages && (($oaPattern26_holder.bound = (MinecraftSemanticRenderer.Intent.BrowseUsages) $oaPattern26_holder.value) != null))) {
            navigate(recipeClient.openUsages($oaPattern26_holder.bound.itemId()));
        } else {
final class $oaPattern27_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; MinecraftSemanticRenderer.Intent.ExactRecipe bound; }
final $oaPattern27_Holder $oaPattern27_holder = new $oaPattern27_Holder();
if ((($oaPattern27_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.ExactRecipe && (($oaPattern27_holder.bound = (MinecraftSemanticRenderer.Intent.ExactRecipe) $oaPattern27_holder.value) != null))) {
            navigate(recipeClient.openExact($oaPattern27_holder.bound.reference()));
        } else {
final class $oaPattern28_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; MinecraftSemanticRenderer.Intent.Source bound; }
final $oaPattern28_Holder $oaPattern28_holder = new $oaPattern28_Holder();
if ((($oaPattern28_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Source && (($oaPattern28_holder.bound = (MinecraftSemanticRenderer.Intent.Source) $oaPattern28_holder.value) != null))) {
            openSemanticSource($oaPattern28_holder.bound.sourceId(), $oaPattern28_holder.bound.originInvocationId());
        } else {
final class $oaPattern29_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; MinecraftSemanticRenderer.Intent.Evidence bound; }
final $oaPattern29_Holder $oaPattern29_holder = new $oaPattern29_Holder();
if ((($oaPattern29_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Evidence && (($oaPattern29_holder.bound = (MinecraftSemanticRenderer.Intent.Evidence) $oaPattern29_holder.value) != null))) {
            openSemanticSource($oaPattern29_holder.bound.evidenceId(), $oaPattern29_holder.bound.originInvocationId());
        } else {
final class $oaPattern30_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; MinecraftSemanticRenderer.Intent.Choice bound; }
final $oaPattern30_Holder $oaPattern30_holder = new $oaPattern30_Holder();
if ((($oaPattern30_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Choice && (($oaPattern30_holder.bound = (MinecraftSemanticRenderer.Intent.Choice) $oaPattern30_holder.value) != null))) {
            notice = GuideUiNotice.info(
                    MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.choice.unavailable", $oaPattern30_holder.bound.choiceId())));
        }
}
}
}
}
}
    }

    private static String semanticIntentNarration(MinecraftSemanticRenderer.Intent intent) {
        java.util.Objects.requireNonNull(intent);
        final class $oaPattern31_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; MinecraftSemanticRenderer.Intent.BrowseRecipes bound; }
final $oaPattern31_Holder $oaPattern31_holder = new $oaPattern31_Holder();
if ((($oaPattern31_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.BrowseRecipes && (($oaPattern31_holder.bound = (MinecraftSemanticRenderer.Intent.BrowseRecipes) $oaPattern31_holder.value) != null))) {
            return "查看 " + $oaPattern31_holder.bound.itemId() + " 的配方";
        } else {
final class $oaPattern32_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; MinecraftSemanticRenderer.Intent.BrowseUsages bound; }
final $oaPattern32_Holder $oaPattern32_holder = new $oaPattern32_Holder();
if ((($oaPattern32_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.BrowseUsages && (($oaPattern32_holder.bound = (MinecraftSemanticRenderer.Intent.BrowseUsages) $oaPattern32_holder.value) != null))) {
            return "查看 " + $oaPattern32_holder.bound.itemId() + " 的用途";
        } else {
final class $oaPattern33_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; MinecraftSemanticRenderer.Intent.ExactRecipe bound; }
final $oaPattern33_Holder $oaPattern33_holder = new $oaPattern33_Holder();
if ((($oaPattern33_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.ExactRecipe && (($oaPattern33_holder.bound = (MinecraftSemanticRenderer.Intent.ExactRecipe) $oaPattern33_holder.value) != null))) {
            return "打开配方 " + $oaPattern33_holder.bound.reference().recipeId();
        } else {
final class $oaPattern34_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; MinecraftSemanticRenderer.Intent.Source bound; }
final $oaPattern34_Holder $oaPattern34_holder = new $oaPattern34_Holder();
if ((($oaPattern34_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Source && (($oaPattern34_holder.bound = (MinecraftSemanticRenderer.Intent.Source) $oaPattern34_holder.value) != null))) {
            return "查看来源 " + $oaPattern34_holder.bound.sourceId();
        } else {
final class $oaPattern35_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; MinecraftSemanticRenderer.Intent.Evidence bound; }
final $oaPattern35_Holder $oaPattern35_holder = new $oaPattern35_Holder();
if ((($oaPattern35_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Evidence && (($oaPattern35_holder.bound = (MinecraftSemanticRenderer.Intent.Evidence) $oaPattern35_holder.value) != null))) {
            return "查看证据 " + $oaPattern35_holder.bound.evidenceId();
        } else {
final class $oaPattern36_Holder { dev.openallay.client.gui.MinecraftSemanticRenderer.Intent value; MinecraftSemanticRenderer.Intent.Choice bound; }
final $oaPattern36_Holder $oaPattern36_holder = new $oaPattern36_Holder();
if ((($oaPattern36_holder.value = intent) instanceof dev.openallay.client.gui.MinecraftSemanticRenderer.Intent.Choice && (($oaPattern36_holder.bound = (MinecraftSemanticRenderer.Intent.Choice) $oaPattern36_holder.value) != null))) {
            return "选择 " + $oaPattern36_holder.bound.choiceId();
        }
}
}
}
}
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
                    final class $oaPattern37_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Assistant bound; }
final $oaPattern37_Holder $oaPattern37_holder = new $oaPattern37_Holder();
if ((($oaPattern37_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Assistant && (($oaPattern37_holder.bound = (GuideUiRow.Assistant) $oaPattern37_holder.value) != null))) {
                        return $oaPattern37_holder.bound.sources().stream();
                    } else {
final class $oaPattern38_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Tool bound; }
final $oaPattern38_Holder $oaPattern38_holder = new $oaPattern38_Holder();
if ((($oaPattern38_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Tool && (($oaPattern38_holder.bound = (GuideUiRow.Tool) $oaPattern38_holder.value) != null))) {
                        return $oaPattern38_holder.bound.activity().invocationId().equals(invocationId)
                                ? $oaPattern38_holder.bound.activity().sources().stream() : java.util.stream.Stream.empty();
                    } else {
                        return java.util.stream.Stream.empty();
                    }
}
                })
                .filter(value -> value.evidence().sourceId().equals(sourceId))
                .findFirst().orElse(null);
        if (source != null) open(source);
    }

    private int renderWrapped(
            GuideGraphics graphics, List<net.minecraft.network.chat.Component> paragraphs, int x, int y, int width, int color) {
        for (net.minecraft.network.chat.Component paragraph : paragraphs) {
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

    static net.minecraft.network.chat.Component assistantLabel(GuideDisplayConfig display, boolean streaming) {
        Objects.requireNonNull(display, "display");
        return streaming
                ? MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.literal(display.assistantName()), " · "), MinecraftComponents.translatable(
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
                MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.detail.close"))));
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
                    MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.detail.tool.status"))
                            + ": " + MinecraftComponents.getString(MinecraftComponents.translatable(toolDetail.displayStatus().translationKey())),
                    detail, y);
            for (DetailSection section : toolDetailSections(toolDetail)) {
                switch ((section)) {
case RESULT:
{
{
                        for (net.minecraft.network.chat.Component reason : toolFailureComponents(toolDetail, selectedTool.activity().toolId())) {
                            y = detailLine(graphics, MinecraftComponents.style(MinecraftComponents.copy(reason), net.minecraft.ChatFormatting.RED), detail, y);
                        }
                        if (toolDetail.failure().isEmpty()) {
                            y = detailLine(graphics, MinecraftComponents.translatable("screen.openallay.detail.output"), detail, y + 4);
                        }
                        for (GuideToolMessage message : toolResultMessages(toolDetail)) {
                            y = detailLine(graphics, toolMessage(message), detail, y);
                        }
                        if (observationImages != null) {
                            for (ImageReference reference : dev.openallay.util.Java8Collections.listCopyOf(observationImages.apply(
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
break;
}
case PROGRAM:
{
{
                        y = detailDisclosure(graphics, MinecraftComponents.translatable("screen.openallay.detail.program"), detail, y + 4, "program");
                        if (expandedDetails.contains("program")) {
                            y = detailCode(graphics, toolProgram(toolDetail), detail, y, "javascript-source");
                        }
                    }
break;
}
case INTENT:
{
{
                        y = detailLine(graphics, MinecraftComponents.translatable("screen.openallay.tool.intent.label"), detail, y + 4);
                        y = detailLine(graphics, intentTitle(toolDetail.intent(), toolDetail.titleKey()), detail, y);
                        if (!dev.openallay.util.Java8Strings.isBlank(toolDetail.intent().description())) {
                            y = detailLine(graphics, toolDescription(toolDetail.intent()), detail, y);
                        }
                    }
break;
}
case SOURCES:
{
{
                        y = sourceGroups(graphics, selectedTool.activity().sources(), detail, y + 4);
                    }
break;
}
case DEBUG:
{
{
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
                            if (!dev.openallay.util.Java8Strings.isBlank(debug.validationDiagnostic())) {
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
break;
}
}

            }
        } else if (selectedSource != null) {
            y = sourceGroup(graphics, selectedSource, detail, y, "selected-source");
        }
        detailContentHeight = Math.max(0, y + detailScroll - detail.y());
        int clampedScroll = net.minecraft.util.Mth.clamp(detailScroll, 0, maximumDetailScroll());
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
        if (!dev.openallay.util.Java8Strings.isBlank(toolProgram(detail))) sections.add(DetailSection.PROGRAM);
        sections.add(DetailSection.INTENT);
        if (detail.debug().isPresent()) sections.add(DetailSection.DEBUG);
        sections.add(DetailSection.SOURCES);
        return dev.openallay.util.Java8Collections.listCopyOf(sections);
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
            GuideGraphics graphics, net.minecraft.network.chat.Component label, GuideUiLayout.Rect detail, int y, String id) {
        net.minecraft.network.chat.Component text = MinecraftComponents.append(MinecraftComponents.literal(expandedDetails.contains(id) ? "▼ " : "▶ "), label);
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
            }, focusId, MinecraftComponents.getString(text)));
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
        final class $oaPattern39_Holder { dev.openallay.guide.ui.GuideDetailCard value; GuideDetailCard.Recipe bound; }
final $oaPattern39_Holder $oaPattern39_holder = new $oaPattern39_Holder();
if ((($oaPattern39_holder.value = card) instanceof dev.openallay.guide.ui.GuideDetailCard.Recipe && (($oaPattern39_holder.bound = (GuideDetailCard.Recipe) $oaPattern39_holder.value) != null))) {
            return recipeCard(graphics, $oaPattern39_holder.bound.recipe(), cardId, detail, y, mouseX, mouseY);
        } else {
final class $oaPattern40_Holder { dev.openallay.guide.ui.GuideDetailCard value; GuideDetailCard.ItemGrid bound; }
final $oaPattern40_Holder $oaPattern40_holder = new $oaPattern40_Holder();
if ((($oaPattern40_holder.value = card) instanceof dev.openallay.guide.ui.GuideDetailCard.ItemGrid && (($oaPattern40_holder.bound = (GuideDetailCard.ItemGrid) $oaPattern40_holder.value) != null))) {
            return itemGridCard(graphics, $oaPattern40_holder.bound, detail, y, mouseX, mouseY);
        } else {
final class $oaPattern41_Holder { dev.openallay.guide.ui.GuideDetailCard value; GuideDetailCard.Requirements bound; }
final $oaPattern41_Holder $oaPattern41_holder = new $oaPattern41_Holder();
if ((($oaPattern41_holder.value = card) instanceof dev.openallay.guide.ui.GuideDetailCard.Requirements && (($oaPattern41_holder.bound = (GuideDetailCard.Requirements) $oaPattern41_holder.value) != null))) {
            return requirementsCard(graphics, $oaPattern41_holder.bound, detail, y, mouseX, mouseY);
        } else {
final class $oaPattern42_Holder { dev.openallay.guide.ui.GuideDetailCard value; GuideDetailCard.Table bound; }
final $oaPattern42_Holder $oaPattern42_holder = new $oaPattern42_Holder();
if ((($oaPattern42_holder.value = card) instanceof dev.openallay.guide.ui.GuideDetailCard.Table && (($oaPattern42_holder.bound = (GuideDetailCard.Table) $oaPattern42_holder.value) != null))) {
            return tableCard(graphics, $oaPattern42_holder.bound, detail, y);
        } else {
final class $oaPattern43_Holder { dev.openallay.guide.ui.GuideDetailCard value; GuideDetailCard.KeyValue bound; }
final $oaPattern43_Holder $oaPattern43_holder = new $oaPattern43_Holder();
if ((($oaPattern43_holder.value = card) instanceof dev.openallay.guide.ui.GuideDetailCard.KeyValue && (($oaPattern43_holder.bound = (GuideDetailCard.KeyValue) $oaPattern43_holder.value) != null))) {
            return keyValueCard(graphics, $oaPattern43_holder.bound, detail, y);
        } else {
final class $oaPattern44_Holder { dev.openallay.guide.ui.GuideDetailCard value; GuideDetailCard.DataPreview bound; }
final $oaPattern44_Holder $oaPattern44_holder = new $oaPattern44_Holder();
if ((($oaPattern44_holder.value = card) instanceof dev.openallay.guide.ui.GuideDetailCard.DataPreview && (($oaPattern44_holder.bound = (GuideDetailCard.DataPreview) $oaPattern44_holder.value) != null))) {
            return dataPreviewCard(graphics, $oaPattern44_holder.bound, detail, y);
        } else {
final class $oaPattern45_Holder { dev.openallay.guide.ui.GuideDetailCard value; GuideDetailCard.Text bound; }
final $oaPattern45_Holder $oaPattern45_holder = new $oaPattern45_Holder();
if ((($oaPattern45_holder.value = card) instanceof dev.openallay.guide.ui.GuideDetailCard.Text && (($oaPattern45_holder.bound = (GuideDetailCard.Text) $oaPattern45_holder.value) != null))) {
            return textCard(graphics, $oaPattern45_holder.bound, detail, y);
        } else {
final class $oaPattern46_Holder { dev.openallay.guide.ui.GuideDetailCard value; GuideDetailCard.Error bound; }
final $oaPattern46_Holder $oaPattern46_holder = new $oaPattern46_Holder();
if ((($oaPattern46_holder.value = card) instanceof dev.openallay.guide.ui.GuideDetailCard.Error && (($oaPattern46_holder.bound = (GuideDetailCard.Error) $oaPattern46_holder.value) != null))) {
            return errorCard(graphics, $oaPattern46_holder.bound, detail, y);
        }
}
}
}
}
}
}
}
        throw new IncompatibleClassChangeError();
    }

    private int tableCard(
            GuideGraphics graphics,
            GuideDetailCard.Table card,
            GuideUiLayout.Rect detail,
            int y) {
        int start = y;
        y = detailLine(graphics, MinecraftComponents.getString(MinecraftComponents.translatable(card.titleKey())), detail, y);
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
        y = detailLine(graphics, MinecraftComponents.getString(MinecraftComponents.translatable(card.titleKey())), detail, y);
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
        y = detailLine(graphics, MinecraftComponents.getString(MinecraftComponents.translatable(card.titleKey())), detail, y);
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
                    ? MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.craftability.ready"))
                    : MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.craftability.missing"));
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
        y = detailLine(graphics, MinecraftComponents.getString(MinecraftComponents.translatable(card.titleKey())), detail, y);
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
            dev.openallay.guide.semantic.RichComponent.RecipeGrid component = new dev.openallay.guide.semantic.RichComponent.RecipeGrid(
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
        for (dev.openallay.guide.ui.GuideRecipeDetailFacts.Line line : dev.openallay.guide.ui.GuideRecipeDetailFacts.project(card)) {
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
        java.util.Optional<dev.openallay.context.RecipeReference> exact = card.references().stream().filter(recipeClient::supportsExact).findFirst();
        recipeAction(graphics, MinecraftComponents.translatable(exact.isPresent()
                        ? "screen.openallay.recipe.open_exact" : "screen.openallay.recipe.open_exact_unavailable"),
                left + 7, y + 2, exact.isPresent(), () -> navigate(recipeClient.openExact(exact.orElseThrow())));
        y += 16;
        if (!recipeClient.canBrowse()) {
            y = detailLine(graphics, MinecraftComponents.style(MinecraftComponents.translatable("screen.openallay.recipe.viewer_unavailable"), net.minecraft.ChatFormatting.YELLOW), detail, y);
        }
        return y + 5;
    }

    private static String toolDetailRecipeNodeId(String cardId) {
        try {
            return dev.openallay.util.Java8Hex.formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(cardId.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private int recipeAction(
            GuideGraphics graphics,
            net.minecraft.network.chat.Component label,
            int x,
            int y,
            boolean enabled,
            Runnable action) {
        int width = GuideNativeFont.width(font, label) + 8;
        int color = enabled ? ACCENT : 0xFF6E7782;
        graphics.fill(x, y - 2, x + width, y + 10, enabled ? 0xFF29443F : 0xFF30343A);
        graphics.text(font, label, x + 4, y, color, false);
        GuideUiLayout.Rect detail = layout.detail();
        if (enabled && y - 2 >= detail.y() + 21 && y + 10 <= detail.bottom()) {
            GuideUiLayout.Rect bounds = new GuideUiLayout.Rect(x, y - 2, width, 12);
            String focusId = "detail:action:" + MinecraftComponents.getString(label) + ":" + x + ":" + (y + detailScroll);
            if (isFocused(focusedContentId, focusId)) {
                graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), ACCENT);
            }
            hits.add(new Hit(bounds, HitKind.DETAIL, action, focusId, MinecraftComponents.getString(label)));
        }
        return x + width;
    }

    private static net.minecraft.world.item.ItemStack itemStack(String itemId, long count) {
        net.minecraft.resources.ResourceLocation id = MinecraftResourceIds.tryParse(itemId);
        if (id == null || !MinecraftNativeRegistries.ITEM.containsKey(id)) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        return new net.minecraft.world.item.ItemStack(dev.openallay.client.gui.GuideNativeItemLookup.item(id.toString()),
                (int) Math.min(Integer.MAX_VALUE, Math.max(1, count)));
    }

    private void renderItem(
            GuideGraphics graphics,
            GuideItemView item,
            int x,
            int y,
            int mouseX,
            int mouseY) {
        net.minecraft.world.item.ItemStack stack = itemStack(item.itemId(), item.count());
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
                ? MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.recipe.viewer_opened"))
                : navigationFailure(result));
    }

    private static String navigationFailure(RecipeNavigationResult result) {
        java.lang.String $oaSwitch13_exit_result;
$oaSwitch13_exit: {
switch ((result.code())) {
case "exact_unsupported":
{
$oaSwitch13_exit_result = "screen.openallay.recipe.exact_unsupported"; break $oaSwitch13_exit;
}
case "preferred_viewer_unavailable":
{
$oaSwitch13_exit_result = "screen.openallay.recipe.preferred_viewer_unavailable"; break $oaSwitch13_exit;
}
case "viewer_unavailable":
{
$oaSwitch13_exit_result = "screen.openallay.recipe.viewer_unavailable"; break $oaSwitch13_exit;
}
case "unknown_item":
{
$oaSwitch13_exit_result = "screen.openallay.recipe.unknown_item"; break $oaSwitch13_exit;
}
case "wrong_thread":
{
$oaSwitch13_exit_result = "screen.openallay.recipe.wrong_thread"; break $oaSwitch13_exit;
}
case "viewer_failure":
{
$oaSwitch13_exit_result = "screen.openallay.recipe.viewer_failure"; break $oaSwitch13_exit;
}
default:
{
$oaSwitch13_exit_result = null; break $oaSwitch13_exit;
}
}
}
String key = $oaSwitch13_exit_result;
        return key == null
                ? result.code() + ": " + result.message()
                : MinecraftComponents.getString(MinecraftComponents.translatable(key));
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
            for (net.minecraft.network.chat.Component component : sourceDetailComponents(group)) {
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
    static List<net.minecraft.network.chat.Component> sourceDetailComponents(GuideEvidencePresentation.Group group) {
        List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
        GuideEvidencePresentation evidence = group.presentation();
        lines.add(MinecraftComponents.translatable(evidence.authorityKey()));
        lines.add(MinecraftComponents.translatable("screen.openallay.evidence.coverage",
                MinecraftComponents.translatable(evidence.coverageKey())));
        lines.add(MinecraftComponents.translatable("screen.openallay.evidence.capture_range",
                group.firstCapturedAt().toString(), group.lastCapturedAt().toString()));
        dev.openallay.guide.ui.GuideEvidencePresentation.Identity identity = group.identity();
        lines.add(MinecraftComponents.literal("toolId: " + identity.toolId()));
        lines.add(MinecraftComponents.literal("sourceId: " + identity.sourceId()));
        lines.add(MinecraftComponents.literal("provenance: " + identity.provenance()));
        lines.add(MinecraftComponents.literal("gameVersion: " + identity.gameVersion()));
        lines.add(MinecraftComponents.literal("loader: " + identity.loader()));
        for (java.util.Map.Entry<java.lang.String, java.lang.String> entry : identity.scope().entrySet()) {
            lines.add(MinecraftComponents.literal(entry.getKey() + ": " + entry.getValue()));
        }
        // Shared scope is already above. Every retained observation-specific value stays available.
        for (GuideSource record : group.records()) {
            lines.add(MinecraftComponents.translatable("screen.openallay.evidence.capture_range",
                    new dev.openallay.context.SourceObservation(
                            record.evidence(), record.lastCapturedAt()).firstCapturedAt().toString(),
                    record.lastCapturedAt().toString()));
            for (java.util.Map.Entry<java.lang.String, java.lang.String> entry : record.evidence().details().entrySet()) {
                if (!identity.scope().containsKey(entry.getKey())) {
                    lines.add(MinecraftComponents.literal(entry.getKey() + ": " + entry.getValue()));
                }
            }
        }
        return dev.openallay.util.Java8Collections.listCopyOf(lines);
    }

    @dev.openallay.value.ValueType(SourceDetailLayout.ValueSchemaProvider.class)
static final class SourceDetailLayout {
    private final GuideEvidencePresentation.Group group;
    private final int width;
    private final String locale;
    private final List<GuideTextLine> lines;
    SourceDetailLayout(GuideEvidencePresentation.Group group, int width, String locale, List<GuideTextLine> lines) {
 lines = dev.openallay.util.Java8Collections.listCopyOf(lines);
        this.group = group;
        this.width = width;
        this.locale = locale;
        this.lines = lines;
    }
    public GuideEvidencePresentation.Group group() { return group; }
    public int width() { return width; }
    public String locale() { return locale; }
    public List<GuideTextLine> lines() { return lines; }
boolean matches(GuideEvidencePresentation.Group current, int currentWidth, String currentLocale) {
            // The groups are immutable. Identity avoids comparing thousands of retained records each frame.
            return group == current && width == currentWidth && locale.equals(currentLocale);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SourceDetailLayout)) return false;
        SourceDetailLayout that = (SourceDetailLayout) other;
        return java.util.Objects.equals(group, that.group) && width == that.width && java.util.Objects.equals(locale, that.locale) && java.util.Objects.equals(lines, that.lines);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(group);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + java.util.Objects.hashCode(locale);
        hash = 31 * hash + java.util.Objects.hashCode(lines);
        return hash;
    }
    @Override public String toString() { return "SourceDetailLayout[group=" + group + ", width=" + width + ", locale=" + locale + ", lines=" + lines + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<SourceDetailLayout> schema() {
            return new dev.openallay.value.ValueSchema<>(SourceDetailLayout.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<SourceDetailLayout>>asList(new dev.openallay.value.ValueSchema.Component<>(SourceDetailLayout.class, "group", SourceDetailLayout::group), new dev.openallay.value.ValueSchema.Component<>(SourceDetailLayout.class, "width", SourceDetailLayout::width), new dev.openallay.value.ValueSchema.Component<>(SourceDetailLayout.class, "locale", SourceDetailLayout::locale), new dev.openallay.value.ValueSchema.Component<>(SourceDetailLayout.class, "lines", SourceDetailLayout::lines)), arguments -> new SourceDetailLayout((GuideEvidencePresentation.Group) arguments[0], (Integer) arguments[1], (String) arguments[2], (List) arguments[3]));
        }
    }
}

    @dev.openallay.value.ValueType(VisibleDetailLines.ValueSchemaProvider.class)
static final class VisibleDetailLines {
    private final int first;
    private final int end;
    VisibleDetailLines(int first, int end) {
        this.first = first;
        this.end = end;
    }
    public int first() { return first; }
    public int end() { return end; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof VisibleDetailLines)) return false;
        VisibleDetailLines that = (VisibleDetailLines) other;
        return first == that.first && end == that.end;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Integer.hashCode(first);
        hash = 31 * hash + Integer.hashCode(end);
        return hash;
    }
    @Override public String toString() { return "VisibleDetailLines[first=" + first + ", end=" + end + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<VisibleDetailLines> schema() {
            return new dev.openallay.value.ValueSchema<>(VisibleDetailLines.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<VisibleDetailLines>>asList(new dev.openallay.value.ValueSchema.Component<>(VisibleDetailLines.class, "first", VisibleDetailLines::first), new dev.openallay.value.ValueSchema.Component<>(VisibleDetailLines.class, "end", VisibleDetailLines::end)), arguments -> new VisibleDetailLines((Integer) arguments[0], (Integer) arguments[1]));
        }
    }
}

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
            GuideGraphics graphics, net.minecraft.network.chat.Component text, GuideUiLayout.Rect detail, int y) {
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
            cached = new CodeLayout(source, width, dev.openallay.util.Java8Collections.listCopyOf(wrapped));
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
                MinecraftComponents.getString(MinecraftComponents.translatable(labelKey)) + ": " + String.join(", ", values),
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
                .orElseGet(() -> GuideEvidencePresentation.groups(dev.openallay.util.Java8Collections.listOf(source)).get(0));
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

    @dev.openallay.value.ValueType(CodeLayout.ValueSchemaProvider.class)
private static final class CodeLayout {
    private final String source;
    private final int width;
    private final List<GuideTextLine> lines;
    private CodeLayout(String source, int width, List<GuideTextLine> lines) {
        this.source = source;
        this.width = width;
        this.lines = lines;
    }
    public String source() { return source; }
    public int width() { return width; }
    public List<GuideTextLine> lines() { return lines; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CodeLayout)) return false;
        CodeLayout that = (CodeLayout) other;
        return java.util.Objects.equals(source, that.source) && width == that.width && java.util.Objects.equals(lines, that.lines);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + java.util.Objects.hashCode(lines);
        return hash;
    }
    @Override public String toString() { return "CodeLayout[source=" + source + ", width=" + width + ", lines=" + lines + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<CodeLayout> schema() {
            return new dev.openallay.value.ValueSchema<>(CodeLayout.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<CodeLayout>>asList(new dev.openallay.value.ValueSchema.Component<>(CodeLayout.class, "source", CodeLayout::source), new dev.openallay.value.ValueSchema.Component<>(CodeLayout.class, "width", CodeLayout::width), new dev.openallay.value.ValueSchema.Component<>(CodeLayout.class, "lines", CodeLayout::lines)), arguments -> new CodeLayout((String) arguments[0], (Integer) arguments[1], (List) arguments[2]));
        }
    }
}

    private void rebuildPresentationWidgets() {
        invalidateContentHits();
        boolean composerFocused = composer != null && guideWidgetFocused(composer.widget());
        GuideWidget previous = getGuideWidgetFocused();
        String contentFocus = focusedContentId;
        guideRebuildWidgets();
        focusedContentId = contentFocus;
        GuideNativeFocus.clear(this);
        if (composerFocused) setFocused(composer.widget());
        else if (previous != null) {
            // Recreated controls may retain focus by native type and label; never reattach an old widget.
            guideWidgetChildren().stream()
                    .filter(widget -> widget.guideNativeType() == previous.guideNativeType()
                            && widget.getMessage().equals(previous.getMessage()) && widget.guideActive() && widget.guideVisible())
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
                final class $oaPattern47_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Assistant bound; }
final $oaPattern47_Holder $oaPattern47_holder = new $oaPattern47_Holder();
if ((($oaPattern47_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Assistant && (($oaPattern47_holder.bound = (GuideUiRow.Assistant) $oaPattern47_holder.value) != null))) {
                    return groupedSources($oaPattern47_holder.bound.sources()).contains(selectedSource);
                } else {
final class $oaPattern48_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Tool bound; }
final $oaPattern48_Holder $oaPattern48_holder = new $oaPattern48_Holder();
if ((($oaPattern48_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Tool && (($oaPattern48_holder.bound = (GuideUiRow.Tool) $oaPattern48_holder.value) != null))) {
                    return groupedSources($oaPattern48_holder.bound.activity().sources()).contains(selectedSource);
                } else {
                    return false;
                }
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
            final class $oaPattern49_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Assistant bound; }
final $oaPattern49_Holder $oaPattern49_holder = new $oaPattern49_Holder();
if ((($oaPattern49_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Assistant && (($oaPattern49_holder.bound = (GuideUiRow.Assistant) $oaPattern49_holder.value) != null))) {
                retainedSources.put($oaPattern49_holder.bound.sources(), true);
            } else {
final class $oaPattern50_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Tool bound; }
final $oaPattern50_Holder $oaPattern50_holder = new $oaPattern50_Holder();
if ((($oaPattern50_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Tool && (($oaPattern50_holder.bound = (GuideUiRow.Tool) $oaPattern50_holder.value) != null))) {
                retainedSources.put($oaPattern50_holder.bound.activity().sources(), true);
            }
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
            modelSelectorCursor = net.minecraft.util.Mth.clamp(
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
                MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.pending.edit_invalid")));
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
        switch ((view.selectedSession().equals(uiState.imageNoticeSession()) ? uiState.imageNotice() : ComposerImageDraft.Notice.NONE)) {
case CLIPBOARD_UNAVAILABLE:
{
notice = GuideUiNotice.error(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.image.clipboard_unavailable")));
break;
}
case IMPORT_FAILED:
{
notice = GuideUiNotice.error(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.image.import_failed")));
break;
}
case PROCESSING:
case READY:
case NONE:
{
{ }
break;
}
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
            dev.openallay.client.observation.GuideImageBitmap bitmap = MinecraftImageTextures.create(preview.width(), preview.height());
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
            new ClientObservationInputCoordinator(observationActions, action -> MinecraftClientWindow.execute(minecraft, action))
                    .refresh(uiState, view.selectedSession());
            notice = GuideUiNotice.info("");
        } catch (RuntimeException unavailable) {
            notice = GuideUiNotice.warning(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.observation.capture_failed")));
        }
    }

    private void attachObservationFrame() {
        if (observationActions == null || observationCapturing) return;
        observationCapturing = true;
        try {
            new ClientObservationInputCoordinator(observationActions, action -> MinecraftClientWindow.execute(minecraft, action))
                    .attachCurrentFrame(uiState, view.selectedSession()).whenComplete((applied, failure) -> MinecraftClientWindow.execute(minecraft, () -> {
                        observationCapturing = false;
                        if (failure != null && attachment != null) notice = GuideUiNotice.warning(
                                MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.observation.capture_failed")));
                        if (attachment != null) sharedDraftChanged();
                    }));
        } catch (RuntimeException unavailable) {
            observationCapturing = false;
            notice = GuideUiNotice.warning(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.observation.capture_failed")));
        }
    }

    /** Leaves the existing image/pending strips and text cursor in their measured bounds. */
    private GuideUiLayout.Rect renderObservationComposer(GuideGraphics graphics,
            GuideUiLayout.Rect strip, int mouseX, int mouseY) {
        java.util.Optional<dev.openallay.world.ClientObservationAnchor> anchor = uiState.observation(view.selectedSession());
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
        net.minecraft.network.chat.Component text = MinecraftComponents.empty();
        if (anchor.isPresent()) {
            for (dev.openallay.client.observation.ObservationAnchorPresentation.Chip chip : ObservationAnchorPresentation.chips(anchor.orElseThrow())) {
                if (!MinecraftComponents.getString(text).isEmpty()) text = MinecraftComponents.append(MinecraftComponents.copy(text), " · ");
                text = MinecraftComponents.append(MinecraftComponents.copy(text), MinecraftComponents.translatable(chip.key(), chip.value()));
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
            net.minecraft.network.chat.Component label, Runnable action, String id, int mouseX, int mouseY) {
        int left = Math.max(bounds.x(), observationComposerBounds.x());
        int right = Math.min(bounds.right(), observationComposerBounds.right());
        if (right <= left || bounds.height() <= 0) return;
        bounds = new GuideUiLayout.Rect(left, bounds.y(), right - left, bounds.height());
        boundedHeaderText(graphics, label, bounds, observationCapturing ? MUTED : ACCENT);
        java.lang.String $oaSwitch10_exit_result;
$oaSwitch10_exit: {
switch ((id)) {
case "observation:refresh":
{
$oaSwitch10_exit_result = "screen.openallay.observation.refresh"; break $oaSwitch10_exit;
}
case "observation:attach":
{
$oaSwitch10_exit_result = "screen.openallay.observation.attach_frame"; break $oaSwitch10_exit;
}
case "observation:remove-frame":
{
$oaSwitch10_exit_result = "screen.openallay.observation.remove_frame"; break $oaSwitch10_exit;
}
case "observation:frame":
{
$oaSwitch10_exit_result = "screen.openallay.observation.open_frame"; break $oaSwitch10_exit;
}
default:
{
$oaSwitch10_exit_result = "screen.openallay.observation.remove"; break $oaSwitch10_exit;
}
}
}
String key = $oaSwitch10_exit_result;
        net.minecraft.network.chat.Component description = MinecraftComponents.translatable(key);
        hits.add(new Hit(bounds, HitKind.COMPOSER, action, id, MinecraftComponents.getString(description)));
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
                        "observation:image:" + reference.sha256(), MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.observation.open_frame"))));
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
            imageScroll = net.minecraft.util.Mth.clamp(imageScroll, 0, Math.max(0, images.size() - visible));
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
                        "remove-image:" + image.id(), MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.image.remove"))));
                if (remove.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font,
                        MinecraftComponents.translatable("screen.openallay.image.remove"), mouseX, mouseY);
                else if (new GuideUiLayout.Rect(x, strip.y(), cell, cell).contains(mouseX, mouseY)) {
                    net.minecraft.network.chat.Component tooltip = image.pending() ? MinecraftComponents.translatable("screen.openallay.image.processing")
                            : MinecraftComponents.translatable("screen.openallay.image.attached", image.reference().width(), image.reference().height());
                    if (!image.pending()) tooltip = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(tooltip), " · "), MinecraftComponents.translatable("screen.openallay.image.cost_unknown"));
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
                    MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.pending.mode_description"))));
            if (mode.contains(mouseX, mouseY)) graphics.setTooltipForNextFrame(font,
                    MinecraftComponents.translatable(steerMode() ? "screen.openallay.pending.steer_description" : "screen.openallay.pending.follow_up_description"), mouseX, mouseY);
        }
        if (pending.isEmpty()) return;
        pendingCursor = net.minecraft.util.Mth.clamp(pendingCursor, 0, pending.size() - 1);
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
        net.minecraft.network.chat.Component label = MinecraftComponents.literal((navigator ? (pendingCursor + 1) + "/" + count + " " : "")
                + (pending.kind() == GuidePendingMessage.Kind.STEER ? "↪ " : "↳ ") + pending.text());
        boundedHeaderText(graphics, label, text, pending.failure() == null ? MUTED : ERROR);
        if (navigator) hits.add(new Hit(text, HitKind.COMPOSER, () -> pendingCursor = (pendingCursor + 1) % count,
                "pending-next", MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.pending.next"))));
        GuideUiLayout.Rect edit = new GuideUiLayout.Rect(area.right() - 38, area.y(), 20, area.height());
        GuideUiLayout.Rect cancel = new GuideUiLayout.Rect(area.right() - 18, area.y(), 18, area.height());
        graphics.text(font, "✎", edit.x() + 4, edit.y() + 3, TEXT, false);
        graphics.text(font, "×", cancel.x() + 4, cancel.y() + 3, TEXT, false);
        hits.add(new Hit(edit, HitKind.COMPOSER, () -> editPending(pending), "pending-edit:" + pending.id(),
                MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.pending.edit"))));
        hits.add(new Hit(cancel, HitKind.COMPOSER, () -> accept(service.cancelPending(pending.id()), ignored -> {
            uiState.invalidatePendingEdit(view.selectedSession(), pending.id());
            refreshComposerLayout();
        }), "pending-cancel:" + pending.id(), MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.pending.cancel"))));
        if (area.contains(mouseX, mouseY)) {
            net.minecraft.network.chat.Component tooltip = MinecraftComponents.translatable(cancel.contains(mouseX, mouseY) ? "screen.openallay.pending.cancel"
                    : edit.contains(mouseX, mouseY) ? "screen.openallay.pending.edit" : "screen.openallay.pending.waiting", pending.text());
            if (pending.failure() != null) tooltip = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(tooltip), " · "), pending.failure().code() + ": " + pending.failure().message());
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
            notice = GuideUiNotice.warning(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.pending.already_consumed")));
            return;
        }
        if (!dev.openallay.util.Java8Strings.isBlank(draft) || !composerImages.empty()) {
            notice = GuideUiNotice.error(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.pending.draft_not_empty")));
            return;
        }
        uiState.beginPendingEdit(view.selectedSession(), pending.id(), pending.kind() == GuidePendingMessage.Kind.STEER
                ? GuideClientUiState.DraftMode.STEER : GuideClientUiState.DraftMode.FOLLOW_UP);
        draft = pending.message().content().stream().filter(ModelContent.Text.class::isInstance)
                .map(ModelContent.Text.class::cast).map(ModelContent.Text::text)
                .collect(java.util.stream.Collectors.joining("\n"));
        composer.setValue(draft);
        uiState.setText(view.selectedSession(), draft);
        composerImages.restore(dev.openallay.util.Java8Collections.toList(pending.message().content().stream().filter(ModelContent.Image.class::isInstance)
                .map(ModelContent.Image.class::cast).map(ModelContent.Image::reference)));
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
                final class $oaPattern51_Holder { dev.openallay.tool.ToolResult<byte[]> value; ToolResult.Success<byte[]> bound; }
final $oaPattern51_Holder $oaPattern51_holder = new $oaPattern51_Holder();
if (failure != null || !((($oaPattern51_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern51_holder.bound = (ToolResult.Success<byte[]>) $oaPattern51_holder.value) != null)))) return;
                IMAGE_EXECUTOR.execute(() -> {
                    try (javax.imageio.stream.MemoryCacheImageInputStream input = new javax.imageio.stream.MemoryCacheImageInputStream(new java.io.ByteArrayInputStream($oaPattern51_holder.bound.value()))) {
                        java.awt.image.BufferedImage bitmap = javax.imageio.ImageIO.read(input);
                        if (bitmap == null) return;
                        ClipboardImageEncoder.Preview preview = ClipboardImageEncoder.encode(bitmap).preview();
                        MinecraftClientWindow.execute(minecraft, () -> {
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
        dev.openallay.guide.composer.SlashCommandDispatcher.Dispatch dispatch = draftIntent().editing()
                ? new dev.openallay.guide.composer.SlashCommandDispatcher.Dispatch(false, true, commandText)
                : dev.openallay.guide.composer.SlashCommandDispatcher.dispatch(commandText, service,
                completion -> MinecraftClientWindow.execute(minecraft, () -> {
                    if (uiState.closed()) return;
                    if (completion.successful()) uiState.clearAcceptedText(commandRevision, commandText);
                    if (attachment == null || !commandScope.session().equals(view.selectedSession())) return;
                    synchronizeComposerSession();
                    notice = completion.successful() ? GuideUiNotice.success(MinecraftComponents.getString(slashCompletionNotice(completion)))
                            : GuideUiNotice.error(MinecraftComponents.getString(slashCompletionNotice(completion)));
                    if (!composerImages.empty()) notice = GuideUiNotice.info(notice.message() + " · " + MinecraftComponents.getString(MinecraftComponents.translatable(
                            "openallay.guide.slash.attachments_retained")));
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
            notice = GuideUiNotice.error(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.image.model_unsupported")));
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
            notice = GuideUiNotice.error(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.composer.submit_failed")));
            updateControls();
            return;
        }
        future.whenComplete((result, failure) -> MinecraftClientWindow.execute(minecraft, () -> {
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
                    notice = GuideUiNotice.error(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.composer.submit_failed")));
                } else if (accepted) {
                    notice = GuideUiNotice.acceptedSubmission(capturedRoute, pendingId, result,
                            service.snapshot(), images.session());
                } else if (pendingId != null && result instanceof ToolResult.Success<?>) {
                    notice = GuideUiNotice.warning(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.pending.already_consumed")));
                } else {
final class $oaPattern52_Holder { dev.openallay.tool.ToolResult<?> value; ToolResult.Failure<?> bound; }
final $oaPattern52_Holder $oaPattern52_holder = new $oaPattern52_Holder();
if ((($oaPattern52_holder.value = result) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern52_holder.bound = (ToolResult.Failure<?>) $oaPattern52_holder.value) != null))) {
                    notice = GuideUiNotice.error($oaPattern52_holder.bound.code() + ": " + $oaPattern52_holder.bound.message());
                } else {
                    notice = GuideUiNotice.error(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.composer.submit_failed")));
                }
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

    static net.minecraft.network.chat.Component slashCompletionNotice(dev.openallay.guide.composer.SlashCommandDispatcher.Completion completion) {
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
        notice = GuideUiNotice.info(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.fork.running")));
        accept(service.forkSelectedSession(), id -> {
            notice = GuideUiNotice.success(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.fork.success", id)));
            sessionOverlay = false;
            scroll = 0;
        });
    }

    private void forkSession(String sourceSessionId, UUID completedRequestId) {
        notice = GuideUiNotice.info(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.fork.running")));
        accept(service.forkSession(sourceSessionId, completedRequestId), id -> {
            notice = GuideUiNotice.success(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.fork.success", id)));
            sessionOverlay = false;
            scroll = 0;
        });
    }

    private void renderForkAction(GuideGraphics graphics, UUID requestId, String text,
            int x, int y, int width) {
        if (!forkableRequest(service.snapshot(), view.selectedSession(), requestId)) return;
        net.minecraft.network.chat.Component label = MinecraftComponents.translatable("screen.openallay.action.fork");
        int copyWidth = text == null || dev.openallay.util.Java8Strings.isBlank(text) ? 0
                : GuideNativeFont.width(font, MinecraftComponents.translatable("screen.openallay.action.copy")) + 14;
        int actionWidth = GuideNativeFont.width(font, label) + 8;
        int actionX = x + width - copyWidth - actionWidth;
        String focusId = "fork:" + requestId;
        if (isFocused(focusedContentId, focusId)) {
            graphics.fill(actionX - 2, y - 2, actionX + actionWidth, y + 10, 0xFF31453F);
        }
        graphics.text(font, label, actionX + 2, y, MUTED, false);
        String source = view.selectedSession();
        hits.add(new Hit(new GuideUiLayout.Rect(actionX - 2, y - 2, actionWidth + 2, 12),
                HitKind.CONTENT, () -> forkSession(source, requestId), focusId,
                MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.action.fork.description"))));
    }

    static boolean completedAssistantBoundary(GuideSnapshot snapshot, String sessionId, GuideUiRow.Assistant row) {
        if (row.streaming()) return false;
        return snapshot.sessions().stream().filter(session -> session.sessionId().equals(sessionId))
                .flatMap(session -> session.requests().stream())
                .filter(request -> request.requestId().equals(row.requestId()) && request.terminal())
                .anyMatch(request -> {
final class $oaPattern53_Holder { dev.openallay.guide.GuideTimelineEntry value; dev.openallay.guide.GuideTimelineEntry.Assistant bound; }
final $oaPattern53_Holder $oaPattern53_holder = new $oaPattern53_Holder();
return !request.timeline().isEmpty()
                        && (($oaPattern53_holder.value = request.timeline().get(request.timeline().size() - 1)) instanceof dev.openallay.guide.GuideTimelineEntry.Assistant && (($oaPattern53_holder.bound = (dev.openallay.guide.GuideTimelineEntry.Assistant) $oaPattern53_holder.value) != null))
                        && $oaPattern53_holder.bound.ordinal() == row.ordinal();
});
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
        dev.openallay.client.gui.MinecraftClientWindow.showScreen(minecraft, GuideNativeDialogs.confirm(
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
        dev.openallay.client.gui.MinecraftClientWindow.showScreen(minecraft, GuideNativeDialogs.confirm(
                confirmed -> {
                    dev.openallay.client.gui.MinecraftClientWindow.showScreen(minecraft, this);
                    if (confirmed) {
                        accept(service.closeSession(sessionId), deleted -> {
                            notice = GuideUiNotice.success(MinecraftComponents.getString(MinecraftComponents.translatable(
                                    deleted
                                            ? "screen.openallay.session.delete.success"
                                            : "screen.openallay.session.delete.missing",
                                    sessionId)));
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
        java.nio.file.Path gameDirectory = MinecraftClientWindow.gameDirectory(minecraft);
        exportRunning = true;
        notice = GuideUiNotice.info(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.session.export.running")));
        exportNotice = notice;
        exportNoticeKey = "screen.openallay.session.export.running";
        updateControls();
        service.captureSelectedSessionForExport().whenComplete((captured, captureFailure) -> {
            if (captureFailure != null || captured == null) {
                completeExportFailure();
                return;
            }
            final class $oaPattern54_Holder { dev.openallay.tool.ToolResult<dev.openallay.guide.export.GuideSessionExportSnapshot> value; ToolResult.Failure<?> bound; }
final $oaPattern54_Holder $oaPattern54_holder = new $oaPattern54_Holder();
if ((($oaPattern54_holder.value = captured) instanceof dev.openallay.tool.ToolResult.Failure && (($oaPattern54_holder.bound = (ToolResult.Failure<?>) $oaPattern54_holder.value) != null))) {
                completeExportFailure();
                return;
            }
            dev.openallay.guide.export.GuideSessionExportSnapshot snapshot = ((ToolResult.Success<
                    dev.openallay.guide.export.GuideSessionExportSnapshot>) captured).value();
            CompletableFuture.supplyAsync(
                            () -> new GuideSessionExporter(gameDirectory).export(snapshot),
                            EXPORT_EXECUTOR)
                    .whenComplete((exported, failure) -> MinecraftClientWindow.execute(minecraft, () -> {
                        exportRunning = false;
                        if (failure == null) {
                            lastExportFilename = exported.filename();
                            lastExportRequestCount = exported.requestCount();
                        }
                        notice = new GuideUiNotice(failure == null ? GuideUiNotice.Severity.SUCCESS : GuideUiNotice.Severity.ERROR, GuideUiNotice.Placement.COMPOSER, failure == null
                                ? MinecraftComponents.getString(MinecraftComponents.translatable(
                                        "screen.openallay.session.export.success",
                                        exported.filename(),
                                        exported.requestCount()))
                                : MinecraftComponents.getString(MinecraftComponents.translatable(
                                        "screen.openallay.session.export.failed")));
                        exportNotice = notice;
                        exportNoticeKey = failure == null ? "screen.openallay.session.export.success"
                                : "screen.openallay.session.export.failed";
                        updateControls();
                    }));
        });
    }

    private void completeExportFailure() {
        MinecraftClientWindow.execute(minecraft, () -> {
            exportRunning = false;
            notice = GuideUiNotice.error(MinecraftComponents.getString(MinecraftComponents.translatable(
                    "screen.openallay.session.export.failed")));
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
        if (text == null || dev.openallay.util.Java8Strings.isBlank(text)) return;
        net.minecraft.network.chat.Component label = MinecraftComponents.translatable("screen.openallay.action.copy");
        int actionWidth = GuideNativeFont.width(font, label) + 8;
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
                MinecraftComponents.getString(label)));
    }

    private void copyChatText(String text) {
        try {
            GuideNativeInput.setClipboard(text);
            notice = GuideUiNotice.success(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.copy.success")));
        } catch (RuntimeException failure) {
            notice = GuideUiNotice.error(MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.copy.failed")));
        }
    }

    static String copyableText(GuideUiRow row) {
        java.util.Objects.requireNonNull(row);
        final class $oaPattern55_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.User bound; }
final $oaPattern55_Holder $oaPattern55_holder = new $oaPattern55_Holder();
if ((($oaPattern55_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.User && (($oaPattern55_holder.bound = (GuideUiRow.User) $oaPattern55_holder.value) != null))) {
            return $oaPattern55_holder.bound.text();
        } else {
final class $oaPattern56_Holder { dev.openallay.guide.ui.GuideUiRow value; GuideUiRow.Assistant bound; }
final $oaPattern56_Holder $oaPattern56_holder = new $oaPattern56_Holder();
if ((($oaPattern56_holder.value = row) instanceof dev.openallay.guide.ui.GuideUiRow.Assistant && (($oaPattern56_holder.bound = (GuideUiRow.Assistant) $oaPattern56_holder.value) != null))) {
            return $oaPattern56_holder.bound.text();
        } else {
            return null;
        }
}
    }

    static net.minecraft.network.chat.Component deleteConfirmationMessage(String sessionId, boolean finalConfirmation) {
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
            final class $oaPattern57_Holder { dev.openallay.tool.ToolResult<T> value; ToolResult.Success<T> bound; }
final $oaPattern57_Holder $oaPattern57_holder = new $oaPattern57_Holder();
if ((($oaPattern57_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern57_holder.bound = (ToolResult.Success<T>) $oaPattern57_holder.value) != null))) success.accept($oaPattern57_holder.bound.value());
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

    private net.minecraft.network.chat.Component modelButtonLabel() {
        net.minecraft.network.chat.Component full = view.modelSwitchPending()
                ? MinecraftComponents.translatable("screen.openallay.model.next_short", view.selectedModel().displayName()) : modelLabel();
        int available = layout.header().model().width() - 12;
        if (available < 24) return MinecraftComponents.literal("▾");
        if (GuideNativeFont.width(font, full) <= available) return full;
        String shortLabel = view.modelSwitchPending()
                ? MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.model.next_short", view.selectedModel().displayName()))
                : "▾ " + view.selectedModel().displayName();
        String text = GuideNativeFont.plainSubstrByWidth(font, shortLabel,
                Math.max(1, available - GuideNativeFont.width(font, "…")));
        return MinecraftComponents.literal(text + "…");
    }

    private net.minecraft.network.chat.Component modelButtonDescription() {
        return MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.translatable("screen.openallay.action.models"), " · "), modelStatus()), " · "), modelLabel());
    }

    private net.minecraft.network.chat.Component modelLabel() {
        GuideUiModelChoice selected = view.selectedModel();
        net.minecraft.network.chat.Component label = choiceLabel(selected);
        net.minecraft.network.chat.Component value = selected.available()
                ? label
                : MinecraftComponents.translatable("screen.openallay.model.unavailable_short", label);
        return MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.literal("▾ "), value), " · "), imageInputLabel(selected));
    }

    private void revealSelectedModel() {
        int selected = Math.max(0, view.modelChoices().indexOf(view.selectedModel()));
        modelSelectorCursor = selected;
        int visible = visibleModelChoiceCount();
        modelSelectorScroll = net.minecraft.util.Mth.clamp(
                selected - Math.max(0, visible - 1),
                0,
                Math.max(0, view.modelChoices().size() - visible));
    }

    private void moveModelSelectorCursor(int delta) {
        int last = view.modelChoices().size() - 1;
        if (last < 0) return;
        modelSelectorCursor = net.minecraft.util.Mth.clamp(modelSelectorCursor + delta, 0, last);
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
        int wantedWidth = view.modelChoices().stream().mapToInt(choice -> GuideNativeFont.width(font, choiceLabel(choice))
                        + GuideNativeFont.width(font, imageInputLabel(choice)) + 48)
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
        modelSelectorScroll = net.minecraft.util.Mth.clamp(modelSelectorScroll, 0, maximum);
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
            net.minecraft.network.chat.Component label = MinecraftComponents.append(MinecraftComponents.literal(choice.selected() ? "✓ " : "  "), choiceLabel(choice));
            if (!choice.available()) {
                label = MinecraftComponents.append(MinecraftComponents.copy(label), MinecraftComponents.translatable(
                        "screen.openallay.model.choice_unavailable"));
            }
            label = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(label), " · "), imageInputLabel(choice));
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
                    MinecraftComponents.getString(label)));
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

    private net.minecraft.network.chat.Component modelStatus() {
        GuideUiModelChoice selected = view.selectedModel();
        net.minecraft.network.chat.Component selectedLabel = choiceLabel(selected);
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

    static net.minecraft.network.chat.Component imageInputLabel(GuideUiModelChoice choice) {
        return MinecraftComponents.translatable("screen.openallay.settings.models.builtin.image_input."
                + choice.imageInput().encoded());
    }

    private static net.minecraft.network.chat.Component choiceLabel(GuideUiModelChoice choice) {
        return choice.selection().kind() == GuideModelSelection.Kind.SERVER
                ? MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.translatable("screen.openallay.model.server")), " · "), choice.displayName())
                : MinecraftComponents.translatable(
                        "screen.openallay.model.client", choice.displayName());
    }

    static String sourceLabel(GuideEvidencePresentation.Group group, boolean debugMode) {
        return sourceLabel(group.records().get(0), debugMode);
    }

    static String sourceLabel(GuideSource source, boolean debugMode) {
        if (!debugMode) {
            GuideEvidencePresentation evidence = GuideEvidencePresentation.from(source);
            return MinecraftComponents.getString(MinecraftComponents.translatable(evidence.sourceKey())) + " · "
                    + MinecraftComponents.getString(MinecraftComponents.translatable(evidence.coverageKey()));
        }
        return MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.detail.tool.source"))
                + " · " + readableSource(source.evidence().sourceId()) + " · "
                + MinecraftComponents.getString(MinecraftComponents.translatable(coverageKey(source.evidence().completeness())));
    }

    static String readableSource(String sourceId) {
        java.lang.String $oaSwitch14_exit_result;
$oaSwitch14_exit: {
switch ((sourceId)) {
case "minecraft:client_player":
{
$oaSwitch14_exit_result = "screen.openallay.detail.tool.source.minecraft.client_player"; break $oaSwitch14_exit;
}
case "minecraft:client_registry":
case "minecraft:registry":
{
$oaSwitch14_exit_result = "screen.openallay.detail.tool.source.minecraft.client_registry"; break $oaSwitch14_exit;
}
case "minecraft:recipe_manager":
{
$oaSwitch14_exit_result = "screen.openallay.detail.tool.source.minecraft.recipe_manager"; break $oaSwitch14_exit;
}
case "minecraft:client_recipe_book":
{
$oaSwitch14_exit_result = "screen.openallay.detail.tool.source.minecraft.client_recipe_book"; break $oaSwitch14_exit;
}
case "viewer:jei":
{
$oaSwitch14_exit_result = "screen.openallay.detail.tool.source.viewer.jei"; break $oaSwitch14_exit;
}
case "viewer:rei":
{
$oaSwitch14_exit_result = "screen.openallay.detail.tool.source.viewer.rei"; break $oaSwitch14_exit;
}
case "patchouli:resources":
{
$oaSwitch14_exit_result = "screen.openallay.detail.tool.source.patchouli.resources"; break $oaSwitch14_exit;
}
default:
{
$oaSwitch14_exit_result = null; break $oaSwitch14_exit;
}
}
}
String translationKey = $oaSwitch14_exit_result;
        return translationKey == null ? sourceId : MinecraftComponents.getString(MinecraftComponents.translatable(translationKey));
    }

    private static String coverageKey(dev.openallay.context.DataCompleteness completeness) {
        {
java.lang.String $oaSwitch6_exit_result;
$oaSwitch6_exit: {
switch ((completeness)) {
case COMPLETE:
{
$oaSwitch6_exit_result = "screen.openallay.detail.tool.coverage.complete"; break $oaSwitch6_exit;
}
case PARTIAL:
{
$oaSwitch6_exit_result = "screen.openallay.detail.tool.coverage.partial"; break $oaSwitch6_exit;
}
case UNKNOWN:
{
$oaSwitch6_exit_result = "screen.openallay.detail.tool.coverage.unknown"; break $oaSwitch6_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch6_exit_result;
}
    }

    static String toolStatusName(GuideToolStatus status) {
        return MinecraftComponents.getString(toolStatus(status));
    }

    static net.minecraft.network.chat.Component toolStatus(GuideToolStatus status) {
        {
java.lang.String $oaSwitch3_exit_result;
$oaSwitch3_exit: {
switch ((status)) {
case RUNNING:
{
$oaSwitch3_exit_result = "screen.openallay.detail.tool.status.running"; break $oaSwitch3_exit;
}
case SUCCEEDED:
{
$oaSwitch3_exit_result = "screen.openallay.detail.tool.status.succeeded"; break $oaSwitch3_exit;
}
case FAILED:
{
$oaSwitch3_exit_result = "screen.openallay.detail.tool.status.failed"; break $oaSwitch3_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return MinecraftComponents.translatable($oaSwitch3_exit_result);
}
    }

    static net.minecraft.network.chat.Component toolTitle(GuideToolActivity activity) {
        return activity.intent().title().isEmpty()
                ? friendlyTool(activity.toolId())
                : MinecraftComponents.literal(activity.intent().title());
    }

    private static net.minecraft.network.chat.Component intentTitle(dev.openallay.guide.GuideToolIntent intent, String titleKey) {
        return intent.title().isEmpty()
                ? MinecraftComponents.translatable(titleKey) : MinecraftComponents.literal(intent.title());
    }

    static net.minecraft.network.chat.Component toolDescription(dev.openallay.guide.GuideToolIntent intent) {
        return intent.description().isEmpty() ? MinecraftComponents.empty() : MinecraftComponents.literal(intent.description());
    }

    static net.minecraft.network.chat.Component toolCardStatus(GuideToolDisplayStatus status) {
        java.lang.String $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((status)) {
case RUNNING:
{
$oaSwitch0_exit_result = "◌"; break $oaSwitch0_exit;
}
case SUCCEEDED:
{
$oaSwitch0_exit_result = "✓"; break $oaSwitch0_exit;
}
case FAILED:
{
$oaSwitch0_exit_result = "!"; break $oaSwitch0_exit;
}
case NO_RESULT_RECORDED:
{
$oaSwitch0_exit_result = "—"; break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
String icon = $oaSwitch0_exit_result;
        return MinecraftComponents.append(MinecraftComponents.literal(icon + " "), MinecraftComponents.translatable(status.translationKey()));
    }

    private static net.minecraft.network.chat.Component friendlyTool(String id) {
        int separator = id.indexOf(':');
        String name = separator >= 0 ? id.substring(separator + 1) : id;
        {
net.minecraft.network.chat.Component $oaSwitch7_exit_result;
$oaSwitch7_exit: {
switch ((name)) {
case "load_skill":
{
$oaSwitch7_exit_result = MinecraftComponents.translatable("screen.openallay.tool.load_skill"); break $oaSwitch7_exit;
}
case "run_javascript":
{
$oaSwitch7_exit_result = MinecraftComponents.translatable("screen.openallay.tool.run_javascript"); break $oaSwitch7_exit;
}
default:
{
$oaSwitch7_exit_result = MinecraftComponents.literal(name); break $oaSwitch7_exit;
}
}
}
return $oaSwitch7_exit_result;
}
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
        List<GuideUiRow.Tool> tools = dev.openallay.util.Java8Collections.toList(view.rows().stream()
                .filter(GuideUiRow.Tool.class::isInstance)
                .map(GuideUiRow.Tool.class::cast));
        if (index < 0 || index >= tools.size()) {
            throw new IllegalArgumentException("tool index is unavailable");
        }
        open(tools.get(index));
    }

    /** Opens the latest actual JavaScript activity, including real failure or interruption. */
    public boolean selectLatestJavascriptForDevelopmentProbe() {
        requireDevelopmentProbe();
        List<GuideUiRow.Tool> tools = dev.openallay.util.Java8Collections.toList(view.rows().stream().filter(GuideUiRow.Tool.class::isInstance)
                .map(GuideUiRow.Tool.class::cast)
                .filter(value -> value.activity().toolId().endsWith(":run_javascript")));
        if (tools.isEmpty()) return false;
        open(tools.get(tools.size() - 1));
        return true;
    }

    /** Opens only an actually retained source from the latest evidenced JavaScript call. */
    public boolean selectLatestSourceForDevelopmentProbe() {
        requireDevelopmentProbe();
        List<GuideUiRow.Tool> tools = dev.openallay.util.Java8Collections.toList(view.rows().stream().filter(GuideUiRow.Tool.class::isInstance)
                .map(GuideUiRow.Tool.class::cast)
                .filter(value -> value.activity().toolId().endsWith(":run_javascript")
                        && !value.activity().sources().isEmpty()));
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
            if (rowId == null || dev.openallay.util.Java8Strings.isBlank(rowId) || measuredHeight <= 0) {
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
    @dev.openallay.value.ValueType(Hit.ValueSchemaProvider.class)
private static final class Hit {
    private final GuideUiLayout.Rect rect;
    private final HitKind kind;
    private final Runnable action;
    private final String focusId;
    private final String narration;
    private Hit(GuideUiLayout.Rect rect, HitKind kind, Runnable action, String focusId, String narration) {
        this.rect = rect;
        this.kind = kind;
        this.action = action;
        this.focusId = focusId;
        this.narration = narration;
    }
    public GuideUiLayout.Rect rect() { return rect; }
    public HitKind kind() { return kind; }
    public Runnable action() { return action; }
    public String focusId() { return focusId; }
    public String narration() { return narration; }
private Hit(GuideUiLayout.Rect rect, HitKind kind, Runnable action) {
            this(rect, kind, action, null, "");
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Hit)) return false;
        Hit that = (Hit) other;
        return java.util.Objects.equals(rect, that.rect) && java.util.Objects.equals(kind, that.kind) && java.util.Objects.equals(action, that.action) && java.util.Objects.equals(focusId, that.focusId) && java.util.Objects.equals(narration, that.narration);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(rect);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(action);
        hash = 31 * hash + java.util.Objects.hashCode(focusId);
        hash = 31 * hash + java.util.Objects.hashCode(narration);
        return hash;
    }
    @Override public String toString() { return "Hit[rect=" + rect + ", kind=" + kind + ", action=" + action + ", focusId=" + focusId + ", narration=" + narration + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Hit> schema() {
            return new dev.openallay.value.ValueSchema<>(Hit.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Hit>>asList(new dev.openallay.value.ValueSchema.Component<>(Hit.class, "rect", Hit::rect), new dev.openallay.value.ValueSchema.Component<>(Hit.class, "kind", Hit::kind), new dev.openallay.value.ValueSchema.Component<>(Hit.class, "action", Hit::action), new dev.openallay.value.ValueSchema.Component<>(Hit.class, "focusId", Hit::focusId), new dev.openallay.value.ValueSchema.Component<>(Hit.class, "narration", Hit::narration)), arguments -> new Hit((GuideUiLayout.Rect) arguments[0], (HitKind) arguments[1], (Runnable) arguments[2], (String) arguments[3], (String) arguments[4]));
        }
    }
}
}
