package dev.openallay.server;

import dev.openallay.bridge.protocol.ServerAgentEventPayload;
import java.util.UUID;

@FunctionalInterface
public interface ServerGuideEvents {
    void send(UUID actorId, ServerAgentEventPayload event);
    default ServerGuideEvents bind(UUID actor) { return this; }
    /** Retirement is action/drop completion, never a network receipt. */
    default void send(UUID actor, ServerAgentEventPayload event, Runnable retired) {
        RuntimeException runtimeFailure = null;
        Error fatalFailure = null;
        try { send(actor, event); }
        catch (RuntimeException failure) { runtimeFailure = failure; }
        catch (Error failure) { fatalFailure = failure; }
        try { retired.run(); }
        catch (RuntimeException | Error failure) {
            if (fatalFailure != null) { if (fatalFailure != failure) fatalFailure.addSuppressed(failure); }
            else if (failure instanceof Error fatal) {
                if (runtimeFailure != null && (Throwable) fatal != runtimeFailure) fatal.addSuppressed(runtimeFailure);
                fatalFailure = fatal;
            } else if (runtimeFailure != null) { if (runtimeFailure != failure) runtimeFailure.addSuppressed(failure); }
            else runtimeFailure = (RuntimeException) failure;
        }
        if (fatalFailure != null) throw fatalFailure;
        if (runtimeFailure != null) throw runtimeFailure;
    }
}
