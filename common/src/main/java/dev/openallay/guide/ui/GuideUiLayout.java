package dev.openallay.guide.ui;

import java.util.ArrayList;
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
    public static GuideUiLayout calculate(int width, int height, boolean detailOpen) {
        return calculate(width, height, detailOpen, 120, 60, 48, 54, true);
    }

    public static GuideUiLayout calculate(
            int width, int height, boolean detailOpen,
            int titleWidth, int sessionsWidth, int exportWidth, int refreshWidth, boolean settings) {
        if (width < 240 || height < 180) throw new IllegalArgumentException("screen is too small");
        int margin = height < 240 ? 4 : 8;
        int available = width - margin * 2;
        Header header = Header.calculate(margin, margin, available,
                titleWidth, sessionsWidth, exportWidth, refreshWidth, settings, height < 240);
        int topHeight = header.status().y() + header.status().height() - margin + 2;
        int progressHeight = 22;
        int composerHeight = 68;
        boolean narrow = width < 560;
        int railWidth = narrow ? 0 : 128;
        boolean inlineDetail = detailOpen && width >= 760;
        int detailWidth = inlineDetail ? 220 : 0;
        int bodyTop = margin + topHeight + margin;
        int composerTop = height - margin - composerHeight;
        int progressTop = composerTop - 4 - progressHeight;
        int bodyBottom = progressTop - margin;
        int bodyHeight = Math.max(0, bodyBottom - bodyTop);
        int transcriptLeft = margin + railWidth + (railWidth == 0 ? 0 : margin);
        int transcriptRight = width - margin - detailWidth - (detailWidth == 0 ? 0 : margin);
        Rect transcript = new Rect(transcriptLeft, bodyTop, transcriptRight - transcriptLeft, bodyHeight);
        int overlayWidth = Math.min(available, Math.max(220, width * 3 / 4));
        Rect detail = !detailOpen
                ? Rect.EMPTY
                : inlineDetail
                        ? new Rect(width - margin - detailWidth, bodyTop, detailWidth, bodyHeight)
                        : new Rect((width - overlayWidth) / 2, bodyTop,
                                overlayWidth, bodyHeight);
        Rect composer = new Rect(transcriptLeft, composerTop, transcript.width(), composerHeight);
        return new GuideUiLayout(
                narrow,
                new Rect(margin, margin, available, topHeight),
                railWidth == 0 ? Rect.EMPTY : new Rect(margin, bodyTop, railWidth, bodyHeight),
                transcript,
                new Rect(transcriptLeft, progressTop, transcript.width(), progressHeight),
                composer,
                detail,
                detailOpen && !inlineDetail,
                header,
                ComposerControls.calculate(composer));
    }

    public record Header(
            Rect title, Rect status, Rect sessions, Rect create, Rect delete,
            Rect export, Rect model, Rect refresh, Rect settings) {
        static Header calculate(
                int x, int y, int width,
                int titleWidth, int sessionsWidth, int exportWidth, int refreshWidth,
                boolean settings, boolean compact) {
            int[] widths = {sessionsWidth, 24, 24, exportWidth, 128, refreshWidth, settings ? 20 : 0};
            int total = java.util.Arrays.stream(widths).sum() + (settings ? 24 : 20);
            // The model can use a shorter label; its tooltip retains the complete identity.
            widths[4] = Math.max(80, Math.min(128, width - (total - 128)));
            total = java.util.Arrays.stream(widths).sum() + (settings ? 24 : 20);
            boolean sharedTitleRow = titleWidth + 20 + total <= width;
            int rowY = y + (sharedTitleRow ? 2 : 18);
            int cursor = sharedTitleRow ? x + width - total : x;
            List<Rect> actions = new ArrayList<>();
            for (int controlWidth : widths) {
                if (controlWidth == 0) {
                    actions.add(Rect.EMPTY);
                    continue;
                }
                if (cursor + controlWidth > x + width) {
                    cursor = x;
                    rowY += y < 8 ? 22 : 24;
                }
                actions.add(new Rect(cursor, rowY, controlWidth, 20));
                cursor += controlWidth + 4;
            }
            int titleAvailable = sharedTitleRow ? actions.getFirst().x() - x - 16 : width - 16;
            return new Header(
                    new Rect(x + 10, y + 4, titleAvailable, 12),
                    new Rect(x + 10, rowY + (compact ? 22 : 24), width - 20, compact ? 0 : 12),
                    actions.get(0), actions.get(1), actions.get(2), actions.get(3),
                    actions.get(4), actions.get(5), actions.get(6));
        }

        public List<Rect> controls() {
            return List.of(sessions, create, delete, export, model, refresh, settings).stream()
                    .filter(rect -> rect.width() > 0).toList();
        }
    }

    public record ComposerControls(Rect input, Rect send, Rect stop, Rect retry) {
        static ComposerControls calculate(Rect area) {
            int actionWidth = 54;
            int x = area.x() + area.width() - actionWidth;
            return new ComposerControls(
                    new Rect(area.x(), area.y(), area.width() - actionWidth - 6, area.height()),
                    new Rect(x, area.y(), actionWidth, 20),
                    new Rect(x, area.y() + 24, actionWidth, 20),
                    new Rect(x, area.y() + 48, actionWidth, 20));
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
