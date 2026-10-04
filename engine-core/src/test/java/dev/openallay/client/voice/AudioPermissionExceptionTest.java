package dev.openallay.client.voice;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Neutral permission diagnostics do not load or call a native capture provider. */
final class AudioPermissionExceptionTest {
    @Test void closedDiagnosticsKeepTheExistingPlayerFacingCodesAndCause() {
        Throwable cause = new IllegalStateException("synthetic authorization failure");
        for (AudioPermissionException.Diagnostic diagnostic : AudioPermissionException.Diagnostic.values()) {
            var failure = new AudioPermissionException(diagnostic, "synthetic permission message", cause);
            assertSame(diagnostic, failure.diagnostic());
            assertEquals("synthetic permission message", failure.getMessage());
            assertSame(cause, failure.getCause());
            assertEquals(switch (diagnostic) {
                case DENIED, RESTRICTED -> "microphone_denied";
                case LAUNCHER_NOT_PREPARED -> "microphone_launcher_unprepared";
                case CHECK_FAILED -> "microphone_permission_unavailable";
            }, VoiceRuntime.safeCode(failure));
        }
    }

    @Test void openOwnerPreservesStructuredPermissionFailureBeforeSelectingAProvider() {
        Throwable cause = new IllegalStateException("synthetic authorization failure");
        var failure = new AudioPermissionException(AudioPermissionException.Diagnostic.CHECK_FAILED,
                "synthetic permission message", cause);
        var selections = new AtomicInteger();
        var owner = new CaptureOpenOwner(() -> { throw failure; }, device -> {
            selections.incrementAndGet();
            throw new AssertionError("Permission preflight must precede provider selection");
        }, Duration.ofSeconds(2));
        var actual = assertThrows(AudioPermissionException.class,
                () -> owner.open("synthetic-device", new VoiceCancellation()));
        assertSame(failure, actual);
        assertSame(cause, actual.getCause());
        assertEquals(0, selections.get());
    }
}
