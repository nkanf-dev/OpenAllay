package dev.openallay.api.extension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Revocable invocation identity and lifecycle, without player or native world handles. */
public interface ExtensionInvocation {
    enum CallerKind { CONSOLE, PLAYER }
    String extensionId();
    String correlationId();
    Instant capturedAt();
    CallerKind callerKind();
    /** Null for console callers; a player caller has its scoped UUID. */
    UUID callerUuid();
    Optional<String> playerDimension();
    void requireActive();
    boolean isCancelled();
    /** Register a callback, including immediate notification if already cancelled. No listener handle. */
    void onCancel(Runnable listener);
    boolean completedSuccessfully();
    void recordEvidence(ExtensionEvidence evidence);
}
