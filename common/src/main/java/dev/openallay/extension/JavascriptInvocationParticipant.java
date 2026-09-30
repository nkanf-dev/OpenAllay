package dev.openallay.extension;

/** Trusted Extension lifecycle around one JavaScript execution, on its worker thread. */
public interface JavascriptInvocationParticipant {
    /** Stable namespaced contribution ID. */
    String id();

    /**
     * Opens an execution-local scope. A participant must unwind its own partial setup if this
     * method throws. Returned scopes are closed on the same worker, in reverse opening order.
     * The participant decides whether the frozen invocation authority permits its own facade.
     */
    AutoCloseable open(JavascriptInvocationContext context) throws Exception;
}
