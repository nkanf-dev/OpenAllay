package dev.openallay.guide.ui;

import java.util.ArrayList;
import java.util.List;

/** One measured compact row shared by paint, native child hits, and virtualization. */
public record GuideToolSummaryGeometry(
        GuideUiLayout.Rect card, GuideUiLayout.Rect icon, GuideUiLayout.Rect title,
        GuideUiLayout.Rect status, GuideUiLayout.Rect description,
        List<GuideUiLayout.Rect> capsules, int rowHeight) {
    public static final int SINGLE_LINE_HEIGHT = 28;
    public static final int DESCRIPTION_HEIGHT = 40;
    private static final int PADDING = 6;
    private static final int GAP = 4;

    public GuideToolSummaryGeometry { capsules = List.copyOf(capsules); }

    public static GuideToolSummaryGeometry measure(int x, int y, int width, int statusWidth,
            List<Integer> capsuleWidths, boolean description, int spacing) {
        if (width < 40 || statusWidth < 0 || spacing < 0
                || capsuleWidths.stream().anyMatch(value -> value < 22)) {
            throw new IllegalArgumentException("invalid Tool summary geometry");
        }
        int available = width - PADDING * 2;
        int badgeWidth = Math.min(statusWidth, Math.max(1, available / 3));
        int titleMinimum = Math.max(1, Math.min(72, available / 3));
        int remaining = available - 10 - GAP - titleMinimum - GAP - badgeWidth;
        List<Integer> fitted = new ArrayList<>();
        for (int capsuleWidth : capsuleWidths) {
            if (capsuleWidth + GAP > remaining) break;
            fitted.add(capsuleWidth);
            remaining -= capsuleWidth + GAP;
        }
        int capsuleTotal = fitted.stream().mapToInt(value -> value + GAP).sum();
        int titleWidth = Math.max(1, available - 10 - GAP * 2 - badgeWidth - capsuleTotal);
        int titleX = x + PADDING + 10 + GAP;
        int statusX = titleX + titleWidth + GAP;
        int capsuleX = statusX + badgeWidth + GAP;
        List<GuideUiLayout.Rect> bounds = new ArrayList<>();
        for (int capsuleWidth : fitted) {
            bounds.add(new GuideUiLayout.Rect(capsuleX, y + PADDING, capsuleWidth, 16));
            capsuleX += capsuleWidth + GAP;
        }
        int height = description ? DESCRIPTION_HEIGHT : SINGLE_LINE_HEIGHT;
        return new GuideToolSummaryGeometry(
                new GuideUiLayout.Rect(x, y, width, height),
                new GuideUiLayout.Rect(x + PADDING, y + PADDING + 3, 10, 10),
                new GuideUiLayout.Rect(titleX, y + PADDING + 3, titleWidth, 10),
                new GuideUiLayout.Rect(statusX, y + PADDING + 3, badgeWidth, 10),
                new GuideUiLayout.Rect(titleX, y + PADDING + 18, width - PADDING - (titleX - x), description ? 10 : 0),
                bounds, height + spacing);
    }

    /** Child native actions always win; any remaining card area opens the full Tool detail. */
    public Hit hit(double mouseX, double mouseY) {
        for (int index = 0; index < capsules.size(); index++) {
            if (capsules.get(index).contains(mouseX, mouseY)) return new Hit(Kind.CAPSULE, index);
        }
        return new Hit(card.contains(mouseX, mouseY) ? Kind.DETAIL : Kind.OUTSIDE, -1);
    }
    public enum Kind { CAPSULE, DETAIL, OUTSIDE }
    public record Hit(Kind kind, int capsuleIndex) {}
}
