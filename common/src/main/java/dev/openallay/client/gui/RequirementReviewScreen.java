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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraft.network.chat.Component;

/** Reviews the already checked candidate; only Continue anyway publishes it. */
public final class RequirementReviewScreen extends Screen {
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
        super(Component.translatable(PREFIX + "title"));
        this.service = Objects.requireNonNull(service, "service");
        this.parent = Objects.requireNonNull(parent, "parent");
        this.review = Objects.requireNonNull(review, "review");
        this.token = review.token();
        this.snapshot = service.snapshot();
    }

    @Override
    public void added() {
        long epoch = attachment.attach();
        listener = service.listen(next -> {
            if (!attached(epoch)) return;
            snapshot = next;
            RequirementReview current = next.requirementReview().orElse(null);
            if (current != null && current.token() == token) {
                review = current;
            } else if (!actionPending && !finished) {
                failure = Component.translatable(PREFIX + "expired").getString();
            }
            if (minecraft != null) rebuildWidgets();
        });
    }

    private boolean attached(long epoch) {
        return attachment.isCurrent(epoch);
    }

    /** Pure lifecycle guard. Detached callbacks cannot update or reopen any screen. */
    static final class AttachmentState {
        private long epoch;
        private boolean attached;

        long attach() {
            attached = true;
            return ++epoch;
        }

        void detach() {
            attached = false;
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
    protected void init() {
        int x = left();
        int w = panelWidth();
        int half = (w - 6) / 2;
        Button cancel = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(PREFIX + "cancel"), ignored -> {
                            if (confirming == null) onClose();
                            else { confirming = null; scroll = 0; rebuildWidgets(); }
                        })
                .bounds(x, height - 29, half, 20).build());
        cancel.active = !actionPending;
        Button proceed = addRenderableWidget(OpenAllayButton.create(
                        Component.translatable(confirming == null
                                ? projection().continueKey() : PREFIX + "confirm_enable"),
                        ignored -> {
                            if (confirming == null) publish();
                            else enable(confirming, true);
                        })
                .bounds(x + half + 6, height - 29, w - half - 6, 20).build());
        proceed.active = ready();
        layoutContents(null, true);
    }

    private int left() { return Math.max(10, (width - 640) / 2); }
    private int panelWidth() { return Math.max(40, width - left() * 2); }
    private int viewportTop() { return 35; }
    private int viewportBottom() { return height - 38; }

    /** Shared measuring/rendering pass keeps every row and action reachable by scrolling. */
    private void layoutContents(GuiGraphicsExtractor graphics, boolean buttons) {
        int x = left() + 8;
        int w = panelWidth() - 16;
        int start = viewportTop() + 7 - scroll;
        int y = start;
        if (!failure.isBlank()) {
            y = text(graphics, Component.literal(failure), x, y, w, ERROR) + 12;
        }
        if (actionPending) {
            y = text(graphics, Component.translatable(PREFIX + "working"), x, y, w, ACCENT) + 12;
        }
        if (confirming != null) {
            y = text(graphics, Component.translatable(PREFIX + "enable_change",
                    Component.translatable(confirming.kindKey()), Component.literal(confirming.id())),
                    x, y, w, ACCENT);
            y = text(graphics, Component.translatable(
                    "screen.openallay.settings.extensions.unrestricted.warning"),
                    x, y + 12, w, ERROR);
            y = text(graphics, Component.translatable(PREFIX + "server_restricted"),
                    x, y + 12, w, MUTED);
            y = text(graphics, Component.translatable(PREFIX + "unrestricted_confirm"),
                    x, y + 12, w, TEXT);
        } else {
            y = text(graphics, Component.literal(review.name() + " · " + review.version()), x, y, w, TEXT);
            y = text(graphics, Component.literal(review.id()), x, y + 4, w, MUTED);
            y = text(graphics, Component.translatable(PREFIX + "advisory"), x, y + 10, w, TEXT);
            y = text(graphics, Component.translatable(PREFIX + "scope_local"), x, y + 6, w, MUTED);
            y = text(graphics, Component.translatable(PREFIX + "continue_notice"), x, y + 6, w, MUTED);
            if (review.catalogRequirementsDiffer()) {
                y = text(graphics, Component.translatable(PREFIX + "package_changed"),
                        x, y + 8, w, ACCENT);
            }
            if (savedSettings) {
                y = text(graphics, Component.translatable(PREFIX + "saved_settings"),
                        x, y + 8, w, MUTED);
            }
            RequirementSettingsProjection projection = projection();
            if (projection.rows().isEmpty()) {
                y = text(graphics, Component.translatable(PREFIX + "none"), x, y + 12, w, MUTED);
            }
            for (Row row : projection.rows()) {
                y = text(graphics, rowLabel(row), x, y + 12, w,
                        row.status() == RequirementStatus.SATISFIED ? ACCENT : TEXT);
                if (!row.detail().isBlank()) {
                    y = text(graphics, Component.literal(row.detail()), x, y + 3, w, MUTED);
                }
                if (row.canEnable()) {
                    y = text(graphics, Component.translatable(PREFIX + "enable_change",
                            Component.translatable(row.kindKey()), Component.literal(row.id())),
                            x, y + 4, w, MUTED);
                    if (buttons) {
                        Button enable = addRenderableWidget(OpenAllayButton.create(
                                        Component.translatable(PREFIX + "enable", Component.literal(row.id())),
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
        Component identity = Component.literal(row.name().equals(row.id())
                ? row.id() : row.name() + " (" + row.id() + ")");
        return Component.translatable(PREFIX + "row",
                Component.translatable(row.kindKey()), identity,
                Component.translatable(row.statusKey()));
    }

    private int text(GuiGraphicsExtractor graphics, Component value, int x, int y, int w, int color) {
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
                minecraft.setScreenAndShow(parent);
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
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public void removed() {
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
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN || event.key() == GLFW.GLFW_KEY_PAGE_UP) {
            int page = Math.max(24, viewportBottom() - viewportTop() - 20);
            scrollBy(event.key() == GLFW.GLFW_KEY_PAGE_DOWN ? page : -page);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_HOME || event.key() == GLFW.GLFW_KEY_END) {
            scrollBy(event.key() == GLFW.GLFW_KEY_HOME ? -contentHeight : contentHeight);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scrollBy(-(int) Math.round(dy * 24));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tick) {
        graphics.fill(0, 0, width, height, 0xF00B0D12);
        graphics.text(font, title, left() + 8, 14, ACCENT, false);
        graphics.fill(left(), viewportTop(), left() + panelWidth(), viewportBottom(), 0xE0181B22);
        graphics.enableScissor(left(), viewportTop(), left() + panelWidth(), viewportBottom());
        layoutContents(graphics, false);
        graphics.disableScissor();
        super.extractRenderState(graphics, mouseX, mouseY, tick);
    }
}
