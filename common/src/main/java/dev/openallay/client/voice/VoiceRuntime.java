package dev.openallay.client.voice;

import dev.openallay.tool.ToolResult;
import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Explicit PTT owner. All capture, recognition and cleanup run off the game thread. */
public final class VoiceRuntime implements VoiceInputActions, AutoCloseable {
    public enum State { IDLE, STARTING, RECORDING, TRANSCRIBING, DELIVERING, READY, PENDING, ERROR }
    public enum CancelReason { USER, FOCUS_LOST, SCREEN_CLOSED, DISCONNECTED, KEY_LOST, LIMIT, DEVICE_BROKEN, FEEDBACK_HIDDEN }
    public enum Insertion { INSERTED, PENDING, REJECTED }
    public enum Origin { FULLSCREEN, GAMEPLAY, HUD_INPUT }
    public enum DeliveryKind { SENT, QUEUED }
    public record DeliveryReceipt(UUID id, DeliveryKind kind) {
        public DeliveryReceipt { Objects.requireNonNull(id); Objects.requireNonNull(kind); }
    }
    public record DraftTarget(UUID actorId, String uiOwnerId, long uiGeneration, String sessionId,
            UUID sessionOwner, UUID connectionGeneration, long draftRevision) {
        public DraftTarget {
            Objects.requireNonNull(actorId); Objects.requireNonNull(uiOwnerId); Objects.requireNonNull(sessionId);
            Objects.requireNonNull(sessionOwner); Objects.requireNonNull(connectionGeneration);
        }
    }
    public interface DraftPort {
        DraftTarget capture();
        Insertion append(DraftTarget target, String text);
        CompletableFuture<ToolResult<DeliveryReceipt>> send(DraftTarget target, String text, BooleanSupplier admissionFence);
        /** Keep refused spoken text separate from typed text, images and pending-edit intent. */
        Insertion retainPending(DraftTarget target, String text);
    }
    public record Status(State state, String code, long elapsedMillis, long maxMillis,
            String source, SpeechToText.Usage usage, UUID receipt) {
        public boolean active() { return state == State.STARTING || state == State.RECORDING
                || state == State.TRANSCRIBING || state == State.DELIVERING; }
        public boolean indicatorVisible() { return state != State.IDLE; }
    }
    private static final class Operation {
        final long id;
        final DraftTarget target;
        final VoiceConfig config;
        final Origin origin;
        final VoiceConfig.GameplayAction action;
        final VoiceCancellation cancellation = new VoiceCancellation();
        final long started;
        volatile boolean finish;
        Operation(long id, DraftTarget target, VoiceConfig config, Origin origin, long started) {
            this.id = id; this.target = target; this.config = config; this.origin = origin; this.started = started;
            this.action = origin == Origin.FULLSCREEN ? VoiceConfig.GameplayAction.DRAFT : config.gameplayAction();
        }
    }
    private final Semaphore captureSlot = new Semaphore(1);
    private final DraftPort drafts;
    private final AudioCapture.Factory captures;
    private final Function<VoiceConfig, SpeechToText> backends;
    private final Supplier<VoiceConfig> config;
    private final Executor worker;
    private final Executor client;
    private final LongSupplier nanoTime;
    private long generation;
    private Operation operation;
    private boolean closed;
    private boolean feedbackVisible = true;
    private long statusSince;
    private Status status = new Status(State.IDLE, "idle", 0, 0, "", null, null);

    public VoiceRuntime(DraftPort drafts, AudioCapture.Factory captures,
            Function<VoiceConfig, SpeechToText> backends, Supplier<VoiceConfig> config,
            Executor worker, Executor client) {
        this(drafts, captures, backends, config, worker, client, System::nanoTime);
    }
    VoiceRuntime(DraftPort drafts, AudioCapture.Factory captures,
            Function<VoiceConfig, SpeechToText> backends, Supplier<VoiceConfig> config,
            Executor worker, Executor client, LongSupplier nanoTime) {
        this.drafts = Objects.requireNonNull(drafts); this.captures = Objects.requireNonNull(captures);
        this.backends = Objects.requireNonNull(backends); this.config = Objects.requireNonNull(config);
        this.worker = Objects.requireNonNull(worker); this.client = Objects.requireNonNull(client);
        this.nanoTime = Objects.requireNonNull(nanoTime);
    }
    @Override public synchronized boolean enabled() { return !closed && config.get().enabled(); }
    @Override public synchronized Status status() {
        if (operation == null) return status;
        return new Status(status.state(), status.code(), elapsed(operation), status.maxMillis(), status.source(), status.usage(), status.receipt());
    }
    @Override public void press() { start(Origin.FULLSCREEN); }
    @Override public void pressPtt() { start(Origin.GAMEPLAY); }
    @Override public void pressExternalPtt() { start(Origin.HUD_INPUT); }
    private synchronized void start(Origin origin) {
        VoiceConfig selected = config.get();
        if (closed || !selected.enabled() || operation != null || !feedbackVisible) return;
        DraftTarget target = drafts.capture();
        if (target == null) { setStatus(State.ERROR, "no_session", 0, "", null); return; }
        Operation next = new Operation(++generation, target, selected, origin, nanoTime.getAsLong());
        operation = next;
        setStatus(State.STARTING, "starting", selected.maxClipSeconds() * 1000L, "", null);
        worker.execute(() -> record(next));
    }
    @Override public synchronized void release() {
        if (operation != null && (status.state() == State.STARTING || status.state() == State.RECORDING)) operation.finish = true;
    }
    @Override public void cancel(CancelReason reason) {
        Operation old;
        synchronized (this) {
            old = operation; operation = null; generation++;
            setStatus(State.IDLE, "cancelled_" + reason.name().toLowerCase(java.util.Locale.ROOT), 0, "", null);
        }
        if (old != null) old.cancellation.cancel(worker);
    }
    /** Loader calls each client tick, independent of optional HUD and notification settings. */
    public void setFeedbackVisible(boolean visible) {
        synchronized (this) { feedbackVisible = visible; }
        boolean active;
        synchronized (this) { active = operation != null; }
        if (!visible && active) cancel(CancelReason.FEEDBACK_HIDDEN);
    }
    public void tick(boolean focused, boolean connected, boolean physicalPttDown) {
        tick(focused, connected, physicalPttDown, true);
    }
    public void tick(boolean focused, boolean connected, boolean physicalPttDown, boolean visibleFeedback) {
        CancelReason cancel = null;
        synchronized (this) {
            feedbackVisible = visibleFeedback;
            if (operation != null) {
                if (!visibleFeedback) cancel = CancelReason.FEEDBACK_HIDDEN;
                else if (!connected) cancel = CancelReason.DISCONNECTED;
                else if (!focused) cancel = CancelReason.FOCUS_LOST;
                else if (operation.origin == Origin.GAMEPLAY && !physicalPttDown && !operation.finish
                        && (status.state() == State.STARTING || status.state() == State.RECORDING)) cancel = CancelReason.KEY_LOST;
            } else if (status.indicatorVisible() && nanoTime.getAsLong() - statusSince > 6_000_000_000L) {
                setStatus(State.IDLE, "idle", 0, "", null);
            }
        }
        if (cancel != null) cancel(cancel);
    }
    private void record(Operation op) {
        boolean acquired = false;
        try {
            while (!(acquired = captureSlot.tryAcquire(50, TimeUnit.MILLISECONDS))) op.cancellation.check();
            op.cancellation.check();
            SpeechToText backend = Objects.requireNonNull(backends.apply(op.config));
            int maximum = op.config.maxClipSeconds() * PcmClip.BYTES_PER_SECOND;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.min(maximum, 64 * 1024));
            try (AudioCapture capture = captures.open(op.config.deviceId(), op.cancellation);
                    AutoCloseable hook = op.cancellation.onCancel(capture::close)) {
                op.cancellation.check();
                dispatch(op, () -> setStatus(State.RECORDING, "recording", op.config.maxClipSeconds() * 1000L, "", null));
                byte[] buffer = new byte[4096];
                long recordingStarted = nanoTime.getAsLong();
                while (!op.finish && bytes.size() < maximum
                        && nanoTime.getAsLong() - recordingStarted < op.config.maxClipSeconds() * 1_000_000_000L) {
                    op.cancellation.check();
                    int count = capture.read(buffer);
                    if (count < 0) throw new IllegalStateException("device_broken");
                    if (count > buffer.length || (count & 1) != 0) throw new IllegalStateException("device_broken");
                    if (count == 0) { LockSupport.parkNanos(5_000_000L); continue; }
                    bytes.write(buffer, 0, Math.min(count, maximum - bytes.size()));
                }
            }
            captureSlot.release(); acquired = false;
            op.cancellation.check();
            if (bytes.size() == 0) throw new IllegalStateException("empty_audio");
            PcmClip clip = new PcmClip(bytes.toByteArray());
            dispatch(op, () -> setStatus(State.TRANSCRIBING, "transcribing", op.config.maxClipSeconds() * 1000L, "", null));
            SpeechToText.Result result = backend.transcribe(new SpeechToText.Request(clip, op.config.language(), op.config.cpuThreads()), op.cancellation);
            op.cancellation.check();
            dispatch(op, () -> deliver(op, result));
        } catch (CancellationException failure) {
            // Only the operation's token proves that the caller fenced this result.
            // A provider cancelling itself must not leave STARTING/RECORDING stuck.
            if (!op.cancellation.cancelled()) dispatch(op, () -> {
                operation = null;
                setStatus(State.ERROR, "microphone_open_failed", 0, "", null);
            });
        } catch (Exception | LinkageError failure) {
            String code = safeCode(failure);
            dispatch(op, () -> { operation = null; setStatus(State.ERROR, code, 0, "", null); });
        } finally {
            if (acquired) captureSlot.release();
        }
    }
    private void deliver(Operation op, SpeechToText.Result transcript) {
        if (op.action == VoiceConfig.GameplayAction.DRAFT) {
            Insertion insertion = drafts.append(op.target, transcript.text());
            operation = null;
            setStatus(insertion == Insertion.INSERTED ? State.READY : insertion == Insertion.PENDING ? State.PENDING : State.ERROR,
                    insertion == Insertion.INSERTED ? "draft_inserted" : insertion == Insertion.PENDING ? "draft_pending" : "draft_rejected",
                    0, transcript.source(), transcript.usage());
            return;
        }
        setStatus(State.DELIVERING, "voice_sending", 0, transcript.source(), transcript.usage());
        try {
            CompletableFuture<ToolResult<DeliveryReceipt>> admission = Objects.requireNonNull(
                    drafts.send(op.target, transcript.text(), () -> current(op)));
            admission.whenComplete((result, failure) -> dispatch(op, () -> {
                if (failure == null && result instanceof ToolResult.Success<DeliveryReceipt> success) {
                    DeliveryReceipt receipt = success.value();
                    if (receipt != null) {
                        operation = null;
                        setStatus(State.READY, receipt.kind() == DeliveryKind.SENT ? "voice_sent" : "voice_queued",
                                0, transcript.source(), transcript.usage(), receipt.id());
                        return;
                    }
                }
                deliveryFailed(op, transcript);
            }));
        } catch (RuntimeException failure) {
            deliveryFailed(op, transcript);
        }
    }
    private void deliveryFailed(Operation op, SpeechToText.Result transcript) {
        if (!current(op)) return;
        Insertion retained;
        try { retained = drafts.retainPending(op.target, transcript.text()); }
        catch (RuntimeException failure) { retained = Insertion.REJECTED; }
        operation = null;
        setStatus(State.ERROR, retained == Insertion.PENDING ? "voice_send_failed" : "voice_send_rejected",
                0, transcript.source(), transcript.usage());
    }
    private synchronized boolean current(Operation op) {
        return !closed && operation == op && generation == op.id && !op.cancellation.cancelled();
    }
    private void dispatch(Operation op, Runnable action) {
        client.execute(() -> { synchronized (VoiceRuntime.this) {
            if (current(op)) action.run();
        }});
    }
    static String safeCode(Throwable failure) {
        if (failure instanceof MacMicrophonePermission.PermissionException permission) {
            return switch (permission.failure()) {
                case DENIED, RESTRICTED -> "microphone_denied";
                case LAUNCHER_NOT_PREPARED -> "microphone_launcher_unprepared";
                case CHECK_FAILED -> "microphone_permission_unavailable";
            };
        }
        if (failure instanceof AudioCapture.CaptureException capture) {
            return switch (capture.failure()) {
                case DEVICE_DISCONNECTED, READ_FAILED -> "device_broken";
                case DEVICE_UNAVAILABLE -> "microphone_device_unavailable";
                case BACKEND_UNAVAILABLE -> "microphone_backend_unavailable";
                case UNSUPPORTED_FORMAT -> "microphone_format_unsupported";
                case OPEN_FAILED -> "microphone_open_failed";
                case OPEN_TIMEOUT, OPEN_BUSY -> "microphone_open_failed";
            };
        }
        if (failure instanceof HttpSpeechToText.Failure http) return http.code();
        if (failure instanceof NativeSpeechToText.Failure nativeFailure) return nativeFailure.code();
        String message = failure.getMessage();
        return message != null && java.util.Set.of("empty_audio", "device_broken", "microphone_denied", "microphone_launcher_unprepared",
                "microphone_permission_unavailable", "model_not_installed").contains(message) ? message : "voice_failed";
    }
    private long elapsed(Operation op) { return Math.max(0, (nanoTime.getAsLong() - op.started) / 1_000_000L); }
    private void setStatus(State state, String code, long maximum, String source, SpeechToText.Usage usage) {
        setStatus(state, code, maximum, source, usage, null);
    }
    private void setStatus(State state, String code, long maximum, String source, SpeechToText.Usage usage, UUID receipt) {
        status = new Status(state, code, 0, maximum, source, usage, receipt); statusSince = nanoTime.getAsLong();
    }
    @Override public void close() {
        synchronized (this) { closed = true; }
        cancel(CancelReason.DISCONNECTED);
    }
}
