package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.MinecraftClientWindow;

import dev.openallay.client.gui.OpenAllayButton;
import dev.openallay.client.gui.GuideNativeInput;
import dev.openallay.client.gui.GuideNativeFocus;
import dev.openallay.guide.ui.GuideDisplayConfig;
import dev.openallay.guide.ui.GuideUiConfig;
import dev.openallay.guide.ui.hud.GuideHudView;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import dev.openallay.client.gui.GuideGraphics;
import dev.openallay.client.gui.GuideNativeSlider;
import dev.openallay.client.gui.GuideTooltip;
import net.minecraft.client.gui.screens.Screen;
import dev.openallay.client.gui.GuideInputMouse;
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.network.chat.Component;

/** Native, non-pausing HUD editor. Changes stay in memory until the player presses Apply. */
public final class GuideHudEditorScreen extends dev.openallay.client.gui.GuideNativeScreen {
    private static final int PANEL = 0xF0181B22;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFFA9B3BE;
    private static final int ACCENT = 0xFF72D5C4;
    private static final int HANDLE = 10;
    private static final String PREFIX = "screen.openallay.hud.editor.";

    private final Draft draft;
    private final Interaction interaction;
    private final Screen returnScreen;
    private final BooleanSupplier ownerValid;
    private final GuideHudRenderer renderer;
    private final Supplier<GuideHudView> view;
    private boolean controlsVisible = true;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private Form form;
    private int formScroll;

    public GuideHudEditorScreen(
            GuideDisplayConfig draft,
            Consumer<GuideDisplayConfig> applied,
            Screen returnScreen,
            BooleanSupplier ownerValid,
            GuideHudRenderer renderer,
            Supplier<GuideHudView> view) {
        super(label("title"));
        this.draft = new Draft(draft, applied);
        this.interaction = new Interaction(this.draft);
        this.returnScreen = returnScreen;
        this.ownerValid = Objects.requireNonNull(ownerValid, "ownerValid");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.view = Objects.requireNonNull(view, "view");
    }

    @Override
    protected void initGuideScreen() {
        interaction.cancel();
        panelWidth = Math.max(0, Math.min(252, width - 12));
        panelHeight = controlsVisible ? Math.min(190, Math.max(0, height - 12)) : Math.min(24, Math.max(0, height - 12));
        GuideHudLayout.Rect hud = GuideHudLayout.calculate(width, height, draft.hud());
        panelX = hud.x() + hud.width() / 2 < width / 2.0
                ? Math.max(6, width - panelWidth - 6) : 6;
        panelY = Math.max(6, (height - panelHeight) / 2);
        int x = panelX + 6;
        int w = Math.max(0, panelWidth - 12);
        addButton(controlsVisible ? "hide_controls" : "show_controls", x + Math.max(0, w - 76),
                panelY + 3, Math.min(76, w), 18, () -> {
                    controlsVisible = !controlsVisible;
                    rebuildWidgets();
                });
        if (!controlsVisible) {
            return;
        }
        form = Form.calculate(panelY, panelHeight);
        formScroll = Math.max(0, Math.min(form.maximumScroll(), formScroll));
        if (form.maximumScroll() > 0) {
            addButton("scroll_up", x, panelY + 3, 22, 18, () -> scrollForm(-20));
            addButton("scroll_down", x + 24, panelY + 3, 22, 18, () -> scrollForm(20));
        }
        addBodyButton("anchor_previous", x, 0, 26, () -> cycleAnchor(-1));
        addBodyButton("anchor_next", x + w - 26, 0, 26, () -> cycleAnchor(1));
        addBodyButton("width_decrease", x, 1, 26, () -> changeSize(-8, 0));
        addBodyButton("width_increase", x + w - 26, 1, 26, () -> changeSize(8, 0));
        addBodyButton("height_decrease", x, 2, 26, () -> changeSize(0, -8));
        addBodyButton("height_increase", x + w - 26, 2, 26, () -> changeSize(0, 8));
        if (form.rowVisible(3, formScroll)) {
            addRenderableWidget(new HudSlider(x, form.rowY(3, formScroll), w, form.rowHeight(), true));
        }
        if (form.rowVisible(4, formScroll)) {
            addRenderableWidget(new HudSlider(x, form.rowY(4, formScroll), w, form.rowHeight(), false));
        }
        int half = Math.max(0, (w - 4) / 2);
        if (form.rowVisible(5, formScroll)) {
            addRenderableWidget(OpenAllayButton.create(enabledLabel(), button -> {
                        draft.update(draft.hud().withEnabled(!draft.hud().enabled()));
                        button.setMessage(enabledLabel());
                    }).bounds(x, form.rowY(5, formScroll), half, form.rowHeight()).build());
        }
        addBodyButton("reset", x + half + 4, 5, half, () -> {
            draft.update(GuideUiConfig.Hud.defaults());
            rebuildWidgets();
        });
        addButton("apply", x, form.footerY(), half, form.footerHeight(), this::apply);
        addButton("cancel", x + half + 4, form.footerY(), half, form.footerHeight(), this::onClose);
    }

    private void addBodyButton(String key, int x, int row, int w, Runnable action) {
        if (form.rowVisible(row, formScroll)) {
            addButton(key, x, form.rowY(row, formScroll), w, form.rowHeight(), action);
        }
    }

    private void scrollForm(int amount) {
        int next = Math.max(0, Math.min(form.maximumScroll(), formScroll + amount));
        if (next != formScroll) {
            formScroll = next;
            setDragging(false);
            rebuildWidgets();
        }
    }

    @Override
    public boolean guideMouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (controlsVisible && form != null && insideControls(x, y) && form.maximumScroll() > 0) {
            scrollForm(scrollY < 0 ? 20 : -20);
            return true;
        }
        return super.guideMouseScrolled(x, y, scrollX, scrollY);
    }

    private void addButton(String key, int x, int y, int w, int h, Runnable action) {
        addRenderableWidget(OpenAllayButton.create(label(key), button -> action.run())
                .bounds(x, y, w, h)
                .tooltip(GuideTooltip.create(label(key + ".tooltip")))
                .build());
    }

    private void cycleAnchor(int step) {
        GuideUiConfig.Anchor[] anchors = GuideUiConfig.Anchor.values();
        GuideUiConfig.Anchor next = anchors[Math.floorMod(draft.hud().anchor().ordinal() + step, anchors.length)];
        draft.update(GuideHudLayout.withAnchorKeepingPosition(width, height, draft.hud(), next));
    }

    private void changeSize(int changeWidth, int changeHeight) {
        GuideUiConfig.Hud hud = draft.hud();
        GuideHudLayout.Rect before = GuideHudLayout.calculate(width, height, hud);
        int newWidth = Math.max(160, Math.min(480, hud.width() + changeWidth));
        int newHeight = Math.max(44, Math.min(240, hud.height() + changeHeight));
        draft.update(GuideHudLayout.resizeAt(width, height, hud, before.x(), before.y(),
                newWidth * hud.scale(), newHeight * hud.scale()));
    }

    private Component enabledLabel() {
        return label(draft.hud().enabled() ? "enabled" : "disabled");
    }

    private void apply() {
        interaction.cancel();
        boolean valid = ownerValid.getAsBoolean();
        draft.apply(valid);
        returnToOwner(valid && ownerValid.getAsBoolean());
    }

    @Override
    public void onClose() {
        interaction.cancel();
        draft.cancel();
        returnToOwner(ownerValid.getAsBoolean());
    }

    private void returnToOwner(boolean valid) {
        // Native teardown will replace this Screen. Do not return to game UI during disconnection.
        if (MinecraftClientWindow.screen(minecraft) == this && MinecraftClientWindow.canInterruptScreen(minecraft)) {
            MinecraftClientWindow.setScreen(minecraft, valid ? returnScreen : null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public void tick() {
        tickGuideWidgets();
        if (!ownerValid.getAsBoolean()) {
            interaction.cancel();
            draft.cancel();
            returnToOwner(false);
            return;
        }
        if (!minecraft.isWindowActive()) {
            interaction.cancel();
            setDragging(false);
            GuideNativeFocus.clear(this);
        }
    }

    @Override
    protected void resizeGuide(int width, int height) {
        interaction.cancel();
        setDragging(false);
        resizeGuideWidgets(width, height);
    }

    @Override
    protected void guideRemoved() {
        interaction.cancel();
        setDragging(false);
        draft.cancel();
        // The native Screen transition owns cursor release/grab and the replacement Screen.
        super.guideRemoved();
    }

    @Override
    public boolean guideMouseClicked(GuideInputMouse event, boolean doubleClick) {
        if (!ownerValid.getAsBoolean() || draft.finished()) {
            return true;
        }
        if (insideControls(event.x(), event.y())) {
            super.guideMouseClicked(event, doubleClick);
            return true;
        }
        if (GuideNativeInput.isLeftClick(event) && interaction.begin(width, height, event.x(), event.y())) {
            GuideNativeFocus.clear(this);
            return true;
        }
        return super.guideMouseClicked(event, doubleClick);
    }

    @Override
    public boolean guideMouseDragged(GuideInputMouse event, double dx, double dy) {
        if (interaction.active()) {
            if (GuideNativeInput.isLeftClick(event) && ownerValid.getAsBoolean() && minecraft.isWindowActive()) {
                interaction.move(width, height, event.x(), event.y());
            } else {
                interaction.cancel();
            }
            return true;
        }
        return super.guideMouseDragged(event, dx, dy);
    }

    @Override
    public boolean guideMouseReleased(GuideInputMouse event) {
        boolean editing = interaction.active();
        interaction.cancel();
        boolean widgetReleased = super.guideMouseReleased(event);
        return editing || widgetReleased;
    }

    private boolean insideControls(double x, double y) {
        return x >= panelX && x < panelX + panelWidth && y >= panelY && y < panelY + panelHeight;
    }

    @Override
    protected void paintGuideBackground(GuideGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the world visible for opacity editing; preserve native in-game subtitle extraction.
        MinecraftClientWindow.extractDeferredSubtitles(minecraft, graphics);
    }

    @Override
    protected void paintGuideScreen(GuideGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!ownerValid.getAsBoolean() || draft.finished()) {
            return;
        }
        GuideUiConfig.Hud hud = draft.hud();
        // Preview is independent of saved enabled/F1 policy; expand only the preview, not the draft.
        renderer.extractPreview(graphics, view.get(), hud.withCollapsed(false));
        GuideHudLayout.Rect bounds = GuideHudLayout.calculate(width, height, hud);
        int left = (int) Math.floor(bounds.x());
        int top = (int) Math.floor(bounds.y());
        int right = (int) Math.ceil(bounds.right());
        int bottom = (int) Math.ceil(bounds.bottom());
        graphics.outline(left, top, right - left, bottom - top, ACCENT);
        graphics.fill(Math.max(left, right - HANDLE), Math.max(top, bottom - HANDLE), right, bottom, ACCENT);
        if (!insideControls(mouseX, mouseY) && bounds.contains(mouseX, mouseY)) {
            graphics.requestResizeCursor();
            graphics.setTooltipForNextFrame(font,
                    label(Interaction.onHandle(bounds, mouseX, mouseY) ? "resize_hint" : "drag_hint"),
                    mouseX, mouseY);
        }
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL);
        if (controlsVisible) {
            if (form.maximumScroll() == 0) {
                boundedText(graphics, label("title"), panelX + 6, panelY + 8, panelWidth - 94, TEXT);
            }
            boundedText(graphics, label("hint"), panelX + 6, panelY + 25, panelWidth - 12, MUTED);
            if (form.bodyTop() - panelY >= 48) {
                boundedText(graphics, label("apply_hint"), panelX + 6, panelY + 36, panelWidth - 12, MUTED);
            }
            graphics.enableScissor(panelX + 6, form.bodyTop(), panelX + panelWidth - 6, form.bodyBottom());
            Component anchor = MinecraftComponents.translatable("screen.openallay.hud.anchor."
                    + hud.anchor().name().toLowerCase(Locale.ROOT));
            if (form.rowVisible(0, formScroll)) {
                boundedCenteredText(graphics, label("anchor", anchor), form.rowY(0, formScroll) + 4);
            }
            if (form.rowVisible(1, formScroll)) {
                boundedCenteredText(graphics, label("width", hud.width()), form.rowY(1, formScroll) + 4);
            }
            if (form.rowVisible(2, formScroll)) {
                boundedCenteredText(graphics, label("height", hud.height()), form.rowY(2, formScroll) + 4);
            }
            graphics.disableScissor();
        } else {
            boundedText(graphics, label("title"), panelX + 6, panelY + 8, panelWidth - 94, TEXT);
        }
        renderGuideWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void boundedCenteredText(GuideGraphics graphics, Component message, int y) {
        int available = Math.max(0, panelWidth - 76);
        String text = font.plainSubstrByWidth(message.getString(), available);
        graphics.text(font, text, panelX + (panelWidth - font.width(text)) / 2, y, TEXT);
    }

    private void boundedText(GuideGraphics graphics, Component message, int x, int y, int width, int color) {
        graphics.text(font, font.plainSubstrByWidth(message.getString(), Math.max(0, width)), x, y, color);
    }

    private static Component label(String key, Object... arguments) {
        return MinecraftComponents.translatable(PREFIX + key, arguments);
    }

    private final class HudSlider extends GuideNativeSlider {
        private final boolean scale;

        HudSlider(int x, int y, int width, int height, boolean scale) {
            super(x, y, width, height, MinecraftComponents.empty(),
                    scale ? (draft.hud().scale() - 0.75) : draft.hud().backgroundOpacity());
            this.scale = scale;
            setTooltip(GuideTooltip.create(label(scale ? "scale.tooltip" : "opacity.tooltip")));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int percent = (int) Math.round((scale ? 0.75 + value : value) * 100);
            setMessage(label(scale ? "scale" : "opacity", percent));
        }

        @Override
        protected void applyValue() {
            GuideUiConfig.Hud hud = draft.hud();
            if (scale) {
                draft.update(hud.withPlacement(hud.anchor(), hud.offsetX(), hud.offsetY(),
                        hud.width(), hud.height(), 0.75 + value));
            } else {
                // This setting is used by the renderer only for its background, never text alpha.
                draft.update(hud.withBackgroundOpacity(value));
            }
        }
    }

    /** Fixed footer and bounded form rows. Short viewports retain every control through scrolling. */
    record Form(int bodyTop, int bodyBottom, int footerY, int footerHeight, int rowHeight, int rowStep) {
        private static final int ROWS = 6;

        static Form calculate(int panelY, int panelHeight) {
            int footerHeight = Math.min(18, Math.max(0, panelHeight - 6));
            int footerY = panelY + Math.max(0, panelHeight - footerHeight - 4);
            int bodyBottom = Math.max(panelY, footerY - 4);
            int headerHeight = panelHeight < 186 ? 36 : 48;
            int bodyTop = Math.min(bodyBottom, panelY + headerHeight);
            int bodyHeight = bodyBottom - bodyTop;
            int rowHeight = bodyHeight >= 106 && bodyHeight < 118 ? 16 : 18;
            return new Form(bodyTop, bodyBottom, footerY, footerHeight, rowHeight, rowHeight + 2);
        }

        int maximumScroll() {
            return Math.max(0, (ROWS - 1) * rowStep + rowHeight - (bodyBottom - bodyTop));
        }

        int rowY(int row, int scroll) {
            return bodyTop + row * rowStep - scroll;
        }

        boolean rowVisible(int row, int scroll) {
            int y = rowY(row, scroll);
            return y >= bodyTop && y + rowHeight <= bodyBottom;
        }
    }

    /** Pure transaction used by native callbacks. It cannot write settings or apply more than once. */
    static final class Draft {
        private final GuideDisplayConfig original;
        private final Consumer<GuideDisplayConfig> applied;
        private GuideDisplayConfig candidate;
        private boolean finished;

        Draft(GuideDisplayConfig original, Consumer<GuideDisplayConfig> applied) {
            this.original = Objects.requireNonNull(original, "draft");
            this.applied = Objects.requireNonNull(applied, "applied");
            candidate = original;
        }

        GuideDisplayConfig candidate() {
            return candidate;
        }

        GuideUiConfig.Hud hud() {
            return candidate.ui().hud();
        }

        boolean finished() {
            return finished;
        }

        void update(GuideUiConfig.Hud hud) {
            if (!finished) {
                candidate = candidate.withUi(candidate.ui().withHud(Objects.requireNonNull(hud, "hud")));
            }
        }

        boolean apply(boolean ownerValid) {
            if (finished) {
                return false;
            }
            if (!ownerValid) {
                cancel();
                return false;
            }
            finished = true;
            applied.accept(candidate);
            return true;
        }

        void cancel() {
            if (!finished) {
                finished = true;
                candidate = original;
            }
        }
    }

    /** Pointer math is GUI-space and remains testable without a running client. */
    static final class Interaction {
        private final Draft draft;
        private GuideHudLayout.Rect start;
        private GuideUiConfig.Hud startHud;
        private double pointerX;
        private double pointerY;
        private boolean resize;

        Interaction(Draft draft) {
            this.draft = draft;
        }

        boolean begin(int width, int height, double x, double y) {
            cancel();
            if (draft.finished()) {
                return false;
            }
            GuideHudLayout.Rect bounds = GuideHudLayout.calculate(width, height, draft.hud());
            if (!bounds.contains(x, y)) {
                return false;
            }
            start = bounds;
            startHud = draft.hud();
            pointerX = x;
            pointerY = y;
            resize = onHandle(bounds, x, y);
            return true;
        }

        boolean active() {
            return start != null;
        }

        void move(int width, int height, double x, double y) {
            if (!active() || draft.finished()) {
                return;
            }
            double dx = x - pointerX;
            double dy = y - pointerY;
            if (resize) {
                draft.update(GuideHudLayout.resizeAt(width, height, startHud, start.x(), start.y(),
                        start.width() + dx, start.height() + dy));
            } else {
                draft.update(GuideHudLayout.placementAt(width, height, startHud, start.x() + dx, start.y() + dy));
            }
        }

        void cancel() {
            start = null;
            startHud = null;
        }

        static boolean onHandle(GuideHudLayout.Rect bounds, double x, double y) {
            return bounds.contains(x, y) && x >= bounds.right() - HANDLE && y >= bounds.bottom() - HANDLE;
        }
    }
}
