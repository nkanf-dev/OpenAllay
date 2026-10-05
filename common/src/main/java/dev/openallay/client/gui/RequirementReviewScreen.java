package dev.openallay.client.gui;

import dev.openallay.client.gui.settings.RequirementSettingsProjection;
import dev.openallay.client.gui.settings.RequirementSettingsProjection.Row;
import dev.openallay.requirement.RequirementStatus;
import dev.openallay.settings.ClientSettingsService;
import dev.openallay.settings.ClientSettingsSnapshot;
import dev.openallay.settings.SettingsOperation;
import dev.openallay.settings.requirement.RequirementReview;
import dev.openallay.tool.ToolResult;
import java.util.Objects;
import dev.openallay.client.gui.GuideGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import dev.openallay.client.gui.GuideInputKey;
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.network.chat.Component;

/** Reviews the already checked candidate; only Continue anyway publishes it. */
public final class RequirementReviewScreen extends dev.openallay.client.gui.GuideNativeScreen {
    private static final String PREFIX = RequirementSettingsProjection.PREFIX;
    private static final int TEXT = 0xFFE8EDF2;
    private static final int MUTED = 0xFFA9B3BE;
    private static final int ACCENT = 0xFF72D5C4;
    private static final int ERROR = 0xFFFF7D7D;
    private final ClientSettingsService service;
    private final Screen parent;
    private final RequirementReview.Token token;
    private ClientSettingsSnapshot snapshot;
    private RequirementReview review;
    private AutoCloseable listener;
    private Row confirming;
    private boolean actionPending;
    private boolean finished;
    private final AttachmentState attachment = new AttachmentState();
    private boolean savedSettings;
    private int scroll;
    private int contentHeight;
    private String failure = "";

    public RequirementReviewScreen(
            ClientSettingsService service, Screen parent, RequirementReview review) {
        super(MinecraftComponents.translatable(PREFIX + "title"));
        this.service = Objects.requireNonNull(service, "service");
        this.parent = Objects.requireNonNull(parent, "parent");
        this.review = Objects.requireNonNull(review, "review");
        this.token = review.token();
        this.snapshot = service.snapshot();
    }

    @Override
    protected void guideAdded() {
        long epoch = attachment.attach();
        listener = service.listen(next -> {
            if (!attached(epoch)) return;
            snapshot = next;
            RequirementReview current = next.requirementReview().orElse(null);
            if (current != null && current.token() == token) {
                review = current;
            } else if (!actionPending && !finished) {
                failure = MinecraftComponents.translatable(PREFIX + "expired").getString();
            }
            // Minecraft 26.2 calls added() before init(width, height). A local dispatcher
            // may deliver this snapshot inline; retain it, but do not create widgets yet.
            if (minecraft != null && attachment.canRebuild(epoch)) rebuildWidgets();
        });
    }

    private boolean attached(long epoch) {
        return attachment.isCurrent(epoch);
    }

    /** Pure lifecycle guard. Detached callbacks cannot update or reopen any screen. */
    static final class AttachmentState {
        private long epoch;
        private boolean attached;
        private boolean layoutReady;

        long attach() {
            attached = true;
            layoutReady = false;
            return ++epoch;
        }

        void detach() {
            attached = false;
            layoutReady = false;
        }

        void layoutInitialized() {
            layoutReady = attached;
        }

        boolean canRebuild(long capturedEpoch) {
            return layoutReady && isCurrent(capturedEpoch);
        }

        long epoch() {
            return epoch;
        }

        boolean isCurrent(long capturedEpoch) {
            return attached && epoch == capturedEpoch;
        }
    }

    private RequirementSettingsProjection projection() {
        return RequirementSettingsProjection.from(review.report(), review.changes());
    }

    private boolean ready() {
        return !actionPending && !finished
                && snapshot.operation().kind() == SettingsOperation.Kind.IDLE
                && snapshot.requirementReview().map(value -> value.token() == token).orElse(false);
    }

    @Override
    protected void initGuideScreen() {
        int x = left();
        int w = panelWidth();
        int half = (w - 6) / 2;
        Button cancel = addRenderableWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable(PREFIX + "cancel"), ignored -> {
                            if (confirming == null) onClose();
                            else { confirming = null; scroll = 0; rebuildWidgets(); }
                        })
                .bounds(x, height - 29, half, 20).build());
        cancel.active = !actionPending;
        Button proceed = addRenderableWidget(OpenAllayButton.create(
                        MinecraftComponents.translatable(confirming == null
                                ? projection().continueKey() : PREFIX + "confirm_enable"),
                        ignored -> {
                            if (confirming == null) publish();
                            else enable(confirming, true);
                        })
                .bounds(x + half + 6, height - 29, w - half - 6, 20).build());
        proceed.active = ready();
        layoutContents(null, true);
        attachment.layoutInitialized();
    }

    private int left() { return Math.max(10, (width - 640) / 2); }
    private int panelWidth() { return Math.max(40, width - left() * 2); }
    private int viewportTop() { return 35; }
    private int viewportBottom() { return height - 38; }

    /** Shared measuring/rendering pass keeps every row and action reachable by scrolling. */
    private void layoutContents(GuideGraphics graphics, boolean buttons) {
        int x = left() + 8;
        int w = panelWidth() - 16;
        int start = viewportTop() + 7 - scroll;
        int y = start;
        if (!failure.isBlank()) {
            y = text(graphics, MinecraftComponents.literal(failure), x, y, w, ERROR) + 12;
        }
        if (actionPending) {
            y = text(graphics, MinecraftComponents.translatable(PREFIX + "working"), x, y, w, ACCENT) + 12;
        }
        if (confirming != null) {
            y = text(graphics, MinecraftComponents.translatable(PREFIX + "enable_change",
                    MinecraftComponents.translatable(confirming.kindKey()), MinecraftComponents.literal(confirming.id())),
                    x, y, w, ACCENT);
            y = text(graphics, MinecraftComponents.translatable(
                    "screen.openallay.settings.extensions.unrestricted.warning"),
                    x, y + 12, w, ERROR);
            y = text(graphics, MinecraftComponents.translatable(PREFIX + "server_restricted"),
                    x, y + 12, w, MUTED);
            y = text(graphics, MinecraftComponents.translatable(PREFIX + "unrestricted_confirm"),
                    x, y + 12, w, TEXT);
        } else {
            y = text(graphics, MinecraftComponents.literal(review.name() + " · " + review.version()), x, y, w, TEXT);
            y = text(graphics, MinecraftComponents.literal(review.id()), x, y + 4, w, MUTED);
            y = text(graphics, MinecraftComponents.translatable(PREFIX + "advisory"), x, y + 10, w, TEXT);
            y = text(graphics, MinecraftComponents.translatable(PREFIX + "scope_local"), x, y + 6, w, MUTED);
            y = text(graphics, MinecraftComponents.translatable(PREFIX + "continue_notice"), x, y + 6, w, MUTED);
            if (review.catalogRequirementsDiffer()) {
                y = text(graphics, MinecraftComponents.translatable(PREFIX + "package_changed"),
                        x, y + 8, w, ACCENT);
            }
            if (savedSettings) {
                y = text(graphics, MinecraftComponents.translatable(PREFIX + "saved_settings"),
                        x, y + 8, w, MUTED);
            }
            RequirementSettingsProjection projection = projection();
            if (projection.rows().isEmpty()) {
                y = text(graphics, MinecraftComponents.translatable(PREFIX + "none"), x, y + 12, w, MUTED);
            }
            for (Row row : projection.rows()) {
                y = text(graphics, rowLabel(row), x, y + 12, w,
                        row.status() == RequirementStatus.SATISFIED ? ACCENT : TEXT);
                if (!row.detail().isBlank()) {
                    y = text(graphics, MinecraftComponents.literal(row.detail()), x, y + 3, w, MUTED);
                }
                if (row.canEnable()) {
                    y = text(graphics, MinecraftComponents.translatable(PREFIX + "enable_change",
                            MinecraftComponents.translatable(row.kindKey()), MinecraftComponents.literal(row.id())),
                            x, y + 4, w, MUTED);
                    if (buttons) {
                        Button enable = addRenderableWidget(OpenAllayButton.create(
                                        MinecraftComponents.translatable(PREFIX + "enable", MinecraftComponents.literal(row.id())),
                                        ignored -> requestEnable(row))
                                .bounds(x, y + 4, w, 20).build());
                        enable.active = ready();
                        enable.visible = y + 4 >= viewportTop() && y + 24 <= viewportBottom();
                    }
                    y += 28;
                }
            }
        }
        contentHeight = y - start + 16;
    }

    public static Component rowLabel(Row row) {
        Component identity = row.kind() == dev.openallay.requirement.RequirementKind.CAPABILITY
                        && dev.openallay.settings.requirement.RequirementSettingsEnvironment
                                .isUnrestrictedJavascript(row.id())
                ? MinecraftComponents.translatable(PREFIX + "name.unrestricted_javascript")
                        .append(MinecraftComponents.literal(" (" + row.id() + ")"))
                : MinecraftComponents.literal(row.name().equals(row.id())
                        ? row.id() : row.name() + " (" + row.id() + ")");
        return MinecraftComponents.translatable(PREFIX + "row",
                MinecraftComponents.translatable(row.kindKey()), identity,
                MinecraftComponents.translatable(row.statusKey()));
    }

    private int text(GuideGraphics graphics, Component value, int x, int y, int w, int color) {
        for (var line : font.split(value, Math.max(20, w))) {
            if (graphics != null) graphics.text(font, line, x, y, color, false);
            y += 11;
        }
        return y;
    }

    private void requestEnable(Row row) {
        if (!ready() || !row.canEnable()) return;
        if (row.unrestrictedConsentRequired()) {
            confirming = row;
            scroll = 0;
            failure = "";
            rebuildWidgets();
        } else {
            enable(row, false);
        }
    }

    private void enable(Row row, boolean consent) {
        if (!ready()) return;
        actionPending = true;
        confirming = null;
        failure = "";
        rebuildWidgets();
        long epoch = attachment.epoch();
        var client = minecraft;
        service.enablePackageRequirement(token, row.kind(), row.id(), consent).thenAccept(result ->
                client.execute(() -> {
                    if (!attached(epoch)) return;
                    actionPending = false;
                    snapshot = service.snapshot();
                    snapshot.requirementReview().filter(value -> value.token() == token)
                            .ifPresent(value -> review = value);
                    if (result instanceof ToolResult.Failure<Boolean> failed) {
                        failure = failed.code() + ": " + failed.message();
                    } else {
                        savedSettings = true;
                    }
                    scroll = 0;
                    rebuildWidgets();
                }));
    }

    private void publish() {
        if (!ready()) return;
        actionPending = true;
        failure = "";
        rebuildWidgets();
        long epoch = attachment.epoch();
        var client = minecraft;
        service.continuePackageInstall(token).thenAccept(result -> client.execute(() -> {
            if (!attached(epoch)) return;
            actionPending = false;
            if (result instanceof ToolResult.Failure<Boolean> failed) {
                failure = failed.code() + ": " + failed.message();
                snapshot = service.snapshot();
                rebuildWidgets();
            } else {
                finished = true;
                dev.openallay.client.gui.MinecraftClientWindow.showScreen(minecraft, parent);
            }
        }));
    }

    @Override
    public void onClose() {
        if (actionPending) return;
        if (confirming != null) {
            confirming = null;
            scroll = 0;
            rebuildWidgets();
            return;
        }
        finished = true;
        service.cancelPackageInstall(token);
        dev.openallay.client.gui.MinecraftClientWindow.showScreen(minecraft, parent);
    }

    @Override
    protected void guideRemoved() {
        attachment.detach();
        if (!finished) service.cancelPackageInstall(token);
        if (listener != null) {
            try { listener.close(); } catch (Exception ignored) { /* Local listener only. */ }
            listener = null;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void scrollBy(int amount) {
        int maximum = Math.max(0, contentHeight - (viewportBottom() - viewportTop()));
        scroll = net.minecraft.util.Mth.clamp(scroll + amount, 0, maximum);
        rebuildWidgets();
    }

    @Override
    public boolean guideKeyPressed(GuideInputKey event) {
        GuideKeyInput input = GuideKeyInput.from(event);
        if (input.intent() == GuideKeyIntent.PAGE_DOWN || input.intent() == GuideKeyIntent.PAGE_UP) {
            int page = Math.max(24, viewportBottom() - viewportTop() - 20);
            scrollBy(input.intent() == GuideKeyIntent.PAGE_DOWN ? page : -page);
            return true;
        }
        if (input.intent() == GuideKeyIntent.HOME || input.intent() == GuideKeyIntent.END) {
            scrollBy(input.intent() == GuideKeyIntent.HOME ? -contentHeight : contentHeight);
            return true;
        }
        return super.guideKeyPressed(event);
    }

    @Override
    public boolean guideMouseScrolled(double x, double y, double dx, double dy) {
        scrollBy(-(int) Math.round(dy * 24));
        return true;
    }

    @Override
    protected void paintGuideScreen(GuideGraphics graphics, int mouseX, int mouseY, float tick) {
        graphics.fill(0, 0, width, height, 0xF00B0D12);
        graphics.text(font, title, left() + 8, 14, ACCENT, false);
        graphics.fill(left(), viewportTop(), left() + panelWidth(), viewportBottom(), 0xE0181B22);
        graphics.enableScissor(left(), viewportTop(), left() + panelWidth(), viewportBottom());
        layoutContents(graphics, false);
        graphics.disableScissor();
        renderGuideWidgets(graphics, mouseX, mouseY, tick);
    }
}
