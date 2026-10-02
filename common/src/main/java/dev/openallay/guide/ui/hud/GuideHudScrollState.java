package dev.openallay.guide.ui.hud;

import dev.openallay.guide.ui.GuideTranscriptVirtualizer;
import dev.openallay.guide.ui.GuideViewportAnchor;
import java.util.List;

/** One compact viewport's full-content navigation. It never changes the source rows. */
public final class GuideHudScrollState {
    private final GuideTranscriptVirtualizer rows = new GuideTranscriptVirtualizer();
    private double scroll;
    private int viewportHeight;
    private boolean followingLatest;

    public void update(List<GuideTranscriptVirtualizer.Row> replacement, int height) {
        GuideViewportAnchor anchor = rows.anchorAt((int) scroll);
        rows.update(replacement);
        viewportHeight = Math.max(0, height);
        scroll = followingLatest ? maximum() : rows.restore(anchor, (int) scroll, viewportHeight);
    }

    public boolean wheel(double amount) {
        if (!Double.isFinite(amount) || amount == 0) return false;
        scroll = clamp(scroll - amount * 24);
        followingLatest = scroll >= maximum() - 1;
        return true;
    }

    public void page(int direction) { move(scroll + direction * Math.max(20, viewportHeight - 12)); }
    public void first() { move(0); followingLatest = false; }
    public void latest() { scroll = maximum(); followingLatest = true; }
    public void move(double position) {
        scroll = clamp(position);
        followingLatest = scroll >= maximum() - 1;
    }
    public int offset() { return (int) Math.round(scroll); }
    public int maximum() { return rows.maximumScroll(viewportHeight); }
    public int totalHeight() { return rows.totalHeight(); }
    public boolean followingLatest() { return followingLatest; }
    public GuideViewportAnchor anchor() { return rows.anchorAt(offset()); }
    public GuideTranscriptVirtualizer.Window visible(int overscan) {
        return rows.visible(offset(), viewportHeight, Math.max(0, overscan));
    }
    public int rowOffset(int index) { return rows.offset(index); }

    public static int passivePageCount(int contentHeight, int viewportHeight) {
        return viewportHeight <= 0 ? 1 : Math.max(1, (contentHeight + viewportHeight - 1) / viewportHeight);
    }
    public static int passivePageOffset(int contentHeight, int viewportHeight, long ticks) {
        int pages = passivePageCount(contentHeight, viewportHeight);
        int page = (int) Math.floorMod(ticks / 160, pages);
        return Math.min(Math.max(0, contentHeight - viewportHeight), page * Math.max(0, viewportHeight));
    }
    private double clamp(double value) { return Math.max(0, Math.min(maximum(), value)); }
}
