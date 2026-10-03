package dev.openallay.client.voice;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import dev.openallay.tool.ToolResult;
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
        String pending; boolean closed; int sendCalls, acceptedSends, retained;
        final UUID actor = UUID.randomUUID(), sessionOwner = UUID.randomUUID(), connection = UUID.randomUUID();
        final UUID receipt = UUID.randomUUID();
        VoiceRuntime.DraftTarget sentTarget; String sentText; BooleanSupplier fence;
        VoiceRuntime.DeliveryKind kind = VoiceRuntime.DeliveryKind.SENT;
        CompletableFuture<ToolResult<VoiceRuntime.DeliveryReceipt>> admission;
        boolean automaticAdmission = true;
        public VoiceRuntime.DraftTarget capture() {
            return new VoiceRuntime.DraftTarget(actor, "connection", 0, capturedSession, sessionOwner, connection, revision);
        }
        public CompletableFuture<ToolResult<VoiceRuntime.DeliveryReceipt>> send(
                VoiceRuntime.DraftTarget target, String transcript, BooleanSupplier admissionFence) {
            sendCalls++; sentTarget = target; sentText = transcript; fence = admissionFence;
            admission = new CompletableFuture<>();
            if (automaticAdmission) admit();
            return admission;
        }
        void admit() {
            if (closed || !fence.getAsBoolean()) {
                admission.complete(new ToolResult.Failure<>("message_cancelled", "Cancelled")); return;
            }
            acceptedSends++;
            admission.complete(new ToolResult.Success<>(new VoiceRuntime.DeliveryReceipt(receipt, kind)));
        }
        public VoiceRuntime.Insertion retainPending(VoiceRuntime.DraftTarget target, String transcript) {
            if (closed) return VoiceRuntime.Insertion.REJECTED;
            retained++; pending = transcript; return VoiceRuntime.Insertion.PENDING;
        }
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
    @Test void explicitExternalPttDefaultsToSendOnlySpeechWithAnActualReceipt() {
        Draft draft = new Draft(); FakeCapture capture = new FakeCapture(); Queue worker = new Queue();
        java.util.concurrent.atomic.AtomicReference<VoiceConfig> config = new java.util.concurrent.atomic.AtomicReference<>(
                VoiceConfig.defaults().withEnabled(true));
        VoiceRuntime runtime = new VoiceRuntime(draft, capture, c -> (r, x) -> {
            draft.selectedSession = "s2"; draft.revision++; draft.text = "typed edit with image and edit intent";
            config.set(config.get().withGameplayAction(VoiceConfig.GameplayAction.DRAFT));
            return new SpeechToText.Result("/compact literal spoken text", "synthetic", null);
        }, config::get, worker, Runnable::run);
        capture.onRead = runtime::release;
        runtime.pressPtt(); worker.drain();
        assertEquals(1, draft.sendCalls); assertEquals(1, draft.acceptedSends);
        assertEquals("/compact literal spoken text", draft.sentText);
        assertEquals("s1", draft.sentTarget.sessionId()); assertEquals(draft.actor, draft.sentTarget.actorId());
        assertEquals(draft.sessionOwner, draft.sentTarget.sessionOwner());
        assertEquals(draft.connection, draft.sentTarget.connectionGeneration());
        assertEquals(0, draft.sentTarget.uiGeneration()); assertEquals(0, draft.sentTarget.draftRevision());
        assertEquals("typed edit with image and edit intent", draft.text);
        assertNull(draft.pending); assertEquals(VoiceRuntime.State.READY, runtime.status().state());
        assertEquals("voice_sent", runtime.status().code()); assertEquals(draft.receipt, runtime.status().receipt());
    }

    @Test void nativeHudHoldOwnsReleaseIndependentlyOfGameplayPhysicalKeyState() {
        for (VoiceConfig.GameplayAction choice : VoiceConfig.GameplayAction.values()) {
            Draft draft = new Draft(); FakeCapture capture = new FakeCapture(); Queue worker = new Queue();
            VoiceRuntime runtime = new VoiceRuntime(draft, capture, c -> (r, x) -> new SpeechToText.Result("HUD speech", "synthetic", null),
                    () -> VoiceConfig.defaults().withEnabled(true).withGameplayAction(choice), worker, Runnable::run);
            capture.onRead = runtime::release;
            runtime.pressExternalPtt(); runtime.tick(true, true, false, true);
            assertEquals(VoiceRuntime.State.STARTING, runtime.status().state());
            worker.drain();
            if (choice == VoiceConfig.GameplayAction.SEND) {
                assertEquals(1, draft.acceptedSends); assertEquals("voice_sent", runtime.status().code());
                assertEquals("existing", draft.text);
            } else {
                assertEquals(0, draft.sendCalls); assertEquals("existing HUD speech", draft.text);
                assertEquals("draft_inserted", runtime.status().code());
            }
        }
    }

    @Test void configuredExternalDraftAndFullscreenMicNeverSendEvenIfChoiceChanges() {
        for (boolean external : List.of(false, true)) {
            Draft draft = new Draft(); FakeCapture capture = new FakeCapture(); Queue worker = new Queue();
            java.util.concurrent.atomic.AtomicReference<VoiceConfig> config = new java.util.concurrent.atomic.AtomicReference<>(
                    VoiceConfig.defaults().withEnabled(true).withGameplayAction(external
                            ? VoiceConfig.GameplayAction.DRAFT : VoiceConfig.GameplayAction.SEND));
            VoiceRuntime runtime = new VoiceRuntime(draft, capture, c -> (r, x) -> {
                config.set(config.get().withGameplayAction(VoiceConfig.GameplayAction.SEND));
                return new SpeechToText.Result("draft speech", "synthetic", null);
            }, config::get, worker, Runnable::run);
            capture.onRead = runtime::release;
            if (external) runtime.pressPtt(); else runtime.press();
            worker.drain();
            assertEquals(0, draft.sendCalls); assertEquals("existing draft speech", draft.text);
            assertEquals("draft_inserted", runtime.status().code()); assertNull(runtime.status().receipt());
        }
    }

    @Test void asyncAdmissionKeepsOperationActiveRejectsAnotherCaptureAndReportsExactQueuedReceipt() {
        Draft draft = new Draft(); draft.automaticAdmission = false; draft.kind = VoiceRuntime.DeliveryKind.QUEUED;
        FakeCapture capture = new FakeCapture(); Queue worker = new Queue();
        VoiceRuntime runtime = new VoiceRuntime(draft, capture, c -> (r, x) -> new SpeechToText.Result("spoken follow-up", "synthetic", null),
                () -> VoiceConfig.defaults().withEnabled(true), worker, Runnable::run);
        capture.onRead = runtime::release;
        runtime.pressPtt(); worker.drain();
        assertEquals(VoiceRuntime.State.DELIVERING, runtime.status().state()); assertTrue(runtime.status().active());
        assertNull(runtime.status().receipt());
        runtime.pressPtt(); worker.drain(); assertEquals(1, capture.opened); assertEquals(1, draft.sendCalls);
        draft.admit();
        assertEquals("voice_queued", runtime.status().code()); assertEquals(draft.receipt, runtime.status().receipt());
        assertEquals("existing", draft.text); assertEquals(1, draft.acceptedSends);
    }

    @Test void rejectedAndFailedAdmissionRetainSpeechSeparatelyWithoutSuccessOrAutomaticRetry() {
        for (boolean exceptional : List.of(false, true)) {
            Draft draft = new Draft(); draft.automaticAdmission = false;
            FakeCapture capture = new FakeCapture(); Queue worker = new Queue();
            VoiceRuntime runtime = new VoiceRuntime(draft, capture, c -> (r, x) -> new SpeechToText.Result("keep spoken text", "synthetic", null),
                    () -> VoiceConfig.defaults().withEnabled(true), worker, Runnable::run);
            capture.onRead = runtime::release; runtime.pressPtt(); worker.drain();
            draft.text = "typed draft stays"; draft.revision++;
            if (exceptional) draft.admission.completeExceptionally(new IllegalStateException("private failure"));
            else draft.admission.complete(new ToolResult.Failure<>("capability_unavailable", "No model"));
            assertEquals(VoiceRuntime.State.ERROR, runtime.status().state()); assertEquals("voice_send_failed", runtime.status().code());
            assertNull(runtime.status().receipt()); assertEquals("keep spoken text", draft.pending);
            assertEquals("typed draft stays", draft.text); assertEquals(1, draft.retained);
            draft.admission.complete(new ToolResult.Failure<>("again", "Duplicate"));
            runtime.tick(true, true, false, true); worker.drain();
            assertEquals(1, draft.sendCalls); assertEquals(0, draft.acceptedSends); assertEquals(1, draft.retained);
        }
    }

    @Test void cancellationBeforeDeliveryAndWhileAdmissionIsQueuedNeverDispatchesLateSpeech() {
        for (boolean duringAdmission : List.of(false, true)) {
            Draft draft = new Draft(); draft.automaticAdmission = false;
            FakeCapture capture = new FakeCapture(); Queue worker = new Queue(); Queue client = new Queue();
            VoiceRuntime runtime = new VoiceRuntime(draft, capture, c -> (r, x) -> new SpeechToText.Result("late speech", "synthetic", null),
                    () -> VoiceConfig.defaults().withEnabled(true), worker, client);
            capture.onRead = runtime::release; runtime.pressPtt(); worker.drain();
            if (duringAdmission) { client.drain(); assertEquals(VoiceRuntime.State.DELIVERING, runtime.status().state()); }
            runtime.cancel(VoiceRuntime.CancelReason.DISCONNECTED);
            if (duringAdmission) { assertFalse(draft.fence.getAsBoolean()); draft.admit(); }
            client.drain(); worker.drain();
            assertEquals(duringAdmission ? 1 : 0, draft.sendCalls); assertEquals(0, draft.acceptedSends);
            assertEquals(0, draft.retained); assertEquals("existing", draft.text);
            assertEquals(VoiceRuntime.State.IDLE, runtime.status().state()); assertNull(runtime.status().receipt());
        }
    }

    @Test void acceptedAdmissionIsNotRolledBackAndLateReceiptCannotOverwriteNewCapture() {
        Draft draft = new Draft(); draft.automaticAdmission = false;
        FakeCapture capture = new FakeCapture(); Queue worker = new Queue(); Queue client = new Queue();
        VoiceRuntime runtime = new VoiceRuntime(draft, capture, c -> (r, x) -> new SpeechToText.Result("accepted speech", "synthetic", null),
                () -> VoiceConfig.defaults().withEnabled(true), worker, client);
        capture.onRead = runtime::release;
        runtime.pressPtt(); worker.drain(); client.drain(); draft.admit();
        assertEquals(1, draft.acceptedSends);
        runtime.cancel(VoiceRuntime.CancelReason.USER); runtime.press(); client.drain();
        assertEquals(VoiceRuntime.State.STARTING, runtime.status().state()); assertEquals(1, draft.acceptedSends);
        assertEquals(0, draft.retained); runtime.close(); worker.drain(); client.drain();
    }

    @Test void captureBackendPermissionFormatAndDeviceErrorsHaveDistinctSafeCodes() {
        for (AudioCapture.Failure kind : AudioCapture.Failure.values()) {
            Draft draft = new Draft(); Queue worker = new Queue(); AtomicInteger transcriptions = new AtomicInteger();
            AudioCapture.Factory capture = new AudioCapture.Factory() {
                public AudioCapture open(String device, VoiceCancellation cancellation) throws Exception {
                    throw new AudioCapture.CaptureException(kind, "untrusted provider detail");
                }
                public List<AudioCapture.Device> devices() { return List.of(); }
            };
            VoiceRuntime runtime = new VoiceRuntime(draft, capture, config -> (request, cancellation) -> {
                transcriptions.incrementAndGet(); throw new AssertionError("Failed capture reached recognition");
            }, () -> VoiceConfig.defaults().withEnabled(true), worker, Runnable::run);
            runtime.press(); worker.drain();
            assertEquals(switch (kind) {
                case DEVICE_UNAVAILABLE -> "microphone_device_unavailable";
                case BACKEND_UNAVAILABLE -> "microphone_backend_unavailable";
                case UNSUPPORTED_FORMAT -> "microphone_format_unsupported";
                case OPEN_FAILED, OPEN_TIMEOUT, OPEN_BUSY -> "microphone_open_failed";
                case READ_FAILED, DEVICE_DISCONNECTED -> "device_broken";
            }, runtime.status().code());
            assertEquals(VoiceRuntime.State.ERROR, runtime.status().state());
            assertEquals(0, transcriptions.get()); assertEquals("existing", draft.text);
        }
        assertEquals("microphone_denied", VoiceRuntime.safeCode(new MacMicrophonePermission.PermissionException(
                MacMicrophonePermission.Failure.DENIED, "not a device failure")));
        assertEquals("microphone_launcher_unprepared", VoiceRuntime.safeCode(new MacMicrophonePermission.PermissionException(
                MacMicrophonePermission.Failure.LAUNCHER_NOT_PREPARED, "not a system-settings fix")));
        assertEquals("microphone_permission_unavailable", VoiceRuntime.safeCode(new MacMicrophonePermission.PermissionException(
                MacMicrophonePermission.Failure.CHECK_FAILED, "not a device failure")));
    }

    @Test void providerCancellationWithoutAnOperationCancellationReportsErrorAndAllowsRetry() {
        Draft draft = new Draft(); Queue worker = new Queue(); FakeCapture next = new FakeCapture();
        AtomicInteger openings = new AtomicInteger(), transcriptions = new AtomicInteger();
        AudioCapture.Factory capture = new AudioCapture.Factory() {
            public AudioCapture open(String device, VoiceCancellation cancellation) {
                assertFalse(cancellation.cancelled());
                if (openings.getAndIncrement() == 0)
                    throw new java.util.concurrent.CancellationException("Provider cancelled itself");
                return next.open(device, cancellation);
            }
            public List<AudioCapture.Device> devices() { return List.of(); }
        };
        VoiceRuntime runtime = new VoiceRuntime(draft, capture, config -> (request, cancellation) -> {
            transcriptions.incrementAndGet(); return new SpeechToText.Result("retry", "fake-native", null);
        }, () -> VoiceConfig.defaults().withEnabled(true), worker, Runnable::run);
        next.onRead = runtime::release;
        runtime.press(); worker.drain();
        assertEquals(VoiceRuntime.State.ERROR, runtime.status().state());
        assertEquals("microphone_open_failed", runtime.status().code());
        assertEquals(0, transcriptions.get()); assertEquals("existing", draft.text);
        runtime.press(); worker.drain();
        assertEquals(2, openings.get()); assertEquals(1, next.closed);
        assertEquals(1, transcriptions.get()); assertEquals(VoiceRuntime.State.READY, runtime.status().state());
        assertEquals("existing retry", draft.text);
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
