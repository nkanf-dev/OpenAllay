package dev.openallay.guide.ui.hud;

import dev.openallay.guide.ui.GuideTranscriptVirtualizer;
import dev.openallay.guide.ui.GuideViewportAnchor;
import java.util.List;

/** One compact viewport's full-content navigation. It never changes the source rows. */
public final class GuideHudScrollState {
    private final GuideTranscriptVirtualizer rows = new GuideTranscriptVirtualizer();
    private double scroll;
    private int viewportHeight;
    private boolean followingLatest = true;

    public void update(List<GuideTranscriptVirtualizer.Row> replacement, int height) {
        GuideViewportAnchor anchor = rows.anchorAt((int) scroll);
        rows.update(replacement);
        viewportHeight = Math.max(0, height);
        scroll = followingLatest ? maximum() : rows.restore(anchor, (int) scroll, viewportHeight);
    }

    public boolean wheel(double amount) {
        if (!Double.isFinite(amount) || amount == 0) return false;
        scroll = clamp(scroll - amount * 24);
        // Manual reading owns its position, even when a wheel step hits the current boundary.
        followingLatest = false;
        return true;
    }

    public void page(int direction) { move(scroll + direction * Math.max(20, viewportHeight - 12)); }
    public void first() { move(0); followingLatest = false; }
    public void latest() { scroll = maximum(); followingLatest = true; }
    public void move(double position) {
        scroll = clamp(position);
        followingLatest = false;
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

    private double clamp(double value) { return Math.max(0, Math.min(maximum(), value)); }
}
