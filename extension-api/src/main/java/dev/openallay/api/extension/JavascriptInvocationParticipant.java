package dev.openallay.api.extension;

/** Trusted Extension scope opened and closed on the same script worker, in reverse close order. */
public interface JavascriptInvocationParticipant {
    String id();
    /** The participant unwinds its own partial setup if opening fails. */
    AutoCloseable open(ExtensionInvocation context) throws Exception;
}
