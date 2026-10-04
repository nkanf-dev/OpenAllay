package dev.openallay.extension;

/** Player-visible lifecycle state of an Extension descriptor or staged package. */
public enum OpenAllayExtensionState {
    ACTIVE,
    RESTART_REQUIRED,
    INCOMPATIBLE,
    UNAVAILABLE,
    COMMUNITY
}
