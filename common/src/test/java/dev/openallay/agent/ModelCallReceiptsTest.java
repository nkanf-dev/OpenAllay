package dev.openallay.agent;

import static org.junit.jupiter.api.Assertions.*;
import dev.openallay.model.ModelUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class ModelCallReceiptsTest {
    @Test void engineCompletionWaitsUntilReceiptHandoffButDoesNotInventAnotherRelease() {
        ModelCallReceipts gate = new ModelCallReceipts();
        UUID call = UUID.randomUUID();
        List<AgentEvent> handedOff = new ArrayList<>();
        gate.accept(new AgentEvent.ModelUsageStarted(call, "model"), handedOff::add);
        CompletableFuture<String> finished = gate.after(CompletableFuture.completedFuture("done"));
        assertFalse(finished.isDone());
        gate.accept(new AgentEvent.Failed("agent_cancelled", "cancelled"), handedOff::add);
        gate.accept(new AgentEvent.ModelUsageObserved(call, "model", ModelUsage.empty()), event -> {
            assertFalse(finished.isDone(), "handoff must precede completion");
            handedOff.add(event);
        });
        assertEquals("done", finished.join());
        assertEquals(3, handedOff.size());
        assertTrue(handedOff.stream().noneMatch(AgentEvent.RequestReleased.class::isInstance));
    }
    @Test void dispatcherCompletionTailKeepsTwoHopUsageBeforeOwnerCleanup() {
        ModelCallReceipts gate = new ModelCallReceipts();
        UUID call = UUID.randomUUID();
        java.util.ArrayDeque<Runnable> dispatcher = new java.util.ArrayDeque<>();
        List<String> applied = new ArrayList<>();
        java.util.function.Consumer<AgentEvent> serviceSink = event -> dispatcher.add(() -> {
            if (event instanceof AgentEvent.ModelUsageObserved) applied.add("usage");
        });
        java.util.function.Consumer<AgentEvent> runtimeHandoff = event ->
                dispatcher.add(() -> serviceSink.accept(event));
        gate.accept(new AgentEvent.ModelUsageStarted(call, "model"), runtimeHandoff);
        CompletableFuture<String> engine = CompletableFuture.completedFuture("done");
        CompletableFuture<String> finished = gate.after(engine).thenCompose(value -> {
            CompletableFuture<String> handedOff = new CompletableFuture<>();
            dispatcher.add(() -> handedOff.complete(value));
            return handedOff;
        });
        finished.whenComplete((value, failure) -> dispatcher.add(() -> applied.add("cleanup")));
        gate.accept(new AgentEvent.ModelUsageObserved(call, "model", new ModelUsage(17, 2, 0)), runtimeHandoff);
        assertFalse(finished.isDone(), "runtime completion must wait for its dispatcher tail");
        while (!dispatcher.isEmpty()) dispatcher.removeFirst().run();
        assertEquals(List.of("usage", "cleanup"), applied);
        assertEquals("done", finished.join());
    }

    @Test void fakeWithoutCallStartsCompletesImmediately() {
        assertEquals("done", new ModelCallReceipts().after(CompletableFuture.completedFuture("done")).join());
    }
}
