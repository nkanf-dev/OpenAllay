package dev.openallay.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Native modern default exceptional recovery vs canonical Java8 standard-future operation. */
public final class ExceptionalFutureJava8Fixture {
    private ExceptionalFutureJava8Fixture() {}
    public static void main(String[] args) throws Exception {
        boolean java8 = args.length == 1 && args[0].equals("java8");
        if (java8 && !"1.8".equals(System.getProperty("java.specification.version"))) throw new AssertionError("trueJava8 required");
        for (boolean modern : java8 ? new boolean[] {false} : new boolean[] {true}) {
            AtomicInteger recoveries = new AtomicInteger();
            CompletableFuture<String> success = new CompletableFuture<>();
            CompletableFuture<String> passed = recover(success, error -> { recoveries.incrementAndGet(); return "bad"; }, modern);
            success.complete("value"); equal("value", passed.join()); equal(0, recoveries.get());
            AtomicReference<Thread> callback = new AtomicReference<>();
            Thread caller = Thread.currentThread(); IllegalStateException original = new IllegalStateException("original");
            CompletableFuture<String> failed = new CompletableFuture<>();
            CompletableFuture<String> recovered = recover(failed, error -> {
                check(error == original, "failure identity unchanged"); callback.set(Thread.currentThread()); recoveries.incrementAndGet(); return "recovered";
            }, modern);
            failed.completeExceptionally(original); equal("recovered", recovered.join()); equal(1, recoveries.get()); check(callback.get() != caller, "failure recovery asynchronous");
            CompletableFuture<String> throwing = new CompletableFuture<>(); RuntimeException replacement = new RuntimeException("replacement");
            CompletableFuture<String> thrown = recover(throwing, error -> { throw replacement; }, modern); throwing.completeExceptionally(original);
            try { thrown.join(); throw new AssertionError("callback throw ignored"); } catch (CompletionException expected) { check(expected.getCause() == replacement, "recovery exception identity"); }
            CompletableFuture<String> nullable = new CompletableFuture<>(); CompletableFuture<String> nullResult = recover(nullable, error -> null, modern);
            nullable.completeExceptionally(original); equal(null, nullResult.join());
            CompletableFuture<String> canceled = new CompletableFuture<>(); CompletableFuture<String> cancelResult = recover(canceled, error -> {
                check(error instanceof java.util.concurrent.CancellationException, "cancellation cause"); return "cancel-recovered";
            }, modern); canceled.cancel(false); equal("cancel-recovered", cancelResult.join());
            java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            AtomicInteger late = new AtomicInteger();
            CompletableFuture<String> blockedSource = new CompletableFuture<>();
            CompletableFuture<String> downstream = recover(blockedSource, error -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException interrupted) { throw new CompletionException(interrupted); }
                late.incrementAndGet(); return "late";
            }, modern);
            blockedSource.completeExceptionally(original);
            check(entered.await(2, java.util.concurrent.TimeUnit.SECONDS), "async callback started");
            check(downstream.cancel(false), "downstream cancelled"); release.countDown();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while (late.get() == 0 && System.nanoTime() < deadline) Thread.sleep(1);
            equal(1, late.get()); check(downstream.isCancelled(), "late recovery cannot overwrite downstream cancellation");
            check(blockedSource.isCompletedExceptionally(), "downstream cancel does not rewrite original source");
            System.out.println("downstreamCancel=lateCallbackRetained/terminalCancelPreserved");
            System.out.println("normal=value/recoveryCalls=0"); System.out.println("failed=recovered/asynchronous/exactCause");
            System.out.println("callbackThrow=replacement/exactCause"); System.out.println("null=null"); System.out.println("cancel=cancel-recovered");
        }
        System.out.println("PASS standard future exceptionallyAsync Java8 equivalent (no subclass contract)");
    }
    @SuppressWarnings("unchecked") private static <T> CompletableFuture<T> recover(CompletableFuture<T> source, Function<Throwable,T> recovery, boolean modern) throws Exception {
        if (!modern) return Java8Futures.exceptionallyAsync(source, recovery);
        return (CompletableFuture<T>) CompletableFuture.class.getMethod("exceptionallyAsync", Function.class).invoke(source, recovery);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) { check(java.util.Objects.equals(expected, actual), "Expected " + expected + ", got " + actual); }
}
