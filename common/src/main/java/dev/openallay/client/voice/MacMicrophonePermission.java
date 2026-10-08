package dev.openallay.client.voice;

import java.util.Locale;
import java.util.Objects;

/** Read-only macOS preflight. Never loads native code until an explicit capture open. */
final class MacMicrophonePermission {
    private MacMicrophonePermission() {}

    static void checkBeforeOpen() throws PermissionException {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac")) return;
        check(new NativeMacMicrophoneAuthorization());
    }

    /** Test seam: injected authorization must not contact the OS. */
    static void check(Authorization authorization) throws PermissionException {
        Objects.requireNonNull(authorization);
        try {
            switch ((authorization.status())) {
case AUTHORIZED:
{
{
                    return;
                }
}
case DENIED:
{
throw new PermissionException(Failure.DENIED,
                        "Microphone access is denied. Enable the game or launcher in System Settings > "
                                + "Privacy & Security > Microphone, then restart the game.");
}
case RESTRICTED:
{
throw new PermissionException(Failure.RESTRICTED,
                        "macOS restricts microphone access for this game. Check Screen Time or device restrictions.");
}
case NOT_DETERMINED:
{
{
                    if (!authorization.hasUsageDescription()) {
                        throw new PermissionException(Failure.LAUNCHER_NOT_PREPARED,
                                "This Java/game host has no NSMicrophoneUsageDescription. Use a macOS launcher "
                                        + "that supplies a microphone usage description for the game process, "
                                        + "then restart and try recording. OpenAllay does not modify app bundles.");
                    }
                    // Only explicit capture open/start may proceed for a prepared host.
                    // Do not construct an Objective-C block or request access during startup/listing.
                    // The provider's first-use permission prompt needs launcher-specific verification.
                }
break;
}
case UNKNOWN:
{
throw new PermissionException(Failure.CHECK_FAILED,
                        "macOS returned an unknown microphone permission status. Restart the game and "
                                + "check System Settings > Privacy & Security > Microphone.");
}
}

        } catch (PermissionException failure) {
            throw failure;
        } catch (Exception | LinkageError failure) {
            throw new PermissionException(Failure.CHECK_FAILED,
                    "Could not check macOS microphone permission. Restart the game with a launcher "
                            + "that supports microphone access.", failure);
        }
    }

    enum Status {
        NOT_DETERMINED, RESTRICTED, DENIED, AUTHORIZED, UNKNOWN
    }

    enum Failure {
        DENIED, RESTRICTED, LAUNCHER_NOT_PREPARED, CHECK_FAILED
    }

    static final class PermissionException extends AudioPermissionException {
        private final Failure failure;

        PermissionException(Failure failure, String message) {
            super(diagnostic(failure), message);
            this.failure = failure;
        }

        PermissionException(Failure failure, String message, Throwable cause) {
            super(diagnostic(failure), message, cause);
            this.failure = failure;
        }

        Failure failure() {
            return failure;
        }

        private static Diagnostic diagnostic(Failure failure) {
            {
dev.openallay.client.voice.AudioPermissionException.Diagnostic $oaSwitch0_exit_result;
$oaSwitch0_exit: {
switch ((failure)) {
case DENIED:
{
$oaSwitch0_exit_result = Diagnostic.DENIED; break $oaSwitch0_exit;
}
case RESTRICTED:
{
$oaSwitch0_exit_result = Diagnostic.RESTRICTED; break $oaSwitch0_exit;
}
case LAUNCHER_NOT_PREPARED:
{
$oaSwitch0_exit_result = Diagnostic.LAUNCHER_NOT_PREPARED; break $oaSwitch0_exit;
}
case CHECK_FAILED:
{
$oaSwitch0_exit_result = Diagnostic.CHECK_FAILED; break $oaSwitch0_exit;
}
default: throw new java.lang.IncompatibleClassChangeError();
}
}
return $oaSwitch0_exit_result;
}
        }
    }

    interface Authorization {
        Status status() throws Exception;
        boolean hasUsageDescription() throws Exception;
    }

}
