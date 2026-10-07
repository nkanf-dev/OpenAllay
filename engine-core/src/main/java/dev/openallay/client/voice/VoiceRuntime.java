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
    @dev.openallay.value.ValueType(DeliveryReceipt.ValueSchemaProvider.class)
public static final class DeliveryReceipt {
    private final UUID id;
    private final DeliveryKind kind;
    public DeliveryReceipt(UUID id, DeliveryKind kind) {
 Objects.requireNonNull(id); Objects.requireNonNull(kind);
        this.id = id;
        this.kind = kind;
    }
    public UUID id() { return id; }
    public DeliveryKind kind() { return kind; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DeliveryReceipt)) return false;
        DeliveryReceipt that = (DeliveryReceipt) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(kind, that.kind);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        return hash;
    }
    @Override public String toString() { return "DeliveryReceipt[id=" + id + ", kind=" + kind + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DeliveryReceipt> schema() {
            return new dev.openallay.value.ValueSchema<>(DeliveryReceipt.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DeliveryReceipt>>asList(new dev.openallay.value.ValueSchema.Component<>(DeliveryReceipt.class, "id", DeliveryReceipt::id), new dev.openallay.value.ValueSchema.Component<>(DeliveryReceipt.class, "kind", DeliveryReceipt::kind)), arguments -> new DeliveryReceipt((UUID) arguments[0], (DeliveryKind) arguments[1]));
        }
    }
}
    @dev.openallay.value.ValueType(DraftTarget.ValueSchemaProvider.class)
public static final class DraftTarget {
    private final UUID actorId;
    private final String uiOwnerId;
    private final long uiGeneration;
    private final String sessionId;
    private final UUID sessionOwner;
    private final UUID connectionGeneration;
    private final long draftRevision;
    public DraftTarget(UUID actorId, String uiOwnerId, long uiGeneration, String sessionId, UUID sessionOwner, UUID connectionGeneration, long draftRevision) {

            Objects.requireNonNull(actorId); Objects.requireNonNull(uiOwnerId); Objects.requireNonNull(sessionId);
            Objects.requireNonNull(sessionOwner); Objects.requireNonNull(connectionGeneration);

        this.actorId = actorId;
        this.uiOwnerId = uiOwnerId;
        this.uiGeneration = uiGeneration;
        this.sessionId = sessionId;
        this.sessionOwner = sessionOwner;
        this.connectionGeneration = connectionGeneration;
        this.draftRevision = draftRevision;
    }
    public UUID actorId() { return actorId; }
    public String uiOwnerId() { return uiOwnerId; }
    public long uiGeneration() { return uiGeneration; }
    public String sessionId() { return sessionId; }
    public UUID sessionOwner() { return sessionOwner; }
    public UUID connectionGeneration() { return connectionGeneration; }
    public long draftRevision() { return draftRevision; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DraftTarget)) return false;
        DraftTarget that = (DraftTarget) other;
        return java.util.Objects.equals(actorId, that.actorId) && java.util.Objects.equals(uiOwnerId, that.uiOwnerId) && uiGeneration == that.uiGeneration && java.util.Objects.equals(sessionId, that.sessionId) && java.util.Objects.equals(sessionOwner, that.sessionOwner) && java.util.Objects.equals(connectionGeneration, that.connectionGeneration) && draftRevision == that.draftRevision;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(actorId);
        hash = 31 * hash + java.util.Objects.hashCode(uiOwnerId);
        hash = 31 * hash + Long.hashCode(uiGeneration);
        hash = 31 * hash + java.util.Objects.hashCode(sessionId);
        hash = 31 * hash + java.util.Objects.hashCode(sessionOwner);
        hash = 31 * hash + java.util.Objects.hashCode(connectionGeneration);
        hash = 31 * hash + Long.hashCode(draftRevision);
        return hash;
    }
    @Override public String toString() { return "DraftTarget[actorId=" + actorId + ", uiOwnerId=" + uiOwnerId + ", uiGeneration=" + uiGeneration + ", sessionId=" + sessionId + ", sessionOwner=" + sessionOwner + ", connectionGeneration=" + connectionGeneration + ", draftRevision=" + draftRevision + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DraftTarget> schema() {
            return new dev.openallay.value.ValueSchema<>(DraftTarget.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DraftTarget>>asList(new dev.openallay.value.ValueSchema.Component<>(DraftTarget.class, "actorId", DraftTarget::actorId), new dev.openallay.value.ValueSchema.Component<>(DraftTarget.class, "uiOwnerId", DraftTarget::uiOwnerId), new dev.openallay.value.ValueSchema.Component<>(DraftTarget.class, "uiGeneration", DraftTarget::uiGeneration), new dev.openallay.value.ValueSchema.Component<>(DraftTarget.class, "sessionId", DraftTarget::sessionId), new dev.openallay.value.ValueSchema.Component<>(DraftTarget.class, "sessionOwner", DraftTarget::sessionOwner), new dev.openallay.value.ValueSchema.Component<>(DraftTarget.class, "connectionGeneration", DraftTarget::connectionGeneration), new dev.openallay.value.ValueSchema.Component<>(DraftTarget.class, "draftRevision", DraftTarget::draftRevision)), arguments -> new DraftTarget((UUID) arguments[0], (String) arguments[1], (Long) arguments[2], (String) arguments[3], (UUID) arguments[4], (UUID) arguments[5], (Long) arguments[6]));
        }
    }
}
    public interface DraftPort {
        DraftTarget capture();
        Insertion append(DraftTarget target, String text);
        CompletableFuture<ToolResult<DeliveryReceipt>> send(DraftTarget target, String text, BooleanSupplier admissionFence);
        /** Keep refused spoken text separate from typed text, images and pending-edit intent. */
        Insertion retainPending(DraftTarget target, String text);
    }
    @dev.openallay.value.ValueType(Status.ValueSchemaProvider.class)
public static final class Status {
    private final State state;
    private final String code;
    private final long elapsedMillis;
    private final long maxMillis;
    private final String source;
    private final SpeechToText.Usage usage;
    private final UUID receipt;
    public Status(State state, String code, long elapsedMillis, long maxMillis, String source, SpeechToText.Usage usage, UUID receipt) {
        this.state = state;
        this.code = code;
        this.elapsedMillis = elapsedMillis;
        this.maxMillis = maxMillis;
        this.source = source;
        this.usage = usage;
        this.receipt = receipt;
    }
    public State state() { return state; }
    public String code() { return code; }
    public long elapsedMillis() { return elapsedMillis; }
    public long maxMillis() { return maxMillis; }
    public String source() { return source; }
    public SpeechToText.Usage usage() { return usage; }
    public UUID receipt() { return receipt; }
public boolean active() { return state == State.STARTING || state == State.RECORDING
                || state == State.TRANSCRIBING || state == State.DELIVERING; }
public boolean indicatorVisible() { return state != State.IDLE; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Status)) return false;
        Status that = (Status) other;
        return java.util.Objects.equals(state, that.state) && java.util.Objects.equals(code, that.code) && elapsedMillis == that.elapsedMillis && maxMillis == that.maxMillis && java.util.Objects.equals(source, that.source) && java.util.Objects.equals(usage, that.usage) && java.util.Objects.equals(receipt, that.receipt);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(state);
        hash = 31 * hash + java.util.Objects.hashCode(code);
        hash = 31 * hash + Long.hashCode(elapsedMillis);
        hash = 31 * hash + Long.hashCode(maxMillis);
        hash = 31 * hash + java.util.Objects.hashCode(source);
        hash = 31 * hash + java.util.Objects.hashCode(usage);
        hash = 31 * hash + java.util.Objects.hashCode(receipt);
        return hash;
    }
    @Override public String toString() { return "Status[state=" + state + ", code=" + code + ", elapsedMillis=" + elapsedMillis + ", maxMillis=" + maxMillis + ", source=" + source + ", usage=" + usage + ", receipt=" + receipt + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Status> schema() {
            return new dev.openallay.value.ValueSchema<>(Status.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Status>>asList(new dev.openallay.value.ValueSchema.Component<>(Status.class, "state", Status::state), new dev.openallay.value.ValueSchema.Component<>(Status.class, "code", Status::code), new dev.openallay.value.ValueSchema.Component<>(Status.class, "elapsedMillis", Status::elapsedMillis), new dev.openallay.value.ValueSchema.Component<>(Status.class, "maxMillis", Status::maxMillis), new dev.openallay.value.ValueSchema.Component<>(Status.class, "source", Status::source), new dev.openallay.value.ValueSchema.Component<>(Status.class, "usage", Status::usage), new dev.openallay.value.ValueSchema.Component<>(Status.class, "receipt", Status::receipt)), arguments -> new Status((State) arguments[0], (String) arguments[1], (Long) arguments[2], (Long) arguments[3], (String) arguments[4], (SpeechToText.Usage) arguments[5], (UUID) arguments[6]));
        }
    }
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
                final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<dev.openallay.client.voice.VoiceRuntime.DeliveryReceipt> value; ToolResult.Success<DeliveryReceipt> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
if (failure == null && (($oaPattern0_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern0_holder.bound = (ToolResult.Success<DeliveryReceipt>) $oaPattern0_holder.value) != null))) {
                    DeliveryReceipt receipt = $oaPattern0_holder.bound.value();
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
        final class $oaPattern1_Holder { java.lang.Throwable value; AudioPermissionException bound; }
final $oaPattern1_Holder $oaPattern1_holder = new $oaPattern1_Holder();
if ((($oaPattern1_holder.value = failure) instanceof dev.openallay.client.voice.AudioPermissionException && (($oaPattern1_holder.bound = (AudioPermissionException) $oaPattern1_holder.value) != null))) {
            return switch ($oaPattern1_holder.bound.diagnostic()) {
                case DENIED, RESTRICTED -> "microphone_denied";
                case LAUNCHER_NOT_PREPARED -> "microphone_launcher_unprepared";
                case CHECK_FAILED -> "microphone_permission_unavailable";
            };
        }
        final class $oaPattern2_Holder { java.lang.Throwable value; AudioCapture.CaptureException bound; }
final $oaPattern2_Holder $oaPattern2_holder = new $oaPattern2_Holder();
if ((($oaPattern2_holder.value = failure) instanceof dev.openallay.client.voice.AudioCapture.CaptureException && (($oaPattern2_holder.bound = (AudioCapture.CaptureException) $oaPattern2_holder.value) != null))) {
            return switch ($oaPattern2_holder.bound.failure()) {
                case DEVICE_DISCONNECTED, READ_FAILED -> "device_broken";
                case DEVICE_UNAVAILABLE -> "microphone_device_unavailable";
                case BACKEND_UNAVAILABLE -> "microphone_backend_unavailable";
                case UNSUPPORTED_FORMAT -> "microphone_format_unsupported";
                case OPEN_FAILED -> "microphone_open_failed";
                case OPEN_TIMEOUT, OPEN_BUSY -> "microphone_open_failed";
            };
        }
        final class $oaPattern3_Holder { java.lang.Throwable value; HttpSpeechToText.Failure bound; }
final $oaPattern3_Holder $oaPattern3_holder = new $oaPattern3_Holder();
if ((($oaPattern3_holder.value = failure) instanceof dev.openallay.client.voice.HttpSpeechToText.Failure && (($oaPattern3_holder.bound = (HttpSpeechToText.Failure) $oaPattern3_holder.value) != null))) return $oaPattern3_holder.bound.code();
        final class $oaPattern4_Holder { java.lang.Throwable value; NativeSpeechToText.Failure bound; }
final $oaPattern4_Holder $oaPattern4_holder = new $oaPattern4_Holder();
if ((($oaPattern4_holder.value = failure) instanceof dev.openallay.client.voice.NativeSpeechToText.Failure && (($oaPattern4_holder.bound = (NativeSpeechToText.Failure) $oaPattern4_holder.value) != null))) return $oaPattern4_holder.bound.code();
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
