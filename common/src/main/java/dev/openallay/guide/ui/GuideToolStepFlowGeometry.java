package dev.openallay.guide.ui;

/** Exact measured geometry shared by Step Flow drawing, hit regions, and virtualization. */
public record GuideToolStepFlowGeometry(
        GuideUiLayout.Rect card,
        GuideUiLayout.Rect title,
        GuideUiLayout.Rect status,
        GuideUiLayout.Rect toggle,
        GuideUiLayout.Rect batch,
        GuideUiLayout.Rect body,
        GuideUiLayout.Rect detail,
        int rowHeight) {
    public static final int PADDING = 7;
    public static final int CONTROL = 16;

    public static int bodyWidth(int width) { return Math.max(1, width - PADDING * 2); }
    public static int titleWidth(int width) { return Math.max(1, width - PADDING * 2 - CONTROL - 4); }

    public static GuideToolStepFlowGeometry measure(
            int x, int y, int width, int titleHeight, int statusHeight,
            int bodyHeight, boolean expanded, int spacing) {
        if (width <= 0 || titleHeight < 10 || statusHeight < 10 || bodyHeight < 0 || spacing < 0) {
            throw new IllegalArgumentException("invalid Step Flow geometry");
        }
        int headerHeight = PADDING + Math.max(CONTROL, titleHeight) + 3 + Math.max(CONTROL, statusHeight);
        int detailHeight = expanded ? CONTROL + 4 : 0;
        int height = headerHeight + (expanded ? bodyHeight + 5 : 0) + detailHeight + PADDING;
        int right = x + width;
        GuideUiLayout.Rect empty = new GuideUiLayout.Rect(x + PADDING, y + headerHeight, bodyWidth(width), 0);
        return new GuideToolStepFlowGeometry(
                new GuideUiLayout.Rect(x, y, width, height),
                new GuideUiLayout.Rect(x + PADDING, y + PADDING, titleWidth(width), titleHeight),
                new GuideUiLayout.Rect(x + PADDING, y + PADDING + Math.max(CONTROL, titleHeight) + 3,
                        titleWidth(width), statusHeight),
                new GuideUiLayout.Rect(right - PADDING - CONTROL, y + PADDING, CONTROL, CONTROL),
                new GuideUiLayout.Rect(right - PADDING - CONTROL, y + PADDING + Math.max(CONTROL, titleHeight) + 3,
                        CONTROL, CONTROL),
                expanded ? new GuideUiLayout.Rect(x + PADDING, y + headerHeight, bodyWidth(width), bodyHeight) : empty,
                expanded ? new GuideUiLayout.Rect(x + PADDING, y + height - PADDING - CONTROL,
                        bodyWidth(width), CONTROL) : empty,
                height + spacing);
    }
}
