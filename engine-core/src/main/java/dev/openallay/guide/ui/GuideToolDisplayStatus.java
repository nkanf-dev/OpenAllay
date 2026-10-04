package dev.openallay.guide.ui;

import dev.openallay.guide.GuideToolStatus;

/** Derived UI state only; never persisted or sent through the Tool protocol. */
public enum GuideToolDisplayStatus {
    RUNNING, SUCCEEDED, FAILED, NO_RESULT_RECORDED;

    public static GuideToolDisplayStatus from(GuideToolStatus actual, boolean requestTerminal) {
        if (requestTerminal && actual == GuideToolStatus.RUNNING) return NO_RESULT_RECORDED;
        return switch (actual) {
            case RUNNING -> RUNNING;
            case SUCCEEDED -> SUCCEEDED;
            case FAILED -> FAILED;
        };
    }

    public String translationKey() {
        return "screen.openallay.detail.tool.status." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
