package dev.openallay.api.extension;

/** Lazy native-session factory. Capture happens only at an authorized actual world invocation. */
public interface MinecraftWorldAccess {
    /**
     * Unavailable adapters throw ExtensionException rather than pretending an operation succeeded.
     * The adapter binds this scope and exact connection/player/server/dimension on opening.
     */
    WorldSession open(ExtensionInvocation invocation);
}
