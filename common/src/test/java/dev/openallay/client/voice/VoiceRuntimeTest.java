package dev.openallay.client.voice;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class VoiceRuntimeTest {
    static class Queue implements Executor {
        final ArrayDeque<Runnable> commands = new ArrayDeque<>();
        public void execute(Runnable command) { commands.add(command); }
        void drain() { while (!commands.isEmpty()) commands.remove().run(); }
    }
    static class Draft implements VoiceRuntime.DraftPort {
        long revision; String text = "existing"; String capturedSession = "s1"; String selectedSession = "s1";
        String pending; boolean closed;
        public VoiceRuntime.DraftTarget capture() { return new VoiceRuntime.DraftTarget("connection", capturedSession, revision); }
        public VoiceRuntime.Insertion append(VoiceRuntime.DraftTarget target, String transcript) {
            assertEquals("s1", target.sessionId());
            if (closed) return VoiceRuntime.Insertion.REJECTED;
            if (revision != target.draftRevision()) { pending = transcript; return VoiceRuntime.Insertion.PENDING; }
            text += " " + transcript; revision++; return VoiceRuntime.Insertion.INSERTED;
        }
    }
    static class FakeCapture implements AudioCapture.Factory {
        int opened, closed; Runnable onRead = () -> {}; boolean broken;
        public AudioCapture open(String device, VoiceCancellation cancellation) {
            cancellation.check();
            opened++;
            return new AudioCapture() {
                boolean shut;
                public int read(byte[] target) {
                    if (broken) return -1;
                    target[0] = 1; target[1] = 0; onRead.run(); return 2;
                }
                public void close() { if (!shut) { closed++; shut = true; } }
            };
        }
        public List<AudioCapture.Device> devices() { return List.of(new AudioCapture.Device("fake", "Fake")); }
    }
    @Test void finalTranscriptAppendsToCapturedSessionNeverSendsOrTouchesImages() {
        Draft draft = new Draft(); FakeCapture capture = new FakeCapture(); Queue worker = new Queue();
        AtomicInteger calls = new AtomicInteger();
        VoiceRuntime runtime = new VoiceRuntime(draft, capture, c -> (request, cancel) -> {
            calls.incrementAndGet(); assertEquals("auto", request.language()); assertEquals(2, request.clip().pcm().length);
            draft.selectedSession = "s2";
            return new SpeechToText.Result(" hello ", "fake-native", new SpeechToText.Usage(null, null, null));
        }, () -> VoiceConfig.defaults().withEnabled(true), worker, Runnable::run);
        capture.onRead = runtime::release;
        runtime.press(); assertEquals(VoiceRuntime.State.STARTING, runtime.status().state()); worker.drain();
        assertEquals("existing hello", draft.text); assertEquals("s2", draft.selectedSession);
        assertEquals(1, calls.get()); assertEquals(1, capture.closed); assertEquals(VoiceRuntime.State.READY, runtime.status().state());
    }
    @Test void staleRevisionMakesPendingAndLateCompletionCannotEnterNewConnection() {
        Draft draft = new Draft(); FakeCapture capture = new FakeCapture(); Queue worker = new Queue(); Queue client = new Queue();
        VoiceRuntime runtime = new VoiceRuntime(draft, capture, c -> (request, cancel) -> new SpeechToText.Result("hello", "fixture", null),
                () -> VoiceConfig.defaults().withEnabled(true), worker, client);
        capture.onRead = runtime::release; runtime.press(); worker.drain(); draft.revision++; draft.text = "edited"; client.drain();
        assertEquals("edited", draft.text); assertEquals("hello", draft.pending); assertEquals(VoiceRuntime.State.PENDING, runtime.status().state());
        runtime.press(); worker.drain(); runtime.close(); client.drain(); worker.drain();
        assertEquals("edited", draft.text); assertEquals(2, capture.closed);
    }
    @Test void cancellationAndLostKeyCloseOnlyVoiceNotGuideTask() {
        for (VoiceRuntime.CancelReason reason : List.of(VoiceRuntime.CancelReason.USER, VoiceRuntime.CancelReason.FOCUS_LOST,
                VoiceRuntime.CancelReason.SCREEN_CLOSED, VoiceRuntime.CancelReason.DISCONNECTED, VoiceRuntime.CancelReason.FEEDBACK_HIDDEN)) {
            Draft draft = new Draft(); FakeCapture capture = new FakeCapture(); Queue worker = new Queue();
            VoiceRuntime runtime = new VoiceRuntime(draft, capture, c -> (r, x) -> { fail("Cancelled operation dispatched"); return null; },
                    () -> VoiceConfig.defaults().withEnabled(true), worker, Runnable::run);
            runtime.press(); runtime.cancel(reason); worker.drain();
            assertEquals(0, capture.opened); assertEquals("existing", draft.text);
            assertEquals(VoiceRuntime.State.IDLE, runtime.status().state());
        }
        Draft draft = new Draft(); FakeCapture capture = new FakeCapture(); Queue worker = new Queue();
        VoiceRuntime runtime = new VoiceRuntime(draft, capture, c -> (r, x) -> { fail(); return null; },
                () -> VoiceConfig.defaults().withEnabled(true), worker, Runnable::run);
        runtime.pressPtt(); runtime.tick(true, true, false, true); worker.drain(); assertEquals(0, capture.opened);
        runtime.setFeedbackVisible(false); runtime.press(); worker.drain(); assertEquals(0, capture.opened);
    }
    @Test void micButtonIsNotCancelledByKeyboardUpAndBrokenDeviceCloses() {
        Draft draft = new Draft(); FakeCapture capture = new FakeCapture(); Queue worker = new Queue();
        VoiceRuntime runtime = new VoiceRuntime(draft, capture, c -> (r, x) -> new SpeechToText.Result("hello", "fake", null),
                () -> VoiceConfig.defaults().withEnabled(true), worker, Runnable::run);
        runtime.press(); runtime.tick(true, true, false, true); assertEquals(VoiceRuntime.State.STARTING, runtime.status().state());
        capture.broken = true; worker.drain(); assertEquals(VoiceRuntime.State.ERROR, runtime.status().state());
        assertEquals("device_broken", runtime.status().code()); assertEquals(1, capture.closed);
    }
    @Test void limitStopsBoundedPcmAndClosesBeforeRecognition() {
        Draft draft = new Draft(); FakeCapture capture = new FakeCapture(); Queue worker = new Queue();
        VoiceRuntime runtime = new VoiceRuntime(draft, capture, c -> (r, x) -> {
            assertEquals(32_000, r.clip().pcm().length); assertEquals(1, capture.closed);
            return new SpeechToText.Result("limit", "fake", null);
        }, () -> VoiceConfig.defaults().withEnabled(true).withLimits(1, 1), worker, Runnable::run, () -> 0);
        runtime.press(); worker.drain(); assertEquals(VoiceRuntime.State.READY, runtime.status().state());
        assertEquals(1, capture.closed);
    }    @Test void cancellationAndCloseWhileFactoryOpenIsPendingReleaseCaptureSlot() throws Exception {
        for (boolean disconnect : List.of(false, true)) {
            Draft draft = new Draft();
            java.util.concurrent.CountDownLatch firstEntered = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch cancelNotified = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch secondOpened = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch secondRead = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch secondFinished = new java.util.concurrent.CountDownLatch(1);
            AtomicInteger openings = new AtomicInteger(), transcriptions = new AtomicInteger(), closes = new AtomicInteger();
            AudioCapture.Factory factory = new AudioCapture.Factory() {
                public AudioCapture open(String device, VoiceCancellation cancellation) throws Exception {
                    if (openings.getAndIncrement() == 0) {
                        try (AutoCloseable hook = cancellation.onCancel(cancelNotified::countDown)) {
                            firstEntered.countDown();
                            assertTrue(cancelNotified.await(2, java.util.concurrent.TimeUnit.SECONDS));
                            cancellation.check();
                            throw new AssertionError("Cancelled opening continued");
                        }
                    }
                    secondOpened.countDown();
                    return new AudioCapture() {
                        boolean closed;
                        public int read(byte[] bytes) throws Exception {
                            secondRead.countDown();
                            assertTrue(secondFinished.await(2, java.util.concurrent.TimeUnit.SECONDS));
                            bytes[0] = 1; bytes[1] = 0; return 2;
                        }
                        public void close() { if (!closed) { closed = true; closes.incrementAndGet(); } }
                    };
                }
                public List<AudioCapture.Device> devices() { return List.of(); }
            };
            try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                VoiceRuntime runtime = new VoiceRuntime(draft, factory, config -> (request, cancellation) -> {
                    transcriptions.incrementAndGet(); return new SpeechToText.Result("hello", "fake-native", null);
                }, () -> VoiceConfig.defaults().withEnabled(true), workers, Runnable::run);
                try {
                runtime.press(); assertTrue(firstEntered.await(2, java.util.concurrent.TimeUnit.SECONDS));
                if (disconnect) runtime.close(); else runtime.cancel(VoiceRuntime.CancelReason.USER);
                assertEquals(VoiceRuntime.State.IDLE, runtime.status().state());
                assertTrue(cancelNotified.await(2, java.util.concurrent.TimeUnit.SECONDS));
                if (disconnect) {
                    runtime.press(); assertEquals(1, openings.get());
                } else {
                    runtime.press(); assertTrue(secondOpened.await(2, java.util.concurrent.TimeUnit.SECONDS));
                    assertTrue(secondRead.await(2, java.util.concurrent.TimeUnit.SECONDS));
                    runtime.release(); secondFinished.countDown();
                    // A barrier queued after active worker completion is not sufficient on a
                    // per-task executor, so the draft callback is the deterministic completion signal.
                    workers.shutdown(); assertTrue(workers.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS));
                    assertEquals(1, transcriptions.get()); assertEquals(1, closes.get());
                    assertEquals("existing hello", draft.text); assertEquals(VoiceRuntime.State.READY, runtime.status().state());
                    runtime.close();
                }
                assertEquals(disconnect ? 0 : 1, transcriptions.get());
                } finally { secondFinished.countDown(); runtime.close(); }
            }
        }
    }

}
