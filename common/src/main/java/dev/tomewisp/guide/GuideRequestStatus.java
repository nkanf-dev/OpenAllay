package dev.tomewisp.guide;

public enum GuideRequestStatus {
    PREPARING,
    COMPACTING,
    MODEL_WAIT,
    TOOL_WAIT,
    RATE_LIMITED,
    COMPLETING,
    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED
}
