package dev.openallay.guide.e2e;

/** Typed native callback port; ordinary callers never load the reserved Mixin interface. */
public interface GuideProbeMouseCallbacks {
    void openallay$onMove(long window, double x, double y);
}
