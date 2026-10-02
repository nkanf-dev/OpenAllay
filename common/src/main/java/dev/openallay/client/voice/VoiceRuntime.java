package dev.openallay.client.voice;

import java.io.ByteArrayOutputStream;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Explicit PTT owner. All capture, recognition and cleanup run off the game thread. */
public final class VoiceRuntime implements VoiceInputActions, AutoCloseable {
    public enum State { IDLE, STARTING, RECORDING, TRANSCRIBING, READY, PENDING, ERROR }
    public enum CancelReason { USER, FOCUS_LOST, SCREEN_CLOSED, DISCONNECTED, KEY_LOST, LIMIT, DEVICE_BROKEN, FEEDBACK_HIDDEN }
    public enum Insertion { INSERTED, PENDING, REJECTED }
    public record DraftTarget(String actorId, String sessionId, long draftRevision) {
        public DraftTarget { Objects.requireNonNull(actorId); Objects.requireNonNull(sessionId); }
    }
    public interface DraftPort {
        DraftTarget capture();
        Insertion append(DraftTarget target, String text);
    }
    public record Status(State state, String code, long elapsedMillis, long maxMillis,
            String source, SpeechToText.Usage usage) {
        public boolean active() { return state == State.STARTING || state == State.RECORDING || state == State.TRANSCRIBING; }
        public boolean indicatorVisible() { return state != State.IDLE; }
    }
    private static final class Operation {
        final long id;
        final DraftTarget target;
        final VoiceConfig config;
        final boolean ptt;
        final VoiceCancellation cancellation = new VoiceCancellation();
        final long started;
        volatile boolean finish;
        Operation(long id, DraftTarget target, VoiceConfig config, boolean ptt, long started) {
            this.id = id; this.target = target; this.config = config; this.ptt = ptt; this.started = started;
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
    private Status status = new Status(State.IDLE, "idle", 0, 0, "", null);

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
        return new Status(status.state(), status.code(), elapsed(operation), status.maxMillis(), status.source(), status.usage());
    }
    @Override public void press() { start(false); }
    public void pressPtt() { start(true); }
    private synchronized void start(boolean ptt) {
        if (!enabled() || operation != null || !feedbackVisible) return;
        DraftTarget target = drafts.capture();
        if (target == null) { setStatus(State.ERROR, "no_session", 0, "", null); return; }
        VoiceConfig selected = config.get();
        Operation next = new Operation(++generation, target, selected, ptt, nanoTime.getAsLong());
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
                else if (operation.ptt && !physicalPttDown && !operation.finish
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
            dispatch(op, () -> {
                Insertion insertion = drafts.append(op.target, result.text());
                operation = null;
                setStatus(insertion == Insertion.INSERTED ? State.READY : insertion == Insertion.PENDING ? State.PENDING : State.ERROR,
                        insertion == Insertion.INSERTED ? "draft_inserted" : insertion == Insertion.PENDING ? "draft_pending" : "draft_rejected",
                        0, result.source(), result.usage());
            });
        } catch (CancellationException ignored) {
            // Caller already fenced this operation; no result enters a new session.
        } catch (Exception | LinkageError failure) {
            String code = safeCode(failure);
            dispatch(op, () -> { operation = null; setStatus(State.ERROR, code, 0, "", null); });
        } finally {
            if (acquired) captureSlot.release();
        }
    }
    private void dispatch(Operation op, Runnable action) {
        client.execute(() -> { synchronized (VoiceRuntime.this) {
            if (!closed && operation == op && generation == op.id && !op.cancellation.cancelled()) action.run();
        }});
    }
    private static String safeCode(Throwable failure) {
        if (failure instanceof MacMicrophonePermission.PermissionException permission) {
            return switch (permission.failure()) {
                case DENIED, RESTRICTED -> "microphone_denied";
                case LAUNCHER_NOT_PREPARED -> "microphone_launcher_unprepared";
                case CHECK_FAILED -> "microphone_permission_unavailable";
            };
        }
        if (failure instanceof JavaSoundCapture.CaptureException capture) {
            return switch (capture.failure()) {
                case DEVICE_DISCONNECTED, READ_FAILED -> "device_broken";
                case DEVICE_UNAVAILABLE -> "microphone_device_unavailable";
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
        status = new Status(state, code, 0, maximum, source, usage); statusSince = nanoTime.getAsLong();
    }
    @Override public void close() {
        synchronized (this) { closed = true; }
        cancel(CancelReason.DISCONNECTED);
    }
}
