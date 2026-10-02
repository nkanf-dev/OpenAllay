package dev.openallay.guide.ui;

import java.util.Objects;

/** Current local UI preferences. Background opacity never changes text opacity. */
public record GuideUiConfig(Fullscreen fullscreen, Hud hud, Notifications notifications) {
    public GuideUiConfig {
        Objects.requireNonNull(fullscreen, "fullscreen");
        Objects.requireNonNull(hud, "hud");
        Objects.requireNonNull(notifications, "notifications");
    }

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

    public record Fullscreen(
            Density density, boolean sessionRailVisible, boolean toolsCollapsed, Theme theme) {
        public Fullscreen {
            Objects.requireNonNull(density, "density");
            Objects.requireNonNull(theme, "theme");
        }

        public static Fullscreen defaults() {
            return new Fullscreen(Density.COMFORTABLE, true, true, Theme.CHARCOAL);
        }
    }

    public record Hud(
            boolean enabled, Anchor anchor, int offsetX, int offsetY,
            int width, int height, double scale, double backgroundOpacity,
            boolean collapsed, int maxReplyLines, boolean showLatestReply,
            boolean showStreamingPreview, boolean hideWithDebug, boolean hideOnOtherScreens) {
        public static final int MIN_WIDTH = 160;
        public static final int MAX_WIDTH = 480;
        public static final int MIN_HEIGHT = 44;
        public static final int MAX_HEIGHT = 240;
        public static final int MAX_OFFSET = 4096;
        public static final double MIN_SCALE = .75;
        public static final double MAX_SCALE = 1.75;

        public Hud {
            Objects.requireNonNull(anchor, "anchor");
            range("offsetX", offsetX, -MAX_OFFSET, MAX_OFFSET);
            range("offsetY", offsetY, -MAX_OFFSET, MAX_OFFSET);
            range("width", width, MIN_WIDTH, MAX_WIDTH);
            range("height", height, MIN_HEIGHT, MAX_HEIGHT);
            range("scale", scale, MIN_SCALE, MAX_SCALE);
            range("backgroundOpacity", backgroundOpacity, 0, 1);
            range("maxReplyLines", maxReplyLines, 1, 10);
        }

        public static Hud defaults() {
            return new Hud(false, Anchor.TOP_LEFT, 12, 12, 280, 88, 1, .78,
                    false, 3, true, false, true, true);
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
    }

    public record Notifications(
            boolean enabled, NotificationPolicy policy, boolean replyCompleted,
            boolean cardBatches, boolean taskFailures, int durationSeconds) {
        public Notifications {
            Objects.requireNonNull(policy, "policy");
            range("durationSeconds", durationSeconds, 3, 15);
        }

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
    }

    private static void range(String field, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " must be in [" + minimum + ", " + maximum + "]");
        }
    }
}
