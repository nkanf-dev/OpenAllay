package dev.openallay.client.voice;

import dev.openallay.concurrent.NamedThreads;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Bounds provider open calls and fences native work that returns after cancellation. */
final class CaptureOpenOwner {
    @FunctionalInterface interface PermissionPreflight { void check() throws Exception; }
    @FunctionalInterface interface Provider { Prepared acquire(String deviceId) throws Exception; }
    interface Prepared extends AudioCapture {
        void open() throws Exception;
        void start() throws Exception;
        /** A provider may finish opening after close; release that late resource too. */
        default void closeAfterAbandonedOpen() { close(); }
    }

    private final PermissionPreflight permission;
    private final Provider provider;
    private final Semaphore openingSlot = new Semaphore(1);
    private final long timeoutNanos;

    CaptureOpenOwner(PermissionPreflight permission, Provider provider, Duration timeout) {
        this.permission = Objects.requireNonNull(permission);
        this.provider = Objects.requireNonNull(provider);
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("openTimeout");
        timeoutNanos = timeout.toNanos();
    }

    AudioCapture open(String deviceId, VoiceCancellation cancellation) throws Exception {
        Objects.requireNonNull(cancellation).check();
        long deadline = System.nanoTime() + timeoutNanos;
        // Java cannot forcibly interrupt a stuck native call. Never pile up native openers.
        while (!openingSlot.tryAcquire(Math.min(TimeUnit.MILLISECONDS.toNanos(25),
                Math.max(0, deadline - System.nanoTime())), TimeUnit.NANOSECONDS)) {
            cancellation.check();
            if (System.nanoTime() >= deadline) throw new AudioCapture.CaptureException(AudioCapture.Failure.OPEN_BUSY,
                    "The previous microphone open has not stopped. Restart the game before trying again.");
        }
        Opening opening = new Opening(deviceId, cancellation);
        boolean transferred = false;
        try (AutoCloseable hook = cancellation.onCancel(opening::abort)) {
            opening.thread = NamedThreads.unstartedDaemon("openallay-microphone-open", opening::run);
            opening.thread.start();
            try {
                AudioCapture capture = opening.result.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                cancellation.check();
                transferred = true;
                return capture;
            } catch (TimeoutException failure) {
                throw new AudioCapture.CaptureException(AudioCapture.Failure.OPEN_TIMEOUT,
                        "Opening the microphone timed out. Check the device and microphone permission.");
            } catch (ExecutionException failure) {
                cancellation.check();
                Throwable cause = failure.getCause();
                if (cause instanceof Exception exception) throw exception;
                throw new AudioCapture.CaptureException(AudioCapture.Failure.OPEN_FAILED,
                        "Could not open the microphone.");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                cancellation.check();
                throw new AudioCapture.CaptureException(AudioCapture.Failure.OPEN_FAILED,
                        "The microphone open worker was interrupted.", failure);
            }
        } finally {
            if (!transferred) opening.abort();
        }
    }

    private final class Opening {
        private final String deviceId;
        private final VoiceCancellation cancellation;
        private final CompletableFuture<AudioCapture> result = new CompletableFuture<>();
        private final AtomicBoolean aborted = new AtomicBoolean();
        private final AtomicBoolean closeScheduled = new AtomicBoolean();
        private final AtomicReference<Prepared> selected = new AtomicReference<>();
        private volatile Thread thread;
        Opening(String deviceId, VoiceCancellation cancellation) {
            this.deviceId = deviceId; this.cancellation = cancellation;
        }
        private void check() {
            cancellation.check();
            if (aborted.get()) throw new CancellationException("Microphone open abandoned");
        }
        private void abort() {
            aborted.set(true);
            result.cancel(false);
            Thread opener = thread;
            if (opener != null) opener.interrupt();
            Prepared capture = selected.get();
            if (capture != null && closeScheduled.compareAndSet(false, true)) {
                // Never block a game-thread cancellation callback on native close.
                NamedThreads.startDaemon("openallay-microphone-open-close", capture::close);
            }
        }
        private void run() {
            Prepared capture = null;
            boolean ready = false;
            Throwable failure = null;
            try {
                check();
                permission.check(); // Must precede even device selection, not only native open.
                check();
                capture = Objects.requireNonNull(provider.acquire(deviceId));
                selected.set(capture);
                check();
                capture.open();
                check();
                capture.start();
                check();
                ready = true;
            } catch (Exception known) {
                if (aborted.get() || cancellation.cancelled())
                    failure = new CancellationException("Microphone open cancelled");
                else if (known instanceof AudioPermissionException
                        || known instanceof AudioCapture.CaptureException) failure = known;
                else failure = new AudioCapture.CaptureException(known instanceof IllegalArgumentException
                                ? AudioCapture.Failure.UNSUPPORTED_FORMAT : AudioCapture.Failure.OPEN_FAILED,
                        "Could not open the microphone. Check the selected device and microphone permission.", known);
            } catch (LinkageError linkage) {
                failure = new AudioCapture.CaptureException(AudioCapture.Failure.BACKEND_UNAVAILABLE,
                        "The microphone capture provider could not load.", linkage);
            } finally {
                if ((!ready || aborted.get() || cancellation.cancelled()) && capture != null) {
                    closeScheduled.set(true);
                    capture.closeAfterAbandonedOpen();
                }
                openingSlot.release();
            }
            if (aborted.get() || cancellation.cancelled()) result.cancel(false);
            else if (failure != null) result.completeExceptionally(failure);
            else if (!result.complete(capture) && capture != null) capture.closeAfterAbandonedOpen();
        }
    }
}
