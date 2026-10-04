package dev.openallay.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class ObservingModelClientTest {
    @Test
    void transportPreparationFailureCreatesNeitherStartNorReceipt() {
        java.util.concurrent.atomic.AtomicInteger sends = new java.util.concurrent.atomic.AtomicInteger();
        List<ModelEvent> events = new ArrayList<>();
        ModelClient realClient = new ModelClient() {
            @Override public boolean reportsAttemptStarted() { return true; }
            @Override public CompletableFuture<ModelTurn> complete(ModelRequest request,
                    Consumer<ModelEvent> sink, CancellationSignal cancellation) {
                try {
                    prepareUnavailableAttachment();
                    sends.incrementAndGet();
                    sink.accept(new ModelEvent.AttemptStarted(1, 5000L));
                    return CompletableFuture.completedFuture(turn(new ModelUsage(1, 1, 0)));
                } catch (IllegalStateException preparationFailure) {
                    return CompletableFuture.failedFuture(preparationFailure);
                }
            }
        };
        var observed = ObservingModelClient.observe(realClient, "configured-model");
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> observed.complete(request(), events::add, new CancellationSignal()).join());
        assertEquals(0, sends.get());
        assertTrue(events.isEmpty());
    }

    @Test
    void actualDispatchProducesOneSharedStartReceiptIdentityEvenOnFailure() {
        java.util.concurrent.atomic.AtomicInteger sends = new java.util.concurrent.atomic.AtomicInteger();
        List<ModelEvent> events = new ArrayList<>();
        ModelClient realClient = new ModelClient() {
            @Override public boolean reportsAttemptStarted() { return true; }
            @Override public CompletableFuture<ModelTurn> complete(ModelRequest request,
                    Consumer<ModelEvent> sink, CancellationSignal cancellation) {
                sends.incrementAndGet();
                sink.accept(new ModelEvent.AttemptStarted(1, 5000L));
                sink.accept(new ModelEvent.AttemptStarted(1, 5000L));
                return CompletableFuture.failedFuture(new IllegalStateException("transport failed"));
            }
        };
        var observed = ObservingModelClient.observe(realClient, "configured-model");
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> observed.complete(request(), events::add, new CancellationSignal()).join());
        assertEquals(1, sends.get());
        var started = events.stream().filter(ModelEvent.UsageStarted.class::isInstance)
                .map(ModelEvent.UsageStarted.class::cast).toList();
        assertEquals(1, started.size());
        assertEquals(1, receipts(events).size());
        assertEquals(started.getFirst().callId(), receipts(events).getFirst().callId());
        assertFalse(receipts(events).getFirst().usage().reported());
    }

    @Test
    void successUsesFinalCountsAndActualModelAndSealsOnlyOnce() {
        List<ModelEvent> events = new ArrayList<>();
        AtomicReference<Consumer<ModelEvent>> callback = new AtomicReference<>();
        CompletableFuture<ModelTurn> raw = new CompletableFuture<>();
        ModelClient observed = ObservingModelClient.observe((request, sink, cancellation) -> {
            callback.set(sink);
            sink.accept(new ModelEvent.UsageUpdate(new ModelUsage(4, 1, 0)));
            sink.accept(new ModelEvent.UsageUpdate(new ModelUsage(4, 1, 0)));
            sink.accept(new ModelEvent.MessageComplete("end_turn"));
            sink.accept(new ModelEvent.MessageComplete("end_turn"));
            return raw;
        }, "configured-model");
        assertSame(observed, ObservingModelClient.observe(observed));
        var future = observed.complete(request(), events::add, new CancellationSignal());
        assertEquals(0, receipts(events).size());
        raw.complete(turn(new ModelUsage(9, 3, 2)));
        assertEquals("actual-model", future.join().model());
        assertEquals(1, receipts(events).size());
        var started = events.stream().filter(ModelEvent.UsageStarted.class::isInstance)
                .map(ModelEvent.UsageStarted.class::cast).toList();
        assertEquals(1, started.size());
        assertEquals(started.getFirst().callId(), receipts(events).getFirst().callId());
        assertEquals("actual-model", receipts(events).getFirst().modelIdentifier());
        assertEquals(new ModelUsage(9, 3, 2), receipts(events).getFirst().usage());
        int sealedSize = events.size();
        callback.get().accept(new ModelEvent.UsageUpdate(new ModelUsage(90, 30, 20)));
        callback.get().accept(new ModelEvent.TextDelta("late"));
        assertEquals(sealedSize, events.size());
    }

    @Test
    void failedAndSynchronousFailedAttemptsRetainReportedOrUnknownUsage() {
        List<ModelEvent> events = new ArrayList<>();
        ModelUsage partial = ModelUsage.openAi(12, true, 0, false, 0, false);
        ModelClient partialFailure = ObservingModelClient.observe((request, sink, cancellation) -> {
            sink.accept(new ModelEvent.UsageUpdate(partial));
            return CompletableFuture.failedFuture(new IllegalStateException("failed stream"));
        }, "configured-model");
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> partialFailure.complete(request(), events::add, new CancellationSignal()).join());
        ModelClient syncFailure = ObservingModelClient.observe((request, sink, cancellation) -> {
            throw new IllegalStateException("failed start");
        }, "configured-model");
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> syncFailure.complete(request(), events::add, new CancellationSignal()).join());
        assertEquals(2, receipts(events).size());
        assertEquals(partial, receipts(events).getFirst().usage());
        assertFalse(receipts(events).getLast().usage().reported());
        assertFalse(receipts(events).getLast().usage().inputKnown());
        assertEquals("configured-model", receipts(events).getLast().modelIdentifier());
        assertNotEquals(receipts(events).getFirst().callId(), receipts(events).getLast().callId());
    }

    @Test
    void cancelledDispatchedAttemptSealsPartialOnceAndLateRawCompletionDoesNotReopen() {
        List<ModelEvent> events = new ArrayList<>();
        AtomicReference<Consumer<ModelEvent>> callback = new AtomicReference<>();
        CompletableFuture<ModelTurn> raw = new CompletableFuture<>();
        CancellationSignal cancellation = new CancellationSignal();
        ModelUsage partial = ModelUsage.openAi(17, true, 0, false, 0, false);
        ModelClient observed = ObservingModelClient.observe((request, sink, signal) -> {
            callback.set(sink);
            sink.accept(new ModelEvent.UsageUpdate(partial));
            return raw;
        }, "captured-model");
        var future = observed.complete(request(), events::add, cancellation);
        cancellation.cancel();
        assertTrue(future.isCompletedExceptionally());
        assertEquals(1, receipts(events).size());
        assertEquals(partial, receipts(events).getFirst().usage());
        callback.get().accept(new ModelEvent.UsageUpdate(new ModelUsage(99, 99, 0)));
        raw.complete(turn(new ModelUsage(99, 99, 0)));
        assertEquals(1, receipts(events).size());
        assertEquals(partial, receipts(events).getFirst().usage());
    }

    @Test
    void preCancelledCallDoesNotDispatchOrCreateReceipt() {
        CancellationSignal cancellation = new CancellationSignal();
        cancellation.cancel();
        List<ModelEvent> events = new ArrayList<>();
        ModelClient observed = ObservingModelClient.observe((request, sink, signal) -> {
            fail("must not dispatch");
            return null;
        });
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> observed.complete(request(), events::add, cancellation).join());
        assertTrue(events.isEmpty());
    }

    @Test
    void directFutureCancellationAlsoSealsExactlyOnce() {
        CompletableFuture<ModelTurn> raw = new CompletableFuture<>();
        List<ModelEvent> events = new ArrayList<>();
        ModelClient observed = ObservingModelClient.observe((request, sink, signal) -> raw);
        var future = observed.complete(request(), events::add, new CancellationSignal());
        future.cancel(true);
        assertTrue(raw.isCancelled());
        assertEquals(1, receipts(events).size());
        assertFalse(receipts(events).getFirst().usage().reported());
    }

    @Test
    void failingReceiptConsumerCannotReplaceSuccessfulProviderResult() {
        ModelTurn expected = turn(new ModelUsage(8, 2, 0));
        ModelClient observed = ObservingModelClient.observe((request, sink, signal) ->
                CompletableFuture.completedFuture(expected));
        assertSame(expected, observed.complete(request(), event -> {
            if (event instanceof ModelEvent.UsageObserved) throw new IllegalStateException("consumer failure");
        }, new CancellationSignal()).join());
    }

    @Test
    void unknownFinalUsageDoesNotEraseAStreamReport() {
        List<ModelEvent> events = new ArrayList<>();
        ModelClient observed = ObservingModelClient.observe((request, sink, signal) -> {
            sink.accept(new ModelEvent.UsageUpdate(new ModelUsage(8, 2, 0)));
            return CompletableFuture.completedFuture(turn(ModelUsage.empty()));
        });
        observed.complete(request(), events::add, new CancellationSignal()).join();
        assertEquals(new ModelUsage(8, 2, 0), receipts(events).getFirst().usage());
    }

    private static void prepareUnavailableAttachment() {
        throw new IllegalStateException("attachment unavailable");
    }

    private static List<ModelEvent.UsageObserved> receipts(List<ModelEvent> events) {
        return events.stream().filter(ModelEvent.UsageObserved.class::isInstance)
                .map(ModelEvent.UsageObserved.class::cast).toList();
    }

    private static ModelRequest request() {
        return new ModelRequest("system", List.of(ModelMessage.userText("question")), List.of(), false, "actor:session");
    }

    private static ModelTurn turn(ModelUsage usage) {
        return new ModelTurn("provider", "actual-model", List.of(new ModelContent.Text("answer")), "end_turn", usage);
    }
}
