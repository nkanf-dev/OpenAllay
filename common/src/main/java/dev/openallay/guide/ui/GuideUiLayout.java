package dev.openallay.guide.ui;

import java.util.List;

/** Deterministic responsive layout; rendering code consumes these rectangles. */
public record GuideUiLayout(
        boolean narrow,
        Rect topBar,
        Rect sessionRail,
        Rect transcript,
        Rect progress,
        Rect composer,
        Rect detail,
        boolean detailOverlay,
        Header header,
        ComposerControls composerControls) {
    private static final int MIN_TRANSCRIPT_HEIGHT = 54; // Four 10 px lines plus viewport padding.
    private static final int NOTICE_HEIGHT = 10;
    private static final int TELEMETRY_HEIGHT = 14;

    public static GuideUiLayout calculate(int width, int height, boolean detailOpen) {
        return calculate(width, height, detailOpen, true);
    }

    public static GuideUiLayout calculate(int width, int height, boolean detailOpen, boolean showRail) {
        return calculate(width, height, detailOpen, 120, 60, 48, 54, true,
                false, false, 0, showRail);
    }

    public static GuideUiLayout calculate(
            int width, int height, boolean detailOpen,
            int titleWidth, int sessionsWidth, int exportWidth, int refreshWidth, boolean settings) {
        return calculate(width, height, detailOpen, titleWidth, sessionsWidth, exportWidth,
                refreshWidth, settings, false, false, 0);
    }

    public static GuideUiLayout calculate(
            int width, int height, boolean detailOpen,
            int titleWidth, int sessionsWidth, int exportWidth, int refreshWidth, boolean settings,
            boolean images, boolean activeMode, int pendingMessages) {
        return calculate(width, height, detailOpen, titleWidth, sessionsWidth, exportWidth,
                refreshWidth, settings, images, activeMode, pendingMessages, true);
    }

    public static GuideUiLayout calculate(
            int width, int height, boolean detailOpen,
            int titleWidth, int sessionsWidth, int exportWidth, int refreshWidth, boolean settings,
            boolean images, boolean activeMode, int pendingMessages, boolean showRail) {
        if (width < 240 || height < 180) throw new IllegalArgumentException("screen is too small");
        int margin = height < 240 ? 4 : 8;
        int available = width - margin * 2;
        Header header = Header.calculate(margin, margin, available,
                titleWidth, sessionsWidth, settings);
        Rect topBar = new Rect(margin, margin, available, 24);
        int bodyTop = topBar.bottom() + 2;
        int progressHeight = activeMode ? (height < 240 ? 12 : 22) : 0;
        boolean footer = activeMode || pendingMessages > 0;
        // On short screens attachments and the queue remain compact summaries.
        int desiredComposerHeight = height < 360
                ? Math.min(68, 44 + (images ? 14 : 0) + (footer ? 14 : 0))
                : 44 + (images ? 42 : 0) + (footer ? 20 : 0)
                        + Math.min(2, Math.max(0, pendingMessages - 1)) * 20;
        int composerBottom = height - margin - TELEMETRY_HEIGHT - 2;
        int composerBudget = composerBottom - bodyTop - MIN_TRANSCRIPT_HEIGHT
                - progressHeight - NOTICE_HEIGHT - 2;
        int composerHeight = Math.min(desiredComposerHeight, composerBudget);
        int composerTop = composerBottom - composerHeight;
        int progressTop = composerTop - NOTICE_HEIGHT - progressHeight;
        int bodyHeight = progressTop - 2 - bodyTop;
        boolean narrow = width < 560;
        int railWidth = narrow || !showRail ? 0 : 128;
        boolean inlineDetail = detailOpen && width >= 760;
        int detailWidth = inlineDetail ? 220 : 0;
        int transcriptLeft = margin + railWidth + (railWidth == 0 ? 0 : margin);
        int transcriptRight = width - margin - detailWidth - (detailWidth == 0 ? 0 : margin);
        Rect transcript = new Rect(transcriptLeft, bodyTop, transcriptRight - transcriptLeft, bodyHeight);
        int overlayWidth = Math.min(available, Math.max(220, width * 3 / 4));
        Rect detail = !detailOpen
                ? Rect.EMPTY
                : inlineDetail
                        ? new Rect(width - margin - detailWidth, bodyTop, detailWidth, bodyHeight)
                        : new Rect((width - overlayWidth) / 2, bodyTop, overlayWidth, bodyHeight);
        Rect composer = new Rect(transcriptLeft, composerTop, transcript.width(), composerHeight);
        return new GuideUiLayout(
                narrow,
                topBar,
                railWidth == 0 ? Rect.EMPTY : new Rect(margin, bodyTop, railWidth, bodyHeight),
                transcript,
                new Rect(transcriptLeft, progressTop, transcript.width(), progressHeight),
                composer,
                detail,
                detailOpen && !inlineDetail,
                header,
                ComposerControls.calculate(composer, activeMode));
    }

    /** Shared budget: preserve the input, then expand attachment and queue summaries if there is room. */
    public ComposerExtras composerExtras(boolean images, boolean activeMode, int pendingMessages) {
        Rect area = composerControls.input();
        boolean footer = activeMode || pendingMessages > 0;
        int footerHeight = footer ? 12 : 0;
        int imageHeight = images ? 12 : 0;
        int gaps = (images ? 2 : 0) + (footer ? 2 : 0);
        int remaining = area.height() - 24 - footerHeight - imageHeight - gaps;
        int inputHeight = 24 + Math.min(20, remaining);
        remaining -= inputHeight - 24;
        int footerExtra = footer ? Math.min(6, remaining) : 0;
        footerHeight += footerExtra;
        remaining -= footerExtra;
        int imageExtra = images ? Math.min(28, remaining) : 0;
        imageHeight += imageExtra;
        remaining -= imageExtra;
        int pendingHeight = Math.min(Math.min(2, Math.max(0, pendingMessages - 1)) * 20,
                remaining / 20 * 20);
        inputHeight += remaining - pendingHeight;
        Rect input = new Rect(area.x(), area.y(), area.width(), inputHeight);
        Rect imageStrip = images
                ? new Rect(area.x(), input.bottom() + 2, area.width(), imageHeight) : Rect.EMPTY;
        int footerY = input.bottom() + (images ? imageHeight + 2 : 0) + (footer ? 2 : 0);
        Rect footerRect = footer
                ? new Rect(area.x(), footerY, area.width(), footerHeight) : Rect.EMPTY;
        Rect pending = pendingHeight == 0 ? Rect.EMPTY
                : new Rect(area.x(), footerRect.bottom(), area.width(), pendingHeight);
        return new ComposerExtras(input, imageStrip, footerRect, pending);
    }

    public record ComposerExtras(Rect input, Rect images, Rect footer, Rect pending) {}

    /** Reserved near the input so a rejected draft can show an error even on the shortest screen. */
    public Rect composerNotice() {
        return new Rect(composer.x(), composer.y() - NOTICE_HEIGHT, composer.width(), NOTICE_HEIGHT);
    }

    /** Usage stays visible in one strip, independent of the session rail. */
    public Rect telemetry() {
        return new Rect(composer.x(), composer.bottom() + 2, composer.width(), TELEMETRY_HEIGHT);
    }

    public record Header(
            Rect title, Rect status, Rect sessions, Rect create, Rect delete,
            Rect export, Rect model, Rect refresh, Rect settings, Rect overflow) {
        static Header calculate(
                int x, int y, int width, int titleWidth, int sessionsWidth, boolean settings) {
            int settingsWidth = settings ? 20 : 0;
            int overflowWidth = 20;
            int gaps = settings ? 12 : 8;
            int sessions = Math.min(96, Math.max(48, sessionsWidth));
            int modelWidth = Math.min(128, width - sessions - settingsWidth - overflowWidth - gaps);
            int total = sessions + modelWidth + settingsWidth + overflowWidth + gaps;
            int cursor = x + width - total;
            int titleAvailable = Math.max(0, cursor - x - 10);
            int visibleTitleWidth = titleAvailable < 32 ? 0 : Math.min(Math.max(0, titleWidth), titleAvailable);
            Rect title = new Rect(x + 2, y + 6, visibleTitleWidth, 12);
            Rect sessionsRect = new Rect(cursor, y + 2, sessions, 20);
            cursor = sessionsRect.right() + 4;
            Rect model = new Rect(cursor, y + 2, modelWidth, 20);
            cursor = model.right() + 4;
            Rect overflow = new Rect(cursor, y + 2, overflowWidth, 20);
            cursor = overflow.right() + 4;
            Rect settingsRect = settings ? new Rect(cursor, y + 2, settingsWidth, 20) : Rect.EMPTY;
            return new Header(title, new Rect(x, y + 22, width, 0), sessionsRect,
                    Rect.EMPTY, Rect.EMPTY, Rect.EMPTY, model, Rect.EMPTY, settingsRect, overflow);
        }

        public List<Rect> controls() {
            return List.of(sessions, model, overflow, settings).stream()
                    .filter(rect -> rect.width() > 0 && rect.height() > 0).toList();
        }
    }

    public record ComposerControls(Rect input, Rect send, Rect stop, Rect retry) {
        static ComposerControls calculate(Rect area, boolean activeMode) {
            int actionWidth = 54;
            int x = area.right() - actionWidth;
            return new ComposerControls(
                    new Rect(area.x(), area.y(), area.width() - actionWidth - 6, area.height()),
                    new Rect(x, area.y(), actionWidth, 20),
                    activeMode ? new Rect(x, area.y() + 24, actionWidth, 20) : Rect.EMPTY,
                    Rect.EMPTY);
        }
    }

    public record Rect(int x, int y, int width, int height) {
        public static final Rect EMPTY = new Rect(0, 0, 0, 0);
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }
}
