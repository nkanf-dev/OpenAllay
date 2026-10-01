package dev.openallay.model.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.openallay.model.CancellationSignal;
import dev.openallay.model.ModelClient;
import dev.openallay.model.ModelContent;
import dev.openallay.model.ModelEvent;
import dev.openallay.model.ModelMessage;
import dev.openallay.model.ModelRateLimitException;
import dev.openallay.model.ModelClientException;
import dev.openallay.model.ModelFailure;
import dev.openallay.model.ModelRequest;
import dev.openallay.model.ModelTurn;
import dev.openallay.model.ModelUsage;
import dev.openallay.model.ModelUpstreamException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class ModelRequestSchedulerTest {
    @Test
    void dispatchesDifferentSessionsWithoutAnArtificialConcurrencyCap() {
        AtomicInteger calls = new AtomicInteger();
        ModelClient delegate = (request, events, cancellation) -> {
            calls.incrementAndGet();
            return new CompletableFuture<>();
        };
        ModelRequestScheduler scheduler = new ModelRequestScheduler(delegate);

        scheduler.complete(request("one"), event -> {}, new CancellationSignal());
        scheduler.complete(request("two"), event -> {}, new CancellationSignal());

        assertEquals(2, calls.get());
        assertEquals(0, scheduler.queuedRequests());
    }

    @Test
    void requeues429AndHonorsRetryDelayUntilSuccess() throws Exception {
        Deque<CompletableFuture<ModelTurn>> turns = new ArrayDeque<>();
        turns.add(CompletableFuture.failedFuture(
                new ModelRateLimitException("limited", Duration.ofMillis(30))));
        turns.add(CompletableFuture.completedFuture(turn("resumed")));
        AtomicInteger calls = new AtomicInteger();
        ModelClient delegate = (request, events, cancellation) -> {
            calls.incrementAndGet();
            return turns.removeFirst();
        };
        ModelRequestScheduler scheduler = new ModelRequestScheduler(delegate);
        List<ModelEvent> events = new ArrayList<>();

        ModelTurn result = scheduler.complete(request("one"), events::add, new CancellationSignal())
                .get(2, TimeUnit.SECONDS);

        assertEquals("resumed", result.text());
        assertEquals(2, calls.get());
        assertTrue(events.stream().anyMatch(event -> event instanceof ModelEvent.RateLimited));
        assertEquals(
                List.of(1, 2),
                events.stream()
                        .filter(ModelEvent.AttemptStarted.class::isInstance)
                        .map(ModelEvent.AttemptStarted.class::cast)
                        .map(ModelEvent.AttemptStarted::attempt)
                        .toList());
    }

    @Test
    void preservesTransportAttemptBudgetWhileReplacingItsLocalAttemptNumber() {
        long attemptTimeoutMillis = 10_000;
        ModelRequestScheduler scheduler = new ModelRequestScheduler(
                (request, events, cancellation) -> {
                    events.accept(new ModelEvent.AttemptStarted(99, attemptTimeoutMillis));
                    return CompletableFuture.completedFuture(turn("done"));
                });
        List<ModelEvent> events = new ArrayList<>();

        scheduler.complete(request("one"), events::add, new CancellationSignal()).join();

        List<ModelEvent.AttemptStarted> attempts = events.stream()
                .filter(ModelEvent.AttemptStarted.class::isInstance)
                .map(ModelEvent.AttemptStarted.class::cast)
                .toList();
        assertEquals(2, attempts.size());
        assertEquals(1, attempts.getFirst().attempt());
        assertEquals(null, attempts.getFirst().attemptTimeoutMillis());
        assertEquals(1, attempts.getLast().attempt());
        assertEquals(attemptTimeoutMillis, attempts.getLast().attemptTimeoutMillis());
    }

    @Test
    void queuedCancellationDoesNotDispatchAfterCooldown() throws Exception {
        CompletableFuture<ModelTurn> firstAttempt = CompletableFuture.failedFuture(
                new ModelRateLimitException("limited", Duration.ofMillis(200)));
        AtomicInteger calls = new AtomicInteger();
        ModelClient delegate = (request, events, cancellation) -> {
            calls.incrementAndGet();
            return firstAttempt;
        };
        ModelRequestScheduler scheduler = new ModelRequestScheduler(delegate);
        CancellationSignal cancellation = new CancellationSignal();
        CompletableFuture<ModelTurn> result =
                scheduler.complete(request("one"), event -> {}, cancellation);
        cancellation.cancel();

        assertTrue(result.isCompletedExceptionally());
        Thread.sleep(300);
        assertEquals(1, calls.get());
        assertFalse(scheduler.queuedRequests() > 0);
    }

    @Test
    void delaysFreshContextCaptureWhileEndpointCooldownIsKnown() throws Exception {
        Deque<CompletableFuture<ModelTurn>> turns = new ArrayDeque<>();
        turns.add(CompletableFuture.failedFuture(
                new ModelRateLimitException("limited", Duration.ofMillis(80))));
        turns.add(CompletableFuture.completedFuture(turn("resumed")));
        ModelRequestScheduler scheduler = new ModelRequestScheduler(
                (request, events, cancellation) -> turns.removeFirst());
        scheduler.complete(request("one"), event -> {}, new CancellationSignal());

        CompletableFuture<Void> ready = scheduler.awaitReady(new CancellationSignal());
        assertFalse(ready.isDone());
        ready.get(2, TimeUnit.SECONDS);
        assertTrue(ready.isDone());
    }

    @Test
    void retriesTransportFailureOnlyBeforeResponseProgress() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ModelClient delegate = (request, events, cancellation) -> {
            if (calls.incrementAndGet() < 3) {
                return CompletableFuture.failedFuture(transportFailure());
            }
            return CompletableFuture.completedFuture(turn("recovered"));
        };
        ModelRequestScheduler scheduler =
                new ModelRequestScheduler(delegate, Duration.ofMillis(1), 2);

        ModelTurn result = scheduler.complete(request("one"), event -> {}, new CancellationSignal())
                .get(2, TimeUnit.SECONDS);

        assertEquals("recovered", result.text());
        assertEquals(3, calls.get());
    }

    @Test
    void doesNotRetryTransportFailureAfterResponseStarted() {
        AtomicInteger calls = new AtomicInteger();
        ModelClient delegate = (request, events, cancellation) -> {
            calls.incrementAndGet();
            events.accept(new ModelEvent.ResponseStarted());
            return CompletableFuture.failedFuture(transportFailure());
        };
        ModelRequestScheduler scheduler =
                new ModelRequestScheduler(delegate, Duration.ofMillis(1), 2);

        try {
            scheduler.complete(request("one"), event -> {}, new CancellationSignal()).join();
        } catch (java.util.concurrent.CompletionException expected) {
            assertEquals("model_transport_error",
                    ((ModelClientException) expected.getCause()).failure().code());
        }

        assertEquals(1, calls.get());
    }

    @Test
    void retriesOnlyTheSameModelRoundAndRetainsFinalUpstreamStatus() throws Exception {
        for (int status : List.of(502, 503, 504)) {
            AtomicInteger calls = new AtomicInteger();
            ModelRequest sameRound = request("one");
            ModelUpstreamException upstream = new ModelUpstreamException(status, null);
            ModelRequestScheduler scheduler = new ModelRequestScheduler((request, events, cancellation) -> {
                assertSame(sameRound, request);
                calls.incrementAndGet();
                return CompletableFuture.failedFuture(upstream);
            }, Duration.ofMillis(1), 2);

            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> scheduler.complete(sameRound, event -> {}, new CancellationSignal())
                            .get(2, TimeUnit.SECONDS));

            assertSame(upstream, failure.getCause());
            assertEquals(3, calls.get());
        }
    }

    @Test
    void neverRetriesAfterAnyModelProgressIncludingToolOrReasoning() {
        for (ModelEvent progress : List.of(
                new ModelEvent.ResponseStarted(), new ModelEvent.TextDelta("partial"),
                new ModelEvent.ReasoningDelta("partial"),
                new ModelEvent.ToolUseComplete("call", "tool", new com.google.gson.JsonObject()),
                new ModelEvent.UsageUpdate(ModelUsage.empty()), new ModelEvent.MessageComplete("done"))) {
            AtomicInteger calls = new AtomicInteger();
            ModelUpstreamException upstream = new ModelUpstreamException(502, null);
            ModelRequestScheduler scheduler = new ModelRequestScheduler((request, events, cancellation) -> {
                calls.incrementAndGet();
                events.accept(progress);
                return CompletableFuture.failedFuture(upstream);
            }, Duration.ZERO, 2);

            var failure = assertThrows(java.util.concurrent.CompletionException.class,
                    () -> scheduler.complete(request("one"), event -> {}, new CancellationSignal()).join());

            assertSame(upstream, failure.getCause());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void doesNotRetryOtherHttpStatusesTimeoutOrAbort() {
        for (ModelClientException terminal : List.of(
                new ModelClientException(new ModelFailure("model_http_error", "HTTP 500", 500)),
                new ModelClientException(new ModelFailure("model_timeout", "Timed out", null)),
                new ModelClientException(new ModelFailure("agent_cancelled", "Cancelled", null)))) {
            AtomicInteger calls = new AtomicInteger();
            ModelRequestScheduler scheduler = new ModelRequestScheduler((request, events, cancellation) -> {
                calls.incrementAndGet();
                return CompletableFuture.failedFuture(terminal);
            }, Duration.ZERO, 2);
            var failure = assertThrows(java.util.concurrent.CompletionException.class,
                    () -> scheduler.complete(request("one"), event -> {}, new CancellationSignal()).join());
            assertSame(terminal, failure.getCause());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void doesNotShortenRetryAfterToFitTheAttemptDeadline() {
        AtomicInteger calls = new AtomicInteger();
        ModelUpstreamException upstream = new ModelUpstreamException(503, Duration.ofSeconds(3));
        ModelRequestScheduler scheduler = new ModelRequestScheduler((request, events, cancellation) -> {
            calls.incrementAndGet();
            events.accept(new ModelEvent.AttemptStarted(1, 200L));
            return CompletableFuture.failedFuture(upstream);
        }, Duration.ZERO, 2);
        var failure = assertThrows(java.util.concurrent.CompletionException.class,
                () -> scheduler.complete(request("one"), event -> {}, new CancellationSignal()).join());
        assertSame(upstream, failure.getCause());
        assertEquals(1, calls.get());
    }

    @Test
    void retryAfterIsAMinimumAndDoesNotCloseThe429EndpointGate() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Long> failedAt = new AtomicReference<>();
        AtomicReference<Long> retriedAt = new AtomicReference<>();
        Duration retryAfter = Duration.ofMillis(50);
        ModelRequestScheduler scheduler = new ModelRequestScheduler((request, events, cancellation) -> {
            if (calls.incrementAndGet() == 1) {
                failedAt.set(System.nanoTime());
                return CompletableFuture.failedFuture(new ModelUpstreamException(503, retryAfter));
            }
            retriedAt.set(System.nanoTime());
            return CompletableFuture.completedFuture(turn("done"));
        }, Duration.ofMillis(1), 2);
        var result = scheduler.complete(request("one"), event -> {}, new CancellationSignal());
        assertTrue(scheduler.awaitReady(new CancellationSignal()).isDone());
        assertEquals("done", result.get(2, TimeUnit.SECONDS).text());
        assertTrue(retriedAt.get() - failedAt.get() >= retryAfter.toNanos());
    }

    @Test
    void recoveryKeepsTheFailedAttemptDeadlineAndCancelsItsActiveExchange() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch exchangeCancelled = new CountDownLatch(1);
        CountDownLatch secondAttempt = new CountDownLatch(1);
        CancellationSignal caller = new CancellationSignal();
        ModelRequestScheduler scheduler = new ModelRequestScheduler((request, events, cancellation) -> {
            events.accept(new ModelEvent.AttemptStarted(1, 150L));
            if (calls.incrementAndGet() == 1) {
                return CompletableFuture.failedFuture(new ModelUpstreamException(502, null));
            }
            cancellation.onCancel(exchangeCancelled::countDown);
            secondAttempt.countDown();
            return new CompletableFuture<>();
        }, Duration.ofMillis(1), 2);
        var result = scheduler.complete(request("one"), event -> {}, caller);
        assertTrue(secondAttempt.await(2, TimeUnit.SECONDS));
        var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> result.get(2, TimeUnit.SECONDS));
        assertEquals("model_timeout", ((ModelClientException) failure.getCause()).failure().code());
        assertTrue(exchangeCancelled.await(2, TimeUnit.SECONDS));
        assertFalse(caller.isCancelled());
        assertEquals(2, calls.get());
    }

    @Test
    void cancellationDuringRecoveryStopsItsWaitAndKeepsAbortCategory() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ModelRequestScheduler scheduler = new ModelRequestScheduler((request, events, cancellation) -> {
            calls.incrementAndGet();
            return CompletableFuture.failedFuture(new ModelUpstreamException(502, Duration.ofSeconds(1)));
        }, Duration.ofMillis(1), 2);
        CancellationSignal cancellation = new CancellationSignal();
        var result = scheduler.complete(request("one"), event -> {}, cancellation);
        cancellation.cancel();
        var failure = assertThrows(java.util.concurrent.CompletionException.class, result::join);
        assertEquals("agent_cancelled", ((ModelClientException) failure.getCause()).failure().code());
        assertEquals(1, calls.get());
        assertEquals(0, scheduler.queuedRequests());
    }

    @Test
    void recoveryJoinsExisting429GateAndKeepsOtherSessionsFair() throws Exception {
        List<String> order = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicInteger gatewayCalls = new AtomicInteger();
        AtomicInteger limitedCalls = new AtomicInteger();
        CompletableFuture<ModelTurn> gatewayAttempt = new CompletableFuture<>();
        ModelRequestScheduler scheduler = new ModelRequestScheduler((request, events, cancellation) -> {
            order.add(request.sessionKey());
            if (request.sessionKey().equals("gateway") && gatewayCalls.incrementAndGet() == 1) {
                return gatewayAttempt;
            }
            if (request.sessionKey().equals("limited") && limitedCalls.incrementAndGet() == 1) {
                return CompletableFuture.failedFuture(
                        new ModelRateLimitException("limited", Duration.ofMillis(80)));
            }
            return CompletableFuture.completedFuture(turn("done"));
        }, Duration.ofMillis(10), 2);
        var gateway = scheduler.complete(request("gateway"), event -> {}, new CancellationSignal());
        var limited = scheduler.complete(request("limited"), event -> {}, new CancellationSignal());
        var other = scheduler.complete(request("other"), event -> {}, new CancellationSignal());
        gatewayAttempt.completeExceptionally(new ModelUpstreamException(502, null));
        CompletableFuture.allOf(gateway, limited, other).get(2, TimeUnit.SECONDS);
        assertEquals(2, gatewayCalls.get());
        assertEquals(2, limitedCalls.get());
        assertTrue(order.indexOf("other") < order.lastIndexOf("gateway"));
        assertEquals(0, scheduler.queuedRequests());
    }

    @Test
    void deferredCallerCancellationAtAdmissionNeverDispatches() {
        AtomicInteger calls = new AtomicInteger();
        CancellationSignal caller = new CancellationSignal();
        List<Runnable> deferred = new ArrayList<>();
        ModelRequestScheduler scheduler = new ModelRequestScheduler((request, events, cancellation) -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(turn("must not run"));
        });
        var result = scheduler.complete(request("one"), event -> caller.cancel(deferred::add), caller);
        var failure = assertThrows(java.util.concurrent.CompletionException.class, result::join);
        assertEquals("agent_cancelled", ((ModelClientException) failure.getCause()).failure().code());
        assertEquals(0, calls.get());
        deferred.forEach(Runnable::run);
    }

    @Test
    void deferredCallerCancellationWinsOverAConcurrentSuccessfulCompletion() {
        CancellationSignal caller = new CancellationSignal();
        List<Runnable> deferred = new ArrayList<>();
        CompletableFuture<ModelTurn> delegate = new CompletableFuture<>();
        AtomicReference<CancellationSignal> exchange = new AtomicReference<>();
        ModelRequestScheduler scheduler = new ModelRequestScheduler((request, events, cancellation) -> {
            exchange.set(cancellation);
            return delegate;
        });
        var result = scheduler.complete(request("one"), event -> {}, caller);
        caller.cancel(deferred::add);
        assertTrue(exchange.get().isCancelled());
        delegate.complete(turn("too late"));
        var failure = assertThrows(java.util.concurrent.CompletionException.class, result::join);
        assertEquals("agent_cancelled", ((ModelClientException) failure.getCause()).failure().code());
        deferred.forEach(Runnable::run);
    }

    @Test
    void blockedRetryDelegateCannotBlockItsDeadlineOrOtherRecoveryTimers() throws Exception {
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch releaseDelegate = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ModelRequestScheduler scheduler = new ModelRequestScheduler((request, events, cancellation) -> {
            events.accept(new ModelEvent.AttemptStarted(1, 150L));
            if (calls.incrementAndGet() == 1) {
                return CompletableFuture.failedFuture(new ModelUpstreamException(502, null));
            }
            blocked.countDown();
            try {
                releaseDelegate.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return CompletableFuture.completedFuture(turn("too late"));
        }, Duration.ofMillis(1), 2);
        var result = scheduler.complete(request("one"), event -> {}, new CancellationSignal());
        try {
            assertTrue(blocked.await(2, TimeUnit.SECONDS));
            var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> result.get(2, TimeUnit.SECONDS));
            assertEquals("model_timeout", ((ModelClientException) failure.getCause()).failure().code());
        } finally {
            releaseDelegate.countDown();
        }
    }

    @Test
    void timeoutRevokesExchangeEvenWhenAnOutwardCompletionCallbackBlocks() throws Exception {
        CountDownLatch releaseConsumer = new CountDownLatch(1);
        CountDownLatch consumerEntered = new CountDownLatch(1);
        CountDownLatch cancelledExchange = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<CancellationSignal> exchange = new AtomicReference<>();
        ModelRequestScheduler scheduler = new ModelRequestScheduler((request, events, cancellation) -> {
            events.accept(new ModelEvent.AttemptStarted(1, 150L));
            if (calls.incrementAndGet() == 1) {
                return CompletableFuture.failedFuture(new ModelUpstreamException(502, null));
            }
            exchange.set(cancellation);
            cancellation.onCancel(cancelledExchange::countDown);
            return new CompletableFuture<>();
        }, Duration.ofMillis(1), 2);
        var result = scheduler.complete(request("one"), event -> {}, new CancellationSignal());
        result.whenComplete((value, failure) -> {
            consumerEntered.countDown();
            try {
                releaseConsumer.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            assertTrue(consumerEntered.await(2, TimeUnit.SECONDS));
            assertTrue(exchange.get().isCancelled());
            assertTrue(cancelledExchange.await(2, TimeUnit.SECONDS));
        } finally {
            releaseConsumer.countDown();
        }
    }

    @Test
    void immediateCallerRevocationWinsEvenWhenDeadlineBeatsDeferredNotifications() throws Exception {
        CountDownLatch secondAttempt = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        CancellationSignal caller = new CancellationSignal();
        List<Runnable> deferred = new ArrayList<>();
        ModelRequestScheduler scheduler = new ModelRequestScheduler((request, events, cancellation) -> {
            events.accept(new ModelEvent.AttemptStarted(1, 150L));
            if (calls.incrementAndGet() == 1) {
                return CompletableFuture.failedFuture(new ModelUpstreamException(502, null));
            }
            secondAttempt.countDown();
            return new CompletableFuture<>();
        }, Duration.ofMillis(1), 2);
        var result = scheduler.complete(request("one"), event -> {}, caller);
        assertTrue(secondAttempt.await(2, TimeUnit.SECONDS));
        caller.cancel(deferred::add);
        var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> result.get(2, TimeUnit.SECONDS));
        assertEquals("agent_cancelled", ((ModelClientException) failure.getCause()).failure().code());
        deferred.forEach(Runnable::run);
    }

    @Test
    void completedRoundsDoNotLeaveCancellationListenersOnTheCaller() throws Exception {
        CancellationSignal caller = new CancellationSignal();
        ModelRequestScheduler scheduler = new ModelRequestScheduler((request, events, cancellation) ->
                CompletableFuture.completedFuture(turn("done")));
        var listenersField = CancellationSignal.class.getDeclaredField("listeners");
        listenersField.setAccessible(true);
        for (int round = 0; round < 10; round++) {
            scheduler.complete(request("one"), event -> {}, caller).join();
            assertTrue(((List<?>) listenersField.get(caller)).isEmpty());
        }
        assertFalse(caller.isCancelled());
    }

    private static ModelClientException transportFailure() {
        return new ModelClientException(new ModelFailure(
                "model_transport_error", "Model transport is unavailable", null));
    }

    private static ModelRequest request(String session) {
        return new ModelRequest(
                "system",
                List.of(ModelMessage.userText("question")),
                List.of(),
                false,
                session);
    }

    private static ModelTurn turn(String text) {
        return new ModelTurn(
                "test",
                "test",
                List.of(new ModelContent.Text(text)),
                "end_turn",
                ModelUsage.empty());
    }
}
