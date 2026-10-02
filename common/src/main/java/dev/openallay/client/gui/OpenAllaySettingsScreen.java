package dev.openallay.client.gui;

import dev.openallay.client.gui.settings.DiagnosticsSettingsProjection;
import dev.openallay.client.gui.settings.ExtensionSettingsProjection;
import dev.openallay.client.gui.settings.GeneralSettingsProjection;
import dev.openallay.client.gui.settings.UiSettingsProjection;
import dev.openallay.client.gui.settings.UiSettingsDraft;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Native settings shell and model-profile editor backed only by ClientSettingsService. */
public final class OpenAllaySettingsScreen extends Screen {
    private static final int BACKGROUND = 0xE00B0D12;
    private static final int PANEL = 0xE0181B22;
    private static final int PANEL_ALT = 0xE0242933;
    private static final int ACCENT = 0xFF72D5C4;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFFA9B3BE;
    private static final int ERROR = 0xFFFF7D7D;
    private static final String REPOSITORY_URL = "https://github.com/nkanf-dev/OpenAllay";
    private static final Identifier ABOUT_BANNER = Identifier.fromNamespaceAndPath(
            "openallay", "textures/gui/about_banner.png");

    private final ClientSettingsService service;
    private final Runnable returnToGuide;
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
    private MultiLineEditBox skillEditor;
    private EditBox skillImportPath;
    private String selectedExtensionId;
    private ExtensionTab extensionTab = ExtensionTab.INSTALLED;
    private boolean extensionCommunityRefreshAttempted;
    private boolean narrowExtensionDetail;
    private String extensionImportPathDraft = "";
    private EditBox extensionImportPath;
    private boolean openingRequirementReview;
    private int skillDetailScroll;
    private int skillDetailContentHeight;
    private int pageScroll;
    private int pageContentHeight;
    private ClientSettingsService.HistoryConfirmationToken historyConfirmation;
    private EditBox id;
    private EditBox displayName;
    private EditBox baseUrl;
    private EditBox model;
    private PasswordEditBox apiKey;
    private String pendingApiKey = "";
    private EditBox contextWindow;
    private EditBox maxOutput;
    private EditBox connectTimeout;
    private EditBox requestTimeout;
    private EditBox assistantName;
    private String assistantNameDraft;
    private List<String> catalogModelIds = List.of();
    private boolean modelCatalogOpen;
    private int modelCatalogPage;
    private long modelCatalogGeneration;
    private final UiSettingsDraft uiDraft;
    private UiSettingsProjection.Group uiGroup = UiSettingsProjection.Group.FULLSCREEN;
    private int uiScroll;
    private int uiContentHeight;
    private int navigationScroll;
    private boolean sectionMenuOpen;
    private UiActions uiActions;

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

    public OpenAllaySettingsScreen(
            ClientSettingsService service,
            Runnable returnToGuide) {
        super(Component.translatable("screen.openallay.settings.title"));
        this.service = Objects.requireNonNull(service, "service");
        this.returnToGuide = Objects.requireNonNull(returnToGuide, "returnToGuide");
        this.snapshot = service.snapshot();
        assistantNameDraft = snapshot.display().assistantName();
        uiDraft = new UiSettingsDraft(snapshot.display());
        selectedSkillName = snapshot.skills().skills().isEmpty()
                ? null
                : snapshot.skills().skills().getFirst().metadata().name();
        selectedCommunitySkillId = snapshot.skillCommunity().packages().isEmpty()
                ? null
                : snapshot.skillCommunity().packages().getFirst().id();
        selectedExtensionId = extensionProjection().installed().isEmpty()
                ? null
                : extensionProjection().installed().getFirst().id();
        select(snapshot.models().config().defaultProfileId());
    }

    @Override
    protected void init() {
        layout = SettingsLayout.calculate(width, height, section);
        addHeaderActions();
        if (sectionMenuOpen) {
            addSectionMenu();
            return;
        }
        if (layout.wide()) {
            addSectionNavigation();
        }
        if (section == SettingsSection.MODELS) {
            addModelsPage();
        } else if (section == SettingsSection.EXTENSIONS) {
            addExtensionsPage();
        } else if (section == SettingsSection.SKILLS) {
            addSkillsPage();
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
    }

    @Override
    public void added() {
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
                if (!selectedServerModel) {
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
                assistantNameDraft = next.display().assistantName();
                confirmation = Confirmation.NONE;
            }
            if (!previous.skills().equals(next.skills())) {
                if (selectedSkillName == null || next.skills().find(selectedSkillName).isEmpty()) {
                    selectedSkillName = next.skills().skills().isEmpty()
                            ? null
                            : next.skills().skills().getFirst().metadata().name();
                }
                skillEditing = false;
                skillDraftMarkdown = "";
            }
            SkillSettingsProjection.Community community = skillProjection().community();
            if (selectedCommunitySkillId == null
                    || community.find(selectedCommunitySkillId).isEmpty()) {
                selectedCommunitySkillId = community.packages().isEmpty()
                        ? null
                        : community.packages().getFirst().id();
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
                selectedExtensionId = cards.isEmpty() ? null : cards.getFirst().id();
            }
            if (layout != null) {
                // Background history/source publications must not rebuild a dragged UI slider.
                if (section == SettingsSection.UI && !sectionMenuOpen) {
                    updateUiApplyButton();
                } else {
                    rebuildWidgets();
                    maybeRefreshVisibleCommunity();
                }
            }
        });
    }

    @Override
    public void removed() {
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
    protected void repositionElements() {
        captureDraft();
        rebuildWidgets();
    }

    @Override
    public void onClose() {
        returnToGuide.run();
    }

    @Override
    public void tick() {
        super.tick();
        service.refreshRuntimeState();
        service.snapshot().requirementReview().ifPresent(review -> {
            captureDraft();
            openingRequirementReview = true;
            try {
                minecraft.setScreenAndShow(new RequirementReviewScreen(service, this, review));
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
    public boolean mouseScrolled(
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
                rebuildWidgets();
            }
            return true;
        }
        if (section == SettingsSection.UI && layout.editor().contains(mouseX, mouseY)) {
            int maximum = Math.max(0, uiContentHeight - Math.max(1,
                    layout.editor().height() - uiControlsInset()));
            int next = net.minecraft.util.Mth.clamp(uiScroll - (int) Math.round(scrollY * 24), 0, maximum);
            if (next != uiScroll) {
                uiScroll = next;
                rebuildWidgets();
            }
            return true;
        }
        if (section == SettingsSection.MODELS && layout.editor().contains(mouseX, mouseY)) {
            if (modelCatalogOpen) {
                int pageSize = modelCatalogPageSize();
                int pages = Math.max(1, (catalogModelIds.size() + pageSize - 1) / pageSize);
                modelCatalogPage = net.minecraft.util.Mth.clamp(
                        modelCatalogPage - (int) Math.signum(scrollY), 0, pages - 1);
                rebuildWidgets();
                return true;
            }
            captureDraft();
            int viewport = Math.max(1, layout.editor().height() - 38);
            int maximum = Math.max(0, modelEditorContentHeight - viewport);
            editorScroll = net.minecraft.util.Mth.clamp(
                    editorScroll - (int) Math.round(scrollY * 22), 0, maximum);
            rebuildWidgets();
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
                rebuildWidgets();
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
                if (section == SettingsSection.EXTENSIONS) rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        panel(graphics, layout.header(), PANEL);
        panel(graphics, layout.content(), PANEL);
        panel(graphics, layout.footer(), PANEL_ALT);
        graphics.text(font, title, layout.header().x() + 8, layout.header().y() + 9, TEXT, false);
        if (layout.wide()) {
            panel(graphics, layout.navigation(), PANEL_ALT);
        }
        if (sectionMenuOpen) {
            renderSectionMenu(graphics);
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
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void addHeaderActions() {
        int y = layout.header().y() + 4;
        if (layout.showBack()) {
            int backX = layout.header().right() - 62;
            addRenderableWidget(OpenAllayButton.create(
                            Component.translatable("screen.openallay.settings.back"),
                            ignored -> backOrClose())
                    .bounds(backX, y, 56, 20)
                    .build());
            int sectionX = layout.header().x() + 90;
            addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(section.translationKey()),
                            ignored -> {
                                sectionMenuOpen = !sectionMenuOpen;
                                navigationScroll = 0;
                                rebuildWidgets();
                            })
                    .bounds(sectionX, y, Math.max(50, backX - sectionX - 4), 20)
                    .build());
        }
    }

    private void addSectionNavigation() {
        int x = layout.navigation().x() + 6;
        int y = layout.navigation().y() + 8 - navigationScroll;
        int buttonWidth = layout.navigation().width() - 12;
        for (SettingsSection candidate : SettingsSection.topLevel()) {
            Button button = addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(candidate.translationKey()),
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
    public boolean keyPressed(KeyEvent event) {
        if (sectionMenuOpen && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            sectionMenuOpen = false;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(event);
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
            addRenderableWidget(OpenAllayButton.create(Component.translatable(candidate.translationKey()),
                            ignored -> switchSection(candidate))
                    .selected(candidate == section)
                    .bounds(area.x() + 6, area.y() + 4 + (index - navigationScroll) * 24,
                            area.width() - 12, 20).build());
        }
        Button previous = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.navigation.previous"), ignored -> {
                            navigationScroll = Math.max(0, navigationScroll - rows);
                            rebuildWidgets();
                        }).bounds(area.x() + 6, area.bottom() - 22, (area.width() - 16) / 2, 20).build());
        previous.active = navigationScroll > 0;
        Button next = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.navigation.next"), ignored -> {
                            navigationScroll = Math.min(maximum, navigationScroll + rows);
                            rebuildWidgets();
                        }).bounds(area.x() + 10 + (area.width() - 16) / 2, area.bottom() - 22,
                                (area.width() - 16) / 2, 20).build());
        next.active = navigationScroll < maximum;
    }

    private void renderSectionMenu(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("screen.openallay.settings.navigation.choose"),
                layout.footer().x() + 8, layout.footer().y() + 9, MUTED, false);
    }

    private void addUiPage() {
        SettingsLayout.Rect area = layout.editor();
        int tabWidth = (area.width() - 14) / UiSettingsProjection.Group.values().length;
        int index = 0;
        for (UiSettingsProjection.Group group : UiSettingsProjection.Group.values()) {
            addRenderableWidget(OpenAllayButton.create(Component.translatable(group.translationKey()), ignored -> {
                        uiGroup = group;
                        uiScroll = 0;
                        rebuildWidgets();
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
                                full.sessionRailVisible(), full.toolsCollapsed(), full.theme())));
                y += 26;
                uiToggle("session_rail", full.sessionRailVisible(), x, y, w, () ->
                        changeFull(new GuideUiConfig.Fullscreen(full.density(), !full.sessionRailVisible(),
                                full.toolsCollapsed(), full.theme())));
                y += 26;
                uiToggle("tools_fold", full.toolsCollapsed(), x, y, w, () ->
                        changeFull(new GuideUiConfig.Fullscreen(full.density(), full.sessionRailVisible(),
                                !full.toolsCollapsed(), full.theme())));
                y += 26;
                uiButton("theme", enumLabel("theme", full.theme()), x, y, w, () ->
                        changeFull(new GuideUiConfig.Fullscreen(full.density(), full.sessionRailVisible(),
                                full.toolsCollapsed(), full.theme() == GuideUiConfig.Theme.CHARCOAL
                                        ? GuideUiConfig.Theme.MINT : GuideUiConfig.Theme.CHARCOAL)));
                y += 26;
                uiToggle("animations", uiDraft.animationsEnabled(), x, y, w, () -> {
                    uiDraft.previewAnimations(!uiDraft.animationsEnabled());
                    rebuildWidgets();
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
                uiButton("opacity_reset", Component.empty(), x, y, w,
                        () -> changeHud(uiDraft.ui().hud().withBackgroundOpacity(GuideUiConfig.Hud.defaults().backgroundOpacity())));
                y += 26;
                uiToggle("collapsed", hud.collapsed(), x, y, w, () -> changeHud(uiDraft.ui().hud().withCollapsed(!uiDraft.ui().hud().collapsed())));
                y += 26;
                uiSlider("reply_lines", hud.maxReplyLines(), 1, 10, true, x, y, w, value -> {
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
                Button edit = uiButton("edit_hud", Component.empty(), x, y, w, () -> {
                    if (uiActions != null) uiActions.editHud(this, uiDraft.candidate(snapshot.display()), candidate -> {
                        uiDraft.preview(candidate.ui());
                        uiDraft.previewAnimations(candidate.animationsEnabled());
                        applyUi();
                    });
                });
                edit.active = uiActions != null && snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
                if (uiActions == null) edit.setTooltip(Tooltip.create(Component.translatable(
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
                Button preview = uiButton("test_notification", Component.empty(), x, y, w,
                        () -> { if (uiActions != null) uiActions.previewNotification(uiDraft.ui().notifications()); });
                preview.active = uiActions != null;
                if (uiActions == null) preview.setTooltip(Tooltip.create(Component.translatable(
                        "screen.openallay.settings.ui.actions_unavailable")));
                y += 26;
            }
        }
        uiContentHeight = y + uiScroll - (area.y() + uiControlsInset());
    }

    private Button uiButton(String key, Component value, int x, int y, int w, Runnable action) {
        Component label = Component.translatable("screen.openallay.settings.ui." + key);
        if (!value.getString().isBlank()) label = label.copy().append(" · ").append(value);
        Button button = addRenderableWidget(OpenAllayButton.create(label, ignored -> action.run())
                .bounds(x, y, w, 20).build());
        button.visible = uiWidgetVisible(y, 20);
        return button;
    }

    private void uiToggle(String key, boolean enabled, int x, int y, int w, Runnable action) {
        uiButton(key, Component.translatable("screen.openallay.settings.ui." + (enabled ? "on" : "off")),
                x, y, w, action);
    }

    private Component enumLabel(String kind, Enum<?> value) {
        return Component.translatable("screen.openallay.settings.ui." + kind + "."
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
        EditBox field = new EditBox(font, x + w / 2, y, w / 2, 20,
                Component.translatable("screen.openallay.settings.ui." + key));
        field.setValue(Integer.toString(value));
        field.setMaxLength(6);
        field.setResponder(text -> {
            try {
                changed.accept(Integer.parseInt(text));
                localNotice = "";
            } catch (IllegalArgumentException invalid) {
                localNotice = Component.translatable("screen.openallay.settings.ui.invalid_range").getString();
            }
        });
        field.setVisible(uiWidgetVisible(y, 20));
        addRenderableWidget(field);
    }

    private void uiSlider(String key, double value, double minimum, double maximum, boolean integral,
            int x, int y, int w, java.util.function.DoubleConsumer changed) {
        UiSlider slider = new UiSlider(x, y, w, key, value, minimum, maximum, integral, changed);
        slider.visible = uiWidgetVisible(y, 20);
        addRenderableWidget(slider);
    }

    private void changeFull(GuideUiConfig.Fullscreen value) {
        uiDraft.preview(uiDraft.ui().withFullscreen(value));
        rebuildWidgets();
    }

    private void previewHud(GuideUiConfig.Hud value) {
        uiDraft.preview(uiDraft.ui().withHud(value));
        updateUiApplyButton();
    }

    private void changeHud(GuideUiConfig.Hud value) {
        previewHud(value);
        rebuildWidgets();
    }

    private void changeNotifications(GuideUiConfig.Notifications value) {
        uiDraft.preview(uiDraft.ui().withNotifications(value));
        rebuildWidgets();
    }

    private void updateUiApplyButton() {
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
            if (child instanceof Button button && button.getMessage().getString().equals(
                    Component.translatable("screen.openallay.settings.ui.apply").getString())) {
                button.active = uiDraft.dirty() && snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
            }
        }
    }

    private void applyUi() {
        localNotice = "";
        accept(service.saveDisplay(uiDraft.candidate(snapshot.display())));
    }

    private void renderUi(GuiGraphicsExtractor graphics) {
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
            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(x + 4, previewY + 4);
                graphics.pose().scale((float) hud.scale(), (float) hud.scale());
                graphics.fill(0, 0, hud.width(), hud.collapsed() ? 24 : hud.height(), hud.backgroundArgb(0x181B22));
                graphics.text(font, Component.translatable("screen.openallay.settings.ui.preview_title"),
                        6, 6, hud.textArgb(0xE8EDF2), false);
                if (!hud.collapsed()) graphics.text(font, Component.translatable("screen.openallay.settings.ui.preview_reply"),
                        6, 20, hud.textArgb(0xE8EDF2), false);
            } finally {
                graphics.pose().popMatrix();
                graphics.disableScissor();
            }
            for (int index = 0; index < 2; index++) {
                int fieldY = y + 52 + index * 26;
                if (uiWidgetVisible(fieldY, 20)) graphics.text(font, Component.translatable(
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

    private static final class UiSlider extends AbstractSliderButton {
        private final String key;
        private final double minimum;
        private final double maximum;
        private final boolean integral;
        private final java.util.function.DoubleConsumer changed;

        private UiSlider(int x, int y, int width, String key, double initial,
                double minimum, double maximum, boolean integral, java.util.function.DoubleConsumer changed) {
            super(x, y, width, 20, Component.empty(), (initial - minimum) / (maximum - minimum));
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
            String amount = integral ? Long.toString(Math.round(actual()))
                    : String.format(java.util.Locale.ROOT, "%.2f", actual());
            String translationKey = "screen.openallay.settings.ui." + key;
            setMessage(Component.translatable(translationKey).copy().append(" · " + amount));
        }

        @Override
        protected void applyValue() { changed.accept(actual()); }
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
        assistantName = new EditBox(
                font,
                x,
                y,
                Math.max(60, width - saveWidth - 4),
                20,
                Component.translatable(general.assistantNameLabelKey()));
        assistantName.setValue(assistantNameDraft);
        assistantName.setMaxLength(Integer.MAX_VALUE);
        assistantName.setResponder(value -> assistantNameDraft = value);
        assistantName.setVisible(layout.pageWidgetVisible(y, 20));
        addRenderableWidget(assistantName);
        Button saveName = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(
                                "screen.openallay.settings.general.assistant_name.save"),
                        ignored -> saveAssistantName(general))
                .bounds(x + width - saveWidth, y, saveWidth, 20)
                .build());
        saveName.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        saveName.visible = layout.pageWidgetVisible(y, 20);
        Button debug = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(
                                general.debugLabelKey()).copy().append(" · ")
                                .append(Component.translatable(general.debugStatusKey())),
                        ignored -> accept(service.saveDisplay(general.toggleDebug())))
                .bounds(x, y + 34, width, 22)
                .build());
        debug.setTooltip(Tooltip.create(
                Component.translatable(general.debugDescriptionKey())));
        debug.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        debug.visible = layout.pageWidgetVisible(y + 34, 22);
        Button animations = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(
                                general.animationsLabelKey()).copy().append(" · ")
                                .append(Component.translatable(general.animationsStatusKey())),
                        ignored -> accept(service.saveDisplay(general.toggleAnimations())))
                .bounds(x, y + 64, width, 22)
                .build());
        animations.setTooltip(Tooltip.create(
                Component.translatable(general.animationsDescriptionKey())));
        animations.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        animations.visible = layout.pageWidgetVisible(y + 64, 22);
    }

    private void addAboutPage() {
        SettingsLayout.Rect area = layout.editor();
        int width = Math.min(240, Math.max(120, area.width() - 20));
        int y = layout.pageOrigin(pageScroll) + aboutCopyOffset();
        Button copy = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.about.copy_repository"),
                        ignored -> copyRepositoryUrl())
                .bounds(area.x() + 10, y, width, 20)
                .build());
        copy.visible = layout.pageWidgetVisible(y, 20);
    }

    private int aboutRepositoryOffset() {
        int contentWidth = Math.max(100, layout.editor().width() - 20);
        int bannerWidth = Math.min(contentWidth, 512);
        int bannerHeight = Math.max(54, bannerWidth * 9 / 16);
        int descriptionHeight = font.split(
                Component.translatable("screen.openallay.settings.about.description"), contentWidth).size() * 10;
        return 31 + bannerHeight + 12 + descriptionHeight + 8;
    }

    private int aboutCopyOffset() {
        int contentWidth = Math.max(100, layout.editor().width() - 20);
        return aboutRepositoryOffset() + 12 + font.split(
                Component.literal(REPOSITORY_URL), contentWidth).size() * 10 + 6;
    }

    private void saveAssistantName(GeneralSettingsProjection general) {
        try {
            accept(service.saveDisplay(general.renameAssistant(assistantNameDraft)));
        } catch (IllegalArgumentException failure) {
            localNotice = Component.translatable(
                    "screen.openallay.settings.general.assistant_name.invalid").getString();
        }
    }

    private void copyRepositoryUrl() {
        try {
            minecraft.keyboardHandler.setClipboard(REPOSITORY_URL);
            localNotice = Component.translatable(
                    "screen.openallay.settings.about.copy_success").getString();
        } catch (RuntimeException failure) {
            localNotice = Component.translatable(
                    "screen.openallay.settings.about.copy_failed").getString();
        }
    }

    private void addHistoryPage() {
        HistorySettingsProjection history = project(snapshot).history();
        SettingsLayout.Rect area = layout.editor();
        int x = area.x() + 10;
        int y = area.y() + 64 - pageScroll;
        int width = Math.min(360, Math.max(140, area.width() - 20));
        for (HistorySettingsProjection.ActionRow row : history.actions()) {
            Button button = OpenAllayButton.create(
                            historyActionLabel(row),
                            ignored -> activateHistory(row.action()))
                    .bounds(x, y, width, 22)
                    .build();
            button.setTooltip(Tooltip.create(Component.translatable(row.descriptionKey())));
            button.active = row.enabled();
            if (y >= area.y() + 48 && y + 22 <= area.bottom() - 4) {
                addRenderableWidget(button);
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
            Component label = Component.literal(
                    (card.defaultProfile() ? "★ " : "")
                            + (card.origin() == ModelSettingsProjection.Origin.SERVER
                                    ? "☁ "
                                    : "")
                            + card.displayName());
            Button button = addRenderableWidget(OpenAllayButton.create(
                            label,
                            ignored -> selectAndRebuild(card.selectionId()))
                    .selected(card.selectionId().equals(selectedModelSelectionId()))
                    .bounds(x, y, buttonWidth, 22)
                    .build());
            button.setTooltip(Tooltip.create(Component.translatable(
                    "screen.openallay.settings.models.builtin.image_input."
                            + card.imageCapability().capability().encoded())));
            button.active = !card.selectionId().equals(selectedModelSelectionId());
            y += 26;
            if (y > layout.list().bottom() - 50) {
                break;
            }
        }
        addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.models.add"),
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
            Button installedTab = addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(
                                    "screen.openallay.settings.extensions.tab.installed"),
                            ignored -> selectExtensionTab(ExtensionTab.INSTALLED))
                    .selected(extensionTab == ExtensionTab.INSTALLED)
                    .bounds(x, y, tabWidth, 20)
                    .build());
            installedTab.active = extensionTab != ExtensionTab.INSTALLED;
            Button communityTab = addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(
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
                Component label = Component.literal(extension.name()).copy()
                        .append(" · ")
                        .append(Component.translatable(extensionStateKey(extension)));
                Button button = addRenderableWidget(OpenAllayButton.create(label, ignored -> {
                            selectedExtensionId = extension.id();
                            narrowExtensionDetail = true;
                            localNotice = "";
                            rebuildWidgets();
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
            selectedExtension().ifPresent(extension -> addExtensionCapabilityActions(extension, detail));
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
                + wrappedHeight(Component.literal(extension.name()), width, 11)
                + wrappedHeight(Component.literal(extension.summary()), width, 10)
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
                        Component.literal(String.join(", ", values)), width, 10) + 2;
            }
        }
        if (!extension.diagnostic().isBlank()) {
            height += wrappedHeight(Component.literal(extension.diagnostic()), width, 10) + 7;
        }
        if (debugMode && !extension.artifact().isBlank()) {
            height += wrappedHeight(Component.literal(extension.artifact()), width, 10) + 12;
        }
        if (debugMode && !extension.sha256().isBlank()) {
            height += wrappedHeight(Component.literal(extension.sha256()), width, 10) + 12;
        }
        if (!extension.capabilities().isEmpty()) {
            height += extensionCapabilityHeaderHeight(width);
            for (var capability : extension.capabilities()) {
                height += 26 + wrappedHeight(Component.literal(capability.description()), width, 10) + 8;
            }
        }
        return height + 82;
    }

    private int extensionCapabilityHeaderHeight(int width) {
        return wrappedHeight(Component.translatable(
                "screen.openallay.settings.extensions.capabilities.title"), width, 11)
                + 4 + wrappedHeight(Component.translatable(
                        "screen.openallay.settings.extensions.capabilities.description"), width, 10) + 8;
    }

    private int extensionCapabilityActionsY(
            ExtensionSettingsProjection.ExtensionCard extension, SettingsLayout.Rect area, int width) {
        Component name = Component.literal(extension.name()).copy().append(" · ")
                .append(Component.translatable(extensionStateKey(extension)));
        return area.y() + 32 - pageScroll
                + wrappedHeight(name, width, 11) + 4
                + wrappedHeight(Component.literal(extension.summary()), width, 10) + 7
                + extensionCapabilityHeaderHeight(width);
    }

    private void addExtensionCapabilityActions(
            ExtensionSettingsProjection.ExtensionCard extension, SettingsLayout.Rect area) {
        int x = area.x() + 10;
        int width = Math.max(80, area.width() - 20);
        int y = extensionCapabilityActionsY(extension, area, width);
        int bottomInset = extensionTab == ExtensionTab.COMMUNITY || extension.installable() ? 114 : 62;
        for (var capability : extension.capabilities()) {
            Button toggle = addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(capability.enabled()
                                            ? "screen.openallay.settings.extensions.capabilities.enabled"
                                            : "screen.openallay.settings.extensions.capabilities.disabled",
                                    capability.name()),
                            ignored -> accept(service.saveExtensionCapability(
                                    extension.id(), capability.id(), !capability.enabled())))
                    .selected(capability.enabled())
                    .tooltip(Tooltip.create(Component.literal(capability.description())))
                    .bounds(x, y, width, 20).build());
            toggle.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE
                    && (extension.state() == dev.openallay.settings.extension.ExtensionSettingsView.State.ACTIVE
                            || extension.state() == dev.openallay.settings.extension.ExtensionSettingsView.State.RESTART_REQUIRED);
            toggle.visible = y >= area.y() + 28 && y + 20 <= area.bottom() - bottomInset;
            y += 26 + wrappedHeight(Component.literal(capability.description()), width, 10) + 8;
        }
    }

    private int renderExtensionCapabilities(
            GuiGraphicsExtractor graphics, ExtensionSettingsProjection.ExtensionCard extension,
            int x, int y, int width) {
        if (extension.capabilities().isEmpty()) return y;
        y = renderWrapped(graphics, Component.translatable(
                "screen.openallay.settings.extensions.capabilities.title"), x, y, width, ACCENT, 11);
        y = renderWrapped(graphics, Component.translatable(
                "screen.openallay.settings.extensions.capabilities.description"), x, y + 4, width, MUTED, 10);
        y += 8;
        for (var capability : extension.capabilities()) {
            // The native switch occupies the same scroll position in addExtensionCapabilityActions.
            y = renderWrapped(graphics, Component.literal(capability.description()),
                    x, y + 26, width, MUTED, 10) + 8;
        }
        return y;
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
            Button install = addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(selected.updateAvailable()
                                    ? "screen.openallay.settings.extensions.community.update"
                                    : "screen.openallay.settings.extensions.community.install"),
                            ignored -> accept(service.installCommunityExtension(selected.id())))
                    .bounds(x, y, half, 20)
                    .build());
            install.active = idle;
        }
        Button refresh = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(
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

        extensionImportPath = new EditBox(
                font,
                x,
                y + 26,
                Math.max(50, width - 72),
                20,
                Component.translatable(
                        "screen.openallay.settings.extensions.community.import_path"));
        extensionImportPath.setHint(Component.translatable(
                "screen.openallay.settings.extensions.community.import_hint"));
        extensionImportPath.setMaxLength(2048);
        extensionImportPath.setValue(extensionImportPathDraft);
        extensionImportPath.active = idle;
        addRenderableWidget(extensionImportPath);
        Button importButton = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(
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
        Button commands = OpenAllayButton.create(
                        Component.translatable(
                                projection.experimentalCommands()
                                        ? "screen.openallay.settings.extensions.commands.disable"
                                        : "screen.openallay.settings.extensions.commands.enable"),
                        ignored -> accept(service.saveExperimentalCommands(
                                !projection.experimentalCommands())))
                .bounds(x, y, width, 20)
                .build();
        commands.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        commands.setTooltip(Tooltip.create(Component.translatable(
                "screen.openallay.settings.extensions.commands.description")));
        addRenderableWidget(commands);
        Button unrestricted = OpenAllayButton.create(
                        Component.translatable(projection.unrestrictedJavascript()
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
        unrestricted.setTooltip(Tooltip.create(Component.translatable(
                "screen.openallay.settings.extensions.unrestricted.warning")));
        addRenderableWidget(unrestricted);
    }

    private void confirmUnrestrictedJavascript() {
        minecraft.setScreenAndShow(new ConfirmScreen(
                confirmed -> {
                    minecraft.setScreenAndShow(this);
                    if (confirmed) accept(service.saveUnrestrictedJavascript(true));
                },
                Component.translatable(RequirementSettingsProjection.PREFIX + "confirm_enable"),
                Component.translatable("screen.openallay.settings.extensions.unrestricted.warning")
                        .append("\n\n")
                        .append(Component.translatable(
                                RequirementSettingsProjection.PREFIX + "unrestricted_confirm")),
                Component.translatable(RequirementSettingsProjection.PREFIX + "confirm_enable"),
                Component.translatable(RequirementSettingsProjection.PREFIX + "cancel")));
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
            Button installedTab = addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(
                                    "screen.openallay.settings.skills.tab.installed"),
                            ignored -> selectSkillTab(SkillTab.INSTALLED))
                    .selected(skillTab == SkillTab.INSTALLED)
                    .bounds(x, y, tabWidth, 20)
                    .build());
            installedTab.active = skillTab != SkillTab.INSTALLED;
            Button communityTab = addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(
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
                Component label = Component.literal(skill.name()).copy().append(" · ")
                        .append(Component.translatable(skill.localOverride()
                                ? "screen.openallay.settings.skills.local"
                                : "screen.openallay.settings.skills.bundled"));
                Button button = addRenderableWidget(OpenAllayButton.create(label, ignored -> {
                            selectedSkillName = skill.name();
                            skillDetailScroll = 0;
                            narrowSkillDetail = true;
                            skillEditing = false;
                            skillDraftMarkdown = "";
                            rebuildWidgets();
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
                Component label = Component.literal(skill.displayName()).copy().append(" · ")
                        .append(Component.translatable(skillStateKey(skill.state())));
                Button button = addRenderableWidget(OpenAllayButton.create(label, ignored -> {
                            selectedCommunitySkillId = skill.id();
                            skillDetailScroll = 0;
                            narrowSkillDetail = true;
                            rebuildWidgets();
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
                skillEditor = MultiLineEditBox.builder()
                        .setX(editorX)
                        .setY(editorY)
                        .setPlaceholder(Component.translatable(
                                "screen.openallay.settings.skills.editor_placeholder"))
                        .build(
                                font,
                                editorWidth,
                                Math.max(70, area.bottom() - editorY - 34),
                                Component.translatable("screen.openallay.settings.skills.editor"));
                skillEditor.setValue(skillDraftMarkdown, true);
                skillEditor.setValueListener(value -> skillDraftMarkdown = value);
                addRenderableWidget(skillEditor);
                addRenderableWidget(OpenAllayButton.create(
                                Component.translatable("screen.openallay.settings.save"),
                                ignored -> saveSkillOverride())
                        .bounds(editorX, area.bottom() - 26, Math.min(120, editorWidth), 20)
                        .build());
                addRenderableWidget(OpenAllayButton.create(
                                Component.translatable("screen.openallay.settings.cancel"),
                                ignored -> {
                                    skillEditing = false;
                                    skillDraftMarkdown = "";
                                    rebuildWidgets();
                                })
                        .bounds(
                                editorX + Math.min(120, editorWidth) + 4,
                                area.bottom() - 26,
                                Math.min(100, Math.max(50, editorWidth - 124)),
                                20)
                        .build());
            } else {
                addRenderableWidget(OpenAllayButton.create(
                                Component.translatable(skill.createsOverrideOnSave()
                                        ? "screen.openallay.settings.skills.create_override"
                                        : "screen.openallay.settings.skills.edit_override"),
                                ignored -> {
                                    skillEditing = true;
                                    skillDraftMarkdown = skill.markdown();
                                    rebuildWidgets();
                                })
                        .bounds(editorX, area.bottom() - 26, Math.min(160, editorWidth), 20)
                        .build());
                if (skill.canDeleteOverride()) {
                    addRenderableWidget(OpenAllayButton.create(
                                    Component.translatable(
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
            Button install = addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(
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
        Button refresh = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(
                                "screen.openallay.settings.skills.community.refresh"),
                        ignored -> accept(service.refreshSkillCommunity()))
                .bounds(refreshX, actionY, refreshWidth, 20)
                .build());
        refresh.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;

        int importWidth = Math.min(84, Math.max(56, width / 4));
        skillImportPath = new EditBox(
                font,
                x,
                area.bottom() - 27,
                Math.max(50, width - importWidth - 4),
                20,
                Component.translatable(
                        "screen.openallay.settings.skills.community.import_path"));
        skillImportPath.setValue(skillImportPathDraft);
        skillImportPath.setMaxLength(Integer.MAX_VALUE);
        skillImportPath.setResponder(value -> skillImportPathDraft = value);
        skillImportPath.setHint(Component.translatable(
                "screen.openallay.settings.skills.community.import_hint"));
        addRenderableWidget(skillImportPath);
        Button importButton = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(
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
        addRenderableWidget(OpenAllayButton.create(protocolLabel(), ignored -> cycleProtocol())
                .bounds(x, y, toggleWidth, 20).build());
        addRenderableWidget(OpenAllayButton.create(enabledLabel(), ignored -> toggleEnabled())
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
        Button fetch = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.models.fetch"),
                        ignored -> fetchModelCatalog())
                .bounds(inputX + modelWidth + 3, y, fetchWidth, 18)
                .build());
        fetch.active = snapshot.operation().kind() == SettingsOperation.Kind.IDLE;
        fetch.visible = model.visible;
        Button choose = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.models.choose"),
                        ignored -> {
                            captureDraft();
                            modelCatalogOpen = true;
                            modelCatalogPage = 0;
                            rebuildWidgets();
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
        contextWindow.setTooltip(Tooltip.create(Component.translatable(
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
        maxOutput.setTooltip(Tooltip.create(Component.translatable(
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
        Button effort = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(reasoning.selectedLabelKey()), ignored -> {
                            captureDraft();
                            draft = draft.withReasoningEffort(reasoningSettings().next());
                            confirmation = Confirmation.NONE;
                            rebuildWidgets();
                        })
                .bounds(inputX, y, inputWidth, 18).build());
        effort.setTooltip(Tooltip.create(reasoningExplanation(reasoning)));
        effort.visible = y >= area.y() + 30 && y + 18 <= area.bottom();
        y += 22;
        ModelImageSettingsProjection imageInput = new ModelImageSettingsProjection(
                draft.imageInputCapabilityOverride());
        Button imageChoice = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(imageInput.selectedLabelKey()), ignored -> {
                            captureDraft();
                            draft = draft.withImageInputCapabilityOverride(
                                    new ModelImageSettingsProjection(draft.imageInputCapabilityOverride()).next());
                            confirmation = Confirmation.NONE;
                            rebuildWidgets();
                        })
                .bounds(inputX, y, inputWidth, 18).build());
        imageChoice.setTooltip(Tooltip.create(Component.translatable(imageInput.explanationKey())));
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

    private EditBox field(int x, int y, int width, String narrationKey, String value) {
        EditBox field = new EditBox(
                font, x, y, width, 18, Component.translatable(narrationKey));
        field.setValue(value == null ? "" : value);
        field.setMaxLength(2048);
        field.setResponder(ignored -> confirmation = Confirmation.NONE);
        field.setVisible(y >= layout.editor().y() + 30
                && y + 18 <= layout.editor().bottom());
        return addRenderableWidget(field);
    }

    private PasswordEditBox passwordField(int x, int y, int width) {
        PasswordEditBox field = new PasswordEditBox(
                font,
                x,
                y,
                width,
                18,
                Component.translatable("screen.openallay.settings.models.api_key"));
        field.setValue(pendingApiKey);
        boolean saved = selectedView().map(
                        ModelProfileSettingsView.Profile::credentialStoredLocally)
                .orElse(false);
        boolean environment = selectedView().map(
                        ModelProfileSettingsView.Profile::credentialFromEnvironment)
                .orElse(false);
        field.setHint(Component.translatable(saved
                ? "screen.openallay.settings.models.api_key_saved_hint"
                : environment
                        ? "screen.openallay.settings.models.api_key_environment_hint"
                        : "screen.openallay.settings.models.api_key_enter_hint"));
        field.setMaxLength(4096);
        field.setResponder(value -> {
            pendingApiKey = value;
            confirmation = Confirmation.NONE;
            invalidateModelCatalog();
        });
        field.setVisible(y >= layout.editor().y() + 30
                && y + 18 <= layout.editor().bottom());
        return addRenderableWidget(field);
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
            Button button = addRenderableWidget(OpenAllayButton.create(
                            Component.translatable(action.translationKey()),
                            ignored -> action.action().run())
                    .bounds(x, y, buttonWidth, 20)
                    .build());
            button.active = actionEnabled(action.translationKey());
        }
    }

    private List<Action> footerActions() {
        return switch (section) {
            case MODELS -> selectedServerModel
                    ? List.of(new Action("screen.openallay.settings.done", this::onClose))
                    : modelCatalogOpen ? List.of(
                    new Action("screen.openallay.settings.models.catalog_close", () -> {
                        modelCatalogOpen = false;
                        rebuildWidgets();
                    }),
                    new Action("screen.openallay.settings.done", this::onClose)) : List.of(
                    new Action("screen.openallay.settings.save", this::saveCurrent),
                    new Action(reloadKey(), this::reloadCurrent),
                    new Action(deleteKey(), this::delete),
                    new Action("screen.openallay.settings.models.default", this::makeDefault),
                    new Action(testKey(), this::testConnection),
                    new Action("screen.openallay.settings.cancel", this::cancel),
                    new Action("screen.openallay.settings.models.refresh", this::refreshMetadata),
                    new Action("screen.openallay.settings.done", this::onClose));
            case EXTENSIONS -> List.of(
                    new Action("screen.openallay.settings.done", this::onClose));
            case SKILLS -> List.of(
                    new Action(
                            "screen.openallay.settings.reload",
                            () -> accept(service.reloadSkills(true))),
                    new Action("screen.openallay.settings.done", this::onClose));
            case UI -> List.of(
                    new Action("screen.openallay.settings.ui.apply", this::applyUi),
                    new Action("screen.openallay.settings.ui.reset", () -> {
                        uiDraft.reset(uiGroup);
                        rebuildWidgets();
                    }),
                    new Action("screen.openallay.settings.ui.cancel", () -> {
                        uiDraft.cancel();
                        localNotice = "";
                        rebuildWidgets();
                    }),
                    new Action("screen.openallay.settings.done", this::onClose));
            case GENERAL -> List.of(
                    new Action(
                            "screen.openallay.settings.reload",
                            () -> accept(service.reloadDisplay())),
                    new Action("screen.openallay.settings.done", this::onClose));
            case HISTORY, DIAGNOSTICS, ABOUT ->
                    List.of(new Action("screen.openallay.settings.done", this::onClose));
        };
    }

    private boolean actionEnabled(String key) {
        boolean busy = snapshot.operation().kind() != SettingsOperation.Kind.IDLE;
        if (key.equals("screen.openallay.settings.ui.apply")) return !busy && uiDraft.dirty();
        if (key.equals("screen.openallay.settings.cancel")) {
            return snapshot.operation().kind() == SettingsOperation.Kind.TESTING_CONNECTION
                    || snapshot.operation().kind()
                            == SettingsOperation.Kind.FETCHING_MODEL_CATALOG;
        }
        if (key.equals("screen.openallay.settings.done")) {
            return true;
        }
        return !busy;
    }

    private void renderModels(GuiGraphicsExtractor graphics) {
        if (layout.wide()) {
            graphics.text(
                    font,
                    Component.translatable("screen.openallay.settings.models.profiles"),
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
                    Component.translatable(
                            "screen.openallay.settings.models.catalog_title",
                            catalogModelIds.size()),
                    area.x() + 8,
                    area.y() + 10,
                    ACCENT,
                    false);
            if (catalogModelIds.isEmpty()) {
                graphics.text(font,
                        Component.translatable("screen.openallay.settings.models.catalog_empty"),
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
                graphics.text(font, Component.translatable(label), x, y + 5, MUTED, false);
            }
            y += 22;
        }
        int statusY = y + 3;
        graphics.enableScissor(area.x(), area.y() + 30, area.right(), area.bottom());
        selectedView().ifPresent(profile -> {
            int color = profile.available() ? 0xFF7FC8A9 : 0xFFFFD479;
            Component status = Component.translatable(
                            profile.available()
                                    ? "screen.openallay.settings.models.available"
                                    : "screen.openallay.settings.models.unavailable")
                    .copy().append(" · ")
                    .append(Component.translatable(pendingApiKey.isBlank()
                            ? (profile.credentialStoredLocally()
                                    ? "screen.openallay.settings.models.api_key_saved"
                                    : profile.credentialFromEnvironment()
                                            ? "screen.openallay.settings.models.api_key_environment"
                                            : "screen.openallay.settings.models.api_key_not_set")
                            : "screen.openallay.settings.models.api_key_replace"));
            graphics.text(font, status, x, statusY, color, false);
        });
        int estimateY = statusY + 18;
        for (var wrapped : font.split(reasoningExplanation(reasoningSettings()),
                Math.max(20, area.width() - 16))) {
            graphics.text(font, wrapped, x, estimateY, MUTED, false);
            estimateY += 11;
        }
        estimateY += 3;
        for (BuiltinModelSettingsProjection.Line line : modelEstimates().lines()) {
            Component text = Component.translatable(line.key(), line.arguments().toArray());
            for (var wrapped : font.split(text, Math.max(20, area.width() - 16))) {
                graphics.text(font, wrapped, x, estimateY, MUTED, false);
                estimateY += 11;
            }
            estimateY += 3;
        }
        modelEditorContentHeight = estimateY + editorScroll - area.y() - 30;
        graphics.disableScissor();
    }

    private void renderServerModel(
            GuiGraphicsExtractor graphics, SettingsLayout.Rect area) {
        var server = snapshot.serverModel();
        int x = area.x() + 10;
        int y = area.y() + 12;
        graphics.text(
                font,
                Component.translatable("screen.openallay.settings.models.server_title"),
                x,
                y,
                ACCENT,
                false);
        y += 22;
        graphics.text(
                font,
                Component.translatable(
                        "screen.openallay.settings.models.server_model",
                        server.canonicalModelId()),
                x,
                y,
                TEXT,
                false);
        y += 18;
        graphics.text(
                font,
                Component.translatable(
                        "screen.openallay.settings.models.server_context",
                        server.contextWindowTokens()),
                x,
                y,
                MUTED,
                false);
        y += 18;
        graphics.text(
                font,
                Component.translatable(
                        "screen.openallay.settings.models.server_output",
                        server.maxOutputTokens()),
                x,
                y,
                MUTED,
                false);
        y += 26;
        for (var line : font.split(
                Component.translatable(
                        "screen.openallay.settings.models.server_read_only"),
                Math.max(80, area.width() - 20))) {
            graphics.text(font, line, x, y, MUTED, false);
            y += 10;
        }
    }

    private void renderGeneral(GuiGraphicsExtractor graphics) {
        GeneralSettingsProjection general = project(snapshot).general();
        SettingsLayout.Rect area = layout.editor();
        int origin = layout.pageOrigin(pageScroll);
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        graphics.text(font, Component.translatable(general.titleKey()),
                area.x() + 10, origin + 12, ACCENT, false);
        // Keep the label together with its fully visible input, not over another scrolled control.
        if (layout.pageWidgetVisible(origin + 31, 33)) {
            graphics.text(font, Component.translatable(general.assistantNameLabelKey()),
                    area.x() + 10, origin + 31, MUTED, false);
        }
        int y = origin + 136;
        for (String key : List.of(general.assistantNameDescriptionKey(),
                general.debugDescriptionKey(), general.animationsDescriptionKey())) {
            for (net.minecraft.util.FormattedCharSequence line : font.split(
                    Component.translatable(key), Math.max(80, area.width() - 20))) {
                graphics.text(font, line, area.x() + 10, y, MUTED, false);
                y += 10;
            }
            y += 5;
        }
        pageContentHeight = y - origin;
        graphics.disableScissor();
    }

    private void renderAbout(GuiGraphicsExtractor graphics) {
        SettingsLayout.Rect area = layout.editor();
        int origin = layout.pageOrigin(pageScroll);
        int x = area.x() + 10;
        int contentWidth = Math.max(100, area.width() - 20);
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        graphics.text(font, Component.translatable("screen.openallay.settings.about.title"),
                x, origin + 12, ACCENT, false);
        int bannerWidth = Math.min(contentWidth, 512);
        int bannerHeight = Math.max(54, bannerWidth * 9 / 16);
        int bannerX = x + Math.max(0, (contentWidth - bannerWidth) / 2);
        int bannerY = origin + 31;
        graphics.blit(RenderPipelines.GUI_TEXTURED, ABOUT_BANNER,
                bannerX, bannerY, 0.0F, 0.0F, bannerWidth, bannerHeight,
                1024, 576, 1024, 576);
        int y = bannerY + bannerHeight + 12;
        for (net.minecraft.util.FormattedCharSequence line : font.split(
                Component.translatable("screen.openallay.settings.about.description"), contentWidth)) {
            graphics.text(font, line, x, y, TEXT, false);
            y += 10;
        }
        y += 8;
        graphics.text(font, Component.translatable("screen.openallay.settings.about.repository"),
                x, y, MUTED, false);
        // Wrapping prevents a long URL from escaping the native content pane.
        y += 12;
        for (net.minecraft.util.FormattedCharSequence line : font.split(
                Component.literal(REPOSITORY_URL), contentWidth)) {
            graphics.text(font, line, x, y, ACCENT, false);
            y += 10;
        }
        pageContentHeight = aboutCopyOffset() + 28;
        graphics.disableScissor();
    }

    private void renderHistory(GuiGraphicsExtractor graphics) {
        HistorySettingsProjection history = project(snapshot).history();
        SettingsLayout.Rect area = layout.editor();
        graphics.text(
                font,
                Component.translatable(history.titleKey()),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        Component status = Component.translatable(history.scopeLabelKey())
                .copy().append(" · ")
                .append(Component.translatable(history.statusKey()));
        graphics.text(font, status, area.x() + 10, area.y() + 31, MUTED, false);
        int actionsBottom = area.y() + 64 - pageScroll + history.actions().size() * 30;
        pageContentHeight = Math.max(0, actionsBottom + pageScroll - area.y());
    }

    private void renderDiagnostics(GuiGraphicsExtractor graphics) {
        DiagnosticsSettingsProjection diagnostics = project(snapshot).diagnostics();
        SettingsLayout.Rect area = layout.editor();
        graphics.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        int x = area.x() + 8;
        int width = Math.max(80, area.width() - 16);
        int y = area.y() + 10 - pageScroll;
        y = settingsHeading(
                graphics,
                Component.translatable(diagnostics.titleKey()),
                x,
                y,
                width,
                ACCENT);
        for (DiagnosticsSettingsProjection.CardRow card : diagnostics.cards()) {
            int cardHeight = 34 + (card.noteKeys().size() + card.metrics().size()) * 11;
            graphics.fill(x, y, x + width, y + cardHeight, PANEL_ALT);
            graphics.text(
                    font,
                    Component.literal(card.statusIcon() + " ")
                            .append(Component.translatable(card.titleKey())),
                    x + 7,
                    y + 6,
                    TEXT,
                    false);
            graphics.text(
                    font,
                    Component.translatable(card.statusTextKey()),
                    x + 7,
                    y + 18,
                    MUTED,
                    false);
            int metricY = y + 30;
            for (String noteKey : card.noteKeys()) {
                graphics.text(font, Component.translatable(noteKey), x + 12, metricY, MUTED, false);
                metricY += 11;
            }
            for (SettingsDiagnosticCard.Metric metric : card.metrics()) {
                graphics.text(
                        font,
                        Component.translatable(metric.labelKey(), metric.value() == null
                                ? Component.translatable("screen.openallay.settings.diagnostics.unknown")
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
            GuiGraphicsExtractor graphics,
            DiagnosticsSettingsProjection.DebugSection section,
            int x,
            int y,
            int width) {
        SettingsDiagnosticsSnapshot.DebugSettingsDiagnostics debug = section.diagnostics();
        y = settingsHeading(
                graphics,
                Component.translatable(section.titleKey()),
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
                                    ? Component.translatable("screen.openallay.settings.diagnostics.unknown").getString()
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
                    Component.translatable("screen.openallay.settings.diagnostics.unknown").getString());
        }
        for (SettingsDiagnosticsSnapshot.DebugSource source : debug.sources()) {
            y = debugLine(graphics, x, y, width,
                    "screen.openallay.settings.diagnostics.debug.source",
                    source.sourceId() + " · " + source.state()
                            + " · generation=" + source.generation()
                            + " · count=" + (source.itemCount() == null
                                    ? Component.translatable("screen.openallay.settings.diagnostics.unknown").getString()
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
            GuiGraphicsExtractor graphics,
            Component text,
            int x,
            int y,
            int width,
            int color) {
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, width)) {
            graphics.text(font, line, x, y, color, false);
            y += 11;
        }
        return y + 4;
    }

    private int debugLine(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            String labelKey,
            String value) {
        Component line = Component.translatable(labelKey).copy().append(": ").append(value);
        for (net.minecraft.util.FormattedCharSequence wrapped : font.split(line, width - 8)) {
            graphics.text(font, wrapped, x + 4, y, MUTED, false);
            y += 10;
        }
        return y + 2;
    }

    private void renderPlaceholder(GuiGraphicsExtractor graphics) {
        SettingsLayout.Rect area = layout.editor();
        graphics.text(
                font,
                Component.translatable(section.translationKey()),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        graphics.text(
                font,
                Component.translatable("screen.openallay.settings.section_pending"),
                area.x() + 10,
                area.y() + 31,
                MUTED,
                false);
    }

    private void renderExtensions(GuiGraphicsExtractor graphics) {
        SettingsLayout.Rect area = layout.editor();
        if (!layout.wide() && !narrowExtensionDetail) {
            return;
        }
        ExtensionSettingsProjection projection = extensionProjection();
        graphics.text(
                font,
                Component.translatable(extensionTab == ExtensionTab.INSTALLED
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
                    ? Component.translatable(
                            "screen.openallay.settings.extensions.community.unavailable")
                    : Component.translatable(
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
                Component.literal(extension.name()).copy().append(" · ")
                        .append(Component.translatable(extensionStateKey(extension))),
                x,
                y,
                width,
                TEXT,
                11);
        y = renderWrapped(
                graphics,
                Component.literal(extension.summary()),
                x,
                y + 4,
                width,
                MUTED,
                10);
        y += 7;
        y = renderExtensionCapabilities(graphics, extension, x, y, width);
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
            y = renderWrapped(graphics, Component.translatable(
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
                    Component.translatable(
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
                    Component.literal(catalogNotice),
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
            GuiGraphicsExtractor graphics,
            String labelKey,
            String value,
            int x,
            int y,
            int width) {
        return renderWrapped(
                graphics,
                Component.translatable(labelKey, value),
                x,
                y,
                width,
                MUTED,
                10);
    }

    private int renderExtensionContributions(
            GuiGraphicsExtractor graphics,
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
                    Component.translatable(line.labelKey(), String.join(", ", line.values())),
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
                    Component.translatable(
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
            GuiGraphicsExtractor graphics,
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
                Component.translatable(runtime.titleKey()),
                x + 7,
                cursor,
                width - 14,
                ACCENT,
                10);
        cursor = renderWrapped(
                graphics,
                Component.translatable(runtime.descriptionKey()),
                x + 7,
                cursor + 3,
                width - 14,
                MUTED,
                10);
        cursor = renderWrapped(
                graphics,
                Component.translatable(
                        "screen.openallay.settings.extensions.runtime.inputs",
                        String.join(", ", runtime.parameters())),
                x + 7,
                cursor + 3,
                width - 14,
                MUTED,
                10);
        renderWrapped(
                graphics,
                Component.translatable(
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
            GuiGraphicsExtractor graphics,
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
                        Component.literal(module.id()),
                        Component.translatable(
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
                Component detail = Component.literal(adapter.summary()).copy()
                        .append("\n")
                        .append(Component.translatable(
                                "screen.openallay.settings.extensions.provider",
                                adapter.provider()));
                String schema = schemaPreview(adapter.schema(), projection.debugMode());
                y = renderExtensionCard(
                        graphics,
                        Component.literal(adapter.id()),
                        detail,
                        schema.isBlank() ? null : Component.literal(schema),
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
            Component detail = Component.literal(root.summary()).copy()
                    .append("\n")
                    .append(Component.translatable(
                            root.availability().equals("REQUEST_SCOPED")
                                    ? "screen.openallay.settings.extensions.request_scoped"
                                    : "screen.openallay.settings.extensions.provider",
                            root.provider()));
            if (projection.debugMode()) {
                detail = detail.copy().append("\nprovider: ")
                        .append(root.provider())
                        .append(" · evidence: ")
                        .append(root.evidenceOwner());
            }
            y = renderExtensionCard(
                    graphics,
                    Component.literal("mc." + root.name()),
                    detail,
                    Component.literal(schemaPreview(root.schema(), projection.debugMode())),
                    x,
                    y,
                    width);
        }
        return y;
    }

    private int renderExtensionHeading(
            GuiGraphicsExtractor graphics,
            String key,
            int count,
            int x,
            int y) {
        graphics.text(
                font,
                Component.translatable(key, count),
                x + 2,
                y,
                ACCENT,
                false);
        return y + 14;
    }

    private int renderExtensionEmpty(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.text(
                font,
                Component.translatable("screen.openallay.settings.extensions.none"),
                x + 7,
                y,
                MUTED,
                false);
        return y + 16;
    }

    private int renderExtensionCard(
            GuiGraphicsExtractor graphics,
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
                    Component.translatable("screen.openallay.settings.extensions.schema")
                            .copy()
                            .append(": ")
                            .append(schema),
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
                                Component.literal(module.id()),
                                Component.translatable(
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
                Component detail = Component.literal(adapter.summary()).copy()
                        .append("\n")
                        .append(Component.translatable(
                                "screen.openallay.settings.extensions.provider",
                                adapter.provider()));
                String schema = schemaPreview(adapter.schema(), projection.debugMode());
                height += extensionCardHeight(
                                Component.literal(adapter.id()),
                                detail,
                                schema.isBlank() ? null : Component.literal(schema),
                                width)
                        + 5;
            }
        }
        height += 18;
        for (ExtensionSettingsProjection.RootCard root : projection.roots()) {
            Component detail = Component.literal(root.summary()).copy()
                    .append("\n")
                    .append(Component.translatable(
                            root.availability().equals("REQUEST_SCOPED")
                                    ? "screen.openallay.settings.extensions.request_scoped"
                                    : "screen.openallay.settings.extensions.provider",
                            root.provider()));
            if (projection.debugMode()) {
                detail = detail.copy().append("\nprovider: ")
                        .append(root.provider())
                        .append(" · evidence: ")
                        .append(root.evidenceOwner());
            }
            height += extensionCardHeight(
                            Component.literal("mc." + root.name()),
                            detail,
                            Component.literal(schemaPreview(
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
                    Component.translatable("screen.openallay.settings.extensions.schema")
                            .copy()
                            .append(": ")
                            .append(schema),
                    inner,
                    10);
        }
        return height;
    }

    private int extensionRuntimeHeight(
            ExtensionSettingsProjection.RuntimeCard runtime, int width) {
        int inner = Math.max(20, width - 14);
        return 18
                + wrappedHeight(Component.translatable(runtime.titleKey()), inner, 10)
                + wrappedHeight(Component.translatable(runtime.descriptionKey()), inner, 10)
                + wrappedHeight(Component.translatable(
                                "screen.openallay.settings.extensions.runtime.inputs",
                                String.join(", ", runtime.parameters())),
                        inner,
                        10)
                + wrappedHeight(Component.translatable(
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
            GuiGraphicsExtractor graphics,
            Component text,
            int x,
            int y,
            int width,
            int color,
            int lineHeight) {
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, Math.max(20, width))) {
            graphics.text(font, line, x, y, color, false);
            y += lineHeight;
        }
        return y;
    }

    private int wrappedHeight(Component text, int width, int lineHeight) {
        return Math.max(1, font.split(text, Math.max(20, width)).size()) * lineHeight;
    }

    private void renderSkills(GuiGraphicsExtractor graphics) {
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
                Component.translatable("screen.openallay.settings.skills"),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        if (selected.isEmpty()) {
            graphics.text(
                    font,
                    Component.translatable("screen.openallay.settings.skills.empty"),
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
                Component.translatable(skill.localOverride()
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
        int y = renderWrapped(graphics, Component.literal(skill.description()),
                area.x() + 10, start, width, MUTED, 10);
        y = renderRequirements(graphics, skill.requirements(), area.x() + 10, y + 8, width, false);
        y = renderWrapped(graphics, Component.literal(skill.body()),
                area.x() + 10, y + 8, width, TEXT, 10);
        if (snapshot.display().debugMode()) {
            y = renderWrapped(graphics, Component.literal(skill.provenance()),
                    area.x() + 10, y + 6, width, MUTED, 10);
        }
        skillDetailContentHeight = y - start + 8;
        graphics.disableScissor();
    }

    private int renderRequirements(
            GuiGraphicsExtractor graphics,
            RequirementSettingsProjection requirements,
            int x, int y, int width, boolean catalogPreview) {
        y = renderWrapped(graphics, Component.translatable(
                RequirementSettingsProjection.PREFIX + "title"), x, y, width, ACCENT, 11);
        if (catalogPreview) {
            y = renderWrapped(graphics, Component.translatable(
                    RequirementSettingsProjection.PREFIX + "catalog_preview"),
                    x, y + 4, width, MUTED, 10);
        }
        if (requirements.rows().isEmpty()) {
            return renderWrapped(graphics, Component.translatable(RequirementSettingsProjection.PREFIX
                    + (catalogPreview ? "package_check" : "none")), x, y + 4, width, MUTED, 10);
        }
        for (RequirementSettingsProjection.Row row : requirements.rows()) {
            y = renderWrapped(graphics, RequirementReviewScreen.rowLabel(row),
                    x, y + 4, width, TEXT, 10);
            if (!row.detail().isBlank()) {
                y = renderWrapped(graphics, Component.literal(row.detail()),
                        x, y + 2, width, MUTED, 10);
            }
        }
        return y;
    }

    private void renderCommunitySkills(
            GuiGraphicsExtractor graphics,
            SettingsLayout.Rect area) {
        SkillSettingsProjection.Community community = skillProjection().community();
        graphics.text(
                font,
                Component.translatable("screen.openallay.settings.skills.community.title"),
                area.x() + 10,
                area.y() + 12,
                ACCENT,
                false);
        if (!community.available()) {
            renderWrapped(
                    graphics,
                    Component.translatable(
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
                    Component.translatable("screen.openallay.settings.skills.community.empty"),
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
                Component.literal(skill.description()),
                x,
                y,
                width,
                MUTED,
                10);
        y += 4;
        graphics.text(
                font,
                Component.translatable("screen.openallay.settings.skills.community.version",
                        skill.version()),
                x,
                y,
                MUTED,
                false);
        y += 13;
        graphics.text(
                font,
                Component.translatable(
                        "screen.openallay.settings.skills.community.publisher",
                        skill.publisher()),
                x,
                y,
                MUTED,
                false);
        y += 13;
        graphics.text(
                font,
                Component.translatable(skillStateKey(skill.state())),
                x,
                y,
                skill.installable() ? ACCENT : MUTED,
                false);
        y = renderRequirements(graphics, new RequirementSettingsProjection(List.of()),
                x, y + 12, width, true);
        y += 18;
        y = renderWrapped(
                graphics,
                Component.translatable(
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
                    Component.translatable(
                            "screen.openallay.settings.skills.community.id",
                            skill.id()),
                    x,
                    y,
                    width,
                    MUTED,
                    10);
            y = renderWrapped(
                    graphics,
                    Component.translatable(
                            "screen.openallay.settings.skills.community.archive",
                            skill.archive()),
                    x,
                    y,
                    width,
                    MUTED,
                    10);
            y = renderWrapped(
                    graphics,
                    Component.translatable(
                            "screen.openallay.settings.skills.community.sha256",
                            skill.sha256()),
                    x,
                    y + 3,
                    width,
                    MUTED,
                    10);
        }
        if (community.notice().isPresent()) {
            y = renderWrapped(graphics, Component.literal(community.notice().orElseThrow().message()),
                    x, y + 18, width, ERROR, 10);
        }
        skillDetailContentHeight = y - start + 8;
        graphics.disableScissor();
    }

    private void renderNotice(GuiGraphicsExtractor graphics) {
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
        boolean localPage = section == SettingsSection.UI;
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

    private void saveCurrent() {
        confirmation = Confirmation.NONE;
        if (section == SettingsSection.MODELS) {
            save();
        }
    }

    private void reloadCurrent() {
        if (confirmation != Confirmation.RELOAD) {
            confirmation = Confirmation.RELOAD;
            localNotice = Component.translatable(
                    "screen.openallay.settings.confirm_reload").getString();
            rebuildWidgets();
            return;
        }
        confirmation = Confirmation.NONE;
        if (section == SettingsSection.MODELS) {
            accept(service.reloadModels(true));
        }
    }

    private void backOrClose() {
        if (!layout.wide() && section == SettingsSection.SKILLS && narrowSkillDetail) {
            narrowSkillDetail = false;
            skillEditing = false;
            skillDraftMarkdown = "";
            rebuildWidgets();
            return;
        }
        if (!layout.wide()
                && section == SettingsSection.EXTENSIONS
                && narrowExtensionDetail) {
            narrowExtensionDetail = false;
            rebuildWidgets();
            return;
        }
        onClose();
    }

    private Component historyActionLabel(HistorySettingsProjection.ActionRow row) {
        if (historyConfirmation == null
                || historyConfirmation.action() != serviceHistoryAction(row.action())) {
            return Component.translatable(row.labelKey());
        }
        if (row.action() == HistorySettingsProjection.Action.RESET_DATABASE
                && historyConfirmation.stage()
                        == ClientSettingsService.ConfirmationStage.FIRST) {
            return Component.translatable(
                    "screen.openallay.settings.history.confirm_reset_again");
        }
        return Component.translatable("screen.openallay.settings.confirm");
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
                localNotice = Component.translatable(
                        action == HistorySettingsProjection.Action.RESET_DATABASE
                                ? "screen.openallay.settings.history.confirm_reset"
                                : "screen.openallay.settings.history.confirm_delete")
                        .getString();
                rebuildWidgets();
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
                localNotice = Component.translatable(
                        "screen.openallay.settings.history.confirm_reset_again_notice")
                        .getString();
                rebuildWidgets();
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
            case "restart_required" -> Component.translatable(
                            "screen.openallay.settings.extensions.diagnostic.restart_required")
                    .getString();
            case "incompatible_loader" -> Component.translatable(
                            "screen.openallay.settings.extensions.diagnostic.loader")
                    .getString();
            case "incompatible_game_version" -> Component.translatable(
                            "screen.openallay.settings.extensions.diagnostic.game")
                    .getString();
            case "incompatible_openallay_api" -> Component.translatable(
                            "screen.openallay.settings.extensions.diagnostic.api")
                    .getString();
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
        rebuildWidgets();
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
        selectedExtensionId = cards.isEmpty() ? null : cards.getFirst().id();
        localNotice = "";
        rebuildWidgets();
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
            localNotice = Component.translatable(
                            "screen.openallay.settings.extensions.community.import_required")
                    .getString();
            return;
        }
        try {
            accept(service.importLocalExtensionPackage(Path.of(extensionImportPathDraft)));
        } catch (InvalidPathException failure) {
            localNotice = Component.translatable(
                            "screen.openallay.settings.extensions.community.import_invalid")
                    .getString();
        }
    }

    private void importLocalSkill() {
        captureDraft();
        if (skillImportPathDraft.isBlank()) {
            localNotice = Component.translatable(
                    "screen.openallay.settings.skills.community.import_required").getString();
            return;
        }
        try {
            accept(service.importLocalSkillPackage(Path.of(skillImportPathDraft)));
        } catch (InvalidPathException failure) {
            localNotice = Component.translatable(
                    "screen.openallay.settings.skills.community.import_invalid").getString();
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

    private void save() {
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
            localNotice = Component.translatable(
                    "screen.openallay.settings.models.invalid").getString();
            return;
        }
        selectedProfileId = definition.id();
        SecretValue replacement = pendingApiKey.isBlank()
                ? null
                : SecretValue.of(pendingApiKey);
        pendingApiKey = "";
        if (apiKey != null) {
            apiKey.setValue("");
        }
        accept(service.saveModels(candidate, definition.id(), replacement));
    }

    private void delete() {
        if (selectedProfileId == null) {
            select(snapshot.models().config().defaultProfileId());
            rebuildWidgets();
            return;
        }
        if (snapshot.models().config().profiles().size() <= 1) {
            localNotice = Component.translatable(
                    "screen.openallay.settings.models.cannot_delete_last").getString();
            return;
        }
        if (confirmation != Confirmation.DELETE) {
            confirmation = Confirmation.DELETE;
            localNotice = Component.translatable(
                    "screen.openallay.settings.confirm_delete").getString();
            rebuildWidgets();
            return;
        }
        List<ModelProfileDefinition> retained = snapshot.models().config().profiles().stream()
                .filter(profile -> !profile.id().equals(selectedProfileId))
                .toList();
        String defaultId = snapshot.models().config().defaultProfileId().equals(selectedProfileId)
                ? retained.getFirst().id()
                : snapshot.models().config().defaultProfileId();
        select(retained.getFirst().id());
        confirmation = Confirmation.NONE;
        accept(service.saveModels(new ModelProfilesConfig(
                defaultId, retained)));
    }

    private void makeDefault() {
        if (selectedProfileId == null) {
            localNotice = Component.translatable(
                    "screen.openallay.settings.models.save_first").getString();
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
            localNotice = Component.translatable(
                    "screen.openallay.settings.confirm_billable_test").getString();
            rebuildWidgets();
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
            localNotice = Component.translatable(
                    "screen.openallay.settings.models.catalog_invalid").getString();
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
                        ? Component.translatable(
                                "screen.openallay.settings.models.catalog_empty").getString()
                        : "";
                rebuildWidgets();
            } else {
                ToolResult.Failure<ModelCatalog> failure =
                        (ToolResult.Failure<ModelCatalog>) result;
                localNotice = Component.translatable(
                        "screen.openallay.settings.models.catalog_failed",
                        failure.message()).getString();
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
            addRenderableWidget(OpenAllayButton.create(Component.literal(modelId), ignored -> {
                        draft = draft.withModel(modelId);
                        refreshAutomaticContext();
                        modelCatalogOpen = false;
                        localNotice = "";
                        rebuildWidgets();
                    })
                    .bounds(x, y, width, 20)
                    .build());
            y += 23;
        }
        int pages = Math.max(1, (catalogModelIds.size() + pageSize - 1) / pageSize);
        int navY = area.bottom() - 24;
        int navWidth = Math.max(30, (width - 8) / 3);
        Button previous = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.models.catalog_previous"),
                        ignored -> {
                            modelCatalogPage--;
                            rebuildWidgets();
                        })
                .bounds(x, navY, navWidth, 20)
                .build());
        previous.active = modelCatalogPage > 0;
        Button next = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.models.catalog_next"),
                        ignored -> {
                            modelCatalogPage++;
                            rebuildWidgets();
                        })
                .bounds(x + navWidth + 4, navY, navWidth, 20)
                .build());
        next.active = modelCatalogPage + 1 < pages;
        addRenderableWidget(OpenAllayButton.create(
                        Component.translatable("screen.openallay.settings.models.catalog_close"),
                        ignored -> {
                            modelCatalogOpen = false;
                            rebuildWidgets();
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
                        ? Component.translatable(
                                "screen.openallay.settings.capability.dependency_conflict")
                                .getString()
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
        captureDraft();
        section = replacement;
        sectionMenuOpen = false;
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
        rebuildWidgets();
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
        rebuildWidgets();
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
        rebuildWidgets();
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
        }
        if (section == SettingsSection.GENERAL && assistantName != null) {
            assistantNameDraft = assistantName.getValue();
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
        return Component.translatable(reasoning.explanationKey(),
                reasoning.wireField(), reasoning.selected().encoded());
    }

    private void cycleProtocol() {
        captureDraft();
        draftProtocol = draftProtocol == ModelProtocol.OPENAI_CHAT
                ? ModelProtocol.ANTHROPIC_MESSAGES
                : ModelProtocol.OPENAI_CHAT;
        invalidateModelCatalog();
        confirmation = Confirmation.NONE;
        rebuildWidgets();
    }

    private void toggleEnabled() {
        captureDraft();
        draftEnabled = !draftEnabled;
        confirmation = Confirmation.NONE;
        rebuildWidgets();
    }


    private Component protocolLabel() {
        return Component.translatable(
                draftProtocol == ModelProtocol.OPENAI_CHAT
                        ? "screen.openallay.settings.models.protocol_openai"
                        : "screen.openallay.settings.models.protocol_anthropic");
    }

    private Component enabledLabel() {
        return Component.translatable(
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
            GuiGraphicsExtractor graphics, SettingsLayout.Rect rect, int color) {
        if (rect.width() > 0 && rect.height() > 0) {
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
        }
    }

    /** Screenshot-harness navigation only; inert in every normal client launch. */
    public void e2eOpenExtensions() {
        requireE2eControls();
        captureDraft();
        section = SettingsSection.EXTENSIONS;
        pageScroll = 0;
        pageContentHeight = 0;
        rebuildWidgets();
    }

    /** Screenshot-harness selection of an existing configured profile; no settings write. */
    public void e2eOpenModels(String profileId) {
        requireE2eControls();
        captureDraft();
        select(Objects.requireNonNull(profileId, "profileId"));
        section = SettingsSection.MODELS;
        editorScroll = 0;
        pageScroll = 0;
        rebuildWidgets();
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
        rebuildWidgets();
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
        rebuildWidgets();
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
        rebuildWidgets();
    }

    /** Screenshot-harness navigation for the player-facing About page. */
    public void e2eOpenAbout() {
        requireE2eControls();
        captureDraft();
        section = SettingsSection.ABOUT;
        pageScroll = 0;
        pageContentHeight = 0;
        rebuildWidgets();
    }

    /** Positive pixels move the Tool detail down; intended for retained screenshot coverage. */
    public void e2eScrollExtensionDetails(int pixels) {
        requireE2eControls();
        if (section != SettingsSection.EXTENSIONS || layout == null) {
            throw new IllegalStateException("E2E Extension details are not open");
        }
        int maximum = Math.max(0, pageContentHeight - layout.content().height() + 18);
        pageScroll = net.minecraft.util.Mth.clamp(pageScroll + pixels, 0, maximum);
        rebuildWidgets();
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

    record Projection(
            List<SettingsSection> sections,
            List<ModelSettingsProjection.ModelCard> models,
            GeneralSettingsProjection general,
            UiSettingsProjection ui,
            RecipeSettingsProjection recipes,
            SkillSettingsProjection skills,
            ExtensionSettingsProjection extensions,
            HistorySettingsProjection history,
            DiagnosticsSettingsProjection diagnostics,
            SettingsOperation operation,
            SettingsNotice notice) {
        Projection {
            sections = List.copyOf(sections);
            models = List.copyOf(models);
            Objects.requireNonNull(general, "general");
            Objects.requireNonNull(ui, "ui");
            Objects.requireNonNull(recipes, "recipes");
            Objects.requireNonNull(skills, "skills");
            Objects.requireNonNull(extensions, "extensions");
            Objects.requireNonNull(history, "history");
            Objects.requireNonNull(diagnostics, "diagnostics");
        }
    }

    private record Action(String translationKey, Runnable action) {}

    private record ContributionLine(String labelKey, List<String> values) {
        private ContributionLine {
            values = List.copyOf(values);
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

    private static final class PasswordEditBox extends EditBox {
        private PasswordEditBox(
                net.minecraft.client.gui.Font font,
                int x,
                int y,
                int width,
                int height,
                Component narration) {
            super(font, x, y, width, height, narration);
            addFormatter((text, offset) -> net.minecraft.util.FormattedCharSequence.forward(
                    "•".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
        }

        @Override
        public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
            if (event.isCopy() || event.isCut()) {
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
            return Component.translatable("screen.openallay.settings.models.api_key");
        }
    }
}
