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
            else {
final class $oaPattern0_Holder { java.lang.Throwable value; Error bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if ((($oaPattern0_holder.value = failure) instanceof java.lang.Error && (($oaPattern0_holder.bound = (Error) $oaPattern0_holder.value) != null))) {
                if (runtimeFailure != null && (Throwable) $oaPattern0_holder.bound != runtimeFailure) $oaPattern0_holder.bound.addSuppressed(runtimeFailure);
                fatalFailure = $oaPattern0_holder.bound;
            } else if (runtimeFailure != null) { if (runtimeFailure != failure) runtimeFailure.addSuppressed(failure); }
            else runtimeFailure = (RuntimeException) failure;
}
        }
        if (fatalFailure != null) throw fatalFailure;
        if (runtimeFailure != null) throw runtimeFailure;
    }
}
