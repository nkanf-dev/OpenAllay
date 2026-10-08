package dev.openallay.client.gui.hud;

import dev.openallay.client.gui.MinecraftClientWindow;

import dev.openallay.client.gui.OpenAllayWidgetTheme;
import dev.openallay.client.presentation.GuideNotificationPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.openallay.client.gui.GuideGraphics;
import dev.openallay.platform.minecraft.MinecraftComponents;

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
    private net.minecraft.client.gui.Font cachedFont;
    private Object cachedLanguage;
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

    public GuideNativeToast(GuideNotificationPort.Notification notification, net.minecraft.client.gui.Font font, int guiWidth) {
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

    @Override protected net.minecraft.client.gui.components.toasts.Toast.Visibility guideWantedVisibility() {
        return valid() && fullyVisible < notification.durationSeconds() * 1000L
                ? net.minecraft.client.gui.components.toasts.Toast.Visibility.SHOW : net.minecraft.client.gui.components.toasts.Toast.Visibility.HIDE;
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
    static Display display(GuideNotificationPort.Notification notification, net.minecraft.network.chat.Component guideKey) {
        net.minecraft.network.chat.Component status = notification.taskFailed() ? MinecraftComponents.translatable("screen.openallay.notification.failed")
                : notification.replyCompleted() || notification.taskCompleted()
                        ? MinecraftComponents.translatable("screen.openallay.notification.completed") : MinecraftComponents.empty();
        net.minecraft.network.chat.Component title, description, secondary;
        if (!notification.cardPreviews().isEmpty()) {
            dev.openallay.guide.GuidePresentationEvent.CardPreview card = notification.cardPreviews().get(0);
            title = MinecraftComponents.literal(card.title());
            description = MinecraftComponents.literal(card.description());
            secondary = dev.openallay.util.Java8Strings.isBlank(notification.preview()) ? MinecraftComponents.empty()
                    : MinecraftComponents.append(MinecraftComponents.append(MinecraftComponents.copy(status), ": "), notification.preview());
        } else {
            title = status;
            description = MinecraftComponents.literal(notification.preview());
            secondary = MinecraftComponents.empty();
        }
        net.minecraft.network.chat.Component summary = MinecraftComponents.empty();
        if (notification.cardCount() > 1) {
            if (!dev.openallay.util.Java8Strings.isBlank(MinecraftComponents.getString(summary))) MinecraftComponents.append(summary, " · ");
            MinecraftComponents.append(summary, MinecraftComponents.translatable("screen.openallay.notification.more_cards", notification.cardCount() - 1));
        }
        if (notification.additionalTasks() > 0) {
            if (!dev.openallay.util.Java8Strings.isBlank(MinecraftComponents.getString(summary))) MinecraftComponents.append(summary, " · ");
            MinecraftComponents.append(summary, MinecraftComponents.translatable("screen.openallay.notification.more_tasks", notification.additionalTasks()));
        }
        // 26.2 ToastManager has no native click target. This is the existing generic Guide key only.
        net.minecraft.network.chat.Component hint = guideKey == null ? MinecraftComponents.empty()
                : MinecraftComponents.translatable("screen.openallay.notification.view", guideKey);
        return new Display(title, description, secondary, summary, hint);
    }

    private void refreshLayout(net.minecraft.client.gui.Font font) {
        net.minecraft.network.chat.Component key = dev.openallay.client.gui.GuideNativeKeyMappings.unbound(dev.openallay.client.gui.OpenAllayKeyMappings.OPEN_GUIDE) ? null
                : dev.openallay.client.gui.GuideNativeKeyMappings.display(dev.openallay.client.gui.OpenAllayKeyMappings.OPEN_GUIDE);
        Display display = display(notification, key);
        java.lang.Object language = GuideNativeFont.languageIdentity();
        if (font == cachedFont && language == cachedLanguage && display.equals(cachedDisplay)) return;
        cachedFont = font;
        cachedLanguage = language;
        cachedDisplay = display;
        width = availableWidth;
        int textWidth = Math.max(1, width - 20);
        int hintWidth = Math.min(textWidth, GuideNativeFont.width(font, display.hint()));
        int summaryWidth = dev.openallay.util.Java8Strings.isBlank(MinecraftComponents.getString(display.hint())) ? textWidth : textWidth - hintWidth - 8;
        layout = new Layout(wrapped(font, MinecraftComponents.style(MinecraftComponents.copy(display.title()), net.minecraft.ChatFormatting.BOLD), textWidth, 1),
                wrapped(font, display.description(), textWidth, dev.openallay.util.Java8Strings.isBlank(MinecraftComponents.getString(display.secondary())) ? 2 : 1),
                wrapped(font, display.secondary(), textWidth, 1),
                summaryWidth > 8 ? wrapped(font, display.summary(), summaryWidth, 1) : dev.openallay.util.Java8Collections.listOf(),
                wrapped(font, display.hint(), Math.max(1, hintWidth), 1), width - 10 - hintWidth);
    }

    private static List<GuideTextLine> wrapped(net.minecraft.client.gui.Font font, net.minecraft.network.chat.Component text, int width, int maximumLines) {
        if (dev.openallay.util.Java8Strings.isBlank(MinecraftComponents.getString(text))) return dev.openallay.util.Java8Collections.listOf();
        List<GuideTextLine> all = GuideNativeFont.split(font, text, width);
        if (all.size() <= maximumLines) return all;
        List<GuideTextLine> visible = new ArrayList<>(all.subList(0, maximumLines));
        StringBuilder last = new StringBuilder(visible.get(visible.size() - 1).plainText());
        String ending = MinecraftComponents.getString(GuideNativeFont.substrByWidth(font, MinecraftComponents.literal(last.toString()), Math.max(1, width - GuideNativeFont.width(font, "…"))));
        visible.set(visible.size() - 1, GuideNativeFont.visual(MinecraftComponents.style(MinecraftComponents.literal(ending + "…"), text.getStyle())));
        return dev.openallay.util.Java8Collections.listCopyOf(visible);
    }

    @Override protected void paintGuideToast(GuideGraphics graphics, net.minecraft.client.gui.Font font, long fullyVisibleForMs) {
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
                MinecraftComponents.getString(cachedDisplay.title()), MinecraftComponents.getString(cachedDisplay.description()), MinecraftComponents.getString(cachedDisplay.hint()),
                width(), height(), guideSlotCount(), layout.title().size(), layout.description().size());
    }

    private static void text(GuideGraphics graphics, net.minecraft.client.gui.Font font,
                             List<GuideTextLine> lines, int y, int color) {
        for (int index = 0; index < lines.size(); index++) graphics.text(font, lines.get(index), 10, y + index * 10, color);
    }

    @dev.openallay.value.ValueType(Receipt.ValueSchemaProvider.class)
public static final class Receipt {
    private final long frame;
    private final java.util.UUID connectionGeneration;
    private final java.util.UUID actorId;
    private final java.util.UUID sessionOwner;
    private final String sessionId;
    private final java.util.UUID requestId;
    private final String title;
    private final String description;
    private final String keyHint;
    private final int width;
    private final int height;
    private final int slots;
    private final int titleLineCount;
    private final int descriptionLineCount;
    private final boolean visible;
    private final boolean fenceValid;
    private final boolean ownedHidden;
    private final long showOrder;
    private final long updateOrder;
    private final long hideOrder;
    private final boolean noClickTarget;
    public Receipt(long frame, java.util.UUID connectionGeneration, java.util.UUID actorId, java.util.UUID sessionOwner, String sessionId, java.util.UUID requestId, String title, String description, String keyHint, int width, int height, int slots, int titleLineCount, int descriptionLineCount, boolean visible, boolean fenceValid, boolean ownedHidden, long showOrder, long updateOrder, long hideOrder, boolean noClickTarget) {
        this.frame = frame;
        this.connectionGeneration = connectionGeneration;
        this.actorId = actorId;
        this.sessionOwner = sessionOwner;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.title = title;
        this.description = description;
        this.keyHint = keyHint;
        this.width = width;
        this.height = height;
        this.slots = slots;
        this.titleLineCount = titleLineCount;
        this.descriptionLineCount = descriptionLineCount;
        this.visible = visible;
        this.fenceValid = fenceValid;
        this.ownedHidden = ownedHidden;
        this.showOrder = showOrder;
        this.updateOrder = updateOrder;
        this.hideOrder = hideOrder;
        this.noClickTarget = noClickTarget;
    }
    public long frame() { return frame; }
    public java.util.UUID connectionGeneration() { return connectionGeneration; }
    public java.util.UUID actorId() { return actorId; }
    public java.util.UUID sessionOwner() { return sessionOwner; }
    public String sessionId() { return sessionId; }
    public java.util.UUID requestId() { return requestId; }
    public String title() { return title; }
    public String description() { return description; }
    public String keyHint() { return keyHint; }
    public int width() { return width; }
    public int height() { return height; }
    public int slots() { return slots; }
    public int titleLineCount() { return titleLineCount; }
    public int descriptionLineCount() { return descriptionLineCount; }
    public boolean visible() { return visible; }
    public boolean fenceValid() { return fenceValid; }
    public boolean ownedHidden() { return ownedHidden; }
    public long showOrder() { return showOrder; }
    public long updateOrder() { return updateOrder; }
    public long hideOrder() { return hideOrder; }
    public boolean noClickTarget() { return noClickTarget; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Receipt)) return false;
        Receipt that = (Receipt) other;
        return frame == that.frame && java.util.Objects.equals(connectionGeneration, that.connectionGeneration) && java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(sessionOwner, that.sessionOwner) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(title, that.title) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(keyHint, that.keyHint) && width == that.width && height == that.height && slots == that.slots && titleLineCount == that.titleLineCount && descriptionLineCount == that.descriptionLineCount && visible == that.visible && fenceValid == that.fenceValid && ownedHidden == that.ownedHidden && showOrder == that.showOrder && updateOrder == that.updateOrder && hideOrder == that.hideOrder && noClickTarget == that.noClickTarget;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(frame);
        hash = 31 * hash + java.util.Objects.hashCode(connectionGeneration);
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionOwner);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(keyHint);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + Integer.hashCode(slots);
        hash = 31 * hash + Integer.hashCode(titleLineCount);
        hash = 31 * hash + Integer.hashCode(descriptionLineCount);
        hash = 31 * hash + Boolean.hashCode(visible);
        hash = 31 * hash + Boolean.hashCode(fenceValid);
        hash = 31 * hash + Boolean.hashCode(ownedHidden);
        hash = 31 * hash + Long.hashCode(showOrder);
        hash = 31 * hash + Long.hashCode(updateOrder);
        hash = 31 * hash + Long.hashCode(hideOrder);
        hash = 31 * hash + Boolean.hashCode(noClickTarget);
        return hash;
    }
    @Override public String toString() { return "Receipt[frame=" + frame + ", connectionGeneration=" + connectionGeneration + ", actorId=" + actorId + ", sessionOwner=" + sessionOwner + ", sessionId=" + sessionId + ", requestId=" + requestId + ", title=" + title + ", description=" + description + ", keyHint=" + keyHint + ", width=" + width + ", height=" + height + ", slots=" + slots + ", titleLineCount=" + titleLineCount + ", descriptionLineCount=" + descriptionLineCount + ", visible=" + visible + ", fenceValid=" + fenceValid + ", ownedHidden=" + ownedHidden + ", showOrder=" + showOrder + ", updateOrder=" + updateOrder + ", hideOrder=" + hideOrder + ", noClickTarget=" + noClickTarget + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Receipt> schema() {
            return new dev.openallay.value.ValueSchema<>(Receipt.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Receipt>>asList(new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "frame", Receipt::frame), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "connectionGeneration", Receipt::connectionGeneration), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "actorId", Receipt::actorId), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "sessionOwner", Receipt::sessionOwner), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "sessionId", Receipt::sessionId), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "requestId", Receipt::requestId), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "title", Receipt::title), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "description", Receipt::description), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "keyHint", Receipt::keyHint), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "width", Receipt::width), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "height", Receipt::height), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "slots", Receipt::slots), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "titleLineCount", Receipt::titleLineCount), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "descriptionLineCount", Receipt::descriptionLineCount), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "visible", Receipt::visible), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "fenceValid", Receipt::fenceValid), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "ownedHidden", Receipt::ownedHidden), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "showOrder", Receipt::showOrder), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "updateOrder", Receipt::updateOrder), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "hideOrder", Receipt::hideOrder), new dev.openallay.value.ValueSchema.Component<>(Receipt.class, "noClickTarget", Receipt::noClickTarget)), arguments -> new Receipt((Long) arguments[0], (java.util.UUID) arguments[1], (java.util.UUID) arguments[2], (java.util.UUID) arguments[3], (String) arguments[4], (java.util.UUID) arguments[5], (String) arguments[6], (String) arguments[7], (String) arguments[8], (Integer) arguments[9], (Integer) arguments[10], (Integer) arguments[11], (Integer) arguments[12], (Integer) arguments[13], (Boolean) arguments[14], (Boolean) arguments[15], (Boolean) arguments[16], (Long) arguments[17], (Long) arguments[18], (Long) arguments[19], (Boolean) arguments[20]));
        }
    }
}
    @dev.openallay.value.ValueType(ExtractSnapshot.ValueSchemaProvider.class)
private static final class ExtractSnapshot {
    private final long frame;
    private final java.util.UUID connectionGeneration;
    private final java.util.UUID actorId;
    private final java.util.UUID sessionOwner;
    private final String sessionId;
    private final java.util.UUID requestId;
    private final String title;
    private final String description;
    private final String keyHint;
    private final int width;
    private final int height;
    private final int slots;
    private final int titleLineCount;
    private final int descriptionLineCount;
    private ExtractSnapshot(long frame, java.util.UUID connectionGeneration, java.util.UUID actorId, java.util.UUID sessionOwner, String sessionId, java.util.UUID requestId, String title, String description, String keyHint, int width, int height, int slots, int titleLineCount, int descriptionLineCount) {
        this.frame = frame;
        this.connectionGeneration = connectionGeneration;
        this.actorId = actorId;
        this.sessionOwner = sessionOwner;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.title = title;
        this.description = description;
        this.keyHint = keyHint;
        this.width = width;
        this.height = height;
        this.slots = slots;
        this.titleLineCount = titleLineCount;
        this.descriptionLineCount = descriptionLineCount;
    }
    public long frame() { return frame; }
    public java.util.UUID connectionGeneration() { return connectionGeneration; }
    public java.util.UUID actorId() { return actorId; }
    public java.util.UUID sessionOwner() { return sessionOwner; }
    public String sessionId() { return sessionId; }
    public java.util.UUID requestId() { return requestId; }
    public String title() { return title; }
    public String description() { return description; }
    public String keyHint() { return keyHint; }
    public int width() { return width; }
    public int height() { return height; }
    public int slots() { return slots; }
    public int titleLineCount() { return titleLineCount; }
    public int descriptionLineCount() { return descriptionLineCount; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExtractSnapshot)) return false;
        ExtractSnapshot that = (ExtractSnapshot) other;
        return frame == that.frame && java.util.Objects.equals(connectionGeneration, that.connectionGeneration) && java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(sessionOwner, that.sessionOwner) && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(requestId, that.requestId) && java.util.Objects.equals(title, that.title) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(keyHint, that.keyHint) && width == that.width && height == that.height && slots == that.slots && titleLineCount == that.titleLineCount && descriptionLineCount == that.descriptionLineCount;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Long.hashCode(frame);
        hash = 31 * hash + java.util.Objects.hashCode(connectionGeneration);
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionOwner);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(requestId);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(keyHint);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + Integer.hashCode(slots);
        hash = 31 * hash + Integer.hashCode(titleLineCount);
        hash = 31 * hash + Integer.hashCode(descriptionLineCount);
        return hash;
    }
    @Override public String toString() { return "ExtractSnapshot[frame=" + frame + ", connectionGeneration=" + connectionGeneration + ", actorId=" + actorId + ", sessionOwner=" + sessionOwner + ", sessionId=" + sessionId + ", requestId=" + requestId + ", title=" + title + ", description=" + description + ", keyHint=" + keyHint + ", width=" + width + ", height=" + height + ", slots=" + slots + ", titleLineCount=" + titleLineCount + ", descriptionLineCount=" + descriptionLineCount + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ExtractSnapshot> schema() {
            return new dev.openallay.value.ValueSchema<>(ExtractSnapshot.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ExtractSnapshot>>asList(new dev.openallay.value.ValueSchema.Component<>(ExtractSnapshot.class, "frame", ExtractSnapshot::frame), new dev.openallay.value.ValueSchema.Component<>(ExtractSnapshot.class, "connectionGeneration", ExtractSnapshot::connectionGeneration), new dev.openallay.value.ValueSchema.Component<>(ExtractSnapshot.class, "actorId", ExtractSnapshot::actorId), new dev.openallay.value.ValueSchema.Component<>(ExtractSnapshot.class, "sessionOwner", ExtractSnapshot::sessionOwner), new dev.openallay.value.ValueSchema.Component<>(ExtractSnapshot.class, "sessionId", ExtractSnapshot::sessionId), new dev.openallay.value.ValueSchema.Component<>(ExtractSnapshot.class, "requestId", ExtractSnapshot::requestId), new dev.openallay.value.ValueSchema.Component<>(ExtractSnapshot.class, "title", ExtractSnapshot::title), new dev.openallay.value.ValueSchema.Component<>(ExtractSnapshot.class, "description", ExtractSnapshot::description), new dev.openallay.value.ValueSchema.Component<>(ExtractSnapshot.class, "keyHint", ExtractSnapshot::keyHint), new dev.openallay.value.ValueSchema.Component<>(ExtractSnapshot.class, "width", ExtractSnapshot::width), new dev.openallay.value.ValueSchema.Component<>(ExtractSnapshot.class, "height", ExtractSnapshot::height), new dev.openallay.value.ValueSchema.Component<>(ExtractSnapshot.class, "slots", ExtractSnapshot::slots), new dev.openallay.value.ValueSchema.Component<>(ExtractSnapshot.class, "titleLineCount", ExtractSnapshot::titleLineCount), new dev.openallay.value.ValueSchema.Component<>(ExtractSnapshot.class, "descriptionLineCount", ExtractSnapshot::descriptionLineCount)), arguments -> new ExtractSnapshot((Long) arguments[0], (java.util.UUID) arguments[1], (java.util.UUID) arguments[2], (java.util.UUID) arguments[3], (String) arguments[4], (java.util.UUID) arguments[5], (String) arguments[6], (String) arguments[7], (String) arguments[8], (Integer) arguments[9], (Integer) arguments[10], (Integer) arguments[11], (Integer) arguments[12], (Integer) arguments[13]));
        }
    }
}
    @dev.openallay.value.ValueType(Display.ValueSchemaProvider.class)
static final class Display {
    private final net.minecraft.network.chat.Component title;
    private final net.minecraft.network.chat.Component description;
    private final net.minecraft.network.chat.Component secondary;
    private final net.minecraft.network.chat.Component summary;
    private final net.minecraft.network.chat.Component hint;
    Display(net.minecraft.network.chat.Component title, net.minecraft.network.chat.Component description, net.minecraft.network.chat.Component secondary, net.minecraft.network.chat.Component summary, net.minecraft.network.chat.Component hint) {
        this.title = title;
        this.description = description;
        this.secondary = secondary;
        this.summary = summary;
        this.hint = hint;
    }
    public net.minecraft.network.chat.Component title() { return title; }
    public net.minecraft.network.chat.Component description() { return description; }
    public net.minecraft.network.chat.Component secondary() { return secondary; }
    public net.minecraft.network.chat.Component summary() { return summary; }
    public net.minecraft.network.chat.Component hint() { return hint; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Display)) return false;
        Display that = (Display) other;
        return java.util.Objects.equals(title, that.title) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(secondary, that.secondary) && java.util.Objects.equals(summary, that.summary) && java.util.Objects.equals(hint, that.hint);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(secondary);
        hash = 31 * hash + java.util.Objects.hashCode(summary);
        hash = 31 * hash + java.util.Objects.hashCode(hint);
        return hash;
    }
    @Override public String toString() { return "Display[title=" + title + ", description=" + description + ", secondary=" + secondary + ", summary=" + summary + ", hint=" + hint + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Display> schema() {
            return new dev.openallay.value.ValueSchema<>(Display.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Display>>asList(new dev.openallay.value.ValueSchema.Component<>(Display.class, "title", Display::title), new dev.openallay.value.ValueSchema.Component<>(Display.class, "description", Display::description), new dev.openallay.value.ValueSchema.Component<>(Display.class, "secondary", Display::secondary), new dev.openallay.value.ValueSchema.Component<>(Display.class, "summary", Display::summary), new dev.openallay.value.ValueSchema.Component<>(Display.class, "hint", Display::hint)), arguments -> new Display((net.minecraft.network.chat.Component) arguments[0], (net.minecraft.network.chat.Component) arguments[1], (net.minecraft.network.chat.Component) arguments[2], (net.minecraft.network.chat.Component) arguments[3], (net.minecraft.network.chat.Component) arguments[4]));
        }
    }
}
    @dev.openallay.value.ValueType(Layout.ValueSchemaProvider.class)
private static final class Layout {
    private final List<GuideTextLine> title;
    private final List<GuideTextLine> description;
    private final List<GuideTextLine> secondary;
    private final List<GuideTextLine> summary;
    private final List<GuideTextLine> hint;
    private final int hintX;
    private Layout(List<GuideTextLine> title, List<GuideTextLine> description, List<GuideTextLine> secondary, List<GuideTextLine> summary, List<GuideTextLine> hint, int hintX) {
        this.title = title;
        this.description = description;
        this.secondary = secondary;
        this.summary = summary;
        this.hint = hint;
        this.hintX = hintX;
    }
    public List<GuideTextLine> title() { return title; }
    public List<GuideTextLine> description() { return description; }
    public List<GuideTextLine> secondary() { return secondary; }
    public List<GuideTextLine> summary() { return summary; }
    public List<GuideTextLine> hint() { return hint; }
    public int hintX() { return hintX; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Layout)) return false;
        Layout that = (Layout) other;
        return java.util.Objects.equals(title, that.title) && java.util.Objects.equals(description, that.description) && java.util.Objects.equals(secondary, that.secondary) && java.util.Objects.equals(summary, that.summary) && java.util.Objects.equals(hint, that.hint) && hintX == that.hintX;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(description);
        hash = 31 * hash + java.util.Objects.hashCode(secondary);
        hash = 31 * hash + java.util.Objects.hashCode(summary);
        hash = 31 * hash + java.util.Objects.hashCode(hint);
        hash = 31 * hash + Integer.hashCode(hintX);
        return hash;
    }
    @Override public String toString() { return "Layout[title=" + title + ", description=" + description + ", secondary=" + secondary + ", summary=" + summary + ", hint=" + hint + ", hintX=" + hintX + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Layout> schema() {
            return new dev.openallay.value.ValueSchema<>(Layout.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Layout>>asList(new dev.openallay.value.ValueSchema.Component<>(Layout.class, "title", Layout::title), new dev.openallay.value.ValueSchema.Component<>(Layout.class, "description", Layout::description), new dev.openallay.value.ValueSchema.Component<>(Layout.class, "secondary", Layout::secondary), new dev.openallay.value.ValueSchema.Component<>(Layout.class, "summary", Layout::summary), new dev.openallay.value.ValueSchema.Component<>(Layout.class, "hint", Layout::hint), new dev.openallay.value.ValueSchema.Component<>(Layout.class, "hintX", Layout::hintX)), arguments -> new Layout((List) arguments[0], (List) arguments[1], (List) arguments[2], (List) arguments[3], (List) arguments[4], (Integer) arguments[5]));
        }
    }
}
}
