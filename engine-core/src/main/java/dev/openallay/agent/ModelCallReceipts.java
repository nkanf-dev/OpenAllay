package dev.openallay.agent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Internal completion gate: visible cancellation can precede a dispatched call's receipt. */
public final class ModelCallReceipts {
    private final Set<UUID> pending = new HashSet<>();
    private final CompletableFuture<Void> sealed = new CompletableFuture<>();
    private boolean finished;

    public synchronized void accept(AgentEvent event, Consumer<AgentEvent> handoff) {
        AgentEvent.requireKnown(event);
        if (event instanceof AgentEvent.ModelUsageStarted) pending.add(((AgentEvent.ModelUsageStarted) event).callId());
        try { handoff.accept(event); }
        finally {
            if (event instanceof AgentEvent.ModelUsageObserved) pending.remove(((AgentEvent.ModelUsageObserved) event).callId());
            sealIfFinished();
        }
    }

    public <T> CompletableFuture<T> after(CompletableFuture<T> engine) {
        CompletableFuture<T> result = new CompletableFuture<>();
        engine.whenComplete((value, failure) -> {
            synchronized (this) {
                finished = true;
                sealIfFinished();
            }
            sealed.whenComplete((ignored, unused) -> {
                if (failure == null) result.complete(value);
                else result.completeExceptionally(failure);
            });
        });
        return result;
    }

    private void sealIfFinished() {
        if (finished && pending.isEmpty()) sealed.complete(null);
    }
}
