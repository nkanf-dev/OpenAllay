package dev.openallay.client.gui;

import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.client.gui.clipboard.ImageClipboard;
import dev.openallay.guide.GuideService;
import dev.openallay.model.image.ImageReference;
import dev.openallay.tool.ToolResult;
import dev.openallay.world.ClientObservationAnchor;
import java.util.Optional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** One connection/actor's in-memory presentation drafts. Views never own draft leases or tasks. */
public final class GuideClientUiState implements AutoCloseable {
    private final String ownerId = UUID.randomUUID().toString();
    private final Map<String, Draft> drafts = new LinkedHashMap<>();
    private final Map<UUID, ViewAttachment> views = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final Map<UUID, ObservationCapture> observationLeases = new LinkedHashMap<>();
    private final ComposerImageDraft images;
    private final Function<List<ImageReference>, CompletableFuture<ToolResult<Boolean>>> retain;
    private final Runnable release;
    private CompletableFuture<ToolResult<Boolean>> leaseWork =
            CompletableFuture.completedFuture(new ToolResult.Success<>(true));
    private String selectedSession;
    private long generation;
    private boolean closed;
    private ComposerImageDraft.Notice imageNotice = ComposerImageDraft.Notice.NONE;

    public GuideClientUiState(GuideService service, ImageClipboard clipboard, Executor worker,
            ClientEventDispatcher client) {
        this(service.snapshot().selectedSession(), clipboard, worker, client,
                service::importImage, service::releaseImportedImage,
                (owner, refs) -> service.retainDraftImages(owner, refs), service::releaseDraftImages);
    }

    /** Pure test seam. Every callback uses the injected client dispatcher; no clipboard read on attach. */
    public GuideClientUiState(String selectedSession, ImageClipboard clipboard, Executor worker,
            ClientEventDispatcher client,
            Function<byte[], CompletableFuture<ToolResult<ImageReference>>> importer,
            Consumer<ImageReference> discardedImport,
            java.util.function.BiFunction<String, List<ImageReference>, CompletableFuture<ToolResult<Boolean>>> retain,
            Consumer<String> release) {
        this.selectedSession = Objects.requireNonNull(selectedSession, "selectedSession");
        this.retain = refs -> retain.apply(ownerId, refs);
        this.release = () -> release.accept(ownerId);
        images = new ComposerImageDraft(clipboard, worker, client, importer, this::imagesChanged, discardedImport);
        draft(selectedSession);
        images.attach(selectedSession); // Owner attached for connection lifetime, not Screen lifetime.
    }

    /** Shared admission receipt semantics; native views do not own acceptance policy. */
    public static boolean submissionAccepted(boolean editing, ToolResult<?> result) {
        final class $oaPattern0_Holder { dev.openallay.tool.ToolResult<?> value; ToolResult.Success<?> bound; }
final $oaPattern0_Holder $oaPattern0_holder = new $oaPattern0_Holder();
return (($oaPattern0_holder.value = result) instanceof dev.openallay.tool.ToolResult.Success && (($oaPattern0_holder.bound = (ToolResult.Success<?>) $oaPattern0_holder.value) != null))
                && (editing ? Boolean.TRUE.equals($oaPattern0_holder.bound.value()) : $oaPattern0_holder.bound.value() instanceof UUID);
    }

    public String ownerId() { return ownerId; }
    public long generation() { return generation; }
    public boolean closed() { return closed; }
    public String selectedSession() { return selectedSession; }
    public ComposerImageDraft images() { return images; }

    /** Latest actual draft-store pin acknowledgement. Never wait on it from the native client thread. */
    public CompletableFuture<ToolResult<Boolean>> observationImagesSettled() {
        return closed ? CompletableFuture.completedFuture(new ToolResult.Failure<>(
                "observation_connection_closed", "Observation connection is closed")) : leaseWork;
    }
    public ComposerImageDraft.Notice imageNotice() { return imageNotice; }
    public String imageNoticeSession() { return images.changedSession(); }

    public void selectSession(String session) {
        if (closed) return;
        selectedSession = Objects.requireNonNull(session, "session");
        draft(session);
        images.selectSession(session);
    }

    public String readText(String session) { return closed ? "" : draft(session).text; }
    public long revision(String session) { return closed ? -1 : draft(session).revision; }
    public void setText(String session, String text) {
        if (closed) return;
        Draft draft = draft(session);
        String value = Objects.requireNonNull(text, "text");
        if (draft.text.equals(value)) return;
        draft.text = value;
        draft.revision++;
        changed();
    }

    public DraftIntent intent(String session) { return closed ? DraftIntent.defaults() : draft(session).intent; }

    public void setMode(String session, DraftMode mode) {
        if (closed) return;
        Draft draft = draft(session);
        setIntent(draft, new DraftIntent(mode, draft.intent.pendingId(), draft.intent.editInvalid()));
    }

    public void beginPendingEdit(String session, UUID pendingId, DraftMode mode) {
        if (closed) return;
        setIntent(draft(session), new DraftIntent(mode, Objects.requireNonNull(pendingId, "pendingId"), false));
    }

    /** Queue consumption/cancellation invalidates the edit; it never silently turns it into a new task. */
    public void invalidatePendingEdit(String session, UUID pendingId) {
        if (closed) return;
        Draft draft = draft(session);
        if (Objects.equals(draft.intent.pendingId(), pendingId) && pendingId != null) {
            setIntent(draft, new DraftIntent(draft.intent.mode(), pendingId, true));
        }
    }

    public boolean invalidatePendingEdit(IntentCapture capture) {
        if (!currentIntent(capture)) return false;
        invalidatePendingEdit(capture.session(), capture.intent().pendingId());
        return true;
    }

    /** Explicit player action: keep all text/images but use the draft as a new message. */
    public void resetIntent(String session) {
        if (!closed) setIntent(draft(session), DraftIntent.defaults());
    }

    public void stopIntent(String session) {
        if (closed) return;
        DraftIntent intent = intent(session);
        if (intent.editing()) invalidatePendingEdit(session, intent.pendingId());
        else resetIntent(session);
    }

    public IntentCapture captureIntent(String session) {
        if (closed) return new IntentCapture(ownerId, generation, session, -1, DraftIntent.defaults());
        Draft draft = draft(session);
        return new IntentCapture(ownerId, generation, session, draft.intentRevision, draft.intent);
    }

    public boolean beginIntentSubmission(IntentCapture capture) {
        if (!currentIntent(capture)) return false;
        Draft draft = drafts.get(capture.session());
        if (draft.inFlight != null) return false;
        draft.inFlight = capture;
        changed();
        return true;
    }

    public void completeIntentSubmission(IntentCapture capture) {
        if (closed || capture == null || !ownerId.equals(capture.ownerId()) || generation != capture.generation()) return;
        Draft draft = drafts.get(capture.session());
        if (draft != null && capture.equals(draft.inFlight)) {
            draft.inFlight = null;
            changed();
        }
    }

    public boolean intentSubmissionInFlight(String session) {
        return !closed && draft(session).inFlight != null;
    }

    /** Skip missing-target validation only while exactly this current pending edit is awaiting acceptance. */
    public boolean pendingEditSubmissionInFlight(String session) {
        if (closed) return false;
        Draft draft = draft(session);
        return draft.intent.editing() && draft.inFlight != null && currentIntent(draft.inFlight);
    }

    /** An old acceptance cannot clear an intent selected after submission. */
    public boolean clearAcceptedIntent(IntentCapture capture) {
        if (!currentIntent(capture)) return false;
        resetIntent(capture.session());
        return true;
    }

    private boolean currentIntent(IntentCapture capture) {
        if (closed || capture == null || !ownerId.equals(capture.ownerId()) || generation != capture.generation()) return false;
        Draft draft = drafts.get(capture.session());
        return draft != null && draft.intentRevision == capture.intentRevision() && draft.intent.equals(capture.intent());
    }

    private void setIntent(Draft draft, DraftIntent intent) {
        if (draft.intent.equals(intent)) return;
        draft.intent = intent;
        draft.intentRevision++;
        draft.revision++;
        changed();
    }

    /** The frozen draft source, separate from the live world.focus sample. */
    public Optional<ClientObservationAnchor> observation(String session) {
        return closed ? Optional.empty() : Optional.ofNullable(draft(session).observation);
    }

    public boolean observationInitialized(String session) {
        return !closed && draft(session).observationInitialized;
    }

    /** Reopening a view never replaces a prior entry source or an explicit removal. */
    public boolean seedObservation(String session, ClientObservationAnchor anchor) {
        if (closed || draft(session).observationInitialized) return false;
        setObservation(draft(session), Objects.requireNonNull(anchor, "anchor"), true);
        return true;
    }

    public ObservationCapture captureObservation(String session) {
        Draft draft = closed ? null : draft(session);
        return new ObservationCapture(ownerId, generation, session,
                draft == null ? -1 : draft.observationRevision,
                draft == null ? Optional.empty() : Optional.ofNullable(draft.observation));
    }

    /** A late capture may only update its unchanged original draft, even after a view switches. */
    public boolean replaceObservation(ObservationCapture capture, ClientObservationAnchor anchor) {
        if (!currentObservation(capture)) return false;
        setObservation(drafts.get(capture.session()), Objects.requireNonNull(anchor, "anchor"), true);
        return true;
    }

    public void removeObservation(String session) {
        if (!closed) setObservation(draft(session), null, true);
    }

    /** Remove pixels without relabeling the remaining focus or its association/source time. */
    public void removeObservationImage(String session) {
        if (closed) return;
        ClientObservationAnchor anchor = draft(session).observation;
        if (anchor == null || dev.openallay.util.Java8ApiSupport.isEmpty(anchor.image())) return;
        setObservation(draft(session), new ClientObservationAnchor(anchor.associationId(),
                anchor.capturedAt(), anchor.focus(), Optional.empty()), true);
    }

    /** An old acceptance must not clear a newer refresh or another connection's draft. */
    public boolean acceptedObservation(ObservationCapture capture) {
        if (!currentObservation(capture)) return false;
        setObservation(drafts.get(capture.session()), null, false);
        return true;
    }

    /** Pins the captured image independently of later draft edits. Close on voice/input completion. */
    public ObservationLease leaseObservation(ObservationCapture capture) {
        if (closed || capture == null || !ownerId.equals(capture.ownerId())
                || generation != capture.generation() || !drafts.containsKey(capture.session())) {
            throw new IllegalStateException("Observation connection is no longer available");
        }
        UUID id = UUID.randomUUID();
        observationLeases.put(id, capture);
        retainImages();
        return new ObservationLease(id, capture);
    }

    /** Public managed refs only; never write imported frames into ComposerImageDraft's internals. */
    public List<ImageReference> inputImageReferences(String session, ObservationCapture capture) {
        java.util.stream.Stream<ImageReference> observed = closed || capture == null || !ownerId.equals(capture.ownerId())
                || generation != capture.generation() || !session.equals(capture.session()) ? java.util.stream.Stream.empty()
                : dev.openallay.util.Java8ApiSupport.stream(capture.anchor()).flatMap(anchor -> dev.openallay.util.Java8ApiSupport.stream(anchor.image())).map(view -> view.image());
        return dev.openallay.util.Java8Collections.toList(java.util.stream.Stream.concat(images.attachments(session).stream()
                .map(ComposerImageDraft.Attachment::reference).filter(Objects::nonNull), observed).distinct());
    }

    private boolean currentObservation(ObservationCapture capture) {
        if (closed || capture == null || !ownerId.equals(capture.ownerId()) || generation != capture.generation()) return false;
        Draft draft = drafts.get(capture.session());
        return draft != null && draft.observationRevision == capture.observationRevision()
                && Objects.equals(Optional.ofNullable(draft.observation), capture.anchor());
    }

    private void setObservation(Draft draft, ClientObservationAnchor anchor, boolean initialized) {
        if (Objects.equals(draft.observation, anchor) && draft.observationInitialized == initialized) return;
        draft.observation = anchor;
        draft.observationInitialized = initialized;
        draft.observationRevision++;
        draft.revision++;
        retainImages();
        changed();
    }

    public Insertion captureInsertion(String session) {
        return new Insertion(ownerId, generation, session, revision(session));
    }

    /** Final transcription appends only to the captured unedited draft; changed drafts get a pending result. */
    public InsertionResult insertTranscript(Insertion capture, String transcript) {
        return insertTranscript(capture, transcript, null);
    }

    public InsertionResult insertTranscript(Insertion capture, String transcript, ObservationCapture observation) {
        if (closed || capture == null || !ownerId.equals(capture.ownerId)
                || generation != capture.generation || transcript == null || dev.openallay.util.Java8Strings.isBlank(transcript)) {
            return InsertionResult.REJECTED;
        }
        Draft draft = drafts.get(capture.session);
        if (draft == null) return InsertionResult.REJECTED;
        if (draft.revision != capture.revision) {
            draft.pending.add(new PendingInsertion(UUID.randomUUID(), capture.session, transcript, pendingAnchor(capture, observation)));
            if (draft.pending.get(draft.pending.size() - 1).observation().isPresent()) retainImages();
            changed();
            return InsertionResult.PENDING;
        }
        setText(capture.session, append(draft.text, transcript));
        return InsertionResult.INSERTED;
    }

    /** Keep a refused voice send for explicit review without changing the composer or its intent. */
    public boolean retainPendingTranscript(Insertion capture, String transcript) {
        return retainPendingTranscript(capture, transcript, null);
    }

    public boolean retainPendingTranscript(Insertion capture, String transcript, ObservationCapture observation) {
        if (closed || capture == null || !ownerId.equals(capture.ownerId())
                || generation != capture.generation() || transcript == null || dev.openallay.util.Java8Strings.isBlank(transcript)) return false;
        Draft draft = drafts.get(capture.session());
        if (draft == null) return false;
        draft.pending.add(new PendingInsertion(UUID.randomUUID(), capture.session(), transcript, pendingAnchor(capture, observation)));
        if (draft.pending.get(draft.pending.size() - 1).observation().isPresent()) retainImages();
        changed();
        return true;
    }

    private Optional<ClientObservationAnchor> pendingAnchor(Insertion insertion, ObservationCapture observation) {
        return observation != null && ownerId.equals(observation.ownerId()) && generation == observation.generation()
                && insertion.session().equals(observation.session()) ? observation.anchor() : Optional.empty();
    }

    public List<PendingInsertion> pendingInsertions(String session) {
        return closed ? dev.openallay.util.Java8Collections.listOf() : dev.openallay.util.Java8Collections.listCopyOf(draft(session).pending);
    }

    public boolean applyPendingInsertion(UUID id) {
        if (closed) return false;
        for (java.util.Map.Entry<java.lang.String, dev.openallay.client.gui.GuideClientUiState.Draft> entry : drafts.entrySet()) {
            PendingInsertion pending = entry.getValue().pending.stream()
                    .filter(value -> value.id.equals(id)).findFirst().orElse(null);
            if (pending == null) continue;
            entry.getValue().pending.remove(pending);
            pending.observation().ifPresent(anchor -> setObservation(entry.getValue(), anchor, true));
            if (pending.observation().isPresent()) retainImages();
            setText(entry.getKey(), append(entry.getValue().text, pending.text));
            return true;
        }
        return false;
    }

    public boolean clearAcceptedText(Insertion capture, String capturedText) {
        if (closed || !ownerId.equals(capture.ownerId) || generation != capture.generation) return false;
        Draft draft = drafts.get(capture.session);
        if (draft == null || draft.revision != capture.revision || !draft.text.equals(capturedText)) return false;
        setText(capture.session, "");
        return true;
    }

    public void clearDraft(String session) {
        if (closed) return;
        String previous = selectedSession;
        selectSession(session);
        setText(session, "");
        resetIntent(session);
        draft(session).inFlight = null;
        draft(session).pending.clear();
        images.clear();
        setObservation(draft(session), null, false);
        retainImages();
        selectSession(previous);
        changed();
    }

    public ViewAttachment attach(Surface surface, String session) {
        if (closed) throw new IllegalStateException("UI connection is closed");
        ViewAttachment attachment = new ViewAttachment(surface, session);
        views.put(attachment.id, attachment);
        return attachment;
    }

    public boolean visible(Surface surface, String session) {
        return !closed && views.values().stream().anyMatch(view -> view.surface == surface && view.session.equals(session));
    }

    public AutoCloseable subscribe(Runnable listener) {
        if (closed) return () -> {};
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    private Draft draft(String session) {
        return drafts.computeIfAbsent(Objects.requireNonNull(session, "session"), ignored -> new Draft());
    }
    private static String append(String existing, String transcript) {
        return dev.openallay.util.Java8Strings.isBlank(existing) ? transcript : existing + (existing.endsWith("\n") ? "" : "\n") + transcript;
    }
    private void imagesChanged(ComposerImageDraft.Notice notice) {
        if (closed) return;
        imageNotice = notice;
        String session = images.changedSession();
        if (session != null) draft(session).revision++;
        retainImages();
        changed();
    }
    private void retainImages() {
        java.util.stream.Stream<dev.openallay.world.ClientObservationAnchor> draftAnchors = drafts.values().stream().map(value -> value.observation).filter(Objects::nonNull);
        java.util.stream.Stream<dev.openallay.world.ClientObservationAnchor> heldAnchors = observationLeases.values().stream().flatMap(value -> dev.openallay.util.Java8ApiSupport.stream(value.anchor()));
        java.util.stream.Stream<dev.openallay.world.ClientObservationAnchor> pendingAnchors = drafts.values().stream().flatMap(value -> value.pending.stream())
                .flatMap(value -> dev.openallay.util.Java8ApiSupport.stream(value.observation()));
        List<ImageReference> refs = dev.openallay.util.Java8Collections.toList(java.util.stream.Stream.concat(images.retainedReferences().stream(),
                java.util.stream.Stream.concat(java.util.stream.Stream.concat(draftAnchors, heldAnchors), pendingAnchors)
                        .flatMap(anchor -> dev.openallay.util.Java8ApiSupport.stream(anchor.image())).map(view -> view.image())).distinct());
        // Serialize retain updates so a slower old lease update cannot drop a newly added image.
        leaseWork = leaseWork.handle((ignored, failure) -> null).thenCompose(ignored -> retain.apply(refs));
    }
    private void changed() { dev.openallay.util.Java8Collections.listCopyOf(listeners).forEach(Runnable::run); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        generation++;
        images.detach();
        views.clear();
        drafts.clear();
        observationLeases.clear();
        listeners.clear();
        leaseWork.handle((ignored, failure) -> null).thenRun(release);
    }

    public enum SubmissionRoute { ASK, FOLLOW_UP, STEER, EDIT_PENDING, EDIT_INVALID }
    public static SubmissionRoute submissionRoute(DraftIntent intent, boolean requestActive) {
        Objects.requireNonNull(intent, "intent");
        if (intent.editInvalid()) return SubmissionRoute.EDIT_INVALID;
        if (intent.editing()) return SubmissionRoute.EDIT_PENDING;
        return requestActive ? intent.steer() ? SubmissionRoute.STEER : SubmissionRoute.FOLLOW_UP : SubmissionRoute.ASK;
    }
    public enum DraftMode { FOLLOW_UP, STEER }
    @dev.openallay.value.ValueType(DraftIntent.ValueSchemaProvider.class)
public static final class DraftIntent {
    private final DraftMode mode;
    private final UUID pendingId;
    private final boolean editInvalid;
    public DraftIntent(DraftMode mode, UUID pendingId, boolean editInvalid) {

            Objects.requireNonNull(mode, "mode");
            if (editInvalid && pendingId == null) throw new IllegalArgumentException("An invalid edit needs its captured target");

        this.mode = mode;
        this.pendingId = pendingId;
        this.editInvalid = editInvalid;
    }
    public DraftMode mode() { return mode; }
    public UUID pendingId() { return pendingId; }
    public boolean editInvalid() { return editInvalid; }
public static DraftIntent defaults() { return new DraftIntent(DraftMode.FOLLOW_UP, null, false); }
public boolean editing() { return pendingId != null; }
public boolean steer() { return mode == DraftMode.STEER; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DraftIntent)) return false;
        DraftIntent that = (DraftIntent) other;
        return java.util.Objects.equals(mode, that.mode) && java.util.Objects.equals(pendingId, that.pendingId) && editInvalid == that.editInvalid;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(mode);
        hash = 31 * hash + java.util.Objects.hashCode(pendingId);
        hash = 31 * hash + Boolean.hashCode(editInvalid);
        return hash;
    }
    @Override public String toString() { return "DraftIntent[mode=" + mode + ", pendingId=" + pendingId + ", editInvalid=" + editInvalid + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<DraftIntent> schema() {
            return new dev.openallay.value.ValueSchema<>(DraftIntent.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<DraftIntent>>asList(new dev.openallay.value.ValueSchema.Component<>(DraftIntent.class, "mode", DraftIntent::mode), new dev.openallay.value.ValueSchema.Component<>(DraftIntent.class, "pendingId", DraftIntent::pendingId), new dev.openallay.value.ValueSchema.Component<>(DraftIntent.class, "editInvalid", DraftIntent::editInvalid)), arguments -> new DraftIntent((DraftMode) arguments[0], (UUID) arguments[1], (Boolean) arguments[2]));
        }
    }
}
    @dev.openallay.value.ValueType(IntentCapture.ValueSchemaProvider.class)
public static final class IntentCapture {
    private final String ownerId;
    private final long generation;
    private final String session;
    private final long intentRevision;
    private final DraftIntent intent;
    public IntentCapture(String ownerId, long generation, String session, long intentRevision, DraftIntent intent) {
        this.ownerId = ownerId;
        this.generation = generation;
        this.session = session;
        this.intentRevision = intentRevision;
        this.intent = intent;
    }
    public String ownerId() { return ownerId; }
    public long generation() { return generation; }
    public String session() { return session; }
    public long intentRevision() { return intentRevision; }
    public DraftIntent intent() { return intent; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IntentCapture)) return false;
        IntentCapture that = (IntentCapture) other;
        return java.util.Objects.equals(ownerId, that.ownerId) && generation == that.generation && java.util.Objects.equals(session, that.session) && intentRevision == that.intentRevision && java.util.Objects.equals(intent, that.intent);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(ownerId);
        hash = 31 * hash + Long.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(session);
        hash = 31 * hash + Long.hashCode(intentRevision);
        hash = 31 * hash + java.util.Objects.hashCode(intent);
        return hash;
    }
    @Override public String toString() { return "IntentCapture[ownerId=" + ownerId + ", generation=" + generation + ", session=" + session + ", intentRevision=" + intentRevision + ", intent=" + intent + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<IntentCapture> schema() {
            return new dev.openallay.value.ValueSchema<>(IntentCapture.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<IntentCapture>>asList(new dev.openallay.value.ValueSchema.Component<>(IntentCapture.class, "ownerId", IntentCapture::ownerId), new dev.openallay.value.ValueSchema.Component<>(IntentCapture.class, "generation", IntentCapture::generation), new dev.openallay.value.ValueSchema.Component<>(IntentCapture.class, "session", IntentCapture::session), new dev.openallay.value.ValueSchema.Component<>(IntentCapture.class, "intentRevision", IntentCapture::intentRevision), new dev.openallay.value.ValueSchema.Component<>(IntentCapture.class, "intent", IntentCapture::intent)), arguments -> new IntentCapture((String) arguments[0], (Long) arguments[1], (String) arguments[2], (Long) arguments[3], (DraftIntent) arguments[4]));
        }
    }
}
    public enum Surface { FULLSCREEN, HUD_INPUT }
    public enum InsertionResult { INSERTED, PENDING, REJECTED }
    @dev.openallay.value.ValueType(ObservationCapture.ValueSchemaProvider.class)
public static final class ObservationCapture {
    private final String ownerId;
    private final long generation;
    private final String session;
    private final long observationRevision;
    private final Optional<ClientObservationAnchor> anchor;
    public ObservationCapture(String ownerId, long generation, String session, long observationRevision, Optional<ClientObservationAnchor> anchor) {

            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(anchor, "anchor");

        this.ownerId = ownerId;
        this.generation = generation;
        this.session = session;
        this.observationRevision = observationRevision;
        this.anchor = anchor;
    }
    public String ownerId() { return ownerId; }
    public long generation() { return generation; }
    public String session() { return session; }
    public long observationRevision() { return observationRevision; }
    public Optional<ClientObservationAnchor> anchor() { return anchor; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ObservationCapture)) return false;
        ObservationCapture that = (ObservationCapture) other;
        return java.util.Objects.equals(ownerId, that.ownerId) && generation == that.generation && java.util.Objects.equals(session, that.session) && observationRevision == that.observationRevision && java.util.Objects.equals(anchor, that.anchor);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(ownerId);
        hash = 31 * hash + Long.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(session);
        hash = 31 * hash + Long.hashCode(observationRevision);
        hash = 31 * hash + java.util.Objects.hashCode(anchor);
        return hash;
    }
    @Override public String toString() { return "ObservationCapture[ownerId=" + ownerId + ", generation=" + generation + ", session=" + session + ", observationRevision=" + observationRevision + ", anchor=" + anchor + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<ObservationCapture> schema() {
            return new dev.openallay.value.ValueSchema<>(ObservationCapture.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<ObservationCapture>>asList(new dev.openallay.value.ValueSchema.Component<>(ObservationCapture.class, "ownerId", ObservationCapture::ownerId), new dev.openallay.value.ValueSchema.Component<>(ObservationCapture.class, "generation", ObservationCapture::generation), new dev.openallay.value.ValueSchema.Component<>(ObservationCapture.class, "session", ObservationCapture::session), new dev.openallay.value.ValueSchema.Component<>(ObservationCapture.class, "observationRevision", ObservationCapture::observationRevision), new dev.openallay.value.ValueSchema.Component<>(ObservationCapture.class, "anchor", ObservationCapture::anchor)), arguments -> new ObservationCapture((String) arguments[0], (Long) arguments[1], (String) arguments[2], (Long) arguments[3], (Optional) arguments[4]));
        }
    }
}
    public final class ObservationLease implements AutoCloseable {
        private final UUID id;
        private final ObservationCapture capture;
        private ObservationLease(UUID id, ObservationCapture capture) { this.id = id; this.capture = capture; }
        public ObservationCapture capture() { return capture; }
        @Override public void close() {
            if (observationLeases.remove(id) != null && !closed) retainImages();
        }
    }
    @dev.openallay.value.ValueType(Insertion.ValueSchemaProvider.class)
public static final class Insertion {
    private final String ownerId;
    private final long generation;
    private final String session;
    private final long revision;
    public Insertion(String ownerId, long generation, String session, long revision) {
        this.ownerId = ownerId;
        this.generation = generation;
        this.session = session;
        this.revision = revision;
    }
    public String ownerId() { return ownerId; }
    public long generation() { return generation; }
    public String session() { return session; }
    public long revision() { return revision; }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Insertion)) return false;
        Insertion that = (Insertion) other;
        return java.util.Objects.equals(ownerId, that.ownerId) && generation == that.generation && java.util.Objects.equals(session, that.session) && revision == that.revision;
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(ownerId);
        hash = 31 * hash + Long.hashCode(generation);
        hash = 31 * hash + java.util.Objects.hashCode(session);
        hash = 31 * hash + Long.hashCode(revision);
        return hash;
    }
    @Override public String toString() { return "Insertion[ownerId=" + ownerId + ", generation=" + generation + ", session=" + session + ", revision=" + revision + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<Insertion> schema() {
            return new dev.openallay.value.ValueSchema<>(Insertion.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<Insertion>>asList(new dev.openallay.value.ValueSchema.Component<>(Insertion.class, "ownerId", Insertion::ownerId), new dev.openallay.value.ValueSchema.Component<>(Insertion.class, "generation", Insertion::generation), new dev.openallay.value.ValueSchema.Component<>(Insertion.class, "session", Insertion::session), new dev.openallay.value.ValueSchema.Component<>(Insertion.class, "revision", Insertion::revision)), arguments -> new Insertion((String) arguments[0], (Long) arguments[1], (String) arguments[2], (Long) arguments[3]));
        }
    }
}
    @dev.openallay.value.ValueType(PendingInsertion.ValueSchemaProvider.class)
public static final class PendingInsertion {
    private final UUID id;
    private final String session;
    private final String text;
    private final Optional<ClientObservationAnchor> observation;
    public PendingInsertion(UUID id, String session, String text, Optional<ClientObservationAnchor> observation) {
 Objects.requireNonNull(observation, "observation");
        this.id = id;
        this.session = session;
        this.text = text;
        this.observation = observation;
    }
    public UUID id() { return id; }
    public String session() { return session; }
    public String text() { return text; }
    public Optional<ClientObservationAnchor> observation() { return observation; }
public PendingInsertion(UUID id, String session, String text) { this(id, session, text, Optional.empty()); }
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PendingInsertion)) return false;
        PendingInsertion that = (PendingInsertion) other;
        return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(session, that.session) && java.util.Objects.equals(text, that.text) && java.util.Objects.equals(observation, that.observation);
    }
    @Override public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(id);
        hash = 31 * hash + java.util.Objects.hashCode(session);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(observation);
        return hash;
    }
    @Override public String toString() { return "PendingInsertion[id=" + id + ", session=" + session + ", text=" + text + ", observation=" + observation + "]"; }
    public static final class ValueSchemaProvider implements dev.openallay.value.ValueSchema.Provider {
        public ValueSchemaProvider() {}
        @Override public dev.openallay.value.ValueSchema<PendingInsertion> schema() {
            return new dev.openallay.value.ValueSchema<>(PendingInsertion.class, java.util.Arrays.<dev.openallay.value.ValueSchema.Component<PendingInsertion>>asList(new dev.openallay.value.ValueSchema.Component<>(PendingInsertion.class, "id", PendingInsertion::id), new dev.openallay.value.ValueSchema.Component<>(PendingInsertion.class, "session", PendingInsertion::session), new dev.openallay.value.ValueSchema.Component<>(PendingInsertion.class, "text", PendingInsertion::text), new dev.openallay.value.ValueSchema.Component<>(PendingInsertion.class, "observation", PendingInsertion::observation)), arguments -> new PendingInsertion((UUID) arguments[0], (String) arguments[1], (String) arguments[2], (Optional) arguments[3]));
        }
    }
}
    private static final class Draft {
        String text = "";
        ClientObservationAnchor observation;
        boolean observationInitialized;
        long observationRevision;
        long revision;
        long intentRevision;
        DraftIntent intent = DraftIntent.defaults();
        IntentCapture inFlight;
        final List<PendingInsertion> pending = new ArrayList<>();
    }
    public final class ViewAttachment implements AutoCloseable {
        private final UUID id = UUID.randomUUID();
        private final Surface surface;
        private String session;
        private ViewAttachment(Surface surface, String session) {
            this.surface = Objects.requireNonNull(surface, "surface");
            this.session = Objects.requireNonNull(session, "session");
        }
        public void selectSession(String session) { this.session = Objects.requireNonNull(session, "session"); }
        @Override public void close() { views.remove(id); }
    }
}
