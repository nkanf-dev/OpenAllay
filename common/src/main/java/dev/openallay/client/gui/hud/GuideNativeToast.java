package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.OpenAllayWidgetTheme;
import dev.openallay.client.presentation.GuideNotificationPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import dev.openallay.client.gui.GuideGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import dev.openallay.platform.minecraft.MinecraftComponents;
import net.minecraft.network.chat.Component;
import dev.openallay.client.gui.GuideTextLine;
import dev.openallay.client.gui.GuideNativeFont;

/** Native owned toast with bounded wrapped card facts and stable slot demand across task upgrades. */
public final class GuideNativeToast extends GuideNativeToastBinding implements GuideNotificationPort.Handle {
    private static final int HEIGHT = 64;
    private static final int MAX_WIDTH = 240;
    private final Object token = new Object();
    private final int availableWidth;
    private GuideNotificationPort.Notification notification;
    private boolean hidden;
    private boolean finished;
    private long fullyVisible;
    private Font cachedFont;
    private net.minecraft.locale.Language cachedLanguage;
    private Display cachedDisplay;
    private Layout layout;
    private int width = 240;
    private long extractedFrames;
    private long lifecycleOrder;
    private long showOrder, updateOrder, hideOrder;
    private ExtractSnapshot extracted;

    void queued() { showOrder = ++lifecycleOrder; }

    public GuideNativeToast(GuideNotificationPort.Notification notification) {
        this.notification = Objects.requireNonNull(notification, "notification");
        availableWidth = MAX_WIDTH;
    }

    public GuideNativeToast(GuideNotificationPort.Notification notification, Font font, int guiWidth) {
        this.notification = Objects.requireNonNull(notification, "notification");
        availableWidth = Math.max(40, Math.min(MAX_WIDTH, guiWidth - 16));
        refreshLayout(font);
    }

    @Override public Object getToken() { return token; }
    @Override public int width() { return width; }
    @Override public int height() { return HEIGHT; }
    @Override protected int guideSlotCount() { return 2; }
    public float yPos(int firstSlotIndex) { return firstSlotIndex * 32.0F; }

    private boolean valid() { return !hidden && !finished && notification.fence().valid(); }

    @Override protected boolean guideToastActive() { return valid(); }

    @Override public boolean finished() { return finished; }

    @Override public void onFinishedRendering() { finished = true; }

    @Override protected Visibility guideWantedVisibility() {
        return valid() && fullyVisible < notification.durationSeconds() * 1000L
                ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override protected void updateGuideToast(long fullyVisibleForMs) {
        fullyVisible = fullyVisibleForMs;
    }

    @Override public void update(GuideNotificationPort.Notification next) {
        Objects.requireNonNull(next, "notification");
        if (!valid() || !next.fence().valid() || !sameTask(notification, next)) return;
        notification = next;
        updateOrder = ++lifecycleOrder;
        cachedDisplay = null;
        if (cachedFont != null) refreshLayout(cachedFont);
        // Native manager captures slots once. Updates never grow height/slots or restart the lifetime.
    }

    private static boolean sameTask(GuideNotificationPort.Notification first, GuideNotificationPort.Notification next) {
        return first.connectionGeneration().equals(next.connectionGeneration())
                && first.actorId().equals(next.actorId()) && first.sessionOwner().equals(next.sessionOwner())
                && first.sessionId().equals(next.sessionId()) && first.requestId().equals(next.requestId())
                && first.fence() == next.fence();
    }

    @Override public void hide() {
        if (hidden) return;
        hidden = true;
        hideOrder = ++lifecycleOrder;
    }

    /** Cached native extraction only. This read never renders, advances frames, or creates UI. */
    Receipt e2eReceipt() {
        ExtractSnapshot snapshot = extracted;
        if (snapshot == null) return null;
        boolean fenced = notification.fence().valid();
        boolean visible = !hidden && !finished && fenced && fullyVisible < notification.durationSeconds() * 1000L;
        return new Receipt(snapshot.frame(), snapshot.connectionGeneration(), snapshot.actorId(), snapshot.sessionOwner(),
                snapshot.sessionId(), snapshot.requestId(), snapshot.title(), snapshot.description(), snapshot.keyHint(),
                snapshot.width(), snapshot.height(), snapshot.slots(), snapshot.titleLineCount(), snapshot.descriptionLineCount(),
                visible, fenced, hidden, showOrder, updateOrder, hideOrder, true);
    }

    /** Pure display selection: card facts stay primary; terminal reply/failure remains a separate line. */
    static Display display(GuideNotificationPort.Notification notification, Component guideKey) {
        Component status = notification.taskFailed() ? MinecraftComponents.translatable("screen.openallay.notification.failed")
                : notification.replyCompleted() || notification.taskCompleted()
                        ? MinecraftComponents.translatable("screen.openallay.notification.completed") : MinecraftComponents.empty();
        Component title, description, secondary;
        if (!notification.cardPreviews().isEmpty()) {
            var card = notification.cardPreviews().get(0);
            title = MinecraftComponents.literal(card.title());
            description = MinecraftComponents.literal(card.description());
            secondary = notification.preview().isBlank() ? MinecraftComponents.empty()
                    : status.copy().append(": ").append(notification.preview());
        } else {
            title = status;
            description = MinecraftComponents.literal(notification.preview());
            secondary = MinecraftComponents.empty();
        }
        var summary = MinecraftComponents.empty();
        if (notification.cardCount() > 1) {
            if (!summary.getString().isBlank()) summary.append(" · ");
            summary.append(MinecraftComponents.translatable("screen.openallay.notification.more_cards", notification.cardCount() - 1));
        }
        if (notification.additionalTasks() > 0) {
            if (!summary.getString().isBlank()) summary.append(" · ");
            summary.append(MinecraftComponents.translatable("screen.openallay.notification.more_tasks", notification.additionalTasks()));
        }
        // 26.2 ToastManager has no native click target. This is the existing generic Guide key only.
        Component hint = guideKey == null ? MinecraftComponents.empty()
                : MinecraftComponents.translatable("screen.openallay.notification.view", guideKey);
        return new Display(title, description, secondary, summary, hint);
    }

    private void refreshLayout(Font font) {
        Component key = dev.openallay.client.gui.OpenAllayKeyMappings.OPEN_GUIDE.isUnbound() ? null
                : dev.openallay.client.gui.OpenAllayKeyMappings.OPEN_GUIDE.getTranslatedKeyMessage();
        Display display = display(notification, key);
        var language = net.minecraft.locale.Language.getInstance();
        if (font == cachedFont && language == cachedLanguage && display.equals(cachedDisplay)) return;
        cachedFont = font;
        cachedLanguage = language;
        cachedDisplay = display;
        width = availableWidth;
        int textWidth = Math.max(1, width - 20);
        int hintWidth = Math.min(textWidth, font.width(display.hint()));
        int summaryWidth = display.hint().getString().isBlank() ? textWidth : textWidth - hintWidth - 8;
        layout = new Layout(wrapped(font, display.title().copy().withStyle(net.minecraft.ChatFormatting.BOLD), textWidth, 1),
                wrapped(font, display.description(), textWidth, display.secondary().getString().isBlank() ? 2 : 1),
                wrapped(font, display.secondary(), textWidth, 1),
                summaryWidth > 8 ? wrapped(font, display.summary(), summaryWidth, 1) : List.of(),
                wrapped(font, display.hint(), Math.max(1, hintWidth), 1), width - 10 - hintWidth);
    }

    private static List<GuideTextLine> wrapped(Font font, Component text, int width, int maximumLines) {
        if (text.getString().isBlank()) return List.of();
        List<GuideTextLine> all = GuideNativeFont.split(font, text, width);
        if (all.size() <= maximumLines) return all;
        List<GuideTextLine> visible = new ArrayList<>(all.subList(0, maximumLines));
        StringBuilder last = new StringBuilder(visible.get(visible.size() - 1).plainText());
        String ending = font.substrByWidth(MinecraftComponents.literal(last.toString()), Math.max(1, width - font.width("…"))).getString();
        visible.set(visible.size() - 1, GuideNativeFont.visual(MinecraftComponents.literal(ending + "…").withStyle(text.getStyle())));
        return List.copyOf(visible);
    }

    @Override protected void paintGuideToast(GuideGraphics graphics, Font font, long fullyVisibleForMs) {
        if (!valid()) return;
        refreshLayout(font);
        graphics.fill(0, 0, width(), height(), OpenAllayWidgetTheme.CHARCOAL);
        graphics.outline(0, 0, width(), height(), OpenAllayWidgetTheme.SLATE_BORDER);
        text(graphics, font, layout.title(), 7,
                notification.taskFailed() ? OpenAllayWidgetTheme.AMBER : OpenAllayWidgetTheme.MINT);
        text(graphics, font, layout.description(), 21, OpenAllayWidgetTheme.WHITE);
        text(graphics, font, layout.secondary(), 31, OpenAllayWidgetTheme.MUTED);
        text(graphics, font, layout.summary(), 49, OpenAllayWidgetTheme.MUTED);
        for (GuideTextLine hint : layout.hint()) graphics.text(font, hint, layout.hintX(), 49, OpenAllayWidgetTheme.MUTED);
        // Only a completed native extraction is a graphical receipt. Reads and lifecycle calls cannot fabricate frames.
        extracted = new ExtractSnapshot(++extractedFrames, notification.connectionGeneration(), notification.actorId(),
                notification.sessionOwner(), notification.sessionId(), notification.requestId(),
                cachedDisplay.title().getString(), cachedDisplay.description().getString(), cachedDisplay.hint().getString(),
                width(), height(), guideSlotCount(), layout.title().size(), layout.description().size());
    }

    private static void text(GuideGraphics graphics, Font font,
                             List<GuideTextLine> lines, int y, int color) {
        for (int index = 0; index < lines.size(); index++) graphics.text(font, lines.get(index), 10, y + index * 10, color);
    }

    public record Receipt(long frame, java.util.UUID connectionGeneration, java.util.UUID actorId,
                          java.util.UUID sessionOwner, String sessionId, java.util.UUID requestId,
                          String title, String description, String keyHint, int width, int height, int slots,
                          int titleLineCount, int descriptionLineCount, boolean visible, boolean fenceValid,
                          boolean ownedHidden, long showOrder, long updateOrder, long hideOrder, boolean noClickTarget) {}
    private record ExtractSnapshot(long frame, java.util.UUID connectionGeneration, java.util.UUID actorId,
                                   java.util.UUID sessionOwner, String sessionId, java.util.UUID requestId,
                                   String title, String description, String keyHint, int width, int height, int slots,
                                   int titleLineCount, int descriptionLineCount) {}
    record Display(Component title, Component description, Component secondary, Component summary, Component hint) {}
    private record Layout(List<GuideTextLine> title, List<GuideTextLine> description,
                          List<GuideTextLine> secondary, List<GuideTextLine> summary,
                          List<GuideTextLine> hint, int hintX) {}
}
