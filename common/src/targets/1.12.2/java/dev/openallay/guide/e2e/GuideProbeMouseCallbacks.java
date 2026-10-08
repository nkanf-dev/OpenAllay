package dev.openallay.guide.e2e;

/** Exact native GuiScreen event facts and callback. Callers do not load a reserved Mixin type. */
public interface GuideProbeMouseCallbacks {
    int openallay$heldButton();
    long openallay$lastMouseEvent();
    void openallay$mouseClickMove(int x, int y, int heldButton, long elapsed);
}
