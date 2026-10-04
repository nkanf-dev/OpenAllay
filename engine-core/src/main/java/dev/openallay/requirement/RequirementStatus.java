package dev.openallay.requirement;

/** UI state, never an authorization or installation decision. */
public enum RequirementStatus {
    SATISFIED,
    DISABLED,
    MISSING,
    UNAVAILABLE,
    UNKNOWN,
    RESTART_REQUIRED
}
