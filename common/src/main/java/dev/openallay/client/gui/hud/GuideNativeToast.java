package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.OpenAllayWidgetTheme;
import dev.openallay.client.presentation.GuideNotificationPort;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Owned fixed-size native toast. Invalid queued entries cannot draw after teardown. */
public final class GuideNativeToast implements Toast, GuideNotificationPort.Handle {
    private final Object token = new Object();
    private GuideNotificationPort.Notification notification;
    private boolean hidden;
    private long fullyVisible;
    private Font cachedFont;
    private String cachedPreview;
    private net.minecraft.locale.Language cachedLanguage;
    private List<FormattedCharSequence> lines = List.of();

    public GuideNativeToast(GuideNotificationPort.Notification notification) {
        this.notification = Objects.requireNonNull(notification, "notification");
    }

    @Override public Object getToken() { return token; }
    @Override public int width() { return 240; }
    @Override public int height() { return 64; }
    @Override public int occcupiedSlotCount() { return 2; }
    @Override public float yPos(int firstSlotIndex) { return firstSlotIndex * 32.0F; }

    private boolean valid() { return !hidden && notification.fence().valid(); }

    @Override public Visibility getWantedVisibility() {
        return valid() && fullyVisible < notification.durationSeconds() * 1000L
                ? Visibility.SHOW : Visibility.HIDE;
    }

    @Override public void update(ToastManager manager, long fullyVisibleForMs) {
        fullyVisible = fullyVisibleForMs;
    }

    @Override public void update(GuideNotificationPort.Notification next) {
        if (!valid() || !next.fence().valid()) return;
        notification = Objects.requireNonNull(next, "notification");
        cachedPreview = null;
        // Same-task coalescing does not reset native lifetime or grow slot demand.
    }

    @Override public void hide() { hidden = true; }

    @Override public void extractRenderState(
            GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        if (!valid()) return;
        String preview = notification.preview();
        if (font != cachedFont || net.minecraft.locale.Language.getInstance() != cachedLanguage
                || !Objects.equals(preview, cachedPreview)) {
            cachedFont = font;
            cachedLanguage = net.minecraft.locale.Language.getInstance();
            cachedPreview = preview;
            lines = font.split(Component.literal(preview), width() - 20);
        }
        graphics.fill(0, 0, width(), height(), OpenAllayWidgetTheme.CHARCOAL);
        graphics.outline(0, 0, width(), height(), OpenAllayWidgetTheme.SLATE_BORDER);
        String titleKey = notification.taskFailed() ? "screen.openallay.notification.failed"
                : notification.replyCompleted() || notification.taskCompleted()
                        ? "screen.openallay.notification.completed"
                        : "screen.openallay.notification.cards";
        graphics.text(font, Component.translatable(titleKey, notification.cardCount()), 10, 7,
                notification.taskFailed() ? OpenAllayWidgetTheme.AMBER : OpenAllayWidgetTheme.MINT);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.text(font, lines.get(i), 10, 21 + i * 10, OpenAllayWidgetTheme.WHITE);
        }
        Component hint = notification.additionalTasks() > 0
                ? Component.translatable("screen.openallay.notification.more_tasks", notification.additionalTasks())
                : Component.translatable("screen.openallay.notification.view",
                        dev.openallay.client.gui.OpenAllayKeyMappings.OPEN_GUIDE.getTranslatedKeyMessage());
        graphics.text(font, hint, 10, 49, OpenAllayWidgetTheme.MUTED);
    }
}
