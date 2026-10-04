package dev.openallay.client.voice;

import java.util.Objects;

/** Structured microphone permission failure, independent of the OS and capture provider. */
public class AudioPermissionException extends Exception {
    public enum Diagnostic { DENIED, RESTRICTED, LAUNCHER_NOT_PREPARED, CHECK_FAILED }

    private final Diagnostic diagnostic;

    public AudioPermissionException(Diagnostic diagnostic, String message) {
        super(message);
        this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
    }

    public AudioPermissionException(Diagnostic diagnostic, String message, Throwable cause) {
        super(message, cause);
        this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
    }

    public Diagnostic diagnostic() { return diagnostic; }
}
