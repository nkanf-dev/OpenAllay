package dev.openallay.guide.ui;

import java.util.Objects;

/** Current local UI preferences. Background opacity never changes text opacity. */
@dev.openallay.value.ValueType(GuideUiConfig.ValueSchemaProvider.class)
public final class GuideUiConfig {
    private final Fullscreen fullscreen;
    private final Hud hud;
    private final Notifications notifications;
    public GuideUiConfig(Fullscreen fullscreen, Hud hud, Notifications notifications) {

        Objects.requireNonNull(fullscreen, "fullscreen");
        Objects.requireNonNull(hud, "hud");
        Objects.requireNonNull(notifications, "notifications");

        this.fullscreen = fullscreen;
        this.hud = hud;
        this.notifications = notifications;
    }
    public Fullscreen fullscreen() { return fullscreen; }
    public Hud hud() { return hud; }
    public Notifications notifications() { return notifications; }
public static GuideUiConfig defaults() {
        return new GuideUiConfig(Fullscreen.defaults(), Hud.defaults(), Notifications.defaults());
    }
public GuideUiConfig withFullscreen(Fullscreen value) {
        return new GuideUiConfig(value, hud, notifications);
    }
public GuideUiConfig withHud(Hud value) {
        return new GuideUiConfig(fullscreen, value, notifications);
    }
public GuideUiConfig withNotifications(Notifications value) {
        return new GuideUiConfig(fullscreen, hud, value);
    }
public enum Density { COMPACT, COMFORTABLE }
public enum Theme { CHARCOAL, MINT }
public enum NotificationPolicy { WHEN_GUIDE_NOT_VISIBLE, ALWAYS }
public enum Anchor {
        TOP_LEFT(0, 0), TOP_CENTER(.5, 0), TOP_RIGHT(1, 0),
        CENTER_LEFT(0, .5), CENTER(.5, .5), CENTER_RIGHT(1, .5),
        BOTTOM_LEFT(0, 1), BOTTOM_CENTER(.5, 1), BOTTOM_RIGHT(1, 1);

        private final double xFactor;
        private final double yFactor;

        Anchor(double xFactor, double yFactor) {
            this.xFactor = xFactor;
            this.yFactor = yFactor;
        }

        public double xFactor() { return xFactor; }
        public double yFactor() { return yFactor; }
    }
@dev.openallay.value.ValueType(Fullscreen.ValueSchemaProvider.class)
public static final class Fullscreen {
    private final Density density;
    private final boolean sessionRailVisible;
    private final Theme theme;
    public Fullscreen(Density density, boolean sessionRailVisible, Theme theme) {

            Objects.requireNonNull(density, "density");
            Objects.requireNonNull(theme, "theme");

        this.density = density;
        this.sessionRailVisible = sessionRailVisible;
        this.theme = theme;
    }
    public Density density() { return density; }
    public boolean sessionRailVisible() { return sessionRailVisible; }
    public Theme theme() { return theme; }
public static Fullscreen defaults() {
            return new Fullscreen(Density.COMFORTABLE, true, Theme.CHARCOAL);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Fullscreen)) return false;
        Fullscreen that = (Fullscreen) other;
        return java.util.Objects.equals(density, that.density) && sessionRailVisible == that.sessionRailVisible && java.util.Objects.equals(theme, that.theme);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(density);
        hash = 31 * hash + Boolean.hashCode(sessionRailVisible);
        hash = 31 * hash + java.util.Objects.hashCode(theme);
        return hash;
    }
    @Override public String toString() { return "Fullscreen[density=" + density + ", sessionRailVisible=" + sessionRailVisible + ", theme=" + theme + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Fullscreen> schema() {
            return new dev.openallay.value.ValueSchema<>(Fullscreen.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Fullscreen>>asList(new dev.openallay.value.ValueSchema.Component<>(Fullscreen.class, "density", Fullscreen::density), new dev.openallay.value.ValueSchema.Component<>(Fullscreen.class, "sessionRailVisible", Fullscreen::sessionRailVisible), new dev.openallay.value.ValueSchema.Component<>(Fullscreen.class, "theme", Fullscreen::theme)), arguments -> new Fullscreen((Density) arguments[0], (Boolean) arguments[1], (Theme) arguments[2]));
        }
    }
}
@dev.openallay.value.ValueType(Hud.ValueSchemaProvider.class)
public static final class Hud {
    private final boolean enabled;
    private final Anchor anchor;
    private final int offsetX;
    private final int offsetY;
    private final int width;
    private final int height;
    private final double scale;
    private final double backgroundOpacity;
    private final boolean collapsed;
    private final int maxReplyLines;
    private final boolean showLatestReply;
    private final boolean showStreamingPreview;
    private final boolean hideWithDebug;
    private final boolean hideOnOtherScreens;
    public Hud(boolean enabled, Anchor anchor, int offsetX, int offsetY, int width, int height, double scale, double backgroundOpacity, boolean collapsed, int maxReplyLines, boolean showLatestReply, boolean showStreamingPreview, boolean hideWithDebug, boolean hideOnOtherScreens) {

            Objects.requireNonNull(anchor, "anchor");
            range("offsetX", offsetX, -MAX_OFFSET, MAX_OFFSET);
            range("offsetY", offsetY, -MAX_OFFSET, MAX_OFFSET);
            range("width", width, MIN_WIDTH, MAX_WIDTH);
            range("height", height, MIN_HEIGHT, MAX_HEIGHT);
            range("scale", scale, MIN_SCALE, MAX_SCALE);
            range("backgroundOpacity", backgroundOpacity, 0, 1);
            // 0 means AUTO. This is a passive viewport budget, never a content limit.
            range("maxReplyLines", maxReplyLines, 0, 80);

        this.enabled = enabled;
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.backgroundOpacity = backgroundOpacity;
        this.collapsed = collapsed;
        this.maxReplyLines = maxReplyLines;
        this.showLatestReply = showLatestReply;
        this.showStreamingPreview = showStreamingPreview;
        this.hideWithDebug = hideWithDebug;
        this.hideOnOtherScreens = hideOnOtherScreens;
    }
    public boolean enabled() { return enabled; }
    public Anchor anchor() { return anchor; }
    public int offsetX() { return offsetX; }
    public int offsetY() { return offsetY; }
    public int width() { return width; }
    public int height() { return height; }
    public double scale() { return scale; }
    public double backgroundOpacity() { return backgroundOpacity; }
    public boolean collapsed() { return collapsed; }
    public int maxReplyLines() { return maxReplyLines; }
    public boolean showLatestReply() { return showLatestReply; }
    public boolean showStreamingPreview() { return showStreamingPreview; }
    public boolean hideWithDebug() { return hideWithDebug; }
    public boolean hideOnOtherScreens() { return hideOnOtherScreens; }
public static final int MIN_WIDTH = 160;
public static final int MAX_WIDTH = 480;
public static final int MIN_HEIGHT = 44;
public static final int MAX_HEIGHT = 240;
public static final int MAX_OFFSET = 4096;
public static final double MIN_SCALE = .75;
public static final double MAX_SCALE = 1.75;
public static Hud defaults() {
            return new Hud(false, Anchor.TOP_LEFT, 12, 12, 320, 240, 1, .78,
                    false, 18, true, false, true, true);
        }
public Hud withEnabled(boolean value) {
            return new Hud(value, anchor, offsetX, offsetY, width, height, scale,
                    backgroundOpacity, collapsed, maxReplyLines, showLatestReply,
                    showStreamingPreview, hideWithDebug, hideOnOtherScreens);
        }
public Hud withPlacement(Anchor nextAnchor, int x, int y, int w, int h, double s) {
            return new Hud(enabled, nextAnchor, x, y, w, h, s, backgroundOpacity,
                    collapsed, maxReplyLines, showLatestReply, showStreamingPreview,
                    hideWithDebug, hideOnOtherScreens);
        }
public Hud withBackgroundOpacity(double value) {
            return new Hud(enabled, anchor, offsetX, offsetY, width, height, scale, value,
                    collapsed, maxReplyLines, showLatestReply, showStreamingPreview,
                    hideWithDebug, hideOnOtherScreens);
        }
public Hud withCollapsed(boolean value) {
            return new Hud(enabled, anchor, offsetX, offsetY, width, height, scale,
                    backgroundOpacity, value, maxReplyLines, showLatestReply,
                    showStreamingPreview, hideWithDebug, hideOnOtherScreens);
        }
public Hud withContent(int lines, boolean latest, boolean streaming) {
            return new Hud(enabled, anchor, offsetX, offsetY, width, height, scale,
                    backgroundOpacity, collapsed, lines, latest, streaming,
                    hideWithDebug, hideOnOtherScreens);
        }
public Hud withVisibility(boolean debug, boolean screens) {
            return new Hud(enabled, anchor, offsetX, offsetY, width, height, scale,
                    backgroundOpacity, collapsed, maxReplyLines, showLatestReply,
                    showStreamingPreview, debug, screens);
        }
public int backgroundArgb(int rgb) {
            return ((int) Math.round(backgroundOpacity * 255) << 24) | (rgb & 0xFFFFFF);
        }
public int textArgb(int rgb) { return 0xFF000000 | (rgb & 0xFFFFFF); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Hud)) return false;
        Hud that = (Hud) other;
        return enabled == that.enabled && java.util.Objects.equals(anchor, that.anchor) && offsetX == that.offsetX && offsetY == that.offsetY && width == that.width && height == that.height && Double.compare(scale, that.scale) == 0 && Double.compare(backgroundOpacity, that.backgroundOpacity) == 0 && collapsed == that.collapsed && maxReplyLines == that.maxReplyLines && showLatestReply == that.showLatestReply && showStreamingPreview == that.showStreamingPreview && hideWithDebug == that.hideWithDebug && hideOnOtherScreens == that.hideOnOtherScreens;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + java.util.Objects.hashCode(anchor);
        hash = 31 * hash + Integer.hashCode(offsetX);
        hash = 31 * hash + Integer.hashCode(offsetY);
        hash = 31 * hash + Integer.hashCode(width);
        hash = 31 * hash + Integer.hashCode(height);
        hash = 31 * hash + Double.hashCode(scale);
        hash = 31 * hash + Double.hashCode(backgroundOpacity);
        hash = 31 * hash + Boolean.hashCode(collapsed);
        hash = 31 * hash + Integer.hashCode(maxReplyLines);
        hash = 31 * hash + Boolean.hashCode(showLatestReply);
        hash = 31 * hash + Boolean.hashCode(showStreamingPreview);
        hash = 31 * hash + Boolean.hashCode(hideWithDebug);
        hash = 31 * hash + Boolean.hashCode(hideOnOtherScreens);
        return hash;
    }
    @Override public String toString() { return "Hud[enabled=" + enabled + ", anchor=" + anchor + ", offsetX=" + offsetX + ", offsetY=" + offsetY + ", width=" + width + ", height=" + height + ", scale=" + scale + ", backgroundOpacity=" + backgroundOpacity + ", collapsed=" + collapsed + ", maxReplyLines=" + maxReplyLines + ", showLatestReply=" + showLatestReply + ", showStreamingPreview=" + showStreamingPreview + ", hideWithDebug=" + hideWithDebug + ", hideOnOtherScreens=" + hideOnOtherScreens + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Hud> schema() {
            return new dev.openallay.value.ValueSchema<>(Hud.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Hud>>asList(new dev.openallay.value.ValueSchema.Component<>(Hud.class, "enabled", Hud::enabled), new dev.openallay.value.ValueSchema.Component<>(Hud.class, "anchor", Hud::anchor), new dev.openallay.value.ValueSchema.Component<>(Hud.class, "offsetX", Hud::offsetX), new dev.openallay.value.ValueSchema.Component<>(Hud.class, "offsetY", Hud::offsetY), new dev.openallay.value.ValueSchema.Component<>(Hud.class, "width", Hud::width), new dev.openallay.value.ValueSchema.Component<>(Hud.class, "height", Hud::height), new dev.openallay.value.ValueSchema.Component<>(Hud.class, "scale", Hud::scale), new dev.openallay.value.ValueSchema.Component<>(Hud.class, "backgroundOpacity", Hud::backgroundOpacity), new dev.openallay.value.ValueSchema.Component<>(Hud.class, "collapsed", Hud::collapsed), new dev.openallay.value.ValueSchema.Component<>(Hud.class, "maxReplyLines", Hud::maxReplyLines), new dev.openallay.value.ValueSchema.Component<>(Hud.class, "showLatestReply", Hud::showLatestReply), new dev.openallay.value.ValueSchema.Component<>(Hud.class, "showStreamingPreview", Hud::showStreamingPreview), new dev.openallay.value.ValueSchema.Component<>(Hud.class, "hideWithDebug", Hud::hideWithDebug), new dev.openallay.value.ValueSchema.Component<>(Hud.class, "hideOnOtherScreens", Hud::hideOnOtherScreens)), arguments -> new Hud((Boolean) arguments[0], (Anchor) arguments[1], (Integer) arguments[2], (Integer) arguments[3], (Integer) arguments[4], (Integer) arguments[5], (Double) arguments[6], (Double) arguments[7], (Boolean) arguments[8], (Integer) arguments[9], (Boolean) arguments[10], (Boolean) arguments[11], (Boolean) arguments[12], (Boolean) arguments[13]));
        }
    }
}
@dev.openallay.value.ValueType(Notifications.ValueSchemaProvider.class)
public static final class Notifications {
    private final boolean enabled;
    private final NotificationPolicy policy;
    private final boolean replyCompleted;
    private final boolean cardBatches;
    private final boolean taskFailures;
    private final int durationSeconds;
    public Notifications(boolean enabled, NotificationPolicy policy, boolean replyCompleted, boolean cardBatches, boolean taskFailures, int durationSeconds) {

            Objects.requireNonNull(policy, "policy");
            range("durationSeconds", durationSeconds, 3, 15);

        this.enabled = enabled;
        this.policy = policy;
        this.replyCompleted = replyCompleted;
        this.cardBatches = cardBatches;
        this.taskFailures = taskFailures;
        this.durationSeconds = durationSeconds;
    }
    public boolean enabled() { return enabled; }
    public NotificationPolicy policy() { return policy; }
    public boolean replyCompleted() { return replyCompleted; }
    public boolean cardBatches() { return cardBatches; }
    public boolean taskFailures() { return taskFailures; }
    public int durationSeconds() { return durationSeconds; }
public static Notifications defaults() {
            return new Notifications(false, NotificationPolicy.WHEN_GUIDE_NOT_VISIBLE,
                    true, true, true, 6);
        }
public Notifications withEnabled(boolean value) {
            return new Notifications(value, policy, replyCompleted, cardBatches,
                    taskFailures, durationSeconds);
        }
public Notifications withPolicy(NotificationPolicy value) {
            return new Notifications(enabled, value, replyCompleted, cardBatches,
                    taskFailures, durationSeconds);
        }
public Notifications withEvents(boolean replies, boolean cards, boolean failures) {
            return new Notifications(enabled, policy, replies, cards, failures, durationSeconds);
        }
public Notifications withDurationSeconds(int value) {
            return new Notifications(enabled, policy, replyCompleted, cardBatches, taskFailures, value);
        }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Notifications)) return false;
        Notifications that = (Notifications) other;
        return enabled == that.enabled && java.util.Objects.equals(policy, that.policy) && replyCompleted == that.replyCompleted && cardBatches == that.cardBatches && taskFailures == that.taskFailures && durationSeconds == that.durationSeconds;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Boolean.hashCode(enabled);
        hash = 31 * hash + java.util.Objects.hashCode(policy);
        hash = 31 * hash + Boolean.hashCode(replyCompleted);
        hash = 31 * hash + Boolean.hashCode(cardBatches);
        hash = 31 * hash + Boolean.hashCode(taskFailures);
        hash = 31 * hash + Integer.hashCode(durationSeconds);
        return hash;
    }
    @Override public String toString() { return "Notifications[enabled=" + enabled + ", policy=" + policy + ", replyCompleted=" + replyCompleted + ", cardBatches=" + cardBatches + ", taskFailures=" + taskFailures + ", durationSeconds=" + durationSeconds + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Notifications> schema() {
            return new dev.openallay.value.ValueSchema<>(Notifications.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Notifications>>asList(new dev.openallay.value.ValueSchema.Component<>(Notifications.class, "enabled", Notifications::enabled), new dev.openallay.value.ValueSchema.Component<>(Notifications.class, "policy", Notifications::policy), new dev.openallay.value.ValueSchema.Component<>(Notifications.class, "replyCompleted", Notifications::replyCompleted), new dev.openallay.value.ValueSchema.Component<>(Notifications.class, "cardBatches", Notifications::cardBatches), new dev.openallay.value.ValueSchema.Component<>(Notifications.class, "taskFailures", Notifications::taskFailures), new dev.openallay.value.ValueSchema.Component<>(Notifications.class, "durationSeconds", Notifications::durationSeconds)), arguments -> new Notifications((Boolean) arguments[0], (NotificationPolicy) arguments[1], (Boolean) arguments[2], (Boolean) arguments[3], (Boolean) arguments[4], (Integer) arguments[5]));
        }
    }
}
private static void range(String field, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " must be in [" + minimum + ", " + maximum + "]");
        }
    }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GuideUiConfig)) return false;
        GuideUiConfig that = (GuideUiConfig) other;
        return java.util.Objects.equals(fullscreen, that.fullscreen) && java.util.Objects.equals(hud, that.hud) && java.util.Objects.equals(notifications, that.notifications);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(fullscreen);
        hash = 31 * hash + java.util.Objects.hashCode(hud);
        hash = 31 * hash + java.util.Objects.hashCode(notifications);
        return hash;
    }
    @Override public String toString() { return "GuideUiConfig[fullscreen=" + fullscreen + ", hud=" + hud + ", notifications=" + notifications + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<GuideUiConfig> schema() {
            return new dev.openallay.value.ValueSchema<>(GuideUiConfig.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<GuideUiConfig>>asList(new dev.openallay.value.ValueSchema.Component<>(GuideUiConfig.class, "fullscreen", GuideUiConfig::fullscreen), new dev.openallay.value.ValueSchema.Component<>(GuideUiConfig.class, "hud", GuideUiConfig::hud), new dev.openallay.value.ValueSchema.Component<>(GuideUiConfig.class, "notifications", GuideUiConfig::notifications)), arguments -> new GuideUiConfig((Fullscreen) arguments[0], (Hud) arguments[1], (Notifications) arguments[2]));
        }
    }
}
