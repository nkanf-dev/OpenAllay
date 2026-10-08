package dev.openallay.client.gui;

import dev.openallay.client.gui.GuideTextLine;
import dev.openallay.client.gui.GuideNativeFont;

import dev.openallay.client.gui.settings.DiagnosticsSettingsProjection;
import dev.openallay.client.gui.settings.ExtensionSettingsProjection;
import dev.openallay.client.gui.settings.GeneralSettingsProjection;
import dev.openallay.client.gui.settings.UiSettingsProjection;
import dev.openallay.client.gui.settings.UiSettingsDraft;
import dev.openallay.client.gui.settings.SettingsSaveCoordinator;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.client.voice.VoiceSettingsActions;
import dev.openallay.client.voice.VoiceSettingsView;
import dev.openallay.client.voice.VoiceConfig;
import dev.openallay.client.gui.GuideNativeSlider;
import dev.openallay.client.gui.GuideInputKey;
import dev.openallay.client.gui.settings.HistorySettingsProjection;
import dev.openallay.client.gui.settings.ModelProfileDraft;
import dev.openallay.client.gui.settings.ModelReasoningSettingsProjection;
import dev.openallay.client.gui.settings.ModelImageSettingsProjection;
import dev.openallay.client.gui.settings.BuiltinModelSettingsProjection;
import dev.openallay.client.gui.settings.ModelSettingsProjection;
import dev.openallay.client.gui.settings.RecipeSettingsProjection;
import dev.openallay.client.gui.settings.RequirementSettingsProjection;
import dev.openallay.settings.requirement.RequirementSettingsEnvironment;
import dev.openallay.client.gui.settings.SettingsLayout;
import dev.openallay.client.gui.settings.SettingsSection;
import dev.openallay.client.gui.settings.SkillSettingsProjection;
import dev.openallay.guide.e2e.GuideClientE2EConfig;
import dev.openallay.model.config.ModelProfileDefinition;
import dev.openallay.model.config.ModelProfilesConfig;
import dev.openallay.model.config.ModelProtocol;
import dev.openallay.model.config.SecretValue;
import dev.openallay.model.catalog.ModelCatalog;
import dev.openallay.model.catalog.ModelCatalogRequest;
import dev.openallay.settings.ClientSettingsService;
import dev.openallay.settings.ClientSettingsSnapshot;
import dev.openallay.settings.SettingsNotice;
import dev.openallay.settings.SettingsOperation;
import dev.openallay.settings.diagnostics.SettingsDiagnosticCard;
import dev.openallay.settings.diagnostics.SettingsDiagnosticsSnapshot;
import dev.openallay.settings.model.ModelConnectionResult;
import dev.openallay.settings.model.ModelProfileSettingsView;
import dev.openallay.tool.ToolResult;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import dev.openallay.client.gui.GuideGraphics;
import dev.openallay.client.gui.GuideNativeButton;
import dev.openallay.client.gui.GuideNativeEditBox;
import dev.openallay.client.gui.GuideMultilineEditor;
import dev.openallay.client.gui.GuideTooltip;
import net.minecraft.client.gui.screens.Screen;
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.network.chat.Component;

/** Native settings shell and model-profile editor backed only by ClientSettingsService. */
public final class OpenAllaySettingsScreen extends dev.openallay.client.gui.GuideNativeScreen {
    private static final int BACKGROUND = 0xE00B0D12;
    private static final int PANEL = 0xE0181B22;
    private static final int PANEL_ALT = 0xE0242933;
    private static final int ACCENT = 0xFF72D5C4;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFFA9B3BE;
    private static final int ERROR = 0xFFFF7D7D;
    private static final String REPOSITORY_URL = "https://github.com/nkanf-dev/OpenAllay";
    private static final String ABOUT_BANNER = "openallay:textures/gui/about_banner.png";

    private final ClientSettingsService service;
    private final Runnable returnToGuide;
    private final java.util.concurrent.Executor saveDispatcher;
    private volatile ClientSettingsSnapshot snapshot;
    private AutoCloseable listener;
    private SettingsLayout layout;
    private SettingsSection section = SettingsSection.GENERAL;
    private String selectedProfileId;
    private boolean selectedServerModel;
    private ModelProfileDraft draft;
    private Confirmation confirmation = Confirmation.NONE;
    private String localNotice = "";
    private boolean draftEnabled;
    private ModelProtocol draftProtocol;
    private int editorScroll;
    private int modelEditorContentHeight = 240;
    private boolean updatingAutomaticContext;
    private boolean updatingAutomaticOutput;
    private final BuiltinModelSettingsProjection.EventCache modelEstimateCache =
            new BuiltinModelSettingsProjection.EventCache();
    private String selectedSkillName;
    private String selectedCommunitySkillId;
    private SkillTab skillTab = SkillTab.INSTALLED;
    private boolean skillCommunityRefreshAttempted;
    private boolean skillEditing;
    private boolean narrowSkillDetail;
    private String skillDraftMarkdown = "";
    private String skillImportPathDraft = "";
    private GuideMultilineEditor skillEditor;
    private GuideNativeEditBox skillImportPath;
    private String selectedExtensionId;
    private ExtensionTab extensionTab = ExtensionTab.INSTALLED;
    private boolean extensionCommunityRefreshAttempted;
    private boolean narrowExtensionDetail;
    private String extensionImportPathDraft = "";
    private GuideNativeEditBox extensionImportPath;
    private boolean openingRequirementReview;
    private int skillDetailScroll;
    private int skillDetailContentHeight;
    private int pageScroll;
    private int pageContentHeight;
    private ClientSettingsService.HistoryConfirmationToken historyConfirmation;
    private GuideNativeEditBox id;
    private GuideNativeEditBox displayName;
    private GuideNativeEditBox baseUrl;
    private GuideNativeEditBox model;
    private PasswordEditBox apiKey;
    private String pendingApiKey = "";
    private GuideNativeEditBox contextWindow;
    private GuideNativeEditBox maxOutput;
    private GuideNativeEditBox connectTimeout;
    private GuideNativeEditBox requestTimeout;
    private GuideNativeEditBox assistantName;
    private String assistantNameDraft;
    private List<String> catalogModelIds = List.of();
    private boolean modelCatalogOpen;
    private int modelCatalogPage;
    private long modelCatalogGeneration;
    private final UiSettingsDraft uiDraft;
    private final SettingsSaveCoordinator editorSave = new SettingsSaveCoordinator();
    private final java.util.Map<String, String> uiIntegerDrafts = new java.util.HashMap<>();
    private final java.util.Map<String, GuideNativeEditBox> uiIntegerFields = new java.util.HashMap<>();
    private UiSettingsProjection.Group uiGroup = UiSettingsProjection.Group.FULLSCREEN;
    private int uiScroll;
    private int uiContentHeight;
    private int navigationScroll;
    private boolean sectionMenuOpen;
    private boolean editorMenuOpen;
    private UiActions uiActions;
    private VoiceSettingsActions voiceActions;
    private VoiceSettingsView voiceView;
    private VoiceConfig voiceDraft;
    private int voiceScroll;
    private int voiceContentHeight;
    private String voiceModelPath = "";
    private String voiceRuntimePath = "";
    private String voiceHttpUrl = "";
    private String voiceHttpModel = "";
    private String voiceApiKeyDraft = "";
    private final java.util.Map<String, GuideNativeEditBox> voiceFields = new java.util.HashMap<>();

    /** Loader hooks only. Neither preview nor editor entry creates an Agent task. */
    public interface UiActions {
        void editHud(OpenAllaySettingsScreen returnScreen, GuideDisplayConfig draft,
                java.util.function.Consumer<GuideDisplayConfig> applied);
        void previewNotification(GuideUiConfig.Notifications config);
    }

    public OpenAllaySettingsScreen withUiActions(UiActions actions) {
        uiActions = Objects.requireNonNull(actions, "actions");
        return this;
    }

    public OpenAllaySettingsScreen withVoiceActions(VoiceSettingsActions actions) {
        voiceActions = Objects.requireNonNull(actions, "actions");
        voiceView = actions.view();
        resetVoiceDraft();
        return this;
    }

    public OpenAllaySettingsScreen(
            ClientSettingsService service,
            Runnable returnToGuide) {
        this(service, returnToGuide, null);
    }

    OpenAllaySettingsScreen(ClientSettingsService service, Runnable returnToGuide,
            java.util.concurrent.Executor saveDispatcher) {
        super(MinecraftComponents.translatable("screen.openallay.settings.title"));
        this.saveDispatcher = saveDispatcher == null ? command -> MinecraftClientWindow.execute(minecraft, command) : saveDispatcher;
        this.service = Objects.requireNonNull(service, "service");
        this.returnToGuide = Objects.requireNonNull(returnToGuide, "returnToGuide");
        this.snapshot = service.snapshot();
        assistantNameDraft = snapshot.display().assistantName();
        uiDraft = new UiSettingsDraft(snapshot.display());
        selectedSkillName = snapshot.skills().skills().isEmpty()
                ? null
                : snapshot.skills().skills().get(0).metadata().name();
        selectedCommunitySkillId = snapshot.skillCommunity().packages().isEmpty()
                ? null
                : snapshot.skillCommunity().packages().get(0).id();
        selectedExtensionId = extensionProjection().installed().isEmpty()
                ? null
                : extensionProjection().installed().get(0).id();
        select(snapshot.models().config().defaultProfileId());
    }

    @Override
    protected void initGuideScreen() {
        id = null;
        assistantName = null;
        uiIntegerFields.clear();
        voiceFields.clear();
        layout = SettingsLayout.calculate(width, height, section);
        addHeaderActions();
        if (sectionMenuOpen) {
            addSectionMenu();
            return;
        }
        if (layout.wide()) {
            addSectionNavigation();
        }
        if (editorMenuOpen) {
            addEditorMenu();
            addFooterActions();
            updateEditorSaveControls();
            return;
        }
        if (section == SettingsSection.MODELS) {
            addModelsPage();
        } else if (section == SettingsSection.EXTENSIONS) {
            addExtensionsPage();
        } else if (section == SettingsSection.SKILLS) {
            addSkillsPage();
        } else if (section == SettingsSection.VOICE) {
            addVoicePage();
        } else if (section == SettingsSection.UI) {
            addUiPage();
        } else if (section == SettingsSection.GENERAL) {
            addGeneralPage();
        } else if (section == SettingsSection.HISTORY) {
            addHistoryPage();
        } else if (section == SettingsSection.ABOUT) {
            addAboutPage();
        }
        addFooterActions();
        updateEditorSaveControls();
    }

    @Override
    protected void guideAdded() {
        listener = service.listen(next -> {
            if (layout != null) {
                captureDraft();
            }
            ClientSettingsSnapshot previous = snapshot;
            snapshot = next;
            uiDraft.published(next.display());
            refreshAutomaticContext();
            if (previous.generation() != next.generation()) {
                historyConfirmation = null;
            }
            if (!previous.models().config().equals(next.models().config())
                    || completedReload(
                            previous, next, SettingsOperation.Kind.RELOADING_MODELS)) {
                if (!selectedServerModel && !editorSave.busy()) {
                    String retained = next.models().profiles().stream()
                            .map(profile -> profile.definition().id())
                            .filter(profileId -> profileId.equals(selectedProfileId))
                            .findFirst()
                            .orElse(next.models().config().defaultProfileId());
                    select(retained);
                }
                confirmation = Confirmation.NONE;
            }
            if (selectedServerModel && !next.serverModel().available()) {
                select(next.models().config().defaultProfileId());
            }
            if (!previous.display().equals(next.display())
                    || completedReload(
                            previous, next, SettingsOperation.Kind.RELOADING_DISPLAY)) {
                if (!editorSave.busy()
                        && assistantNameDraft.equals(previous.display().assistantName())) {
                    assistantNameDraft = next.display().assistantName();
                }
                confirmation = Confirmation.NONE;
            }
            if (!previous.skills().equals(next.skills())) {
                if (selectedSkillName == null || next.skills().find(selectedSkillName).isEmpty()) {
                    selectedSkillName = next.skills().skills().isEmpty()
                            ? null
                            : next.skills().skills().get(0).metadata().name();
                }
                skillEditing = false;
                skillDraftMarkdown = "";
            }
            SkillSettingsProjection.Community community = skillProjection().community();
            if (selectedCommunitySkillId == null
                    || community.find(selectedCommunitySkillId).isEmpty()) {
                selectedCommunitySkillId = community.packages().isEmpty()
                        ? null
                        : community.packages().get(0).id();
            }
            ExtensionSettingsProjection extensionProjection = extensionProjection();
            List<ExtensionSettingsProjection.ExtensionCard> cards =
                    extensionTab == ExtensionTab.INSTALLED
                            ? extensionProjection.installed()
                            : extensionProjection.community();
            boolean selectionVisible = selectedExtensionId != null
                    && cards.stream()
                            .anyMatch(extension -> extension.id().equals(selectedExtensionId));
            if (!selectionVisible) {
                selectedExtensionId = cards.isEmpty() ? null : cards.get(0).id();
            }
            if (layout != null) {
                // Background history/source publications must not rebuild a dragged UI slider.
                if (section == SettingsSection.UI && !sectionMenuOpen) {
                    updateUiApplyButton();
                    updateEditorSaveControls();
                } else {
                    guideRebuildWidgets();
                    maybeRefreshVisibleCommunity();
                }
            }
        });
    }

    @Override
    protected void guideRemoved() {
        GuideTextInputFocus.release(this);
        voiceApiKeyDraft = "";
        if (!openingRequirementReview) {
            service.cancelPackagePreparation();
            snapshot.requirementReview().ifPresent(review -> service.cancelPackageInstall(review.token()));
        }
        service.cancelConnectionTest();
        service.cancelModelCatalog();
        historyConfirmation = null;
        narrowSkillDetail = false;
        narrowExtensionDetail = false;
        skillEditing = false;
        if (listener != null) {
            try {
                listener.close();
            } catch (Exception ignored) {
                // Detaching a local listener has no recovery action.
            }
            listener = null;
        }
    }

    @Override
    protected void repositionGuideElements() {
        captureDraft();
        guideRebuildWidgets();
    }

    @Override
    public void onClose() { done(); }

    private void closeAfterSave() { returnToGuide.run(); }

    @Override
    public void tick() {
        tickGuideWidgets();
        super.tick();
        service.refreshRuntimeState();
        if (voiceActions != null) {
            VoiceSettingsView next = voiceActions.view();
            boolean controlsChanged = voiceView == null || voiceView.busy() != next.busy()
                    || !voiceView.devices().equals(next.devices())
                    || !voiceView.config().equals(next.config());
            voiceView = next;
            if (controlsChanged && section == SettingsSection.VOICE && layout != null) guideRebuildWidgets();
        }
        service.snapshot().requirementReview().ifPresent(review -> {
            captureDraft();
            openingRequirementReview = true;
            try {
                dev.openallay.client.gui.MinecraftClientWindow.showScreen(minecraft, new RequirementReviewScreen(service, this, review));
            } finally {
                openingRequirementReview = false;
            }
        });
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean guideMouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY) {
        if ((layout.wide() && layout.navigation().contains(mouseX, mouseY)) || sectionMenuOpen) {
            int maximum = sectionMenuOpen
                    ? Math.max(0, SettingsSection.topLevel().size() - sectionMenuRows())
                    : layout.maximumNavigationScroll(SettingsSection.topLevel().size());
            int next = net.minecraft.util.Mth.clamp(navigationScroll
                    - (int) Math.round(scrollY * (sectionMenuOpen ? 1 : 24)), 0, maximum);
            if (next != navigationScroll) {
                navigationScroll = next;
                guideRebuildWidgets();
            }
            return true;
        }
        if (section == SettingsSection.VOICE && layout.editor().contains(mouseX, mouseY)) {
            int next = net.minecraft.util.Mth.clamp(voiceScroll - (int) Math.round(scrollY * 24),
                    0, Math.max(0, voiceContentHeight - layout.editor().height()));
            if (next != voiceScroll) {
                voiceScroll = next;
                guideRebuildWidgets();
            }
            return true;
        }
        if (section == SettingsSection.UI && layout.editor().contains(mouseX, mouseY)) {
            int viewport = Math.max(1, layout.editor().height() - uiControlsInset());
            int maximum = Math.max(0, uiContentHeight - viewport);
            // A wheel event must not skip the interval where a 20-pixel control is fully visible.
            int maximumStep = Math.max(1, viewport - 20);
            int delta = net.minecraft.util.Mth.clamp((int) Math.round(scrollY * 24), -maximumStep, maximumStep);
            int next = net.minecraft.util.Mth.clamp(uiScroll - delta, 0, maximum);
            if (next != uiScroll) {
                uiScroll = next;
                guideRebuildWidgets();
            }
            return true;
        }
        if (section == SettingsSection.MODELS && layout.editor().contains(mouseX, mouseY)) {
            if (modelCatalogOpen) {
                int pageSize = modelCatalogPageSize();
                int pages = Math.max(1, (catalogModelIds.size() + pageSize - 1) / pageSize);
                modelCatalogPage = net.minecraft.util.Mth.clamp(
                        modelCatalogPage - (int) Math.signum(scrollY), 0, pages - 1);
                guideRebuildWidgets();
                return true;
            }
            captureDraft();
            int viewport = Math.max(1, layout.editor().height() - 38);
            int maximum = Math.max(0, modelEditorContentHeight - viewport);
            editorScroll = net.minecraft.util.Mth.clamp(
                    editorScroll - (int) Math.round(scrollY * 22), 0, maximum);
            guideRebuildWidgets();
            return true;
        }
        if (section == SettingsSection.SKILLS && !skillEditing
                && (layout.wide() || narrowSkillDetail)
                && layout.editor().contains(mouseX, mouseY)) {
            int inset = skillTab == SkillTab.COMMUNITY ? 90 : 94;
            int viewport = Math.max(1, layout.editor().height() - inset);
            skillDetailScroll = net.minecraft.util.Mth.clamp(
                    skillDetailScroll - (int) Math.round(scrollY * 24),
                    0, Math.max(0, skillDetailContentHeight - viewport));
            return true;
        }
        if ((section == SettingsSection.GENERAL || section == SettingsSection.ABOUT)
                && layout.editor().contains(mouseX, mouseY)) {
            captureDraft();
            int replacement = net.minecraft.util.Mth.clamp(
                    pageScroll - (int) Math.round(scrollY * 24),
                    0, layout.maximumPageScroll(pageContentHeight));
            if (replacement != pageScroll) {
                pageScroll = replacement;
                guideRebuildWidgets();
            }
            return true;
        }
        boolean skillList = section == SettingsSection.SKILLS
                && (layout.wide() ? layout.list() : layout.content()).contains(mouseX, mouseY)
                && (layout.wide() || !narrowSkillDetail);
        boolean extensionList = section == SettingsSection.EXTENSIONS
                && (layout.wide() ? layout.list() : layout.content()).contains(mouseX, mouseY)
                && (layout.wide() || !narrowExtensionDetail);
        boolean scrollablePage = ((section == SettingsSection.DIAGNOSTICS
                        || section == SettingsSection.HISTORY
                        || (section == SettingsSection.EXTENSIONS
                                && (layout.wide() || narrowExtensionDetail)))
                && layout.content().contains(mouseX, mouseY))
                || skillList
                || extensionList;
        if (scrollablePage) {
            int maximum = Math.max(0, pageContentHeight - layout.content().height() + 18);
            int replacement = net.minecraft.util.Mth.clamp(
                    pageScroll - (int) Math.round(scrollY * 24), 0, maximum);
            if (replacement != pageScroll) {
                pageScroll = replacement;
                if (section == SettingsSection.EXTENSIONS) guideRebuildWidgets();
            }
            return true;
        }
        return super.guideMouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void paintGuideScreen(
            GuideGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        panel(graphics, layout.header(), PANEL);
        panel(graphics, layout.content(), PANEL);
        panel(graphics, layout.footer(), PANEL_ALT);
        graphics.text(font, getTitle(), layout.header().x() + 8, layout.header().y() + 9, TEXT, false);
        if (layout.wide()) {
            panel(graphics, layout.navigation(), PANEL_ALT);
        }
        if (sectionMenuOpen) {
            renderSectionMenu(graphics);
        } else if (editorMenuOpen) {
            graphics.text(font, MinecraftComponents.translatable("screen.openallay.settings.more"),
                    layout.editor().x() + 8, layout.editor().y() + 9, ACCENT, false);
        } else if (section == SettingsSection.VOICE) {
            renderVoice(graphics);
        } else if (section == SettingsSection.UI) {
            renderUi(graphics);
        } else if (section == SettingsSection.MODELS) {
            renderModels(graphics);
        } else if (section == SettingsSection.EXTENSIONS) {
            renderExtensions(graphics);
        } else if (section == SettingsSection.SKILLS) {
            renderSkills(graphics);
        } else if (section == SettingsSection.GENERAL) {
            renderGeneral(graphics);
        } else if (section == SettingsSection.HISTORY) {
            renderHistory(graphics);
        } else if (section == SettingsSection.DIAGNOSTICS) {
            renderDiagnostics(graphics);
        } else if (section == SettingsSection.ABOUT) {
            renderAbout(graphics);
        } else {
            renderPlaceholder(graphics);
        }
        renderNotice(graphics);
        renderGuideWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void addHeaderActions() {
        int y = layout.header().y() + 4;
        boolean hasEditorMenu = section == SettingsSection.UI || section == SettingsSection.VOICE;
        int moreX = layout.header().right() - (layout.showBack() ? 90 : 28);
        if (hasEditorMenu) {
            GuideNativeButton more = addGuideWidget(OpenAllayButton.create(MinecraftComponents.literal("⋯"), ignored -> {
                        captureDraft();
                        editorMenuOpen = !editorMenuOpen;
                        sectionMenuOpen = false;
                        guideRebuildWidgets();
                    }).bounds(moreX, y, 22, 20).build());
            dev.openallay.client.gui.GuideNativeWidgetTooltips.set(more, GuideTooltip.create(MinecraftComponents.translatable("screen.openallay.settings.more")));
        }
        if (layout.showBack()) {
            int backX = layout.header().right() - 62;
            addGuideWidget(OpenAllayButton.create(
                            MinecraftComponents.translatable("screen.openallay.settings.back"),
                            ignored -> backOrClose())
                    .bounds(backX, y, 56, 20)
                    .build());
            int sectionX = layout.header().x() + 90;
            addGuideWidget(OpenAllayButton.create(
                            MinecraftComponents.translatable(section.translationKey()),
                            ignored -> {
                                captureDraft();
                                sectionMenuOpen = !sectionMenuOpen;
                                editorMenuOpen = false;
                                navigationScroll = 0;
                                guideRebuildWidgets();
                            })
                    .bounds(sectionX, y, Math.max(24,
                            (hasEditorMenu ? moreX : backX) - sectionX - 4), 20)
                    .build());
        }
    }

    private void addEditorMenu() {
        int x = layout.editor().x() + 8;
        int y = layout.editor().y() + 30;
        int w = Math.max(80, layout.editor().width() - 16);
        addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable("screen.openallay.settings.discard_unsaved"),
                        ignored -> discardEditorDraft()).bounds(x, y, w, 20).build());
        if (section == SettingsSection.VOICE && voiceActions != null) {
            GuideNativeButton reload = addGuideWidget(OpenAllayButton.create(
                            MinecraftComponents.translatable("screen.openallay.settings.voice.reload"), ignored -> {
                                editorMenuOpen = false;
                                acceptVoice(voiceActions.reload(), true);
                            }).bounds(x, y + 26, w, 20).build());
            reload.active = voiceView != null && !voiceView.busy();
        }
    }

    private void discardEditorDraft() {
        if (editorSave.busy()) return;
        if (section == SettingsSection.UI) {
            uiDraft.cancel();
            uiIntegerDrafts.clear();
        } else if (section == SettingsSection.VOICE) resetVoiceDraft();
        editorMenuOpen = false;
        localNotice = "";
        if (layout != null) guideRebuildWidgets();
    }

    private void resetUiGroup() {
        if (editorSave.busy()) return;
        uiDraft.reset(uiGroup);
        if (uiGroup == UiSettingsProjection.Group.HUD) uiIntegerDrafts.clear();
        localNotice = "";
        if (layout != null) guideRebuildWidgets();
    }

    private void resetVoiceDefaults() {
        if (editorSave.busy() || voiceDraft == null) return;
        captureDraft();
        VoiceConfig defaults = VoiceConfig.defaults();
        voiceDraft = new VoiceConfig(defaults.enabled(), defaults.backend(), defaults.deviceId(),
                defaults.maxClipSeconds(), defaults.language(), defaults.cpuThreads(),
                voiceDraft.nativeModelDirectory(), defaults.httpBaseUrl(), defaults.httpModel(), voiceDraft.credential(),
                defaults.gameplayAction());
        voiceHttpUrl = defaults.httpBaseUrl().toString();
        voiceHttpModel = defaults.httpModel();
        // Existing model selection and credential stay available; reset never removes files or keys.
        localNotice = "";
        if (layout != null) guideRebuildWidgets();
    }

    private void addSectionNavigation() {
        int x = layout.navigation().x() + 6;
        int y = layout.navigation().y() + 8 - navigationScroll;
        int buttonWidth = layout.navigation().width() - 12;
        for (SettingsSection candidate : SettingsSection.topLevel()) {
            GuideNativeButton button = addGuideWidget(OpenAllayButton.create(
                            MinecraftComponents.translatable(candidate.translationKey()),
                            ignored -> switchSection(candidate))
                    .selected(candidate == section)
                    .bounds(x, y, buttonWidth, 20)
                    .build());
            button.active = candidate != section;
            button.visible = y >= layout.navigation().y()
                    && y + 20 <= layout.navigation().bottom();
            y += 24;
        }
    }

    @Override
    public boolean guideKeyPressed(GuideInputKey event) {
        GuideKeyInput input = GuideKeyInput.from(event);
        if (editorMenuOpen && input.intent() == GuideKeyIntent.ESCAPE) {
            editorMenuOpen = false;
            guideRebuildWidgets();
            return true;
        }
        if (sectionMenuOpen && input.intent() == GuideKeyIntent.ESCAPE) {
            sectionMenuOpen = false;
            guideRebuildWidgets();
            return true;
        }
        if (input.intent() == GuideKeyIntent.ESCAPE) {
            done();
            return true;
        }
        return super.guideKeyPressed(event);
    }

    private int sectionMenuRows() {
        return Math.max(1, (layout.content().height() - 26) / 24);
    }

    private void addSectionMenu() {
        SettingsLayout.Rect area = layout.content();
        int rows = sectionMenuRows();
        List<SettingsSection> sections = SettingsSection.topLevel();
        int maximum = Math.max(0, sections.size() - rows);
        navigationScroll = net.minecraft.util.Mth.clamp(navigationScroll, 0, maximum);
        for (int index = navigationScroll; index < Math.min(sections.size(), navigationScroll + rows); index++) {
            SettingsSection candidate = sections.get(index);
            addGuideWidget(OpenAllayButton.create(MinecraftComponents.translatable(candidate.translationKey()),
                            ignored -> switchSection(candidate))
                    .selected(candidate == section)
                    .bounds(area.x() + 6, area.y() + 4 + (index - navigationScroll) * 24,
                            area.width() - 12, 20).build());
        }
        GuideNativeButton previous = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable("screen.openallay.settings.navigation.previous"), ignored -> {
                            navigationScroll = Math.max(0, navigationScroll - rows);
                            guideRebuildWidgets();
                        }).bounds(area.x() + 6, area.bottom() - 22, (area.width() - 16) / 2, 20).build());
        previous.active = navigationScroll > 0;
        GuideNativeButton next = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable("screen.openallay.settings.navigation.next"), ignored -> {
                            navigationScroll = Math.min(maximum, navigationScroll + rows);
                            guideRebuildWidgets();
                        }).bounds(area.x() + 10 + (area.width() - 16) / 2, area.bottom() - 22,
                                (area.width() - 16) / 2, 20).build());
        next.active = navigationScroll < maximum;
    }

    private void renderSectionMenu(GuideGraphics graphics) {
        graphics.text(font, MinecraftComponents.translatable("screen.openallay.settings.navigation.choose"),
                layout.footer().x() + 8, layout.footer().y() + 9, MUTED, false);
    }

    private void addUiPage() {
        SettingsLayout.Rect area = layout.editor();
        int tabWidth = (area.width() - 14) / UiSettingsProjection.Group.values().length;
        int index = 0;
        for (UiSettingsProjection.Group group : UiSettingsProjection.Group.values()) {
            addGuideWidget(OpenAllayButton.create(MinecraftComponents.translatable(group.translationKey()), ignored -> {
                        uiGroup = group;
                        uiScroll = 0;
                        guideRebuildWidgets();
                    }).selected(group == uiGroup)
                    .bounds(area.x() + 6 + index++ * (tabWidth + 2), area.y() + 4, tabWidth, 20).build());
        }
        int x = area.x() + 10;
        int w = Math.max(120, area.width() - 20);
        int y = area.y() + 34 - uiScroll;
        GuideUiConfig.Fullscreen full = uiDraft.ui().fullscreen();
        GuideUiConfig.Hud hud = uiDraft.ui().hud();
        GuideUiConfig.Notifications notifications = uiDraft.ui().notifications();
        switch (uiGroup) {
            case FULLSCREEN -> {
                uiButton("density", enumLabel("density", full.density()), x, y, w, () ->
                        changeFull(new GuideUiConfig.Fullscreen(full.density() == GuideUiConfig.Density.COMPACT
                                ? GuideUiConfig.Density.COMFORTABLE : GuideUiConfig.Density.COMPACT,
                                full.sessionRailVisible(), full.theme())));
                y += 26;
                uiToggle("session_rail", full.sessionRailVisible(), x, y, w, () ->
                        changeFull(new GuideUiConfig.Fullscreen(full.density(), !full.sessionRailVisible(),
                                full.theme())));
                y += 26;
                uiButton("theme", enumLabel("theme", full.theme()), x, y, w, () ->
                        changeFull(new GuideUiConfig.Fullscreen(full.density(), full.sessionRailVisible(),
                                full.theme() == GuideUiConfig.Theme.CHARCOAL
                                        ? GuideUiConfig.Theme.MINT : GuideUiConfig.Theme.CHARCOAL)));
                y += 26;
                uiToggle("animations", uiDraft.animationsEnabled(), x, y, w, () -> {
                    uiDraft.previewAnimations(!uiDraft.animationsEnabled());
                    guideRebuildWidgets();
                });
                y += 26;
            }
            case HUD -> {
                y = area.y() + uiControlsInset() - uiScroll;
                uiToggle("hud_enabled", hud.enabled(), x, y, w, () -> changeHud(uiDraft.ui().hud().withEnabled(!uiDraft.ui().hud().enabled())));
                y += 26;
                uiButton("anchor", enumLabel("anchor", hud.anchor()), x, y, w, () -> {
                    GuideUiConfig.Anchor[] anchors = GuideUiConfig.Anchor.values();
                    GuideUiConfig.Hud current = uiDraft.ui().hud();
                    changeHud(current.withPlacement(anchors[(current.anchor().ordinal() + 1) % anchors.length],
                            current.offsetX(), current.offsetY(), current.width(), current.height(), current.scale()));
                });
                y += 26;
                uiInteger("offset_x", hud.offsetX(), x, y, w, value -> previewHud(uiDraft.ui().hud().withPlacement(
                        uiDraft.ui().hud().anchor(), value, uiDraft.ui().hud().offsetY(),
                        uiDraft.ui().hud().width(), uiDraft.ui().hud().height(), uiDraft.ui().hud().scale())));
                y += 26;
                uiInteger("offset_y", hud.offsetY(), x, y, w, value -> previewHud(uiDraft.ui().hud().withPlacement(
                        uiDraft.ui().hud().anchor(), uiDraft.ui().hud().offsetX(), value,
                        uiDraft.ui().hud().width(), uiDraft.ui().hud().height(), uiDraft.ui().hud().scale())));
                y += 26;
                uiSlider("width", hud.width(), 160, 480, true, x, y, w, value -> {
                    GuideUiConfig.Hud current = uiDraft.ui().hud();
                    previewHud(current.withPlacement(current.anchor(), current.offsetX(), current.offsetY(),
                            (int) Math.round(value), current.height(), current.scale()));
                });
                y += 26;
                uiSlider("height", hud.height(), 44, 240, true, x, y, w, value -> {
                    GuideUiConfig.Hud current = uiDraft.ui().hud();
                    previewHud(current.withPlacement(current.anchor(), current.offsetX(), current.offsetY(),
                            current.width(), (int) Math.round(value), current.scale()));
                });
                y += 26;
                uiSlider("scale", hud.scale(), .75, 1.75, false, x, y, w, value -> {
                    GuideUiConfig.Hud current = uiDraft.ui().hud();
                    previewHud(current.withPlacement(current.anchor(), current.offsetX(), current.offsetY(),
                            current.width(), current.height(), Math.round(value * 100) / 100.0));
                });
                y += 26;
                uiSlider("opacity", hud.backgroundOpacity(), 0, 1, false, x, y, w,
                        value -> previewHud(uiDraft.ui().hud().withBackgroundOpacity(Math.round(value * 100) / 100.0)));
                y += 26;
                uiButton("opacity_reset", MinecraftComponents.empty(), x, y, w,
                        () -> changeHud(uiDraft.ui().hud().withBackgroundOpacity(GuideUiConfig.Hud.defaults().backgroundOpacity())));
                y += 26;
                uiToggle("collapsed", hud.collapsed(), x, y, w, () -> changeHud(uiDraft.ui().hud().withCollapsed(!uiDraft.ui().hud().collapsed())));
                y += 26;
                uiSlider("reply_lines", hud.maxReplyLines(), 0, 80, true, x, y, w, value -> {
                    GuideUiConfig.Hud current = uiDraft.ui().hud();
                    previewHud(current.withContent((int) Math.round(value), current.showLatestReply(), current.showStreamingPreview()));
                });
                y += 26;
                uiToggle("latest_reply", hud.showLatestReply(), x, y, w,
                        () -> changeHud(uiDraft.ui().hud().withContent(uiDraft.ui().hud().maxReplyLines(), !uiDraft.ui().hud().showLatestReply(), uiDraft.ui().hud().showStreamingPreview())));
                y += 26;
                uiToggle("streaming", hud.showStreamingPreview(), x, y, w,
                        () -> changeHud(uiDraft.ui().hud().withContent(uiDraft.ui().hud().maxReplyLines(), uiDraft.ui().hud().showLatestReply(), !uiDraft.ui().hud().showStreamingPreview())));
                y += 26;
                uiToggle("hide_debug", hud.hideWithDebug(), x, y, w,
                        () -> changeHud(uiDraft.ui().hud().withVisibility(!uiDraft.ui().hud().hideWithDebug(), uiDraft.ui().hud().hideOnOtherScreens())));
                y += 26;
                uiToggle("hide_screens", hud.hideOnOtherScreens(), x, y, w,
                        () -> changeHud(uiDraft.ui().hud().withVisibility(uiDraft.ui().hud().hideWithDebug(), !uiDraft.ui().hud().hideOnOtherScreens())));
                y += 26;
                GuideNativeButton edit = uiButton("edit_hud", MinecraftComponents.empty(), x, y, w, this::editHud);
                edit.active = uiActions != null && snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
                if (uiActions == null) dev.openallay.client.gui.GuideNativeWidgetTooltips.set(edit, GuideTooltip.create(MinecraftComponents.translatable(
                        "screen.openallay.settings.ui.actions_unavailable")));
                y += 26;
            }
            case NOTIFICATIONS -> {
                uiToggle("notifications_enabled", notifications.enabled(), x, y, w,
                        () -> changeNotifications(uiDraft.ui().notifications().withEnabled(!uiDraft.ui().notifications().enabled())));
                y += 26;
                uiButton("notification_policy", enumLabel("policy", notifications.policy()), x, y, w,
                        () -> changeNotifications(uiDraft.ui().notifications().withPolicy(
                                uiDraft.ui().notifications().policy() == GuideUiConfig.NotificationPolicy.ALWAYS
                                        ? GuideUiConfig.NotificationPolicy.WHEN_GUIDE_NOT_VISIBLE
                                        : GuideUiConfig.NotificationPolicy.ALWAYS)));
                y += 26;
                uiToggle("notify_replies", uiDraft.ui().notifications().replyCompleted(), x, y, w,
                        () -> changeNotifications(uiDraft.ui().notifications().withEvents(!uiDraft.ui().notifications().replyCompleted(),
                                uiDraft.ui().notifications().cardBatches(), uiDraft.ui().notifications().taskFailures())));
                y += 26;
                uiToggle("notify_cards", notifications.cardBatches(), x, y, w,
                        () -> changeNotifications(uiDraft.ui().notifications().withEvents(uiDraft.ui().notifications().replyCompleted(),
                                !uiDraft.ui().notifications().cardBatches(), uiDraft.ui().notifications().taskFailures())));
                y += 26;
                uiToggle("notify_failures", notifications.taskFailures(), x, y, w,
                        () -> changeNotifications(uiDraft.ui().notifications().withEvents(uiDraft.ui().notifications().replyCompleted(),
                                uiDraft.ui().notifications().cardBatches(), !uiDraft.ui().notifications().taskFailures())));
                y += 26;
                uiSlider("duration", notifications.durationSeconds(), 3, 15, true, x, y, w,
                        value -> {
                            uiDraft.preview(uiDraft.ui().withNotifications(uiDraft.ui().notifications()
                                    .withDurationSeconds((int) Math.round(value))));
                            updateUiApplyButton();
                        });
                y += 26;
                GuideNativeButton preview = uiButton("test_notification", MinecraftComponents.empty(), x, y, w,
                        () -> { if (uiActions != null) uiActions.previewNotification(uiDraft.ui().notifications()); });
                preview.active = uiActions != null;
                if (uiActions == null) dev.openallay.client.gui.GuideNativeWidgetTooltips.set(preview, GuideTooltip.create(MinecraftComponents.translatable(
                        "screen.openallay.settings.ui.actions_unavailable")));
                y += 26;
            }
        }
        uiContentHeight = y + uiScroll - (area.y() + uiControlsInset());
    }

    private GuideNativeButton uiButton(String key, Component value, int x, int y, int w, Runnable action) {
        Component label = MinecraftComponents.translatable("screen.openallay.settings.ui." + key);
        if (!MinecraftComponents.getString(value).isBlank()) label = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(label), " · "), value);
        GuideNativeButton button = addGuideWidget(OpenAllayButton.create(label, ignored -> action.run())
                .bounds(x, y, w, 20).build());
        button.visible = uiWidgetVisible(y, 20);
        return button;
    }

    private void uiToggle(String key, boolean enabled, int x, int y, int w, Runnable action) {
        uiButton(key, MinecraftComponents.translatable("screen.openallay.settings.ui." + (enabled ? "on" : "off")),
                x, y, w, action);
    }

    private Component enumLabel(String kind, Enum<?> value) {
        return MinecraftComponents.translatable("screen.openallay.settings.ui." + kind + "."
                + value.name().toLowerCase(java.util.Locale.ROOT));
    }

    private int uiControlsInset() {
        return uiGroup == UiSettingsProjection.Group.HUD
                ? Math.min(100, Math.max(34, layout.editor().height() - 24)) : 34;
    }

    private boolean uiWidgetVisible(int y, int h) {
        return y >= layout.editor().y() + uiControlsInset() && y + h <= layout.editor().bottom();
    }

    private void uiInteger(String key, int value, int x, int y, int w, java.util.function.IntConsumer changed) {
        GuideNativeEditBox field = new GuideNativeEditBox(font, x + w / 2, y, w / 2, 20,
                MinecraftComponents.translatable("screen.openallay.settings.ui." + key));
        field.setMaxLength(6);
        field.setValue(uiIntegerDrafts.getOrDefault(key, Integer.toString(value)));
        field.setResponder(text -> {
            uiIntegerDrafts.put(key, text);
            try {
                changed.accept(Integer.parseInt(text));
                localNotice = "";
            } catch (IllegalArgumentException invalid) {
                localNotice = MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.ui.invalid_range"));
            }
        });
        field.setVisible(uiWidgetVisible(y, 20));
        uiIntegerFields.put(key, field);
        addGuideWidget(field);
    }

    private void uiSlider(String key, double value, double minimum, double maximum, boolean integral,
            int x, int y, int w, java.util.function.DoubleConsumer changed) {
        UiSlider slider = new UiSlider(x, y, w, key, value, minimum, maximum, integral, changed);
        slider.visible = uiWidgetVisible(y, 20);
        addGuideWidget(slider);
    }

    private void changeFull(GuideUiConfig.Fullscreen value) {
        uiDraft.preview(uiDraft.ui().withFullscreen(value));
        guideRebuildWidgets();
    }

    private void previewHud(GuideUiConfig.Hud value) {
        uiDraft.preview(uiDraft.ui().withHud(value));
        updateUiApplyButton();
    }

    private void changeHud(GuideUiConfig.Hud value) {
        previewHud(value);
        guideRebuildWidgets();
    }

    private void changeNotifications(GuideUiConfig.Notifications value) {
        uiDraft.preview(uiDraft.ui().withNotifications(value));
        guideRebuildWidgets();
    }

    private void updateUiApplyButton() {
        for (var child : children()) {
            if (child instanceof GuideNativeButton button && MinecraftComponents.getString(button.getMessage()).equals(
                    MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.ui.apply")))) {
                button.active = !editorSave.busy() && (uiDraft.dirty() || !uiIntegerDrafts.isEmpty())
                        && snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
            }
        }
    }

    private void applyUi() { saveUi(false); }

    private void saveUi(boolean closeOnSuccess) {
        captureDraft();
        if (!validateUiDraft()) return;
        saveUiCandidate(uiDraft.candidate(snapshot.display()), closeOnSuccess);
    }

    private void editHud() {
        if (uiActions == null || editorSave.busy()) return;
        captureDraft();
        if (!validateUiDraft()) return;
        uiActions.editHud(this, uiDraft.candidate(snapshot.display()), this::applyUiCandidate);
    }

    private void applyUiCandidate(GuideDisplayConfig returned) {
        if (editorSave.busy()) return;
        // Freeze all UI fields from the child editor, but keep the latest independently saved General fields.
        GuideDisplayConfig candidate = service.snapshot().display().withUi(returned.ui())
                .withAnimationsEnabled(returned.animationsEnabled());
        uiDraft.adopt(candidate);
        // These widgets belonged to the removed parent Screen. Synchronize them before service listeners
        // can capture drafts, and retain the same values if persistence fails and the editor is rebuilt.
        synchronizeUiInteger("offset_x", candidate.ui().hud().offsetX());
        synchronizeUiInteger("offset_y", candidate.ui().hud().offsetY());
        saveUiCandidate(candidate, false);
    }

    private void synchronizeUiInteger(String key, int value) {
        String text = Integer.toString(value);
        uiIntegerDrafts.put(key, text);
        GuideNativeEditBox field = uiIntegerFields.get(key);
        if (field != null) field.setValue(text);
    }

    private void saveUiCandidate(GuideDisplayConfig candidate, boolean closeOnSuccess) {
        // Candidate-owned saves must never capture or validate stale widgets from the parent Screen.
        if (candidate.equals(service.snapshot().display())) {
            if (closeOnSuccess) closeAfterSave();
            return;
        }
        saveEditor(() -> service.saveDisplay(candidate), closeOnSuccess,
                result -> {
                    uiDraft.published(snapshot.display());
                    uiIntegerDrafts.clear();
                });
    }

    private boolean validateUiDraft() {
        try {
            GuideUiConfig.Hud hud = uiDraft.ui().hud();
            int x = Integer.parseInt(uiIntegerDrafts.getOrDefault("offset_x",
                    Integer.toString(hud.offsetX())));
            int y = Integer.parseInt(uiIntegerDrafts.getOrDefault("offset_y",
                    Integer.toString(hud.offsetY())));
            uiDraft.preview(uiDraft.ui().withHud(hud.withPlacement(hud.anchor(), x, y,
                    hud.width(), hud.height(), hud.scale())));
            return true;
        } catch (IllegalArgumentException invalid) {
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.ui.invalid_range"));
            return false;
        }
    }

    private void renderUi(GuideGraphics graphics) {
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 10;
        int y = area.y() + uiControlsInset() - uiScroll;
        int w = Math.max(120, area.width() - 20);
        graphics.enableScissor(area.x(), area.y() + 30, area.right(), area.bottom());
        if (uiGroup == UiSettingsProjection.Group.HUD) {
            int previewY = area.y() + 32;
            GuideUiConfig.Hud hud = uiDraft.ui().hud();
            int previewHeight = Math.max(0, uiControlsInset() - 36);
            graphics.fill(x, previewY, x + w, previewY + previewHeight, 0xFF52645D);
            for (int stripe = 0; stripe < w; stripe += 20) {
                graphics.fill(x + stripe, previewY, Math.min(x + w, x + stripe + 10), previewY + previewHeight, 0xFF809786);
            }
            graphics.enableScissor(x, Math.max(area.y() + 30, previewY), x + w,
                    Math.min(area.bottom(), previewY + previewHeight));
            graphics.pushPose();
            try {
                graphics.translatePose(x + 4, previewY + 4);
                graphics.scalePose((float) hud.scale(), (float) hud.scale());
                graphics.fill(0, 0, hud.width(), hud.collapsed() ? 24 : hud.height(), hud.backgroundArgb(0x181B22));
                graphics.text(font, MinecraftComponents.translatable("screen.openallay.settings.ui.preview_title"),
                        6, 6, hud.textArgb(0xE8EDF2), false);
                if (!hud.collapsed()) graphics.text(font, MinecraftComponents.translatable("screen.openallay.settings.ui.preview_reply"),
                        6, 20, hud.textArgb(0xE8EDF2), false);
            } finally {
                graphics.popPose();
                graphics.disableScissor();
            }
            for (int index = 0; index < 2; index++) {
                int fieldY = y + 52 + index * 26;
                if (uiWidgetVisible(fieldY, 20)) graphics.text(font, MinecraftComponents.translatable(
                                "screen.openallay.settings.ui." + (index == 0 ? "offset_x" : "offset_y")),
                        x, fieldY + 6, MUTED, false);
            }
        }
        graphics.disableScissor();
        if (uiContentHeight > area.height() - uiControlsInset()) {
            int trackTop = area.y() + uiControlsInset();
            int trackHeight = Math.max(1, area.height() - uiControlsInset() - 2);
            int maximum = Math.max(1, uiContentHeight - (area.height() - uiControlsInset()));
            int thumb = Math.max(6, trackHeight * Math.max(1, area.height() - uiControlsInset()) / uiContentHeight);
            int top = trackTop + (trackHeight - thumb) * uiScroll / maximum;
            graphics.fill(area.right() - 4, trackTop, area.right() - 2, area.bottom() - 2, 0xFF39424D);
            graphics.fill(area.right() - 4, top, area.right() - 2, top + thumb, ACCENT);
        }
    }

    private static final class UiSlider extends GuideNativeSlider {
        private final String key;
        private final double minimum;
        private final double maximum;
        private final boolean integral;
        private final java.util.function.DoubleConsumer changed;

        private UiSlider(int x, int y, int width, String key, double initial,
                double minimum, double maximum, boolean integral, java.util.function.DoubleConsumer changed) {
            super(x, y, width, 20, MinecraftComponents.empty(), (initial - minimum) / (maximum - minimum));
            this.key = key;
            this.minimum = minimum;
            this.maximum = maximum;
            this.integral = integral;
            this.changed = changed;
            updateMessage();
        }

        private double actual() { return minimum + value * (maximum - minimum); }

        @Override
        protected void updateMessage() {
            String amount = key.equals("reply_lines") && Math.round(actual()) == 0
                    ? MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.ui.reply_lines.auto"))
                    : integral ? Long.toString(Math.round(actual()))
                    : String.format(java.util.Locale.ROOT, "%.2f", actual());
            String translationKey = key.startsWith("voice.")
                    ? "screen.openallay.settings." + key
                    : "screen.openallay.settings.ui." + key;
            setMessage(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.translatable(translationKey)), " · " + amount));
        }

        @Override
        protected void applyValue() { changed.accept(actual()); }
    }

    private void resetVoiceDraft() {
        if (voiceActions == null) return;
        voiceView = voiceActions.view();
        voiceDraft = voiceView.config();
        voiceModelPath = voiceDraft.nativeModelDirectory();
        voiceHttpUrl = voiceDraft.httpBaseUrl().toString();
        voiceHttpModel = voiceDraft.httpModel();
        voiceApiKeyDraft = "";
    }

    private void addVoicePage() {
        if (voiceActions == null || voiceDraft == null) return;
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 10;
        int w = Math.max(120, area.width() - 20);
        int y = area.y() + 34 - voiceScroll;
        voiceButton("enabled", MinecraftComponents.translatable("screen.openallay.settings.ui."
                + (voiceDraft.enabled() ? "on" : "off")), x, y, w, () -> {
            voiceDraft = voiceDraft.withEnabled(!voiceDraft.enabled());
            guideRebuildWidgets();
        });
        y += 26;
        voiceButton("gameplay_action", MinecraftComponents.translatable("screen.openallay.settings.voice.gameplay_action."
                + voiceDraft.gameplayAction().name().toLowerCase(java.util.Locale.ROOT)), x, y, w, () -> {
            voiceDraft = voiceDraft.withGameplayAction(voiceDraft.gameplayAction() == VoiceConfig.GameplayAction.SEND
                    ? VoiceConfig.GameplayAction.DRAFT : VoiceConfig.GameplayAction.SEND);
            guideRebuildWidgets();
        });
        y += 26;
        voiceButton("backend", MinecraftComponents.translatable("screen.openallay.settings.voice.backend."
                + voiceDraft.backend().name().toLowerCase(java.util.Locale.ROOT)), x, y, w, () -> {
            voiceDraft = voiceDraft.withBackend(voiceDraft.backend() == VoiceConfig.Backend.NATIVE
                    ? VoiceConfig.Backend.HTTP : VoiceConfig.Backend.NATIVE);
            guideRebuildWidgets();
        });
        y += 26;
        String deviceName = voiceView.devices().stream().filter(device -> device.id().equals(voiceDraft.deviceId()))
                .map(dev.openallay.client.voice.AudioCapture.Device::name).findFirst().orElse(voiceDraft.deviceId());
        voiceButton("device", MinecraftComponents.literal(deviceName), x, y, w, () -> {
            List<dev.openallay.client.voice.AudioCapture.Device> devices = voiceView.devices();
            if (devices.isEmpty()) return;
            int current = -1;
            for (int index = 0; index < devices.size(); index++) {
                if (devices.get(index).id().equals(voiceDraft.deviceId())) current = index;
            }
            voiceDraft = voiceDraft.withDevice(devices.get((current + 1) % devices.size()).id());
            guideRebuildWidgets();
        });
        y += 26;
        voiceButton("refresh_devices", MinecraftComponents.empty(), x, y, w, () -> acceptVoice(voiceActions.refreshDevices(), false));
        y += 26;
        voiceButton("language", MinecraftComponents.literal(voiceDraft.language()), x, y, w, () -> {
            voiceDraft = voiceDraft.withLanguage(switch (voiceDraft.language()) {
                case "auto" -> "zh";
                case "zh" -> "en";
                default -> "auto";
            });
            guideRebuildWidgets();
        });
        y += 26;
        voiceSlider("clip_seconds", voiceDraft.maxClipSeconds(), 1, 60, x, y, w,
                value -> voiceDraft = voiceDraft.withLimits((int) Math.round(value), voiceDraft.cpuThreads()));
        y += 26;
        voiceSlider("cpu_threads", voiceDraft.cpuThreads(), 1, 8, x, y, w,
                value -> voiceDraft = voiceDraft.withLimits(voiceDraft.maxClipSeconds(), (int) Math.round(value)));
        y += 26;
        if (voiceDraft.backend() == VoiceConfig.Backend.NATIVE) {
            voiceText("model_directory", voiceModelPath, x, y, w, value -> voiceModelPath = value, false);
            y += 42;
            voiceButton("choose_model", MinecraftComponents.empty(), x, y, w, () -> chooseVoiceDirectory(false));
            y += 26;
            voiceButton("import_model", MinecraftComponents.empty(), x, y, w, () -> {
                try {
                    if (voiceModelPath.isBlank()) throw new IllegalArgumentException("empty model directory");
                    acceptVoice(voiceActions.importModel(Path.of(voiceModelPath)), true);
                } catch (IllegalArgumentException invalid) {
                    localNotice = MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.voice.invalid"));
                }
            });
            y += 26;
            voiceText("runtime_directory", voiceRuntimePath, x, y, w, value -> voiceRuntimePath = value, false);
            y += 42;
            voiceButton("choose_runtime", MinecraftComponents.empty(), x, y, w, () -> chooseVoiceDirectory(true));
            y += 26;
            voiceButton("import_runtime", MinecraftComponents.empty(), x, y, w, () -> {
                try {
                    if (voiceRuntimePath.isBlank()) throw new IllegalArgumentException("empty runtime directory");
                    acceptVoice(voiceActions.importRuntime(Path.of(voiceRuntimePath)), false);
                } catch (IllegalArgumentException invalid) {
                    localNotice = MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.voice.invalid"));
                }
            });
            y += 26;
            voiceButton("download_model", MinecraftComponents.empty(), x, y, w,
                    () -> acceptVoice(voiceActions.downloadDefaultModel(), true));
            y += 26;
            GuideNativeButton cancel = voiceButton("cancel_download", MinecraftComponents.empty(), x, y, w, voiceActions::cancelDownload);
            cancel.active = voiceView.busy();
            y += 26;
            GuideNativeButton notices = voiceButton("runtime_notices", MinecraftComponents.empty(), x, y, w,
                    () -> GuideNativeDialogs.openDirectory(voiceActions.runtimeNoticesDirectory()));
            dev.openallay.client.gui.GuideNativeWidgetTooltips.set(notices, GuideTooltip.create(MinecraftComponents.translatable(
                    "screen.openallay.settings.voice.runtime_notices.description")));
            y += 26;
        } else {
            voiceText("http_url", voiceHttpUrl, x, y, w, value -> voiceHttpUrl = value, false);
            y += 42;
            voiceText("http_model", voiceHttpModel, x, y, w, value -> voiceHttpModel = value, false);
            y += 42;
            voiceText("api_key", voiceApiKeyDraft, x, y, w, value -> voiceApiKeyDraft = value, true);
            y += 42;
            voiceButton("store_api_key", MinecraftComponents.empty(), x, y, w, this::applyVoice);
            y += 26;
            voiceButton("clear_credential", MinecraftComponents.empty(), x, y, w, () -> {
                voiceDraft = voiceDraft.withCredential(null);
                guideRebuildWidgets();
            });
            y += 26;
        }
        int copyLines = GuideNativeFont.split(font, MinecraftComponents.translatable("screen.openallay.settings.voice.gameplay_action.description"), w).size()
                + GuideNativeFont.split(font, MinecraftComponents.translatable("screen.openallay.settings.voice.not_ready"), w).size()
                + GuideNativeFont.split(font, voiceSettingsStatus(voiceView.statusCode()), w).size()
                + GuideNativeFont.split(font, MinecraftComponents.literal(voiceView.modelName()), w).size() + 4;
        if (voiceDraft.backend() == VoiceConfig.Backend.NATIVE) {
            copyLines += GuideNativeFont.split(font, MinecraftComponents.translatable("screen.openallay.settings.voice.native_source"), w).size();
        }
        voiceContentHeight = y + voiceScroll - area.y() + copyLines * 10 + 18;
    }

    private GuideNativeButton voiceButton(String key, Component value, int x, int y, int w, Runnable action) {
        Component label = MinecraftComponents.translatable("screen.openallay.settings.voice." + key);
        if (!MinecraftComponents.getString(value).isBlank()) label = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(label), " · "), value);
        GuideNativeButton button = addGuideWidget(OpenAllayButton.create(label, ignored -> action.run())
                .bounds(x, y, w, 20).build());
        button.visible = layout.pageWidgetVisible(y, 20);
        button.active = voiceView != null && !voiceView.busy();
        return button;
    }

    private void voiceText(String key, String value, int x, int y, int w,
            java.util.function.Consumer<String> changed, boolean secret) {
        GuideNativeEditBox field = secret ? new PasswordEditBox(font, x, y + 14, w, 20,
                MinecraftComponents.translatable("screen.openallay.settings.voice." + key))
                : new GuideNativeEditBox(font, x, y + 14, w, 20,
                        MinecraftComponents.translatable("screen.openallay.settings.voice." + key));
        field.setMaxLength(4096);
        field.setValue(value);
        field.setResponder(changed);
        field.setVisible(layout.pageWidgetVisible(y, 34));
        field.active = voiceView != null && !voiceView.busy();
        voiceFields.put(key, field);
        addGuideWidget(field);
    }

    private void voiceSlider(String key, double value, double minimum, double maximum,
            int x, int y, int w, java.util.function.DoubleConsumer changed) {
        UiSlider slider = new UiSlider(x, y, w, "voice." + key, value, minimum, maximum, true, changed);
        slider.visible = layout.pageWidgetVisible(y, 20);
        slider.active = voiceView != null && !voiceView.busy();
        addGuideWidget(slider);
    }

    private void chooseVoiceDirectory(boolean runtime) {
        String key = runtime ? "runtime_directory" : "model_directory";
        GuideNativeDialogs.selectDirectory(minecraft,
                MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.voice." + key)),
                runtime ? voiceRuntimePath : voiceModelPath).whenComplete((selected, failure) -> MinecraftClientWindow.execute(minecraft, () -> {
                    if (MinecraftClientWindow.screen(minecraft) != this) return;
                    if (failure != null) {
                        localNotice = MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.voice.chooser_unavailable"));
                    } else if (selected != null && !selected.isBlank()) {
                        if (runtime) voiceRuntimePath = selected;
                        else voiceModelPath = selected;
                        guideRebuildWidgets();
                    }
                }));
    }

    private void applyVoice() { saveVoice(false); }

    private void saveVoice(boolean closeOnSuccess) {
        if (voiceActions == null || voiceDraft == null) {
            if (closeOnSuccess) closeAfterSave();
            return;
        }
        captureDraft();
        try {
            VoiceConfig candidate = voiceDraft.withHttp(java.net.URI.create(voiceHttpUrl), voiceHttpModel);
            String modelDirectory = voiceModelPath.isBlank() ? ""
                    : Path.of(voiceModelPath).toAbsolutePath().normalize().toString();
            candidate = new VoiceConfig(candidate.enabled(), candidate.backend(), candidate.deviceId(),
                    candidate.maxClipSeconds(), candidate.language(), candidate.cpuThreads(), modelDirectory,
                    candidate.httpBaseUrl(), candidate.httpModel(), candidate.credential(), candidate.gameplayAction());
            if (candidate.equals(voiceActions.view().config()) && voiceApiKeyDraft.isBlank()) {
                if (closeOnSuccess) closeAfterSave();
                return;
            }
            VoiceConfig submitted = candidate;
            saveEditor(() -> voiceActions.update(submitted, voiceApiKeyDraft.isBlank()
                            ? null : voiceApiKeyDraft.toCharArray()), closeOnSuccess,
                    result -> resetVoiceDraft());
        } catch (IllegalArgumentException invalid) {
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.voice.invalid"));
        }
    }

    private String voiceFieldValue(String key, String fallback) {
        GuideNativeEditBox field = voiceFields.get(key);
        return field == null ? fallback : field.getValue();
    }

    private void acceptVoice(java.util.concurrent.CompletableFuture<? extends ToolResult<?>> future,
            boolean resetOnSuccess) {
        localNotice = "";
        future.whenComplete((result, failure) -> MinecraftClientWindow.execute(minecraft, () -> {
            if (failure != null || result instanceof ToolResult.Failure<?>) {
                localNotice = result instanceof ToolResult.Failure<?> rejected ? rejected.message()
                        : MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.voice.failed"));
            } else if (resetOnSuccess) {
                // Import/download/credential storage may change config; preserve other unsaved fields.
                VoiceConfig previous = voiceDraft;
                String previousHttpUrl = voiceHttpUrl;
                String previousHttpModel = voiceHttpModel;
                resetVoiceDraft();
                voiceDraft = voiceDraft.withEnabled(previous.enabled()).withBackend(previous.backend())
                        .withDevice(previous.deviceId()).withLanguage(previous.language())
                        .withLimits(previous.maxClipSeconds(), previous.cpuThreads())
                        .withGameplayAction(previous.gameplayAction());
                voiceHttpUrl = previousHttpUrl;
                voiceHttpModel = previousHttpModel;
            }
            if (layout != null && section == SettingsSection.VOICE) guideRebuildWidgets();
        }));
    }

    private Component voiceSettingsStatus(String code) {
        String known = switch (code) {
            case "model_downloading" -> "downloading";
            case "model_download_cancelled" -> "download_cancelled";
            case "model_download_failed" -> "download_failed";
            case "voice_settings_failed", "voice_save_failed", "invalid_voice_config" -> "voice_failed";
            case "runtime_imported", "runtime_invalid", "model_imported", "model_invalid", "voice_saved",
                    "voice_reloaded", "downloading", "download_cancelled", "download_failed", "voice_disabled",
                    "microphone_denied", "microphone_launcher_unprepared", "microphone_permission_unavailable",
                    "microphone_device_unavailable", "microphone_format_unsupported", "microphone_open_failed",
                    "device_broken", "empty_audio", "voice_failed" -> code;
            default -> "status";
        };
        return MinecraftComponents.translatable("screen.openallay.settings.voice.status." + known);
    }

    private void renderVoice(GuideGraphics graphics) {
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 10;
        int y = area.y() + 12 - voiceScroll;
        int w = Math.max(100, area.width() - 20);
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        graphics.text(font, MinecraftComponents.translatable("screen.openallay.settings.voice.title"), x, y, ACCENT, false);
        if (voiceActions == null || voiceView == null) {
            renderWrapped(graphics, MinecraftComponents.translatable("screen.openallay.settings.voice.unavailable"),
                    x, y + 22, w, MUTED, 10);
        } else {
            int fieldsY = area.y() + 34 - voiceScroll + 8 * 26;
            if (voiceDraft.backend() == VoiceConfig.Backend.NATIVE) {
                if (layout.pageWidgetVisible(fieldsY, 34)) graphics.text(font,
                        MinecraftComponents.translatable("screen.openallay.settings.voice.model_directory"),
                        x, fieldsY, MUTED, false);
                int runtimeY = fieldsY + 94;
                if (layout.pageWidgetVisible(runtimeY, 34)) graphics.text(font,
                        MinecraftComponents.translatable("screen.openallay.settings.voice.runtime_directory"),
                        x, runtimeY, MUTED, false);
            } else {
                for (int index = 0; index < 3; index++) {
                    int fieldY = fieldsY + index * 42;
                    if (layout.pageWidgetVisible(fieldY, 34)) graphics.text(font, MinecraftComponents.translatable(
                                    "screen.openallay.settings.voice." + List.of("http_url", "http_model", "api_key").get(index)),
                            x, fieldY, MUTED, false);
                }
            }
            int bodyHeight = voiceDraft.backend() == VoiceConfig.Backend.NATIVE ? 474 : 386;
            int bottom = area.y() + 34 + bodyHeight - voiceScroll;
            bottom = renderWrapped(graphics, MinecraftComponents.translatable("screen.openallay.settings.voice.gameplay_action.description"),
                    x, bottom, w, MUTED, 10);
            String progress = voiceView.totalBytes() > 0
                    ? " · " + voiceView.downloadedBytes() / 1048576 + "/" + voiceView.totalBytes() / 1048576 + " MiB" : "";
            bottom = renderWrapped(graphics, MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.translatable(voiceDraft.backend() == VoiceConfig.Backend.HTTP
                            ? "screen.openallay.settings.voice.http_selected" : voiceView.modelReady()
                            ? "screen.openallay.settings.voice.ready" : "screen.openallay.settings.voice.not_ready")), " · " + voiceView.modelName() + progress),
                    x, bottom + 4, w, voiceView.modelReady() ? ACCENT : MUTED, 10);
            bottom = renderWrapped(graphics, voiceSettingsStatus(voiceView.statusCode()), x, bottom + 3, w, MUTED, 10);
            if (voiceDraft.backend() == VoiceConfig.Backend.NATIVE) {
                renderWrapped(graphics, MinecraftComponents.translatable("screen.openallay.settings.voice.native_source"),
                        x, bottom + 3, w, MUTED, 10);
            }
        }
        graphics.disableScissor();
    }

    private void addModelsPage() {
        if (layout.wide()) {
            addProfileList();
        }
        if (selectedServerModel) {
            return;
        } else if (modelCatalogOpen) {
            addModelCatalogPicker();
        } else {
            addEditor();
        }
    }

    private void addGeneralPage() {
        GeneralSettingsProjection general = project(snapshot).general();
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 10;
        int y = layout.pageOrigin(pageScroll) + 44;
        int width = Math.min(280, Math.max(120, area.width() - 20));
        int saveWidth = Math.min(72, Math.max(50, width / 4));
        assistantName = new GuideNativeEditBox(
                font,
                x,
                y,
                Math.max(60, width - saveWidth - 4),
                20,
                MinecraftComponents.translatable(general.assistantNameLabelKey()));
        assistantName.setMaxLength(Integer.MAX_VALUE);
        assistantName.setValue(assistantNameDraft);
        assistantName.setResponder(value -> assistantNameDraft = value);
        assistantName.setVisible(layout.pageWidgetVisible(y, 20));
        addGuideWidget(assistantName);
        GuideNativeButton saveName = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable(
                                "screen.openallay.settings.general.assistant_name.save"),
                        ignored -> saveAssistantName(general))
                .bounds(x + width - saveWidth, y, saveWidth, 20)
                .build());
        saveName.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        saveName.visible = layout.pageWidgetVisible(y, 20);
        GuideNativeButton debug = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.translatable(
                                general.debugLabelKey())), " · "), MinecraftComponents.translatable(general.debugStatusKey())),
                        ignored -> accept(service.saveDisplay(general.toggleDebug())))
                .bounds(x, y + 34, width, 22)
                .build());
        dev.openallay.client.gui.GuideNativeWidgetTooltips.set(debug, GuideTooltip.create(
                MinecraftComponents.translatable(general.debugDescriptionKey())));
        debug.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        debug.visible = layout.pageWidgetVisible(y + 34, 22);
        GuideNativeButton animations = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.translatable(
                                general.animationsLabelKey())), " · "), MinecraftComponents.translatable(general.animationsStatusKey())),
                        ignored -> accept(service.saveDisplay(general.toggleAnimations())))
                .bounds(x, y + 64, width, 22)
                .build());
        dev.openallay.client.gui.GuideNativeWidgetTooltips.set(animations, GuideTooltip.create(
                MinecraftComponents.translatable(general.animationsDescriptionKey())));
        animations.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        animations.visible = layout.pageWidgetVisible(y + 64, 22);
    }

    private void addAboutPage() {
        SettingsLayout.Rect area = layout.editor();
        int width = Math.min(240, Math.max(120, area.width() - 20));
        int y = layout.pageOrigin(pageScroll) + aboutCopyOffset();
        GuideNativeButton copy = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable("screen.openallay.settings.about.copy_repository"),
                        ignored -> copyRepositoryUrl())
                .bounds(area.x() + 10, y, width, 20)
                .build());
        copy.visible = layout.pageWidgetVisible(y, 20);
    }

    private int aboutRepositoryOffset() {
        int contentWidth = Math.max(100, layout.editor().width() - 20);
        int bannerWidth = Math.min(contentWidth, 512);
        int bannerHeight = Math.max(54, bannerWidth * 9 / 16);
        int descriptionHeight = GuideNativeFont.split(font,
                MinecraftComponents.translatable("screen.openallay.settings.about.description"), contentWidth).size() * 10;
        return 31 + bannerHeight + 12 + descriptionHeight + 8;
    }

    private int aboutCopyOffset() {
        int contentWidth = Math.max(100, layout.editor().width() - 20);
        return aboutRepositoryOffset() + 12 + GuideNativeFont.split(font,
                MinecraftComponents.literal(REPOSITORY_URL), contentWidth).size() * 10 + 6;
    }

    private void saveAssistantName(GeneralSettingsProjection general) { saveGeneral(false); }

    private void saveGeneral(boolean closeOnSuccess) {
        captureDraft();
        try {
            GuideDisplayConfig candidate = GeneralSettingsProjection.from(snapshot.display())
                    .renameAssistant(assistantNameDraft);
            if (candidate.equals(snapshot.display())) {
                if (closeOnSuccess) closeAfterSave();
                return;
            }
            saveEditor(() -> service.saveDisplay(candidate), closeOnSuccess,
                    result -> assistantNameDraft = candidate.assistantName());
        } catch (IllegalArgumentException failure) {
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                    "screen.openallay.settings.general.assistant_name.invalid"));
        }
    }

    private void copyRepositoryUrl() {
        try {
            GuideNativeInput.setClipboard(REPOSITORY_URL);
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                    "screen.openallay.settings.about.copy_success"));
        } catch (RuntimeException failure) {
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                    "screen.openallay.settings.about.copy_failed"));
        }
    }

    private void addHistoryPage() {
        HistorySettingsProjection history = project(snapshot).history();
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 10;
        int y = area.y() + 64 - pageScroll;
        int width = Math.min(360, Math.max(140, area.width() - 20));
        for (HistorySettingsProjection.ActionRow row : history.actions()) {
            GuideNativeButton button = OpenAllayButton.create(
                            historyActionLabel(row),
                            ignored -> activateHistory(row.action()))
                    .bounds(x, y, width, 22)
                    .build();
            dev.openallay.client.gui.GuideNativeWidgetTooltips.set(button, GuideTooltip.create(MinecraftComponents.translatable(row.descriptionKey())));
            button.active = row.enabled();
            if (y >= area.y() + 48 && y + 22 <= area.bottom() - 4) {
                addGuideWidget(button);
            }
            y += 30;
        }
        pageContentHeight = Math.max(0, y + pageScroll - area.y());
    }

    private void addProfileList() {
        int x = layout.list().x() + 6;
        int y = layout.list().y() + 26;
        int buttonWidth = layout.list().width() - 12;
        for (ModelSettingsProjection.ModelCard card : project(snapshot).models()) {
            Component label = MinecraftComponents.literal(
                    (card.defaultProfile() ? "★ " : "")
                            + (card.origin() == ModelSettingsProjection.Origin.SERVER
                                    ? "☁ "
                                    : "")
                            + card.displayName());
            GuideNativeButton button = addGuideWidget(OpenAllayButton.create(
                            label,
                            ignored -> selectAndRebuild(card.selectionId()))
                    .selected(card.selectionId().equals(selectedModelSelectionId()))
                    .bounds(x, y, buttonWidth, 22)
                    .build());
            dev.openallay.client.gui.GuideNativeWidgetTooltips.set(button, GuideTooltip.create(MinecraftComponents.translatable(
                    "screen.openallay.settings.models.builtin.image_input."
                            + card.imageCapability().capability().encoded())));
            button.active = !card.selectionId().equals(selectedModelSelectionId());
            y += 26;
            if (y > layout.list().bottom() - 50) {
                break;
            }
        }
        addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable("screen.openallay.settings.models.add"),
                        ignored -> createProfile())
                .bounds(x, layout.list().bottom() - 28, buttonWidth, 20)
                .build());
    }

    private void addExtensionsPage() {
        ExtensionSettingsProjection projection = extensionProjection();
        SettingsLayout.Rect listArea = layout.wide() ? layout.list() : layout.content();
        boolean showList = layout.wide() || !narrowExtensionDetail;
        int x = listArea.x() + 7;
        int y = listArea.y() + 7;
        int width = Math.max(80, listArea.width() - 14);
        if (showList) {
            int tabWidth = Math.max(40, (width - 4) / 2);
            GuideNativeButton installedTab = addGuideWidget(OpenAllayButton.create(
                            MinecraftComponents.translatable(
                                    "screen.openallay.settings.extensions.tab.installed"),
                            ignored -> selectExtensionTab(ExtensionTab.INSTALLED))
                    .selected(extensionTab == ExtensionTab.INSTALLED)
                    .bounds(x, y, tabWidth, 20)
                    .build());
            installedTab.active = extensionTab != ExtensionTab.INSTALLED;
            GuideNativeButton communityTab = addGuideWidget(OpenAllayButton.create(
                            MinecraftComponents.translatable(
                                    "screen.openallay.settings.extensions.tab.community"),
                            ignored -> selectExtensionTab(ExtensionTab.COMMUNITY))
                    .selected(extensionTab == ExtensionTab.COMMUNITY)
                    .bounds(x + tabWidth + 4, y, Math.max(40, width - tabWidth - 4), 20)
                    .build());
            communityTab.active = extensionTab != ExtensionTab.COMMUNITY;
            y += 28 - pageScroll;
        }

        List<ExtensionSettingsProjection.ExtensionCard> cards =
                extensionTab == ExtensionTab.INSTALLED
                        ? projection.installed()
                        : projection.community();
        if (showList) {
            for (ExtensionSettingsProjection.ExtensionCard extension : cards) {
                Component label = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.literal(extension.name())), " · "), MinecraftComponents.translatable(extensionStateKey(extension)));
                GuideNativeButton button = addGuideWidget(OpenAllayButton.create(label, ignored -> {
                            selectedExtensionId = extension.id();
                            narrowExtensionDetail = true;
                            localNotice = "";
                            guideRebuildWidgets();
                        })
                        .selected(extension.id().equals(selectedExtensionId))
                        .bounds(x, y, width, 22)
                        .build());
                button.active = !layout.wide() || !extension.id().equals(selectedExtensionId);
                int bottomInset = !layout.wide() && extensionTab == ExtensionTab.COMMUNITY
                        ? 80
                        : 4;
                button.visible = y >= listArea.y() + 31
                        && y + 22 <= listArea.bottom() - bottomInset;
                y += 26;
            }
            pageContentHeight = 28 + cards.size() * 26;
        }

        if (!layout.wide()
                && showList
                && extensionTab == ExtensionTab.COMMUNITY) {
            addExtensionCommunityActions(
                    projection,
                    listArea.x() + 7,
                    listArea.bottom() - 74,
                    Math.max(80, listArea.width() - 14),
                    false);
        }
        if (layout.wide() || narrowExtensionDetail) {
            SettingsLayout.Rect detail = layout.editor();
            int actionX = detail.x() + 9;
            int actionWidth = Math.max(80, detail.width() - 18);
            selectedExtension().ifPresent(extension -> pageContentHeight = Math.max(
                    pageContentHeight,
                    extensionDetailHeight(
                            extension,
                            Math.max(80, detail.width() - 20),
                            projection.debugMode())));
            int actionY = detail.bottom() - 106;
            if (extensionTab == ExtensionTab.COMMUNITY
                    || selectedExtension().map(
                                    ExtensionSettingsProjection.ExtensionCard::installable)
                            .orElse(false)) {
                addExtensionCommunityActions(
                        projection, actionX, actionY, actionWidth, true);
            }
            addExperimentalCommandAction(
                    projection,
                    actionX,
                    detail.bottom() - 54,
                    actionWidth);
        }
    }

    private int extensionDetailHeight(
            ExtensionSettingsProjection.ExtensionCard extension,
            int width,
            boolean debugMode) {
        int height = 48
                + wrappedHeight(MinecraftComponents.literal(extension.name()), width, 11)
                + wrappedHeight(MinecraftComponents.literal(extension.summary()), width, 10)
                + 7 * 12;
        var contributions = extension.contributions();
        for (List<String> values : List.of(
                contributions.roots(),
                contributions.dataModules(),
                contributions.javascriptModules(),
                contributions.skills(),
                contributions.resultViews(),
                contributions.hostBindings())) {
            if (!values.isEmpty()) {
                height += wrappedHeight(
                        MinecraftComponents.literal(String.join(", ", values)), width, 10) + 2;
            }
        }
        if (!extension.diagnostic().isBlank()) {
            height += wrappedHeight(MinecraftComponents.literal(extension.diagnostic()), width, 10) + 7;
        }
        if (debugMode && !extension.artifact().isBlank()) {
            height += wrappedHeight(MinecraftComponents.literal(extension.artifact()), width, 10) + 12;
        }
        if (debugMode && !extension.sha256().isBlank()) {
            height += wrappedHeight(MinecraftComponents.literal(extension.sha256()), width, 10) + 12;
        }
        return height + 82;
    }

    private void addExtensionCommunityActions(
            ExtensionSettingsProjection projection,
            int x,
            int y,
            int width,
            boolean includeInstall) {
        boolean idle = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        ExtensionSettingsProjection.ExtensionCard selected =
                selectedExtension().orElse(null);
        int half = Math.max(44, (width - 4) / 2);
        if (includeInstall && selected != null && selected.installable()) {
            GuideNativeButton install = addGuideWidget(OpenAllayButton.create(
                            MinecraftComponents.translatable(selected.updateAvailable()
                                    ? "screen.openallay.settings.extensions.community.update"
                                    : "screen.openallay.settings.extensions.community.install"),
                            ignored -> accept(service.installCommunityExtension(selected.id())))
                    .bounds(x, y, half, 20)
                    .build());
            install.active = idle;
        }
        GuideNativeButton refresh = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable(
                                "screen.openallay.settings.extensions.community.refresh"),
                        ignored -> accept(service.refreshExtensionCommunity()))
                .bounds(
                        includeInstall && selected != null && selected.installable()
                                ? x + half + 4
                                : x,
                        y,
                        includeInstall && selected != null && selected.installable()
                                ? Math.max(44, width - half - 4)
                                : width,
                        20)
                .build());
        refresh.active = idle && projection.catalog().configured();

        extensionImportPath = new GuideNativeEditBox(
                font,
                x,
                y + 26,
                Math.max(50, width - 72),
                20,
                MinecraftComponents.translatable(
                        "screen.openallay.settings.extensions.community.import_path"));
        dev.openallay.client.gui.GuideNativeTextHints.setHint(extensionImportPath, MinecraftComponents.translatable(
                "screen.openallay.settings.extensions.community.import_hint"));
        extensionImportPath.setMaxLength(2048);
        extensionImportPath.setValue(extensionImportPathDraft);
        extensionImportPath.active = idle;
        addGuideWidget(extensionImportPath);
        GuideNativeButton importButton = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable(
                                "screen.openallay.settings.extensions.community.import"),
                        ignored -> importLocalExtension())
                .bounds(x + width - 68, y + 26, 68, 20)
                .build());
        importButton.active = idle;
    }

    private void addExperimentalCommandAction(
            ExtensionSettingsProjection projection,
            int x,
            int y,
            int width) {
        if (!projection.unrestrictedJavascript()) {
            GuideNativeButton commands = OpenAllayButton.create(
                            MinecraftComponents.translatable(
                                    projection.experimentalCommands()
                                            ? "screen.openallay.settings.extensions.commands.disable"
                                            : "screen.openallay.settings.extensions.commands.enable"),
                            ignored -> accept(service.saveExperimentalCommands(
                                    !projection.experimentalCommands())))
                    .bounds(x, y, width, 20)
                    .build();
            commands.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
            dev.openallay.client.gui.GuideNativeWidgetTooltips.set(commands, GuideTooltip.create(MinecraftComponents.translatable(
                    "screen.openallay.settings.extensions.commands.description")));
            addGuideWidget(commands);
        }
        GuideNativeButton unrestricted = OpenAllayButton.create(
                        MinecraftComponents.translatable(projection.unrestrictedJavascript()
                                ? "screen.openallay.settings.extensions.unrestricted.disable"
                                : "screen.openallay.settings.extensions.unrestricted.enable"),
                        ignored -> {
                            if (projection.unrestrictedJavascript()) {
                                accept(service.saveUnrestrictedJavascript(false));
                            } else {
                                confirmUnrestrictedJavascript();
                            }
                        })
                .bounds(x, y + 25, width, 20).build();
        unrestricted.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        dev.openallay.client.gui.GuideNativeWidgetTooltips.set(unrestricted, GuideTooltip.create(MinecraftComponents.translatable(
                "screen.openallay.settings.extensions.unrestricted.warning")));
        addGuideWidget(unrestricted);
    }

    private void confirmUnrestrictedJavascript() {
        dev.openallay.client.gui.MinecraftClientWindow.showScreen(minecraft, GuideNativeDialogs.confirm(
                confirmed -> {
                    dev.openallay.client.gui.MinecraftClientWindow.showScreen(minecraft, this);
                    if (confirmed) accept(service.saveUnrestrictedJavascript(true));
                },
                MinecraftComponents.translatable(RequirementSettingsProjection.PREFIX + "confirm_enable"),
                MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.translatable("screen.openallay.settings.extensions.unrestricted.warning"), "\n\n"), MinecraftComponents.translatable(
                                RequirementSettingsProjection.PREFIX + "unrestricted_confirm")),
                MinecraftComponents.translatable(RequirementSettingsProjection.PREFIX + "confirm_enable"),
                MinecraftComponents.translatable(RequirementSettingsProjection.PREFIX + "cancel")));
    }

    private void addSkillsPage() {
        SkillSettingsProjection projection = skillProjection();
        SettingsLayout.Rect listArea = layout.wide() ? layout.list() : layout.content();
        int x = listArea.x() + 7;
        int y = listArea.y() + 7;
        int width = listArea.width() - 14;
        boolean showList = layout.wide() || !narrowSkillDetail;
        if (showList) {
            int tabWidth = Math.max(40, (width - 4) / 2);
            GuideNativeButton installedTab = addGuideWidget(OpenAllayButton.create(
                            MinecraftComponents.translatable(
                                    "screen.openallay.settings.skills.tab.installed"),
                            ignored -> selectSkillTab(SkillTab.INSTALLED))
                    .selected(skillTab == SkillTab.INSTALLED)
                    .bounds(x, y, tabWidth, 20)
                    .build());
            installedTab.active = skillTab != SkillTab.INSTALLED;
            GuideNativeButton communityTab = addGuideWidget(OpenAllayButton.create(
                            MinecraftComponents.translatable(
                                    "screen.openallay.settings.skills.tab.community"),
                            ignored -> selectSkillTab(SkillTab.COMMUNITY))
                    .selected(skillTab == SkillTab.COMMUNITY)
                    .bounds(x + tabWidth + 4, y, Math.max(40, width - tabWidth - 4), 20)
                    .build());
            communityTab.active = skillTab != SkillTab.COMMUNITY;
            y += 28 - pageScroll;
        }

        if (showList && skillTab == SkillTab.INSTALLED) {
            for (SkillSettingsProjection.Skill skill : projection.skills()) {
                Component label = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.literal(skill.name())), " · "), MinecraftComponents.translatable(skill.localOverride()
                                ? "screen.openallay.settings.skills.local"
                                : "screen.openallay.settings.skills.bundled"));
                GuideNativeButton button = addGuideWidget(OpenAllayButton.create(label, ignored -> {
                            selectedSkillName = skill.name();
                            skillDetailScroll = 0;
                            narrowSkillDetail = true;
                            skillEditing = false;
                            skillDraftMarkdown = "";
                            guideRebuildWidgets();
                        })
                        .selected(skill.name().equals(selectedSkillName))
                        .bounds(x, y, width, 22)
                        .build());
                button.active = !layout.wide() || !skill.name().equals(selectedSkillName);
                button.visible = y >= listArea.y() + 31 && y + 22 <= listArea.bottom() - 4;
                y += 26;
            }
            pageContentHeight = 28 + projection.skills().size() * 26;
        }
        if (showList && skillTab == SkillTab.COMMUNITY) {
            for (SkillSettingsProjection.Package skill : projection.community().packages()) {
                Component label = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.literal(skill.displayName())), " · "), MinecraftComponents.translatable(skillStateKey(skill.state())));
                GuideNativeButton button = addGuideWidget(OpenAllayButton.create(label, ignored -> {
                            selectedCommunitySkillId = skill.id();
                            skillDetailScroll = 0;
                            narrowSkillDetail = true;
                            guideRebuildWidgets();
                        })
                        .selected(skill.id().equals(selectedCommunitySkillId))
                        .bounds(x, y, width, 22)
                        .build());
                button.active = !layout.wide() || !skill.id().equals(selectedCommunitySkillId);
                int listBottomInset = layout.wide() ? 4 : 60;
                button.visible = y >= listArea.y() + 31
                        && y + 22 <= listArea.bottom() - listBottomInset;
                y += 26;
            }
            pageContentHeight = 28
                    + projection.community().packages().size() * 26
                    + (layout.wide() ? 0 : 56);
        }

        if ((layout.wide() || narrowSkillDetail) && skillTab == SkillTab.INSTALLED) {
            selectedSkill().ifPresent(skill -> {
            SettingsLayout.Rect area = layout.editor();
            int editorX = area.x() + 9;
            int editorY = area.y() + 58;
            int editorWidth = area.width() - 18;
            if (skillEditing) {
                skillEditor = GuideNativeMultilineText.create(font, editorX, editorY, editorWidth,
                        Math.max(70, area.bottom() - editorY - 34),
                        MinecraftComponents.translatable("screen.openallay.settings.skills.editor_placeholder"),
                        MinecraftComponents.translatable("screen.openallay.settings.skills.editor"));
                dev.openallay.client.gui.GuideNativeMultilineText.setValue(skillEditor, skillDraftMarkdown, true);
                skillEditor.setValueListener(value -> skillDraftMarkdown = value);
                addGuideWidgetHandle(skillEditor.widget());
                addGuideWidget(OpenAllayButton.create(
                                MinecraftComponents.translatable("screen.openallay.settings.save"),
                                ignored -> saveSkillOverride())
                        .bounds(editorX, area.bottom() - 26, Math.min(120, editorWidth), 20)
                        .build());
                addGuideWidget(OpenAllayButton.create(
                                MinecraftComponents.translatable("screen.openallay.settings.cancel"),
                                ignored -> {
                                    skillEditing = false;
                                    skillDraftMarkdown = "";
                                    guideRebuildWidgets();
                                })
                        .bounds(
                                editorX + Math.min(120, editorWidth) + 4,
                                area.bottom() - 26,
                                Math.min(100, Math.max(50, editorWidth - 124)),
                                20)
                        .build());
            } else {
                addGuideWidget(OpenAllayButton.create(
                                MinecraftComponents.translatable(skill.createsOverrideOnSave()
                                        ? "screen.openallay.settings.skills.create_override"
                                        : "screen.openallay.settings.skills.edit_override"),
                                ignored -> {
                                    skillEditing = true;
                                    skillDraftMarkdown = skill.markdown();
                                    guideRebuildWidgets();
                                })
                        .bounds(editorX, area.bottom() - 26, Math.min(160, editorWidth), 20)
                        .build());
                if (skill.canDeleteOverride()) {
                    addGuideWidget(OpenAllayButton.create(
                                    MinecraftComponents.translatable(
                                            "screen.openallay.settings.skills.delete_override"),
                                    ignored -> accept(service.deleteSkillOverride(skill.name())))
                            .bounds(
                                    editorX + Math.min(160, editorWidth) + 4,
                                    area.bottom() - 26,
                                    Math.min(140, Math.max(60, editorWidth - 164)),
                                    20)
                            .build());
                }
            }
            });
        }
        if ((layout.wide() || narrowSkillDetail) && skillTab == SkillTab.COMMUNITY) {
            addCommunitySkillActions(true);
        } else if (!layout.wide() && skillTab == SkillTab.COMMUNITY) {
            addCommunitySkillActions(false);
        }
    }

    private void addCommunitySkillActions(boolean includeInstall) {
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 9;
        int width = area.width() - 18;
        int actionY = area.bottom() - 54;
        SkillSettingsProjection.Package selected = selectedCommunitySkill().orElse(null);
        int refreshX = x;
        int refreshWidth = width;
        if (includeInstall && selected != null && selected.installable()) {
            int installWidth = Math.max(60, (width - 4) / 2);
            GuideNativeButton install = addGuideWidget(OpenAllayButton.create(
                            MinecraftComponents.translatable(
                                    selected.state()
                                                    == SkillSettingsProjection.PackageState.UPDATE_AVAILABLE
                                            ? "screen.openallay.settings.skills.community.update"
                                            : "screen.openallay.settings.skills.community.install"),
                            ignored -> accept(service.installCommunitySkill(selected.id())))
                    .bounds(x, actionY, installWidth, 20)
                    .build());
            install.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
            refreshX = x + installWidth + 4;
            refreshWidth = Math.max(60, width - installWidth - 4);
        }
        GuideNativeButton refresh = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable(
                                "screen.openallay.settings.skills.community.refresh"),
                        ignored -> accept(service.refreshSkillCommunity()))
                .bounds(refreshX, actionY, refreshWidth, 20)
                .build());
        refresh.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;

        int importWidth = Math.min(84, Math.max(56, width / 4));
        skillImportPath = new GuideNativeEditBox(
                font,
                x,
                area.bottom() - 27,
                Math.max(50, width - importWidth - 4),
                20,
                MinecraftComponents.translatable(
                        "screen.openallay.settings.skills.community.import_path"));
        skillImportPath.setMaxLength(Integer.MAX_VALUE);
        skillImportPath.setValue(skillImportPathDraft);
        skillImportPath.setResponder(value -> skillImportPathDraft = value);
        dev.openallay.client.gui.GuideNativeTextHints.setHint(skillImportPath, MinecraftComponents.translatable(
                "screen.openallay.settings.skills.community.import_hint"));
        addGuideWidget(skillImportPath);
        GuideNativeButton importButton = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable(
                                "screen.openallay.settings.skills.community.import"),
                        ignored -> importLocalSkill())
                .bounds(x + width - importWidth, area.bottom() - 27, importWidth, 20)
                .build());
        importButton.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE
                && !skillImportPathDraft.isBlank();
    }

    private void addEditor() {
        refreshAutomaticContext();
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 8;
        int y = area.y() + 8;
        int editorWidth = Math.max(100, area.width() - 16);
        int toggleWidth = Math.min(118, Math.max(70, (editorWidth - 8) / 2));
        addGuideWidget(OpenAllayButton.create(protocolLabel(), ignored -> cycleProtocol())
                .bounds(x, y, toggleWidth, 20).build());
        addGuideWidget(OpenAllayButton.create(enabledLabel(), ignored -> toggleEnabled())
                .bounds(x + toggleWidth + 6, y, toggleWidth, 20).build());
        y += 32 - editorScroll;
        int labelWidth = Math.min(104, Math.max(72, editorWidth / 3));
        int inputX = x + labelWidth;
        int inputWidth = Math.max(70, editorWidth - labelWidth);
        id = field(inputX, y, inputWidth, "screen.openallay.settings.models.id", draft.id());
        y += 22;
        displayName = field(
                inputX, y, inputWidth, "screen.openallay.settings.models.name", draft.displayName());
        y += 22;
        baseUrl = field(
                inputX, y, inputWidth, "screen.openallay.settings.models.base_url", draft.baseUrl());
        baseUrl.setResponder(value -> {
            confirmation = Confirmation.NONE;
            invalidateModelCatalog();
            captureDraft();
            refreshAutomaticContext();
            updateAutomaticContextWidget();
            updateAutomaticOutputWidget();
        });
        y += 22;
        int fetchWidth = inputWidth >= 130 ? 46 : 30;
        int chooseWidth = inputWidth >= 130 ? 32 : 20;
        int modelWidth = Math.max(20, inputWidth - fetchWidth - chooseWidth - 6);
        model = field(
                inputX, y, modelWidth, "screen.openallay.settings.models.model_id", draft.model());
        model.setResponder(value -> {
            confirmation = Confirmation.NONE;
            draft = draft.withModel(value);
            refreshAutomaticContext();
            updateAutomaticContextWidget();
            updateAutomaticOutputWidget();
        });
        GuideNativeButton fetch = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable("screen.openallay.settings.models.fetch"),
                        ignored -> fetchModelCatalog())
                .bounds(inputX + modelWidth + 3, y, fetchWidth, 18)
                .build());
        fetch.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        fetch.visible = model.visible;
        GuideNativeButton choose = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable("screen.openallay.settings.models.choose"),
                        ignored -> {
                            captureDraft();
                            modelCatalogOpen = true;
                            modelCatalogPage = 0;
                            guideRebuildWidgets();
                        })
                .bounds(inputX + modelWidth + fetchWidth + 6, y, chooseWidth, 18)
                .build());
        choose.active = !catalogModelIds.isEmpty();
        choose.visible = model.visible;
        y += 22;
        apiKey = passwordField(inputX, y, inputWidth);
        y += 22;
        contextWindow = field(
                inputX,
                y,
                inputWidth,
                "screen.openallay.settings.models.context_window",
                draft.contextWindowTokens());
        dev.openallay.client.gui.GuideNativeWidgetTooltips.set(contextWindow, GuideTooltip.create(MinecraftComponents.translatable(
                "screen.openallay.settings.models.context_window.description")));
        contextWindow.setResponder(value -> {
            confirmation = Confirmation.NONE;
            if (!updatingAutomaticContext) {
                draft = draft.withContextWindow(value);
                refreshAutomaticContext();
                updateAutomaticContextWidget();
                updateAutomaticOutputWidget();
            }
        });
        y += 22;
        maxOutput = field(
                inputX, y, inputWidth, "screen.openallay.settings.models.max_output", draft.maxOutputTokens());
        dev.openallay.client.gui.GuideNativeWidgetTooltips.set(maxOutput, GuideTooltip.create(MinecraftComponents.translatable(
                "screen.openallay.settings.models.max_output.description")));
        maxOutput.setResponder(value -> {
            if (draft != null && !updatingAutomaticOutput) {
                draft = draft.withMaxOutput(value);
                refreshAutomaticContext();
                updateAutomaticOutputWidget();
            }
        });
        y += 22;
        ModelReasoningSettingsProjection reasoning = reasoningSettings();
        GuideNativeButton effort = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable(reasoning.selectedLabelKey()), ignored -> {
                            captureDraft();
                            draft = draft.withReasoningEffort(reasoningSettings().next());
                            confirmation = Confirmation.NONE;
                            guideRebuildWidgets();
                        })
                .bounds(inputX, y, inputWidth, 18).build());
        dev.openallay.client.gui.GuideNativeWidgetTooltips.set(effort, GuideTooltip.create(reasoningExplanation(reasoning)));
        effort.visible = y >= area.y() + 30 && y + 18 <= area.bottom();
        y += 22;
        ModelImageSettingsProjection imageInput = new ModelImageSettingsProjection(
                draft.imageInputCapabilityOverride());
        GuideNativeButton imageChoice = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable(imageInput.selectedLabelKey()), ignored -> {
                            captureDraft();
                            draft = draft.withImageInputCapabilityOverride(
                                    new ModelImageSettingsProjection(draft.imageInputCapabilityOverride()).next());
                            confirmation = Confirmation.NONE;
                            guideRebuildWidgets();
                        })
                .bounds(inputX, y, inputWidth, 18).build());
        dev.openallay.client.gui.GuideNativeWidgetTooltips.set(imageChoice, GuideTooltip.create(MinecraftComponents.translatable(imageInput.explanationKey())));
        imageChoice.visible = y >= area.y() + 30 && y + 18 <= area.bottom();
        y += 22;
        connectTimeout = field(
                inputX,
                y,
                inputWidth,
                "screen.openallay.settings.models.connect_timeout",
                draft.connectTimeoutSeconds());
        y += 22;
        requestTimeout = field(
                inputX,
                y,
                inputWidth,
                "screen.openallay.settings.models.request_timeout",
                draft.requestTimeoutSeconds());
    }

    private GuideNativeEditBox field(int x, int y, int width, String narrationKey, String value) {
        GuideNativeEditBox field = new GuideNativeEditBox(
                font, x, y, width, 18, MinecraftComponents.translatable(narrationKey));
        field.setMaxLength(2048);
        field.setValue(value == null ? "" : value);
        field.setResponder(ignored -> confirmation = Confirmation.NONE);
        field.setVisible(y >= layout.editor().y() + 30
                && y + 18 <= layout.editor().bottom());
        return addGuideWidget(field);
    }

    private PasswordEditBox passwordField(int x, int y, int width) {
        PasswordEditBox field = new PasswordEditBox(
                font,
                x,
                y,
                width,
                18,
                MinecraftComponents.translatable("screen.openallay.settings.models.api_key"));
        field.setMaxLength(4096);
        field.setValue(pendingApiKey);
        boolean saved = selectedView().map(
                        ModelProfileSettingsView.Profile::credentialStoredLocally)
                .orElse(false);
        boolean environment = selectedView().map(
                        ModelProfileSettingsView.Profile::credentialFromEnvironment)
                .orElse(false);
        dev.openallay.client.gui.GuideNativeTextHints.setHint(field, MinecraftComponents.translatable(saved
                ? "screen.openallay.settings.models.api_key_saved_hint"
                : environment
                        ? "screen.openallay.settings.models.api_key_environment_hint"
                        : "screen.openallay.settings.models.api_key_enter_hint"));
        field.setResponder(value -> {
            pendingApiKey = value;
            confirmation = Confirmation.NONE;
            invalidateModelCatalog();
        });
        field.setVisible(y >= layout.editor().y() + 30
                && y + 18 <= layout.editor().bottom());
        return addGuideWidget(field);
    }

    private void addFooterActions() {
        List<Action> actions = footerActions();
        int gap = 4;
        int columns = Math.min(4, actions.size());
        int available = layout.footer().width() - 12;
        int buttonWidth = Math.max(34, (available - gap * (columns - 1)) / columns);
        for (int index = 0; index < actions.size(); index++) {
            Action action = actions.get(index);
            int column = index % columns;
            int row = index / columns;
            int x = layout.footer().x() + 6 + column * (buttonWidth + gap);
            int y = layout.footer().y() + 4 + row * 23;
            GuideNativeButton button = addGuideWidget(OpenAllayButton.create(
                            MinecraftComponents.translatable(action.translationKey()),
                            ignored -> action.action().run())
                    .bounds(x, y, buttonWidth, 20)
                    .build());
            button.active = actionEnabled(action.translationKey());
        }
    }

    private List<Action> footerActions() {
        return switch (section) {
            case MODELS -> selectedServerModel
                    ? List.of(new Action("screen.openallay.settings.done", this::done))
                    : modelCatalogOpen ? List.of(
                    new Action("screen.openallay.settings.models.catalog_close", () -> {
                        modelCatalogOpen = false;
                        guideRebuildWidgets();
                    }),
                    new Action("screen.openallay.settings.done", this::done)) : List.of(
                    new Action("screen.openallay.settings.save", this::saveCurrent),
                    new Action(reloadKey(), this::reloadCurrent),
                    new Action(deleteKey(), this::delete),
                    new Action("screen.openallay.settings.models.default", this::makeDefault),
                    new Action(testKey(), this::testConnection),
                    new Action("screen.openallay.settings.cancel", this::cancel),
                    new Action("screen.openallay.settings.models.refresh", this::refreshMetadata),
                    new Action("screen.openallay.settings.done", this::done));
            case EXTENSIONS -> List.of(
                    new Action("screen.openallay.settings.done", this::done));
            case SKILLS -> List.of(
                    new Action(
                            "screen.openallay.settings.reload",
                            () -> accept(service.reloadSkills(true))),
                    new Action("screen.openallay.settings.done", this::done));
            case UI -> List.of(
                    new Action("screen.openallay.settings.ui.apply", this::applyUi),
                    new Action("screen.openallay.settings.ui.reset", this::resetUiGroup),
                    new Action("screen.openallay.settings.done", this::done));
            case VOICE -> voiceActions == null
                    ? List.of(new Action("screen.openallay.settings.done", this::done))
                    : List.of(new Action("screen.openallay.settings.voice.apply", this::applyVoice),
                            new Action("screen.openallay.settings.voice.reset", this::resetVoiceDefaults),
                            new Action("screen.openallay.settings.done", this::done));
            case GENERAL -> List.of(
                    new Action(
                            "screen.openallay.settings.reload",
                            () -> accept(service.reloadDisplay())),
                    new Action("screen.openallay.settings.done", this::done));
            case HISTORY, DIAGNOSTICS, ABOUT ->
                    List.of(new Action("screen.openallay.settings.done", this::done));
        };
    }

    private boolean actionEnabled(String key) {
        if (editorSave.busy()) return false;
        boolean busy = snapshot.operation().kind() != SettingsOperation.Kind.IDLE;
        if (key.startsWith("screen.openallay.settings.voice.")) return voiceView != null && !voiceView.busy();
        if (key.equals("screen.openallay.settings.ui.apply")) {
            return !busy && (uiDraft.dirty() || !uiIntegerDrafts.isEmpty());
        }
        if (key.equals("screen.openallay.settings.cancel")) {
            return snapshot.operation().kind() == SettingsOperation.Kind.TESTING_CONNECTION
                    || snapshot.operation().kind()
                            == SettingsOperation.Kind.FETCHING_MODEL_CATALOG;
        }
        if (key.equals("screen.openallay.settings.done")) {
            return !busy && (section != SettingsSection.VOICE
                    || voiceView == null || !voiceView.busy());
        }
        return !busy;
    }

    private void renderModels(GuideGraphics graphics) {
        if (layout.wide()) {
            graphics.text(
                    font,
                    MinecraftComponents.translatable("screen.openallay.settings.models.profiles"),
                    layout.list().x() + 8,
                    layout.list().y() + 9,
                    ACCENT,
                    false);
        }
        SettingsLayout.Rect area = layout.editor();
        if (selectedServerModel) {
            renderServerModel(graphics, area);
            return;
        }
        if (modelCatalogOpen) {
            graphics.text(
                    font,
                    MinecraftComponents.translatable(
                            "screen.openallay.settings.models.catalog_title",
                            catalogModelIds.size()),
                    area.x() + 8,
                    area.y() + 10,
                    ACCENT,
                    false);
            if (catalogModelIds.isEmpty()) {
                graphics.text(font,
                        MinecraftComponents.translatable("screen.openallay.settings.models.catalog_empty"),
                        area.x() + 8, area.y() + 34, MUTED, false);
            }
            return;
        }
        int x = area.x() + 8;
        int y = area.y() + 43 - editorScroll;
        String[] labels = {
            "screen.openallay.settings.models.id",
            "screen.openallay.settings.models.name",
            "screen.openallay.settings.models.base_url",
            "screen.openallay.settings.models.model_id",
            "screen.openallay.settings.models.api_key",
            "screen.openallay.settings.models.context_window",
            "screen.openallay.settings.models.max_output",
            "screen.openallay.settings.models.reasoning_effort",
            "screen.openallay.settings.models.image_input",
            "screen.openallay.settings.models.connect_timeout",
            "screen.openallay.settings.models.request_timeout"
        };
        for (String label : labels) {
            if (y >= area.y() + 30 && y + 18 <= area.bottom()) {
                graphics.text(font, MinecraftComponents.translatable(label), x, y + 5, MUTED, false);
            }
            y += 22;
        }
        int statusY = y + 3;
        graphics.enableScissor(area.x(), area.y() + 30, area.right(), area.bottom());
        selectedView().ifPresent(profile -> {
            int color = profile.available() ? 0xFF7FC8A9 : 0xFFFFD479;
            Component status = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.translatable(
                            profile.available()
                                    ? "screen.openallay.settings.models.available"
                                    : "screen.openallay.settings.models.unavailable")), " · "), MinecraftComponents.translatable(pendingApiKey.isBlank()
                            ? (profile.credentialStoredLocally()
                                    ? "screen.openallay.settings.models.api_key_saved"
                                    : profile.credentialFromEnvironment()
                                            ? "screen.openallay.settings.models.api_key_environment"
                                            : "screen.openallay.settings.models.api_key_not_set")
                            : "screen.openallay.settings.models.api_key_replace"));
            graphics.text(font, status, x, statusY, color, false);
        });
        int estimateY = statusY + 18;
        for (var wrapped : GuideNativeFont.split(font, reasoningExplanation(reasoningSettings()),
                Math.max(20, area.width() - 16))) {
            graphics.text(font, wrapped, x, estimateY, MUTED, false);
            estimateY += 11;
        }
        estimateY += 3;
        for (BuiltinModelSettingsProjection.Line line : modelEstimates().lines()) {
            Component text = MinecraftComponents.translatable(line.key(), line.arguments().toArray());
            for (var wrapped : GuideNativeFont.split(font, text, Math.max(20, area.width() - 16))) {
                graphics.text(font, wrapped, x, estimateY, MUTED, false);
                estimateY += 11;
            }
            estimateY += 3;
        }
        modelEditorContentHeight = estimateY + editorScroll - area.y() - 30;
        graphics.disableScissor();
    }

    private void renderServerModel(
            GuideGraphics graphics, SettingsLayout.Rect area) {
        var server = snapshot.serverModel();
        int x = area.x() + 10;
        int y = area.y() + 12;
        graphics.text(
                font,
                MinecraftComponents.translatable("screen.openallay.settings.models.server_title"),
                x,
                y,
                ACCENT,
                false);
        y += 22;
        graphics.text(
                font,
                MinecraftComponents.translatable(
                        "screen.openallay.settings.models.server_model",
                        server.canonicalModelId()),
                x,
                y,
                TEXT,
                false);
        y += 18;
        graphics.text(
                font,
                MinecraftComponents.translatable(
                        "screen.openallay.settings.models.server_context",
                        server.contextWindowTokens()),
                x,
                y,
                MUTED,
                false);
        y += 18;
        graphics.text(
                font,
                MinecraftComponents.translatable(
                        "screen.openallay.settings.models.server_output",
                        server.maxOutputTokens()),
                x,
                y,
                MUTED,
                false);
        y += 26;
        for (var line : GuideNativeFont.split(font,
                MinecraftComponents.translatable(
                        "screen.openallay.settings.models.server_read_only"),
                Math.max(80, area.width() - 20))) {
            graphics.text(font, line, x, y, MUTED, false);
            y += 10;
        }
    }

    private void renderGeneral(GuideGraphics graphics) {
        GeneralSettingsProjection general = project(snapshot).general();
        SettingsLayout.Rect area = layout.editor();
        int origin = layout.pageOrigin(pageScroll);
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        graphics.text(font, MinecraftComponents.translatable(general.titleKey()),
                area.x() + 10, origin + 12, ACCENT, false);
        // Keep the label together with its fully visible input, not over another scrolled control.
        if (layout.pageWidgetVisible(origin + 31, 33)) {
            graphics.text(font, MinecraftComponents.translatable(general.assistantNameLabelKey()),
                    area.x() + 10, origin + 31, MUTED, false);
        }
        int y = origin + 136;
        for (String key : List.of(general.assistantNameDescriptionKey(),
                general.debugDescriptionKey(), general.animationsDescriptionKey())) {
            for (dev.openallay.client.gui.GuideTextLine line : GuideNativeFont.split(font,
                    MinecraftComponents.translatable(key), Math.max(80, area.width() - 20))) {
                graphics.text(font, line, area.x() + 10, y, MUTED, false);
                y += 10;
            }
            y += 5;
        }
        pageContentHeight = y - origin;
        graphics.disableScissor();
    }

    private void renderAbout(GuideGraphics graphics) {
        SettingsLayout.Rect area = layout.editor();
        int origin = layout.pageOrigin(pageScroll);
        int x = area.x() + 10;
        int contentWidth = Math.max(100, area.width() - 20);
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        graphics.text(font, MinecraftComponents.translatable("screen.openallay.settings.about.title"),
                x, origin + 12, ACCENT, false);
        int bannerWidth = Math.min(contentWidth, 512);
        int bannerHeight = Math.max(54, bannerWidth * 9 / 16);
        int bannerX = x + Math.max(0, (contentWidth - bannerWidth) / 2);
        int bannerY = origin + 31;
        graphics.blitTexture( ABOUT_BANNER,
                bannerX, bannerY, 0.0F, 0.0F, bannerWidth, bannerHeight,
                1024, 576, 1024, 576);
        int y = bannerY + bannerHeight + 12;
        for (dev.openallay.client.gui.GuideTextLine line : GuideNativeFont.split(font,
                MinecraftComponents.translatable("screen.openallay.settings.about.description"), contentWidth)) {
            graphics.text(font, line, x, y, TEXT, false);
            y += 10;
        }
        y += 8;
        graphics.text(font, MinecraftComponents.translatable("screen.openallay.settings.about.repository"),
                x, y, MUTED, false);
        // Wrapping prevents a long URL from escaping the native content pane.
        y += 12;
        for (dev.openallay.client.gui.GuideTextLine line : GuideNativeFont.split(font,
                MinecraftComponents.literal(REPOSITORY_URL), contentWidth)) {
            graphics.text(font, line, x, y, ACCENT, false);
            y += 10;
        }
        pageContentHeight = aboutCopyOffset() + 28;
        graphics.disableScissor();
    }

    private void renderHistory(GuideGraphics graphics) {
        HistorySettingsProjection history = project(snapshot).history();
        SettingsLayout.Rect area = layout.editor();
        graphics.text(
                font,
                MinecraftComponents.translatable(history.titleKey()),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        Component status = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.translatable(history.scopeLabelKey())), " · "), MinecraftComponents.translatable(history.statusKey()));
        graphics.text(font, status, area.x() + 10, area.y() + 31, MUTED, false);
        int actionsBottom = area.y() + 64 - pageScroll + history.actions().size() * 30;
        pageContentHeight = Math.max(0, actionsBottom + pageScroll - area.y());
    }

    private void renderDiagnostics(GuideGraphics graphics) {
        DiagnosticsSettingsProjection diagnostics = project(snapshot).diagnostics();
        SettingsLayout.Rect area = layout.editor();
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        int x = area.x() + 8;
        int width = Math.max(80, area.width() - 16);
        int y = area.y() + 10 - pageScroll;
        y = settingsHeading(
                graphics,
                MinecraftComponents.translatable(diagnostics.titleKey()),
                x,
                y,
                width,
                ACCENT);
        for (DiagnosticsSettingsProjection.CardRow card : diagnostics.cards()) {
            int cardHeight = 34 + (card.noteKeys().size() + card.metrics().size()) * 11;
            graphics.fill(x, y, x + width, y + cardHeight, PANEL_ALT);
            graphics.text(
                    font,
                    MinecraftComponents.append(MinecraftComponents.literal(card.statusIcon() + " "), MinecraftComponents.translatable(card.titleKey())),
                    x + 7,
                    y + 6,
                    TEXT,
                    false);
            graphics.text(
                    font,
                    MinecraftComponents.translatable(card.statusTextKey()),
                    x + 7,
                    y + 18,
                    MUTED,
                    false);
            int metricY = y + 30;
            for (String noteKey : card.noteKeys()) {
                graphics.text(font, MinecraftComponents.translatable(noteKey), x + 12, metricY, MUTED, false);
                metricY += 11;
            }
            for (SettingsDiagnosticCard.Metric metric : card.metrics()) {
                graphics.text(
                        font,
                        MinecraftComponents.translatable(metric.labelKey(), metric.value() == null
                                ? MinecraftComponents.translatable("screen.openallay.settings.diagnostics.unknown")
                                : metric.value()),
                        x + 12,
                        metricY,
                        MUTED,
                        false);
                metricY += 11;
            }
            y += cardHeight + 6;
        }
        if (diagnostics.debug().isPresent()) {
            y = renderDebugDiagnostics(
                    graphics, diagnostics.debug().orElseThrow(), x, y + 4, width);
        }
        pageContentHeight = Math.max(0, y + pageScroll - area.y() + 8);
        graphics.disableScissor();
    }

    private int renderDebugDiagnostics(
            GuideGraphics graphics,
            DiagnosticsSettingsProjection.DebugSection section,
            int x,
            int y,
            int width) {
        SettingsDiagnosticsSnapshot.DebugSettingsDiagnostics debug = section.diagnostics();
        y = settingsHeading(
                graphics,
                MinecraftComponents.translatable(section.titleKey()),
                x,
                y,
                width,
                0xFFFFD479);
        y = debugLine(graphics, x, y, width,
                "screen.openallay.settings.diagnostics.debug.settings_generation",
                Long.toString(debug.settingsGeneration()));
        for (SettingsDiagnosticsSnapshot.DebugModelProfile model : debug.models()) {
            String value = model.profileId() + " · " + model.protocol()
                    + " · " + model.endpointAuthority() + " · " + model.modelId()
                    + " · context=" + model.effectiveContextWindowTokens()
                    + " · credential=" + model.credentialPresent();
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.model", value);
        }
        SettingsDiagnosticsSnapshot.DebugCapabilities capabilities = debug.capabilities();
        y = debugLine(graphics, x, y, width,
                "screen.openallay.settings.diagnostics.debug.capabilities",
                capabilities.catalogEntries() + "/" + capabilities.enabledEntries()
                        + " · sources=" + capabilities.knowledgeSources()
                        + " · tools=" + capabilities.tools()
                        + " · skills=" + capabilities.skills());
        if (debug.guide().isPresent()) {
            SettingsDiagnosticsSnapshot.DebugGuide guide = debug.guide().orElseThrow();
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.guide",
                    guide.scopeKind() + " · session=" + guide.selectedSessionId()
                            + " · " + guide.modelMode()
                            + " · persistence=" + guide.persistenceState()
                            + " · generations=" + guide.committedGeneration()
                            + "/" + guide.submittedGeneration()
                            + " · pending=" + guide.pendingWrites()
                            + " · active=" + guide.activeRequestCount());
            if (guide.request().isPresent()) {
                SettingsDiagnosticsSnapshot.DebugRequest request =
                        guide.request().orElseThrow();
                y = debugLine(graphics, x, y, width,
                        "screen.openallay.settings.diagnostics.debug.request",
                        request.requestId() + " · " + request.topology()
                                + " · " + request.status()
                                + " · retryMs=" + request.retryAfterMillis()
                                + " · tools=" + request.toolCount()
                                + " · sources=" + request.sourceCount());
            }
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.context",
                    "checkpoints=" + guide.context().checkpointCount()
                            + " · failed=" + guide.context().failedCheckpoints()
                            + " · estimatedTokens="
                            + (guide.context().estimatedProjectionTokens() == null
                                    ? MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.diagnostics.unknown"))
                                    : guide.context().estimatedProjectionTokens()));
            SettingsDiagnosticsSnapshot.DebugHistory history = guide.history();
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.history_window",
                    "loaded=" + history.loadedRequests() + "/" + history.totalRequests()
                            + " · cursorCounts=" + history.firstLoadedCount()
                            + ".." + history.lastLoadedCount()
                            + " · page=" + history.pageState());
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.presentation",
                    "cache=" + history.cacheHits() + "/" + history.cacheMisses()
                            + " · fallbacks=" + history.semanticFallbackCount());
        }
        if (!debug.sourcesKnown()) {
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.source",
                    MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.diagnostics.unknown")));
        }
        for (SettingsDiagnosticsSnapshot.DebugSource source : debug.sources()) {
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.source",
                    source.sourceId() + " · " + source.state()
                            + " · generation=" + source.generation()
                            + " · count=" + (source.itemCount() == null
                                    ? MinecraftComponents.getString(MinecraftComponents.translatable("screen.openallay.settings.diagnostics.unknown"))
                                    : source.itemCount())
                            + (source.failureCode() == null
                                    ? ""
                                    : " · failure=" + source.failureCode()));
        }
        for (String code : debug.failureCodes()) {
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.failure", code);
        }
        return y;
    }

    private int settingsHeading(
            GuideGraphics graphics,
            Component text,
            int x,
            int y,
            int width,
            int color) {
        for (dev.openallay.client.gui.GuideTextLine line : GuideNativeFont.split(font, text, width)) {
            graphics.text(font, line, x, y, color, false);
            y += 11;
        }
        return y + 4;
    }

    private int debugLine(
            GuideGraphics graphics,
            int x,
            int y,
            int width,
            String labelKey,
            String value) {
        Component line = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.translatable(labelKey)), ": "), value);
        for (dev.openallay.client.gui.GuideTextLine wrapped : GuideNativeFont.split(font, line, width - 8)) {
            graphics.text(font, wrapped, x + 4, y, MUTED, false);
            y += 10;
        }
        return y + 2;
    }

    private void renderPlaceholder(GuideGraphics graphics) {
        SettingsLayout.Rect area = layout.editor();
        graphics.text(
                font,
                MinecraftComponents.translatable(section.translationKey()),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        graphics.text(
                font,
                MinecraftComponents.translatable("screen.openallay.settings.section_pending"),
                area.x() + 10,
                area.y() + 31,
                MUTED,
                false);
    }

    private void renderExtensions(GuideGraphics graphics) {
        SettingsLayout.Rect area = layout.editor();
        if (!layout.wide() && !narrowExtensionDetail) {
            return;
        }
        ExtensionSettingsProjection projection = extensionProjection();
        if (projection.unrestrictedJavascript()) {
            renderWrapped(graphics, MinecraftComponents.translatable(
                            "screen.openallay.settings.extensions.commands.included"),
                    area.x() + 9, area.bottom() - 52,
                    Math.max(80, area.width() - 18), ACCENT, 10);
        }
        graphics.text(
                font,
                MinecraftComponents.translatable(extensionTab == ExtensionTab.INSTALLED
                        ? "screen.openallay.settings.extensions.installed.title"
                        : "screen.openallay.settings.extensions.community.title"),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        ExtensionSettingsProjection.ExtensionCard extension =
                selectedExtension().orElse(null);
        if (extension == null) {
            Component empty = extensionTab == ExtensionTab.COMMUNITY
                            && !projection.catalog().available()
                    ? MinecraftComponents.translatable(
                            "screen.openallay.settings.extensions.community.unavailable")
                    : MinecraftComponents.translatable(
                            "screen.openallay.settings.extensions.community.empty");
            renderWrapped(
                    graphics,
                    empty,
                    area.x() + 10,
                    area.y() + 31,
                    Math.max(80, area.width() - 20),
                    MUTED,
                    10);
            return;
        }

        int x = area.x() + 10;
        int width = Math.max(80, area.width() - 20);
        int bottomInset = extensionTab == ExtensionTab.COMMUNITY
                        || extension.installable()
                ? 114
                : 62;
        graphics.enableScissor(
                area.x(), area.y() + 28, area.right(), area.bottom() - bottomInset);
        int y = area.y() + 32 - pageScroll;
        y = renderWrapped(
                graphics,
                MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.literal(extension.name())), " · "), MinecraftComponents.translatable(extensionStateKey(extension))),
                x,
                y,
                width,
                TEXT,
                11);
        y = renderWrapped(
                graphics,
                MinecraftComponents.literal(extension.summary()),
                x,
                y + 4,
                width,
                MUTED,
                10);
        y += 7;
        y = extensionDetailLine(
                graphics,
                "screen.openallay.settings.extensions.detail.version",
                extension.version(),
                x,
                y,
                width);
        if (extension.updateAvailable()
                || (extension.state() == dev.openallay.settings.extension.ExtensionSettingsView.State
                        .RESTART_REQUIRED
                        && !extension.availableVersion().equals(extension.version()))) {
            y = extensionDetailLine(
                    graphics,
                    "screen.openallay.settings.extensions.detail.available_version",
                    extension.availableVersion(),
                    x,
                    y,
                    width);
        }
        y = extensionDetailLine(
                graphics,
                "screen.openallay.settings.extensions.detail.provider",
                extension.provider(),
                x,
                y,
                width);
        y = extensionDetailLine(
                graphics,
                "screen.openallay.settings.extensions.detail.loaders",
                String.join(", ", extension.loaders()),
                x,
                y,
                width);
        y = extensionDetailLine(
                graphics,
                "screen.openallay.settings.extensions.detail.minecraft",
                extension.minecraftVersionRange(),
                x,
                y,
                width);
        y = extensionDetailLine(
                graphics,
                "screen.openallay.settings.extensions.detail.api",
                extension.openAllayApiVersionRange(),
                x,
                y,
                width);
        y = extensionDetailLine(
                graphics,
                "screen.openallay.settings.extensions.detail.source",
                extension.source(),
                x,
                y,
                width);

        boolean catalogDeclaration = extension.state()
                        == dev.openallay.settings.extension.ExtensionSettingsView.State.COMMUNITY
                || extension.state()
                        == dev.openallay.settings.extension.ExtensionSettingsView.State.INCOMPATIBLE;
        y = renderRequirements(graphics, extension.requirements(), x, y + 8, width,
                catalogDeclaration);
        if (extension.updateAvailable()) {
            y = renderWrapped(graphics, MinecraftComponents.translatable(
                    RequirementSettingsProjection.PREFIX + "package_check"),
                    x, y + 4, width, MUTED, 10);
        }
        y += 8;
        y = renderExtensionContributions(
                graphics, extension.contributions(), x, y, width);
        if (!extension.diagnostic().isBlank()) {
            y += 7;
            y = renderWrapped(
                    graphics,
                    MinecraftComponents.translatable(
                            "screen.openallay.settings.extensions.detail.diagnostic",
                            extensionDiagnostic(extension.diagnostic())),
                    x,
                    y,
                    width,
                    extension.state()
                                    == dev.openallay.settings.extension.ExtensionSettingsView.State
                                            .ACTIVE
                            ? MUTED
                            : ERROR,
                    10);
        }
        if (projection.debugMode() && !extension.artifact().isBlank()) {
            y += 7;
            y = extensionDetailLine(
                    graphics,
                    "screen.openallay.settings.extensions.detail.artifact",
                    extension.artifact(),
                    x,
                    y,
                    width);
        }
        if (projection.debugMode() && !extension.sha256().isBlank()) {
            y += 7;
            y = extensionDetailLine(
                    graphics,
                    "screen.openallay.settings.extensions.detail.sha256",
                    extension.sha256(),
                    x,
                    y,
                    width);
        }
        String catalogNotice = projection.catalog().noticeMessage();
        if (!catalogNotice.isBlank()) {
            renderWrapped(
                    graphics,
                    MinecraftComponents.literal(catalogNotice),
                    x,
                    Math.min(y + 10, area.bottom() - bottomInset - 14),
                    width,
                    ERROR,
                    10);
        }
        pageContentHeight = Math.max(pageContentHeight,
                y + pageScroll - area.y() + bottomInset + 8);
        graphics.disableScissor();
    }

    private int extensionDetailLine(
            GuideGraphics graphics,
            String labelKey,
            String value,
            int x,
            int y,
            int width) {
        return renderWrapped(
                graphics,
                MinecraftComponents.translatable(labelKey, value),
                x,
                y,
                width,
                MUTED,
                10);
    }

    private int renderExtensionContributions(
            GuideGraphics graphics,
            dev.openallay.settings.extension.ExtensionSettingsView.Contributions contributions,
            int x,
            int y,
            int width) {
        List<ContributionLine> lines = List.of(
                new ContributionLine(
                        "screen.openallay.settings.extensions.detail.roots",
                        contributions.roots()),
                new ContributionLine(
                        "screen.openallay.settings.extensions.detail.data_modules",
                        contributions.dataModules()),
                new ContributionLine(
                        "screen.openallay.settings.extensions.detail.javascript_modules",
                        contributions.javascriptModules()),
                new ContributionLine(
                        "screen.openallay.settings.extensions.detail.skills",
                        contributions.skills()),
                new ContributionLine(
                        "screen.openallay.settings.extensions.detail.result_views",
                        contributions.resultViews()),
                new ContributionLine(
                        "screen.openallay.settings.extensions.detail.host_bindings",
                        contributions.hostBindings()));
        boolean any = false;
        for (ContributionLine line : lines) {
            if (line.values().isEmpty()) {
                continue;
            }
            any = true;
            y = renderWrapped(
                    graphics,
                    MinecraftComponents.translatable(line.labelKey(), String.join(", ", line.values())),
                    x,
                    y,
                    width,
                    TEXT,
                    10);
            y += 2;
        }
        if (!any) {
            y = renderWrapped(
                    graphics,
                    MinecraftComponents.translatable(
                            "screen.openallay.settings.extensions.detail.no_contributions"),
                    x,
                    y,
                    width,
                    MUTED,
                    10);
        }
        return y;
    }

    private int renderExtensionRuntime(
            GuideGraphics graphics,
            ExtensionSettingsProjection.RuntimeCard runtime,
            int x,
            int y,
            int width) {
        int height = extensionRuntimeHeight(runtime, width);
        graphics.fill(x, y, x + width, y + height, PANEL_ALT);
        graphics.outline(x, y, width, height, ACCENT);
        int cursor = y + 7;
        cursor = renderWrapped(
                graphics,
                MinecraftComponents.translatable(runtime.titleKey()),
                x + 7,
                cursor,
                width - 14,
                ACCENT,
                10);
        cursor = renderWrapped(
                graphics,
                MinecraftComponents.translatable(runtime.descriptionKey()),
                x + 7,
                cursor + 3,
                width - 14,
                MUTED,
                10);
        cursor = renderWrapped(
                graphics,
                MinecraftComponents.translatable(
                        "screen.openallay.settings.extensions.runtime.inputs",
                        String.join(", ", runtime.parameters())),
                x + 7,
                cursor + 3,
                width - 14,
                MUTED,
                10);
        renderWrapped(
                graphics,
                MinecraftComponents.translatable(
                        "screen.openallay.settings.extensions.runtime.outputs",
                        String.join(", ", runtime.returns())),
                x + 7,
                cursor + 3,
                width - 14,
                MUTED,
                10);
        return y + height;
    }

    private int renderExtensionCatalog(
            GuideGraphics graphics,
            ExtensionSettingsProjection projection,
            int x,
            int y,
            int width) {
        y = renderExtensionHeading(
                graphics,
                "screen.openallay.settings.extensions.modules",
                projection.modules().size(),
                x,
                y);
        if (projection.modules().isEmpty()) {
            y = renderExtensionEmpty(graphics, x, y);
        } else {
            for (ExtensionSettingsProjection.ModuleCard module : projection.modules()) {
                y = renderExtensionCard(
                        graphics,
                        MinecraftComponents.literal(module.id()),
                        MinecraftComponents.translatable(
                                "screen.openallay.settings.extensions.module.bundled"),
                        null,
                        x,
                        y,
                        width);
            }
        }

        y = renderExtensionHeading(
                graphics,
                "screen.openallay.settings.extensions.adapters",
                projection.adapters().size(),
                x,
                y + 4);
        if (projection.adapters().isEmpty()) {
            y = renderExtensionEmpty(graphics, x, y);
        } else {
            for (ExtensionSettingsProjection.AdapterCard adapter : projection.adapters()) {
                Component detail = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.literal(adapter.summary())), "\n"), MinecraftComponents.translatable(
                                "screen.openallay.settings.extensions.provider",
                                adapter.provider()));
                String schema = schemaPreview(adapter.schema(), projection.debugMode());
                y = renderExtensionCard(
                        graphics,
                        MinecraftComponents.literal(adapter.id()),
                        detail,
                        schema.isBlank() ? null : MinecraftComponents.literal(schema),
                        x,
                        y,
                        width);
            }
        }

        y = renderExtensionHeading(
                graphics,
                "screen.openallay.settings.extensions.roots",
                projection.roots().size(),
                x,
                y + 4);
        for (ExtensionSettingsProjection.RootCard root : projection.roots()) {
            Component detail = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.literal(root.summary())), "\n"), MinecraftComponents.translatable(
                            root.availability().equals("REQUEST_SCOPED")
                                    ? "screen.openallay.settings.extensions.request_scoped"
                                    : "screen.openallay.settings.extensions.provider",
                            root.provider()));
            if (projection.debugMode()) {
                detail = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(detail), "\nprovider: "), root.provider()), " · evidence: "), root.evidenceOwner());
            }
            y = renderExtensionCard(
                    graphics,
                    MinecraftComponents.literal("mc." + root.name()),
                    detail,
                    MinecraftComponents.literal(schemaPreview(root.schema(), projection.debugMode())),
                    x,
                    y,
                    width);
        }
        return y;
    }

    private int renderExtensionHeading(
            GuideGraphics graphics,
            String key,
            int count,
            int x,
            int y) {
        graphics.text(
                font,
                MinecraftComponents.translatable(key, count),
                x + 2,
                y,
                ACCENT,
                false);
        return y + 14;
    }

    private int renderExtensionEmpty(GuideGraphics graphics, int x, int y) {
        graphics.text(
                font,
                MinecraftComponents.translatable("screen.openallay.settings.extensions.none"),
                x + 7,
                y,
                MUTED,
                false);
        return y + 16;
    }

    private int renderExtensionCard(
            GuideGraphics graphics,
            Component title,
            Component detail,
            Component schema,
            int x,
            int y,
            int width) {
        int height = extensionCardHeight(title, detail, schema, width);
        graphics.fill(x, y, x + width, y + height, PANEL_ALT);
        graphics.outline(x, y, width, height, 0xFF46515F);
        int cursor = y + 6;
        cursor = renderWrapped(graphics, title, x + 7, cursor, width - 14, TEXT, 10);
        cursor = renderWrapped(graphics, detail, x + 7, cursor + 2, width - 14, MUTED, 10);
        if (schema != null) {
            cursor = renderWrapped(
                    graphics,
                    MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.translatable("screen.openallay.settings.extensions.schema")), ": "), schema),
                    x + 7,
                    cursor + 2,
                    width - 14,
                    0xFFFFD479,
                    10);
        }
        return y + height + 5;
    }

    private int extensionCatalogHeight(
            ExtensionSettingsProjection projection, int width) {
        int height = 14;
        if (projection.modules().isEmpty()) {
            height += 16;
        } else {
            for (ExtensionSettingsProjection.ModuleCard module : projection.modules()) {
                height += extensionCardHeight(
                                MinecraftComponents.literal(module.id()),
                                MinecraftComponents.translatable(
                                        "screen.openallay.settings.extensions.module.bundled"),
                                null,
                                width)
                        + 5;
            }
        }
        height += 18;
        if (projection.adapters().isEmpty()) {
            height += 16;
        } else {
            for (ExtensionSettingsProjection.AdapterCard adapter : projection.adapters()) {
                Component detail = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.literal(adapter.summary())), "\n"), MinecraftComponents.translatable(
                                "screen.openallay.settings.extensions.provider",
                                adapter.provider()));
                String schema = schemaPreview(adapter.schema(), projection.debugMode());
                height += extensionCardHeight(
                                MinecraftComponents.literal(adapter.id()),
                                detail,
                                schema.isBlank() ? null : MinecraftComponents.literal(schema),
                                width)
                        + 5;
            }
        }
        height += 18;
        for (ExtensionSettingsProjection.RootCard root : projection.roots()) {
            Component detail = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.literal(root.summary())), "\n"), MinecraftComponents.translatable(
                            root.availability().equals("REQUEST_SCOPED")
                                    ? "screen.openallay.settings.extensions.request_scoped"
                                    : "screen.openallay.settings.extensions.provider",
                            root.provider()));
            if (projection.debugMode()) {
                detail = MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(detail), "\nprovider: "), root.provider()), " · evidence: "), root.evidenceOwner());
            }
            height += extensionCardHeight(
                            MinecraftComponents.literal("mc." + root.name()),
                            detail,
                            MinecraftComponents.literal(schemaPreview(
                                    root.schema(), projection.debugMode())),
                            width)
                    + 5;
        }
        return height;
    }

    private int extensionCardHeight(
            Component title, Component detail, Component schema, int width) {
        int inner = Math.max(20, width - 14);
        int height = 12
                + wrappedHeight(title, inner, 10)
                + wrappedHeight(detail, inner, 10);
        if (schema != null) {
            height += 2 + wrappedHeight(
                    MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(MinecraftComponents.translatable("screen.openallay.settings.extensions.schema")), ": "), schema),
                    inner,
                    10);
        }
        return height;
    }

    private int extensionRuntimeHeight(
            ExtensionSettingsProjection.RuntimeCard runtime, int width) {
        int inner = Math.max(20, width - 14);
        return 18
                + wrappedHeight(MinecraftComponents.translatable(runtime.titleKey()), inner, 10)
                + wrappedHeight(MinecraftComponents.translatable(runtime.descriptionKey()), inner, 10)
                + wrappedHeight(MinecraftComponents.translatable(
                                "screen.openallay.settings.extensions.runtime.inputs",
                                String.join(", ", runtime.parameters())),
                        inner,
                        10)
                + wrappedHeight(MinecraftComponents.translatable(
                                "screen.openallay.settings.extensions.runtime.outputs",
                                String.join(", ", runtime.returns())),
                        inner,
                        10);
    }

    private static String schemaPreview(String schema, boolean debugMode) {
        if (schema == null || schema.isBlank()) {
            return "";
        }
        int maximum = debugMode ? 2_000 : 260;
        return schema.length() <= maximum
                ? schema
                : schema.substring(0, maximum - 1) + "…";
    }

    private int renderWrapped(
            GuideGraphics graphics,
            Component text,
            int x,
            int y,
            int width,
            int color,
            int lineHeight) {
        for (dev.openallay.client.gui.GuideTextLine line : GuideNativeFont.split(font, text, Math.max(20, width))) {
            graphics.text(font, line, x, y, color, false);
            y += lineHeight;
        }
        return y;
    }

    private int wrappedHeight(Component text, int width, int lineHeight) {
        return Math.max(1, GuideNativeFont.split(font, text, Math.max(20, width)).size()) * lineHeight;
    }

    private void renderSkills(GuideGraphics graphics) {
        SettingsLayout.Rect area = layout.editor();
        if (!layout.wide() && !narrowSkillDetail) {
            return;
        }
        if (skillTab == SkillTab.COMMUNITY) {
            renderCommunitySkills(graphics, area);
            return;
        }
        Optional<SkillSettingsProjection.Skill> selected = selectedSkill();
        graphics.text(
                font,
                MinecraftComponents.translatable("screen.openallay.settings.skills"),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        if (selected.isEmpty()) {
            graphics.text(
                    font,
                    MinecraftComponents.translatable("screen.openallay.settings.skills.empty"),
                    area.x() + 10,
                    area.y() + 31,
                    MUTED,
                    false);
            return;
        }
        SkillSettingsProjection.Skill skill = selected.orElseThrow();
        graphics.text(font, skill.name(), area.x() + 10, area.y() + 30, TEXT, false);
        graphics.text(
                font,
                MinecraftComponents.translatable(skill.localOverride()
                        ? "screen.openallay.settings.skills.local"
                        : "screen.openallay.settings.skills.bundled"),
                area.x() + 10,
                area.y() + 42,
                MUTED,
                false);
        if (skillEditing) {
            return;
        }
        int width = Math.max(80, area.width() - 20);
        graphics.enableScissor(area.x(), area.y() + 58, area.right(), area.bottom() - 34);
        int start = area.y() + 60 - skillDetailScroll;
        int y = renderWrapped(graphics, MinecraftComponents.literal(skill.description()),
                area.x() + 10, start, width, MUTED, 10);
        y = renderRequirements(graphics, skill.requirements(), area.x() + 10, y + 8, width, false);
        y = renderWrapped(graphics, MinecraftComponents.literal(skill.body()),
                area.x() + 10, y + 8, width, TEXT, 10);
        if (snapshot.display().debugMode()) {
            y = renderWrapped(graphics, MinecraftComponents.literal(skill.provenance()),
                    area.x() + 10, y + 6, width, MUTED, 10);
        }
        skillDetailContentHeight = y - start + 8;
        graphics.disableScissor();
    }

    private int renderRequirements(
            GuideGraphics graphics,
            RequirementSettingsProjection requirements,
            int x, int y, int width, boolean catalogPreview) {
        y = renderWrapped(graphics, MinecraftComponents.translatable(
                RequirementSettingsProjection.PREFIX + "title"), x, y, width, ACCENT, 11);
        if (catalogPreview) {
            y = renderWrapped(graphics, MinecraftComponents.translatable(
                    RequirementSettingsProjection.PREFIX + "catalog_preview"),
                    x, y + 4, width, MUTED, 10);
        }
        if (requirements.rows().isEmpty()) {
            return renderWrapped(graphics, MinecraftComponents.translatable(RequirementSettingsProjection.PREFIX
                    + (catalogPreview ? "package_check" : "none")), x, y + 4, width, MUTED, 10);
        }
        for (RequirementSettingsProjection.Row row : requirements.rows()) {
            y = renderWrapped(graphics, RequirementReviewScreen.rowLabel(row),
                    x, y + 4, width, TEXT, 10);
            if (!row.detail().isBlank()) {
                y = renderWrapped(graphics, MinecraftComponents.literal(row.detail()),
                        x, y + 2, width, MUTED, 10);
            }
        }
        return y;
    }

    private void renderCommunitySkills(
            GuideGraphics graphics,
            SettingsLayout.Rect area) {
        SkillSettingsProjection.Community community = skillProjection().community();
        graphics.text(
                font,
                MinecraftComponents.translatable("screen.openallay.settings.skills.community.title"),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        if (!community.available()) {
            renderWrapped(
                    graphics,
                    MinecraftComponents.translatable(
                            "screen.openallay.settings.skills.community.unavailable"),
                    area.x() + 10,
                    area.y() + 31,
                    Math.max(80, area.width() - 20),
                    MUTED,
                    10);
            return;
        }
        SkillSettingsProjection.Package skill = selectedCommunitySkill().orElse(null);
        if (skill == null) {
            graphics.text(
                    font,
                    MinecraftComponents.translatable("screen.openallay.settings.skills.community.empty"),
                    area.x() + 10,
                    area.y() + 31,
                    MUTED,
                    false);
            return;
        }
        int x = area.x() + 10;
        int width = Math.max(80, area.width() - 20);
        graphics.enableScissor(area.x(), area.y() + 28, area.right(), area.bottom() - 62);
        int start = area.y() + 31 - skillDetailScroll;
        int y = start;
        graphics.text(font, skill.displayName(), x, y, TEXT, false);
        y += 14;
        y = renderWrapped(
                graphics,
                MinecraftComponents.literal(skill.description()),
                x,
                y,
                width,
                MUTED,
                10);
        y += 4;
        graphics.text(
                font,
                MinecraftComponents.translatable("screen.openallay.settings.skills.community.version",
                        skill.version()),
                x,
                y,
                MUTED,
                false);
        y += 13;
        graphics.text(
                font,
                MinecraftComponents.translatable(
                        "screen.openallay.settings.skills.community.publisher",
                        skill.publisher()),
                x,
                y,
                MUTED,
                false);
        y += 13;
        graphics.text(
                font,
                MinecraftComponents.translatable(skillStateKey(skill.state())),
                x,
                y,
                skill.installable() ? ACCENT : MUTED,
                false);
        y = renderRequirements(graphics, new RequirementSettingsProjection(List.of()),
                x, y + 12, width, true);
        y += 18;
        y = renderWrapped(
                graphics,
                MinecraftComponents.translatable(
                        "screen.openallay.settings.skills.community.source", skill.source()),
                x,
                y,
                width,
                TEXT,
                10);
        if (skillProjection().debugMode()) {
            y += 7;
            y = renderWrapped(
                    graphics,
                    MinecraftComponents.translatable(
                            "screen.openallay.settings.skills.community.id",
                            skill.id()),
                    x,
                    y,
                    width,
                    MUTED,
                    10);
            y = renderWrapped(
                    graphics,
                    MinecraftComponents.translatable(
                            "screen.openallay.settings.skills.community.archive",
                            skill.archive()),
                    x,
                    y,
                    width,
                    MUTED,
                    10);
            y = renderWrapped(
                    graphics,
                    MinecraftComponents.translatable(
                            "screen.openallay.settings.skills.community.sha256",
                            skill.sha256()),
                    x,
                    y + 3,
                    width,
                    MUTED,
                    10);
        }
        if (community.notice().isPresent()) {
            y = renderWrapped(graphics, MinecraftComponents.literal(community.notice().orElseThrow().message()),
                    x, y + 18, width, ERROR, 10);
        }
        skillDetailContentHeight = y - start + 8;
        graphics.disableScissor();
    }

    private void renderNotice(GuideGraphics graphics) {
        String message = localNotice;
        int color = ERROR;
        SettingsNotice serviceNotice = snapshot.notice();
        if (message.isBlank() && serviceNotice != null) {
            message = serviceNotice.message();
            color = serviceNotice.level() == SettingsNotice.Level.SUCCESS ? 0xFF7FC8A9 : ERROR;
        }
        if (message.isBlank()) {
            return;
        }
        boolean localPage = section == SettingsSection.UI || section == SettingsSection.VOICE;
        int x = localPage ? layout.footer().x() + 8 : layout.header().x() + 150;
        int right = localPage ? layout.footer().right() - 8
                : layout.header().right() - (layout.showBack() ? 66 : 8);
        int y = localPage ? layout.footer().y() + 32 : layout.header().y() + 10;
        if (right <= x) return;
        graphics.enableScissor(x, y, right, y + 10);
        graphics.text(font, message, x, y, color, false);
        graphics.disableScissor();
    }

    private static boolean completedReload(
            ClientSettingsSnapshot previous,
            ClientSettingsSnapshot next,
            SettingsOperation.Kind reloadKind) {
        return previous.operation().kind() == reloadKind
                && next.operation().kind() == SettingsOperation.Kind.IDLE
                && next.notice() != null
                && next.notice().level() == SettingsNotice.Level.SUCCESS;
    }

    private void done() {
        if (editorSave.busy() || snapshot.operation().kind() != SettingsOperation.Kind.IDLE
                || section == SettingsSection.VOICE && voiceView != null && voiceView.busy()) return;
        captureDraft();
        confirmation = Confirmation.NONE;
        switch (section) {
            case GENERAL -> saveGeneral(true);
            case MODELS -> {
                if (selectedServerModel) closeAfterSave();
                else saveModel(true);
            }
            case UI -> saveUi(true);
            case VOICE -> saveVoice(true);
            // These pages persist each toggle explicitly; Done is not an install or world action.
            case EXTENSIONS, SKILLS, HISTORY, DIAGNOSTICS, ABOUT -> closeAfterSave();
        }
    }

    private void saveEditor(
            java.util.function.Supplier<? extends java.util.concurrent.CompletableFuture<? extends ToolResult<?>>> action,
            boolean closeOnSuccess, java.util.function.Consumer<ToolResult<?>> committed) {
        editorSave.save(action, saveDispatcher, () -> {
            localNotice = "";
            updateEditorSaveControls();
        }, result -> {
            snapshot = service.snapshot();
            if (result instanceof ToolResult.Failure<?> failure) {
                localNotice = failure.message();
            } else {
                localNotice = "";
                committed.accept(result);
                if (closeOnSuccess) {
                    closeAfterSave();
                    return;
                }
            }
            if (layout != null) guideRebuildWidgets();
        });
    }

    private void updateEditorSaveControls() {
        if (!editorSave.busy()) return;
        for (GuideWidget widget : guideWidgetChildren()) {
            widget.guideActive(false);
        }
    }

    private void saveCurrent() {
        confirmation = Confirmation.NONE;
        if (section == SettingsSection.MODELS) {
            save();
        }
    }

    private void reloadCurrent() {
        if (confirmation != Confirmation.RELOAD) {
            confirmation = Confirmation.RELOAD;
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                    "screen.openallay.settings.confirm_reload"));
            guideRebuildWidgets();
            return;
        }
        confirmation = Confirmation.NONE;
        if (section == SettingsSection.MODELS) {
            accept(service.reloadModels(true));
        }
    }

    private void backOrClose() {
        if (layout != null && !layout.wide() && section == SettingsSection.SKILLS && narrowSkillDetail) {
            narrowSkillDetail = false;
            skillEditing = false;
            skillDraftMarkdown = "";
            guideRebuildWidgets();
            return;
        }
        if (layout != null && !layout.wide()
                && section == SettingsSection.EXTENSIONS
                && narrowExtensionDetail) {
            narrowExtensionDetail = false;
            guideRebuildWidgets();
            return;
        }
        done();
    }

    private Component historyActionLabel(HistorySettingsProjection.ActionRow row) {
        if (historyConfirmation == null
                || historyConfirmation.action() != serviceHistoryAction(row.action())) {
            return MinecraftComponents.translatable(row.labelKey());
        }
        if (row.action() == HistorySettingsProjection.Action.RESET_DATABASE
                && historyConfirmation.stage()
                        == ClientSettingsService.ConfirmationStage.FIRST) {
            return MinecraftComponents.translatable(
                    "screen.openallay.settings.history.confirm_reset_again");
        }
        return MinecraftComponents.translatable("screen.openallay.settings.confirm");
    }

    private void activateHistory(HistorySettingsProjection.Action action) {
        ClientSettingsService.HistoryAction serviceAction = serviceHistoryAction(action);
        if (historyConfirmation == null
                || historyConfirmation.action() != serviceAction
                || historyConfirmation.generation() != snapshot.generation()) {
            ToolResult<ClientSettingsService.HistoryConfirmationToken> requested =
                    service.requestHistoryConfirmation(serviceAction);
            if (requested instanceof ToolResult.Success<
                    ClientSettingsService.HistoryConfirmationToken> success) {
                historyConfirmation = success.value();
                localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                        action == HistorySettingsProjection.Action.RESET_DATABASE
                                ? "screen.openallay.settings.history.confirm_reset"
                                : "screen.openallay.settings.history.confirm_delete"));
                guideRebuildWidgets();
            } else {
                ToolResult.Failure<ClientSettingsService.HistoryConfirmationToken> failure =
                        (ToolResult.Failure<ClientSettingsService.HistoryConfirmationToken>) requested;
                localNotice = failure.message();
            }
            return;
        }
        if (action == HistorySettingsProjection.Action.RESET_DATABASE
                && historyConfirmation.stage()
                        == ClientSettingsService.ConfirmationStage.FIRST) {
            ToolResult<ClientSettingsService.HistoryConfirmationToken> second =
                    service.confirmHistoryReset(historyConfirmation);
            if (second instanceof ToolResult.Success<
                    ClientSettingsService.HistoryConfirmationToken> success) {
                historyConfirmation = success.value();
                localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                        "screen.openallay.settings.history.confirm_reset_again_notice"));
                guideRebuildWidgets();
            } else {
                historyConfirmation = null;
                localNotice = ((ToolResult.Failure<
                        ClientSettingsService.HistoryConfirmationToken>) second).message();
            }
            return;
        }

        ClientSettingsService.HistoryConfirmationToken confirmed = historyConfirmation;
        historyConfirmation = null;
        confirmation = Confirmation.NONE;
        accept(switch (action) {
            case DELETE_CURRENT -> service.deleteCurrentHistory(confirmed);
            case DELETE_ACTOR -> service.deleteActorHistory(confirmed);
            case RESET_DATABASE -> service.resetHistoryDatabase(confirmed);
        });
    }

    private static ClientSettingsService.HistoryAction serviceHistoryAction(
            HistorySettingsProjection.Action action) {
        return switch (action) {
            case DELETE_CURRENT -> ClientSettingsService.HistoryAction.DELETE_CURRENT;
            case DELETE_ACTOR -> ClientSettingsService.HistoryAction.DELETE_ACTOR;
            case RESET_DATABASE -> ClientSettingsService.HistoryAction.RESET_DATABASE;
        };
    }

    private ExtensionSettingsProjection extensionProjection() {
        return ExtensionSettingsProjection.from(
                snapshot.extensions(),
                snapshot.experimentalCommands(),
                snapshot.unrestrictedJavascript(),
                RequirementSettingsEnvironment.from(snapshot),
                snapshot.display().debugMode());
    }

    private SkillSettingsProjection skillProjection() {
        return SkillSettingsProjection.from(
                snapshot.skills(),
                snapshot.skillCommunity(),
                RequirementSettingsEnvironment.from(snapshot),
                snapshot.display().debugMode());
    }

    private Optional<SkillSettingsProjection.Skill> selectedSkill() {
        return selectedSkillName == null
                ? Optional.empty()
                : skillProjection().find(selectedSkillName);
    }

    private Optional<SkillSettingsProjection.Package> selectedCommunitySkill() {
        return selectedCommunitySkillId == null
                ? Optional.empty()
                : skillProjection().community().find(selectedCommunitySkillId);
    }

    private Optional<ExtensionSettingsProjection.ExtensionCard> selectedExtension() {
        if (selectedExtensionId == null) {
            return Optional.empty();
        }
        ExtensionSettingsProjection projection = extensionProjection();
        return extensionTab == ExtensionTab.INSTALLED
                ? projection.findInstalled(selectedExtensionId)
                : projection.findCommunity(selectedExtensionId);
    }

    private static String extensionStateKey(
            ExtensionSettingsProjection.ExtensionCard extension) {
        if (extension.updateAvailable()) {
            return "screen.openallay.settings.extensions.state.update_available";
        }
        return switch (extension.state()) {
            case ACTIVE -> "screen.openallay.settings.extensions.state.active";
            case RESTART_REQUIRED ->
                    "screen.openallay.settings.extensions.state.restart_required";
            case INCOMPATIBLE ->
                    "screen.openallay.settings.extensions.state.incompatible";
            case UNAVAILABLE -> "screen.openallay.settings.extensions.state.unavailable";
            case COMMUNITY -> "screen.openallay.settings.extensions.state.available";
        };
    }

    private static String extensionDiagnostic(String diagnostic) {
        return switch (diagnostic) {
            case "restart_required" -> MinecraftComponents.getString(MinecraftComponents.translatable(
                            "screen.openallay.settings.extensions.diagnostic.restart_required"));
            case "incompatible_loader" -> MinecraftComponents.getString(MinecraftComponents.translatable(
                            "screen.openallay.settings.extensions.diagnostic.loader"));
            case "incompatible_game_version" -> MinecraftComponents.getString(MinecraftComponents.translatable(
                            "screen.openallay.settings.extensions.diagnostic.game"));
            case "incompatible_openallay_api" -> MinecraftComponents.getString(MinecraftComponents.translatable(
                            "screen.openallay.settings.extensions.diagnostic.api"));
            default -> diagnostic;
        };
    }

    private static String skillStateKey(SkillSettingsProjection.PackageState state) {
        return switch (state) {
            case AVAILABLE -> "screen.openallay.settings.skills.community.available";
            case INSTALLED -> "screen.openallay.settings.skills.community.installed";
            case UPDATE_AVAILABLE ->
                    "screen.openallay.settings.skills.community.update_available";
            case INCOMPATIBLE -> "screen.openallay.settings.skills.community.incompatible";
        };
    }

    private void selectSkillTab(SkillTab replacement) {
        if (skillTab == replacement) {
            return;
        }
        captureDraft();
        skillTab = replacement;
        pageScroll = 0;
        narrowSkillDetail = false;
        skillEditing = false;
        skillDraftMarkdown = "";
        localNotice = "";
        guideRebuildWidgets();
        maybeRefreshVisibleCommunity();
    }

    private void selectExtensionTab(ExtensionTab replacement) {
        if (extensionTab == replacement) {
            return;
        }
        captureDraft();
        extensionTab = replacement;
        pageScroll = 0;
        narrowExtensionDetail = false;
        List<ExtensionSettingsProjection.ExtensionCard> cards =
                replacement == ExtensionTab.INSTALLED
                        ? extensionProjection().installed()
                        : extensionProjection().community();
        selectedExtensionId = cards.isEmpty() ? null : cards.get(0).id();
        localNotice = "";
        guideRebuildWidgets();
        maybeRefreshVisibleCommunity();
    }

    private void maybeRefreshVisibleCommunity() {
        if (snapshot.operation().kind() != SettingsOperation.Kind.IDLE) {
            return;
        }
        if (section == SettingsSection.SKILLS
                && skillTab == SkillTab.COMMUNITY
                && shouldRefreshSkillCommunity(
                        skillProjection().community(), skillCommunityRefreshAttempted)) {
            skillCommunityRefreshAttempted = true;
            accept(service.refreshSkillCommunity());
        } else if (section == SettingsSection.EXTENSIONS
                && extensionTab == ExtensionTab.COMMUNITY
                && shouldRefreshExtensionCommunity(
                        extensionProjection().catalog(),
                        extensionCommunityRefreshAttempted)) {
            extensionCommunityRefreshAttempted = true;
            accept(service.refreshExtensionCommunity());
        }
    }

    static boolean shouldRefreshSkillCommunity(
            SkillSettingsProjection.Community community, boolean attempted) {
        Objects.requireNonNull(community, "community");
        return !attempted && community.notice().isEmpty();
    }

    static boolean shouldRefreshExtensionCommunity(
            ExtensionSettingsProjection.CatalogCard catalog, boolean attempted) {
        Objects.requireNonNull(catalog, "catalog");
        return !attempted && catalog.configured() && catalog.noticeCode().isBlank();
    }

    private void importLocalExtension() {
        captureDraft();
        if (extensionImportPathDraft.isBlank()) {
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                            "screen.openallay.settings.extensions.community.import_required"));
            return;
        }
        try {
            accept(service.importLocalExtensionPackage(Path.of(extensionImportPathDraft)));
        } catch (InvalidPathException failure) {
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                            "screen.openallay.settings.extensions.community.import_invalid"));
        }
    }

    private void importLocalSkill() {
        captureDraft();
        if (skillImportPathDraft.isBlank()) {
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                    "screen.openallay.settings.skills.community.import_required"));
            return;
        }
        try {
            accept(service.importLocalSkillPackage(Path.of(skillImportPathDraft)));
        } catch (InvalidPathException failure) {
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                    "screen.openallay.settings.skills.community.import_invalid"));
        }
    }

    private void saveSkillOverride() {
        SkillSettingsProjection.Skill selected = selectedSkill().orElse(null);
        if (selected == null || skillDraftMarkdown.isBlank()) {
            return;
        }
        skillEditing = false;
        accept(service.saveSkillOverride(selected.name(), skillDraftMarkdown));
        skillDraftMarkdown = "";
    }

    private void save() { saveModel(false); }

    private void saveModel(boolean closeOnSuccess) {
        captureDraft();
        ToolResult<ModelProfileDefinition> validated = draft.validate();
        if (validated instanceof ToolResult.Failure<ModelProfileDefinition> failure) {
            localNotice = failure.message();
            return;
        }
        ModelProfileDefinition definition =
                ((ToolResult.Success<ModelProfileDefinition>) validated).value();
        ModelProfilesConfig candidate;
        try {
            candidate = candidateWith(definition);
        } catch (RuntimeException failure) {
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                    "screen.openallay.settings.models.invalid"));
            return;
        }
        if (candidate.equals(snapshot.models().config()) && pendingApiKey.isBlank()) {
            if (closeOnSuccess) closeAfterSave();
            return;
        }
        SecretValue replacement = pendingApiKey.isBlank() ? null : SecretValue.of(pendingApiKey);
        saveEditor(() -> service.saveModels(candidate, definition.id(), replacement), closeOnSuccess,
                result -> select(definition.id()));
    }

    private void delete() {
        if (selectedProfileId == null) {
            select(snapshot.models().config().defaultProfileId());
            guideRebuildWidgets();
            return;
        }
        if (snapshot.models().config().profiles().size() <= 1) {
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                    "screen.openallay.settings.models.cannot_delete_last"));
            return;
        }
        if (confirmation != Confirmation.DELETE) {
            confirmation = Confirmation.DELETE;
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                    "screen.openallay.settings.confirm_delete"));
            guideRebuildWidgets();
            return;
        }
        List<ModelProfileDefinition> retained = snapshot.models().config().profiles().stream()
                .filter(profile -> !profile.id().equals(selectedProfileId))
                .toList();
        String defaultId = snapshot.models().config().defaultProfileId().equals(selectedProfileId)
                ? retained.get(0).id()
                : snapshot.models().config().defaultProfileId();
        select(retained.get(0).id());
        confirmation = Confirmation.NONE;
        accept(service.saveModels(new ModelProfilesConfig(
                defaultId, retained)));
    }

    private void makeDefault() {
        if (selectedProfileId == null) {
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                    "screen.openallay.settings.models.save_first"));
            return;
        }
        ModelProfilesConfig current = snapshot.models().config();
        accept(service.saveModels(new ModelProfilesConfig(
                selectedProfileId, current.profiles())));
    }

    private void testConnection() {
        captureDraft();
        ToolResult<ModelProfileDefinition> validated = draft.validate();
        if (validated instanceof ToolResult.Failure<ModelProfileDefinition> failure) {
            localNotice = failure.message();
            return;
        }
        if (confirmation != Confirmation.TEST_CONNECTION) {
            confirmation = Confirmation.TEST_CONNECTION;
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                    "screen.openallay.settings.confirm_billable_test"));
            guideRebuildWidgets();
            return;
        }
        confirmation = Confirmation.NONE;
        ModelProfileDefinition definition =
                ((ToolResult.Success<ModelProfileDefinition>) validated).value();
        SecretValue replacement = pendingApiKey.isBlank()
                ? null
                : SecretValue.of(pendingApiKey);
        service.testConnection(definition, replacement).thenAccept(result -> {
            if (result instanceof ModelConnectionResult.Failure failure) {
                localNotice = failure.message();
            } else {
                localNotice = "";
            }
        });
    }

    private void cancel() {
        if (snapshot.operation().kind() == SettingsOperation.Kind.FETCHING_MODEL_CATALOG) {
            service.cancelModelCatalog();
        } else {
            service.cancelConnectionTest();
        }
    }

    private void refreshMetadata() {
        accept(service.refreshMetadata());
    }

    private void fetchModelCatalog() {
        captureDraft();
        ToolResult<ModelCatalogRequest> validated = draft.catalogRequest();
        if (validated instanceof ToolResult.Failure<ModelCatalogRequest> failure) {
            localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                    "screen.openallay.settings.models.catalog_invalid"));
            return;
        }
        ModelCatalogRequest request =
                ((ToolResult.Success<ModelCatalogRequest>) validated).value();
        SecretValue replacement = pendingApiKey.isBlank()
                ? null
                : SecretValue.of(pendingApiKey);
        long generation = ++modelCatalogGeneration;
        service.fetchModelCatalog(request, replacement).thenAccept(result -> {
            if (generation != modelCatalogGeneration) {
                return;
            }
            if (result instanceof ToolResult.Success<ModelCatalog> success) {
                catalogModelIds = success.value().modelIds();
                modelCatalogPage = 0;
                modelCatalogOpen = true;
                localNotice = catalogModelIds.isEmpty()
                        ? MinecraftComponents.getString(MinecraftComponents.translatable(
                                "screen.openallay.settings.models.catalog_empty"))
                        : "";
                guideRebuildWidgets();
            } else {
                ToolResult.Failure<ModelCatalog> failure =
                        (ToolResult.Failure<ModelCatalog>) result;
                localNotice = MinecraftComponents.getString(MinecraftComponents.translatable(
                        "screen.openallay.settings.models.catalog_failed",
                        failure.message()));
            }
        });
    }

    private void addModelCatalogPicker() {
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 8;
        int y = area.y() + 30;
        int width = Math.max(80, area.width() - 16);
        int pageSize = modelCatalogPageSize();
        int start = Math.min(catalogModelIds.size(), modelCatalogPage * pageSize);
        int end = Math.min(catalogModelIds.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            String modelId = catalogModelIds.get(index);
            addGuideWidget(OpenAllayButton.create(MinecraftComponents.literal(modelId), ignored -> {
                        draft = draft.withModel(modelId);
                        refreshAutomaticContext();
                        modelCatalogOpen = false;
                        localNotice = "";
                        guideRebuildWidgets();
                    })
                    .bounds(x, y, width, 20)
                    .build());
            y += 23;
        }
        int pages = Math.max(1, (catalogModelIds.size() + pageSize - 1) / pageSize);
        int navY = area.bottom() - 24;
        int navWidth = Math.max(30, (width - 8) / 3);
        GuideNativeButton previous = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable("screen.openallay.settings.models.catalog_previous"),
                        ignored -> {
                            modelCatalogPage--;
                            guideRebuildWidgets();
                        })
                .bounds(x, navY, navWidth, 20)
                .build());
        previous.active = modelCatalogPage > 0;
        GuideNativeButton next = addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable("screen.openallay.settings.models.catalog_next"),
                        ignored -> {
                            modelCatalogPage++;
                            guideRebuildWidgets();
                        })
                .bounds(x + navWidth + 4, navY, navWidth, 20)
                .build());
        next.active = modelCatalogPage + 1 < pages;
        addGuideWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable("screen.openallay.settings.models.catalog_close"),
                        ignored -> {
                            modelCatalogOpen = false;
                            guideRebuildWidgets();
                        })
                .bounds(x + (navWidth + 4) * 2, navY,
                        Math.max(30, width - (navWidth + 4) * 2), 20)
                .build());
    }

    private int modelCatalogPageSize() {
        return Math.max(1, (layout.editor().height() - 64) / 23);
    }

    private void invalidateModelCatalog() {
        modelCatalogGeneration++;
        catalogModelIds = List.of();
        modelCatalogOpen = false;
        modelCatalogPage = 0;
    }

    private void accept(java.util.concurrent.CompletableFuture<ToolResult<Boolean>> future) {
        future.thenAccept(result -> {
            if (result instanceof ToolResult.Failure<Boolean> failure) {
                localNotice = failure.code().equals("capability_dependency_conflict")
                        ? MinecraftComponents.getString(MinecraftComponents.translatable(
                                "screen.openallay.settings.capability.dependency_conflict"))
                        : failure.message();
            } else {
                localNotice = "";
            }
        });
    }

    private ModelProfilesConfig candidateWith(ModelProfileDefinition replacement) {
        ModelProfilesConfig current = snapshot.models().config();
        List<ModelProfileDefinition> profiles = new ArrayList<>();
        boolean replaced = false;
        for (ModelProfileDefinition profile : current.profiles()) {
            if (selectedProfileId != null && profile.id().equals(selectedProfileId)) {
                profiles.add(replacement);
                replaced = true;
            } else {
                profiles.add(profile);
            }
        }
        if (!replaced) {
            profiles.add(replacement);
        }
        String defaultId = current.defaultProfileId();
        if (selectedProfileId != null
                && defaultId.equals(selectedProfileId)
                && !selectedProfileId.equals(replacement.id())) {
            defaultId = replacement.id();
        }
        return new ModelProfilesConfig(defaultId, profiles);
    }

    private void switchSection(SettingsSection replacement) {
        if (editorSave.busy()) return;
        captureDraft();
        section = replacement;
        sectionMenuOpen = false;
        editorMenuOpen = false;
        editorScroll = 0;
        pageScroll = 0;
        pageContentHeight = 0;
        historyConfirmation = null;
        narrowSkillDetail = false;
        narrowExtensionDetail = false;
        skillEditing = false;
        skillDraftMarkdown = "";
        confirmation = Confirmation.NONE;
        localNotice = "";
        if (replacement != SettingsSection.MODELS) {
            modelCatalogOpen = false;
        }
        guideRebuildWidgets();
    }

    private void selectAndRebuild(String profileId) {
        if (ModelSettingsProjection.SERVER_SELECTION_ID.equals(profileId)) {
            selectedServerModel = true;
            invalidateModelCatalog();
        } else {
            select(profileId.startsWith("client:")
                    ? profileId.substring("client:".length())
                    : profileId);
        }
        editorScroll = 0;
        confirmation = Confirmation.NONE;
        localNotice = "";
        editorScroll = 0;
        guideRebuildWidgets();
    }

    private void select(String profileId) {
        ModelProfileDefinition definition = snapshot.models().config().profiles().stream()
                .filter(profile -> profile.id().equals(profileId))
                .findFirst()
                .orElseThrow();
        selectedProfileId = definition.id();
        selectedServerModel = false;
        draft = ModelProfileDraft.from(definition);
        refreshAutomaticContext();
        draftEnabled = definition.enabled();
        draftProtocol = definition.protocol();
        pendingApiKey = "";
        invalidateModelCatalog();
    }

    private BuiltinModelSettingsProjection modelEstimates() {
        return modelEstimateCache.projection();
    }

    /** Event-owned projection. Rendering never invokes metadata resolution or matching. */
    private void refreshAutomaticContext() {
        if (draft == null || selectedServerModel) return;
        draft = modelEstimateCache.refresh(draft,
                dev.openallay.model.metadata.BuiltinModelCatalog.bundled(),
                this::draftContextResolution,
                this::draftOutputResolution,
                this::draftImageCapabilityResolution);
    }

    private dev.openallay.model.metadata.ModelContextResolution draftContextResolution() {
        try {
            Integer manual = draft.automaticContextWindowTokens() != null
                    || draft.contextWindowTokens() == null || draft.contextWindowTokens().isBlank()
                    ? null : Integer.valueOf(draft.contextWindowTokens().trim());
            return service.modelContext(java.net.URI.create(draft.baseUrl()), draft.model(), manual);
        } catch (RuntimeException invalidDraft) {
            return new dev.openallay.model.metadata.ModelContextResolution(null,
                    dev.openallay.model.metadata.ModelContextResolution.Origin.REQUIRED);
        }
    }

    private dev.openallay.model.metadata.ModelOutputResolution draftOutputResolution() {
        try {
            Integer manual = draft.automaticMaxOutputTokens() != null
                    || draft.maxOutputTokens() == null || draft.maxOutputTokens().isBlank()
                    ? null : Integer.valueOf(draft.maxOutputTokens().trim());
            return service.modelOutput(java.net.URI.create(draft.baseUrl()), draft.model(), manual);
        } catch (RuntimeException invalidDraft) {
            return new dev.openallay.model.metadata.ModelOutputResolution(null,
                    dev.openallay.model.metadata.ModelOutputResolution.Origin.REQUIRED);
        }
    }

    private dev.openallay.model.metadata.ModelImageCapabilityResolution draftImageCapabilityResolution() {
        try {
            return service.modelImageCapability(java.net.URI.create(draft.baseUrl()), draft.model(),
                    draft.imageInputCapabilityOverride());
        } catch (RuntimeException invalidDraft) {
            return dev.openallay.model.metadata.ModelImageCapabilityResolution.unknown();
        }
    }

    private void updateAutomaticOutputWidget() {
        if (maxOutput == null) return;
        updatingAutomaticOutput = true;
        try { maxOutput.setValue(draft.maxOutputTokens()); }
        finally { updatingAutomaticOutput = false; }
    }

    private void updateAutomaticContextWidget() {
        if (contextWindow == null) return;
        updatingAutomaticContext = true;
        try { contextWindow.setValue(draft.contextWindowTokens()); }
        finally { updatingAutomaticContext = false; }
    }

    private void createProfile() {
        int suffix = 1;
        String candidate = "profile-" + suffix;
        while (containsProfile(candidate)) {
            candidate = "profile-" + ++suffix;
        }
        selectedProfileId = null;
        selectedServerModel = false;
        draft = ModelProfileDraft.create(candidate);
        draftEnabled = true;
        draftProtocol = ModelProtocol.OPENAI_CHAT;
        pendingApiKey = "";
        invalidateModelCatalog();
        confirmation = Confirmation.NONE;
        localNotice = "";
        guideRebuildWidgets();
    }

    private boolean containsProfile(String profileId) {
        return snapshot.models().config().profiles().stream()
                .anyMatch(profile -> profile.id().equals(profileId));
    }

    private void captureDraft() {
        if (section == SettingsSection.MODELS && !selectedServerModel && id != null) {
            draft = new ModelProfileDraft(
                    id.getValue(),
                    displayName.getValue(),
                    draftEnabled,
                    draftProtocol,
                    baseUrl.getValue(),
                    model.getValue(),
                    draft.credentialRef(),
                    contextWindow.getValue(),
                    maxOutput.getValue(),
                    connectTimeout.getValue(),
                    requestTimeout.getValue(),
                    draft.metadata(),
                    Objects.equals(contextWindow.getValue(), draft.automaticContextWindowTokens())
                            ? draft.automaticContextWindowTokens() : null,
                    Objects.equals(maxOutput.getValue(), draft.automaticMaxOutputTokens())
                            ? draft.automaticMaxOutputTokens() : null,
                    draft.reasoningEffort(), draft.tokenEncoding(), draft.imageInputCapabilityOverride());
            if (apiKey != null) pendingApiKey = apiKey.getValue();
        }
        if (section == SettingsSection.GENERAL && assistantName != null) {
            assistantNameDraft = assistantName.getValue();
        }
        if (section == SettingsSection.UI) {
            uiIntegerFields.forEach((key, field) -> uiIntegerDrafts.put(key, field.getValue()));
        }
        if (section == SettingsSection.VOICE) {
            voiceModelPath = voiceFieldValue("model_directory", voiceModelPath);
            voiceRuntimePath = voiceFieldValue("runtime_directory", voiceRuntimePath);
            voiceHttpUrl = voiceFieldValue("http_url", voiceHttpUrl);
            voiceHttpModel = voiceFieldValue("http_model", voiceHttpModel);
            voiceApiKeyDraft = voiceFieldValue("api_key", voiceApiKeyDraft);
        }
        if (section == SettingsSection.SKILLS && skillImportPath != null) {
            skillImportPathDraft = skillImportPath.getValue();
        }
        if (section == SettingsSection.EXTENSIONS && extensionImportPath != null) {
            extensionImportPathDraft = extensionImportPath.getValue();
        }
    }

    private ModelReasoningSettingsProjection reasoningSettings() {
        return ModelReasoningSettingsProjection.from(draftProtocol, draft.reasoningEffort());
    }

    private Component reasoningExplanation(ModelReasoningSettingsProjection reasoning) {
        return MinecraftComponents.translatable(reasoning.explanationKey(),
                reasoning.wireField(), reasoning.selected().encoded());
    }

    private void cycleProtocol() {
        captureDraft();
        draftProtocol = draftProtocol == ModelProtocol.OPENAI_CHAT
                ? ModelProtocol.ANTHROPIC_MESSAGES
                : ModelProtocol.OPENAI_CHAT;
        invalidateModelCatalog();
        confirmation = Confirmation.NONE;
        guideRebuildWidgets();
    }

    private void toggleEnabled() {
        captureDraft();
        draftEnabled = !draftEnabled;
        confirmation = Confirmation.NONE;
        guideRebuildWidgets();
    }


    private Component protocolLabel() {
        return MinecraftComponents.translatable(
                draftProtocol == ModelProtocol.OPENAI_CHAT
                        ? "screen.openallay.settings.models.protocol_openai"
                        : "screen.openallay.settings.models.protocol_anthropic");
    }

    private Component enabledLabel() {
        return MinecraftComponents.translatable(
                draftEnabled
                        ? "screen.openallay.settings.models.enabled"
                        : "screen.openallay.settings.models.disabled");
    }

    private String reloadKey() {
        return confirmation == Confirmation.RELOAD
                ? "screen.openallay.settings.confirm"
                : "screen.openallay.settings.reload";
    }

    private String deleteKey() {
        return confirmation == Confirmation.DELETE
                ? "screen.openallay.settings.confirm"
                : "screen.openallay.settings.delete";
    }

    private String testKey() {
        return confirmation == Confirmation.TEST_CONNECTION
                ? "screen.openallay.settings.confirm"
                : "screen.openallay.settings.models.test";
    }

    private java.util.Optional<ModelProfileSettingsView.Profile> selectedView() {
        return snapshot.models().profiles().stream()
                .filter(profile -> profile.definition().id().equals(selectedProfileId))
                .findFirst();
    }

    private String selectedModelSelectionId() {
        return selectedServerModel
                ? ModelSettingsProjection.SERVER_SELECTION_ID
                : "client:" + selectedProfileId;
    }

    private static void panel(
            GuideGraphics graphics, SettingsLayout.Rect rect, int color) {
        if (rect.width() > 0 && rect.height() > 0) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
        }
    }

    /** Inert test entry points press the real controls; they never assign drafts or save directly. */
    public void e2eChooseSection(SettingsSection target) {
        requireE2eControls();
        if (section == target) return;
        if (!layout.wide() && !sectionMenuOpen) e2ePressButton(section.translationKey());
        e2ePressButton(target.translationKey());
    }

    public void e2eCycleTheme() {
        requireE2eControls();
        if (section != SettingsSection.UI || uiGroup != UiSettingsProjection.Group.FULLSCREEN) {
            throw new IllegalStateException("Fullscreen UI settings are not open");
        }
        e2ePressButton("screen.openallay.settings.ui.theme");
    }

    public void e2ePressDone() {
        requireE2eControls();
        e2ePressButton("screen.openallay.settings.done");
    }

    public void e2ePressEscape() {
        requireE2eControls();
        GuideWidgetInputs.keyPressed(GuideNativeInput.widgetInput(this), GuideNativeInput.keyEvent(dev.openallay.client.gui.GuideInputCodes.KEY_ESCAPE, 0));
    }

    public void e2ePressBack() {
        requireE2eControls();
        e2ePressButton("screen.openallay.settings.back");
    }

    private void e2ePressButton(String translationKey) {
        String label = MinecraftComponents.getString(MinecraftComponents.translatable(translationKey));
        for (var child : children()) {
            if (child instanceof GuideNativeButton button && button.visible && button.active
                    && (MinecraftComponents.getString(button.getMessage()).equals(label)
                            || MinecraftComponents.getString(button.getMessage()).startsWith(label + " · "))) {
                GuideNativeInput.press(button, GuideNativeInput.keyEvent(dev.openallay.client.gui.GuideInputCodes.KEY_RETURN, 0));
                return;
            }
        }
        throw new IllegalStateException("Settings control is unavailable: " + translationKey);
    }

    public E2eSettingsState e2eSettingsState() {
        requireE2eControls();
        return new E2eSettingsState(section, layout != null, editorSave.busy(),
                snapshot.operation().kind(), uiDraft.dirty(),
                uiDraft.ui().fullscreen().theme(), snapshot.display().ui().fullscreen().theme());
    }

    @dev.openallay.value.ValueType(E2eSettingsState.ValueSchemaProvider.class)
public static final class E2eSettingsState {
    private final SettingsSection section;
    private final boolean ready;
    private final boolean saving;
    private final SettingsOperation.Kind operation;
    private final boolean uiDirty;
    private final GuideUiConfig.Theme previewTheme;
    private final GuideUiConfig.Theme savedTheme;
    public E2eSettingsState(SettingsSection section, boolean ready, boolean saving, SettingsOperation.Kind operation, boolean uiDirty, GuideUiConfig.Theme previewTheme, GuideUiConfig.Theme savedTheme) {
        this.section = section;
        this.ready = ready;
        this.saving = saving;
        this.operation = operation;
        this.uiDirty = uiDirty;
        this.previewTheme = previewTheme;
        this.savedTheme = savedTheme;
    }
    public SettingsSection section() { return section; }
    public boolean ready() { return ready; }
    public boolean saving() { return saving; }
    public SettingsOperation.Kind operation() { return operation; }
    public boolean uiDirty() { return uiDirty; }
    public GuideUiConfig.Theme previewTheme() { return previewTheme; }
    public GuideUiConfig.Theme savedTheme() { return savedTheme; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof E2eSettingsState)) return false;
        E2eSettingsState that = (E2eSettingsState) other;
        return java.util.Objects.equals(section, that.section) && ready == that.ready && saving == that.saving && java.util.Objects.equals(operation, that.operation) && uiDirty == that.uiDirty && java.util.Objects.equals(previewTheme, that.previewTheme) && java.util.Objects.equals(savedTheme, that.savedTheme);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(section);
        hash = 31 * hash + Boolean.hashCode(ready);
        hash = 31 * hash + Boolean.hashCode(saving);
        hash = 31 * hash + java.util.Objects.hashCode(operation);
        hash = 31 * hash + Boolean.hashCode(uiDirty);
        hash = 31 * hash + java.util.Objects.hashCode(previewTheme);
        hash = 31 * hash + java.util.Objects.hashCode(savedTheme);
        return hash;
    }
    @Override public String toString() { return "E2eSettingsState[section=" + section + ", ready=" + ready + ", saving=" + saving + ", operation=" + operation + ", uiDirty=" + uiDirty + ", previewTheme=" + previewTheme + ", savedTheme=" + savedTheme + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<E2eSettingsState> schema() {
            return new dev.openallay.value.ValueSchema<>(E2eSettingsState.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<E2eSettingsState>>asList(new dev.openallay.value.ValueSchema.Component<>(E2eSettingsState.class, "section", E2eSettingsState::section), new dev.openallay.value.ValueSchema.Component<>(E2eSettingsState.class, "ready", E2eSettingsState::ready), new dev.openallay.value.ValueSchema.Component<>(E2eSettingsState.class, "saving", E2eSettingsState::saving), new dev.openallay.value.ValueSchema.Component<>(E2eSettingsState.class, "operation", E2eSettingsState::operation), new dev.openallay.value.ValueSchema.Component<>(E2eSettingsState.class, "uiDirty", E2eSettingsState::uiDirty), new dev.openallay.value.ValueSchema.Component<>(E2eSettingsState.class, "previewTheme", E2eSettingsState::previewTheme), new dev.openallay.value.ValueSchema.Component<>(E2eSettingsState.class, "savedTheme", E2eSettingsState::savedTheme)), arguments -> new E2eSettingsState((SettingsSection) arguments[0], (Boolean) arguments[1], (Boolean) arguments[2], (SettingsOperation.Kind) arguments[3], (Boolean) arguments[4], (GuideUiConfig.Theme) arguments[5], (GuideUiConfig.Theme) arguments[6]));
        }
    }
}

    /** Screenshot-harness navigation only; inert in every normal client launch. */
    public void e2eOpenExtensions() {
        requireE2eControls();
        captureDraft();
        section = SettingsSection.EXTENSIONS;
        pageScroll = 0;
        pageContentHeight = 0;
        guideRebuildWidgets();
    }

    /** Screenshot-harness selection of an existing configured profile; no settings write. */
    public void e2eOpenModels(String profileId) {
        requireE2eControls();
        captureDraft();
        select(Objects.requireNonNull(profileId, "profileId"));
        section = SettingsSection.MODELS;
        editorScroll = 0;
        pageScroll = 0;
        guideRebuildWidgets();
    }

    /** Selects actual installed metadata, never a synthetic Extension card. */
    public void e2eSelectExtension(String extensionId) {
        requireE2eControls();
        if (extensionProjection().installed().stream().noneMatch(value -> value.id().equals(extensionId)))
            throw new IllegalArgumentException("E2E Extension is not installed");
        e2eOpenExtensions();
        extensionTab = ExtensionTab.INSTALLED;
        selectedExtensionId = extensionId;
        narrowExtensionDetail = true;
        guideRebuildWidgets();
    }

    /** Moves an existing settings page to its real measured bottom. */
    public void e2eScrollPageBottom() {
        requireE2eControls();
        if (layout == null) throw new IllegalStateException("E2E settings layout is not ready");
        if (section == SettingsSection.MODELS) {
            editorScroll = Math.max(0, modelEditorContentHeight - layout.editor().height() + 16);
        } else {
            pageScroll = Math.max(0, pageContentHeight - layout.content().height() + 18);
        }
        guideRebuildWidgets();
    }

    /** Screenshot-harness navigation that exercises the real display-settings save path. */
    public void e2eOpenGeneral(String assistantDisplayName) {
        requireE2eControls();
        Objects.requireNonNull(assistantDisplayName, "assistantDisplayName");
        captureDraft();
        section = SettingsSection.GENERAL;
        assistantNameDraft = assistantDisplayName;
        pageScroll = 0;
        pageContentHeight = 0;
        accept(service.saveDisplay(snapshot.display().withAssistantName(assistantDisplayName)));
        guideRebuildWidgets();
    }

    /** Screenshot-harness navigation for the player-facing About page. */
    public void e2eOpenAbout() {
        requireE2eControls();
        captureDraft();
        section = SettingsSection.ABOUT;
        pageScroll = 0;
        pageContentHeight = 0;
        guideRebuildWidgets();
    }

    /** Positive pixels move the Tool detail down; intended for retained screenshot coverage. */
    public void e2eScrollExtensionDetails(int pixels) {
        requireE2eControls();
        if (section != SettingsSection.EXTENSIONS || layout == null) {
            throw new IllegalStateException("E2E Extension details are not open");
        }
        int maximum = Math.max(0, pageContentHeight - layout.content().height() + 18);
        pageScroll = net.minecraft.util.Mth.clamp(pageScroll + pixels, 0, maximum);
        guideRebuildWidgets();
    }

    static boolean e2eControlsEnabled() {
        return Boolean.getBoolean(GuideClientE2EConfig.ENABLED);
    }

    private static void requireE2eControls() {
        if (!e2eControlsEnabled()) {
            throw new IllegalStateException("OpenAllay E2E controls are disabled");
        }
    }

    static Projection project(ClientSettingsSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<ModelSettingsProjection.ModelCard> cards =
                ModelSettingsProjection.from(snapshot.models(), snapshot.serverModel()).models();
        return new Projection(
                SettingsSection.topLevel(),
                cards,
                GeneralSettingsProjection.from(snapshot.display()),
                UiSettingsProjection.from(snapshot.display()),
                RecipeSettingsProjection.from(
                        snapshot.recipes(),
                        snapshot.recipes().config(),
                        snapshot.display().debugMode()),
                SkillSettingsProjection.from(
                        snapshot.skills(),
                        snapshot.skillCommunity(),
                        RequirementSettingsEnvironment.from(snapshot),
                        snapshot.display().debugMode()),
                ExtensionSettingsProjection.from(
                        snapshot.extensions(),
                        snapshot.experimentalCommands(),
                        snapshot.unrestrictedJavascript(),
                        RequirementSettingsEnvironment.from(snapshot),
                        snapshot.display().debugMode()),
                HistorySettingsProjection.from(
                        snapshot.history(),
                        snapshot.display().debugMode(),
                        snapshot.operation()),
                DiagnosticsSettingsProjection.from(snapshot.diagnostics()),
                snapshot.operation(),
                snapshot.notice());
    }

    @dev.openallay.value.ValueType(Projection.ValueSchemaProvider.class)
static final class Projection {
    private final List<SettingsSection> sections;
    private final List<ModelSettingsProjection.ModelCard> models;
    private final GeneralSettingsProjection general;
    private final UiSettingsProjection ui;
    private final RecipeSettingsProjection recipes;
    private final SkillSettingsProjection skills;
    private final ExtensionSettingsProjection extensions;
    private final HistorySettingsProjection history;
    private final DiagnosticsSettingsProjection diagnostics;
    private final SettingsOperation operation;
    private final SettingsNotice notice;
    Projection(List<SettingsSection> sections, List<ModelSettingsProjection.ModelCard> models, GeneralSettingsProjection general, UiSettingsProjection ui, RecipeSettingsProjection recipes, SkillSettingsProjection skills, ExtensionSettingsProjection extensions, HistorySettingsProjection history, DiagnosticsSettingsProjection diagnostics, SettingsOperation operation, SettingsNotice notice) {

            sections = List.copyOf(sections);
            models = List.copyOf(models);
            Objects.requireNonNull(general, "general");
            Objects.requireNonNull(ui, "ui");
            Objects.requireNonNull(recipes, "recipes");
            Objects.requireNonNull(skills, "skills");
            Objects.requireNonNull(extensions, "extensions");
            Objects.requireNonNull(history, "history");
            Objects.requireNonNull(diagnostics, "diagnostics");

        this.sections = sections;
        this.models = models;
        this.general = general;
        this.ui = ui;
        this.recipes = recipes;
        this.skills = skills;
        this.extensions = extensions;
        this.history = history;
        this.diagnostics = diagnostics;
        this.operation = operation;
        this.notice = notice;
    }
    public List<SettingsSection> sections() { return sections; }
    public List<ModelSettingsProjection.ModelCard> models() { return models; }
    public GeneralSettingsProjection general() { return general; }
    public UiSettingsProjection ui() { return ui; }
    public RecipeSettingsProjection recipes() { return recipes; }
    public SkillSettingsProjection skills() { return skills; }
    public ExtensionSettingsProjection extensions() { return extensions; }
    public HistorySettingsProjection history() { return history; }
    public DiagnosticsSettingsProjection diagnostics() { return diagnostics; }
    public SettingsOperation operation() { return operation; }
    public SettingsNotice notice() { return notice; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Projection)) return false;
        Projection that = (Projection) other;
        return java.util.Objects.equals(sections, that.sections) && java.util.Objects.equals(models, that.models) && java.util.Objects.equals(general, that.general) && java.util.Objects.equals(ui, that.ui) && java.util.Objects.equals(recipes, that.recipes) && java.util.Objects.equals(skills, that.skills) && java.util.Objects.equals(extensions, that.extensions) && java.util.Objects.equals(history, that.history) && java.util.Objects.equals(diagnostics, that.diagnostics) && java.util.Objects.equals(operation, that.operation) && java.util.Objects.equals(notice, that.notice);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(sections);
        hash = 31 * hash + java.util.Objects.hashCode(models);
        hash = 31 * hash + java.util.Objects.hashCode(general);
        hash = 31 * hash + java.util.Objects.hashCode(ui);
        hash = 31 * hash + java.util.Objects.hashCode(recipes);
        hash = 31 * hash + java.util.Objects.hashCode(skills);
        hash = 31 * hash + java.util.Objects.hashCode(extensions);
        hash = 31 * hash + java.util.Objects.hashCode(history);
        hash = 31 * hash + java.util.Objects.hashCode(diagnostics);
        hash = 31 * hash + java.util.Objects.hashCode(operation);
        hash = 31 * hash + java.util.Objects.hashCode(notice);
        return hash;
    }
    @Override public String toString() { return "Projection[sections=" + sections + ", models=" + models + ", general=" + general + ", ui=" + ui + ", recipes=" + recipes + ", skills=" + skills + ", extensions=" + extensions + ", history=" + history + ", diagnostics=" + diagnostics + ", operation=" + operation + ", notice=" + notice + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Projection> schema() {
            return new dev.openallay.value.ValueSchema<>(Projection.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Projection>>asList(new dev.openallay.value.ValueSchema.Component<>(Projection.class, "sections", Projection::sections), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "models", Projection::models), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "general", Projection::general), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "ui", Projection::ui), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "recipes", Projection::recipes), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "skills", Projection::skills), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "extensions", Projection::extensions), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "history", Projection::history), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "diagnostics", Projection::diagnostics), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "operation", Projection::operation), new dev.openallay.value.ValueSchema.Component<>(Projection.class, "notice", Projection::notice)), arguments -> new Projection((List) arguments[0], (List) arguments[1], (GeneralSettingsProjection) arguments[2], (UiSettingsProjection) arguments[3], (RecipeSettingsProjection) arguments[4], (SkillSettingsProjection) arguments[5], (ExtensionSettingsProjection) arguments[6], (HistorySettingsProjection) arguments[7], (DiagnosticsSettingsProjection) arguments[8], (SettingsOperation) arguments[9], (SettingsNotice) arguments[10]));
        }
    }
}

    @dev.openallay.value.ValueType(Action.ValueSchemaProvider.class)
private static final class Action {
    private final String translationKey;
    private final Runnable action;
    private Action(String translationKey, Runnable action) {
        this.translationKey = translationKey;
        this.action = action;
    }
    public String translationKey() { return translationKey; }
    public Runnable action() { return action; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Action)) return false;
        Action that = (Action) other;
        return java.util.Objects.equals(translationKey, that.translationKey) && java.util.Objects.equals(action, that.action);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(translationKey);
        hash = 31 * hash + java.util.Objects.hashCode(action);
        return hash;
    }
    @Override public String toString() { return "Action[translationKey=" + translationKey + ", action=" + action + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Action> schema() {
            return new dev.openallay.value.ValueSchema<>(Action.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Action>>asList(new dev.openallay.value.ValueSchema.Component<>(Action.class, "translationKey", Action::translationKey), new dev.openallay.value.ValueSchema.Component<>(Action.class, "action", Action::action)), arguments -> new Action((String) arguments[0], (Runnable) arguments[1]));
        }
    }
}

    @dev.openallay.value.ValueType(ContributionLine.ValueSchemaProvider.class)
private static final class ContributionLine {
    private final String labelKey;
    private final List<String> values;
    private ContributionLine(String labelKey, List<String> values) {

            values = List.copyOf(values);

        this.labelKey = labelKey;
        this.values = values;
    }
    public String labelKey() { return labelKey; }
    public List<String> values() { return values; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ContributionLine)) return false;
        ContributionLine that = (ContributionLine) other;
        return java.util.Objects.equals(labelKey, that.labelKey) && java.util.Objects.equals(values, that.values);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(labelKey);
        hash = 31 * hash + java.util.Objects.hashCode(values);
        return hash;
    }
    @Override public String toString() { return "ContributionLine[labelKey=" + labelKey + ", values=" + values + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ContributionLine> schema() {
            return new dev.openallay.value.ValueSchema<>(ContributionLine.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ContributionLine>>asList(new dev.openallay.value.ValueSchema.Component<>(ContributionLine.class, "labelKey", ContributionLine::labelKey), new dev.openallay.value.ValueSchema.Component<>(ContributionLine.class, "values", ContributionLine::values)), arguments -> new ContributionLine((String) arguments[0], (List) arguments[1]));
        }
    }
}

    private enum SkillTab {
        INSTALLED,
        COMMUNITY
    }

    private enum ExtensionTab {
        INSTALLED,
        COMMUNITY
    }

    private enum Confirmation {
        NONE,
        RELOAD,
        DELETE,
        TEST_CONNECTION
    }

    private static final class PasswordEditBox extends GuideNativeEditBox {
        private final Component narration;

        private PasswordEditBox(
                net.minecraft.client.gui.Font font,
                int x,
                int y,
                int width,
                int height,
                Component narration) {
            super(font, x, y, width, height, narration);
            this.narration = narration;
            formatGuideText((text, offset) -> GuideNativeFont.plain("•".repeat(text.length())));
        }

        @Override
        public boolean guideKeyPressed(dev.openallay.client.gui.GuideInputKey event) {
            if (event.isCopy() || event.isCut()) {
                return true;
            }
            return super.guideKeyPressed(event);
        }

        @Override
        protected Component guideNarrationMessage() {
            return MinecraftComponents.copy(narration);
        }
    }
}
