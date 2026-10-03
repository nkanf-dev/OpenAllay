package dev.openallay.client.gui;

import dev.openallay.client.ClientEventDispatcher;
import dev.openallay.client.gui.clipboard.ImageClipboard;
import dev.openallay.client.gui.clipboard.SystemImageClipboard;
import dev.openallay.guide.GuideService;
import dev.openallay.model.image.ImageReference;
import dev.openallay.tool.ToolResult;
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
    private final ComposerImageDraft images;
    private final Function<List<ImageReference>, CompletableFuture<ToolResult<Boolean>>> retain;
    private final Runnable release;
    private CompletableFuture<?> leaseWork = CompletableFuture.completedFuture(null);
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

    public static GuideClientUiState create(GuideService service, ClientEventDispatcher client) {
        return new GuideClientUiState(service, new SystemImageClipboard(),
                job -> Thread.ofVirtual().name("openallay-draft-image").start(job), client);
    }

    public String ownerId() { return ownerId; }
    public long generation() { return generation; }
    public boolean closed() { return closed; }
    public String selectedSession() { return selectedSession; }
    public ComposerImageDraft images() { return images; }
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

    public Insertion captureInsertion(String session) {
        return new Insertion(ownerId, generation, session, revision(session));
    }

    /** Final transcription appends only to the captured unedited draft; changed drafts get a pending result. */
    public InsertionResult insertTranscript(Insertion capture, String transcript) {
        if (closed || capture == null || !ownerId.equals(capture.ownerId)
                || generation != capture.generation || transcript == null || transcript.isBlank()) {
            return InsertionResult.REJECTED;
        }
        Draft draft = drafts.get(capture.session);
        if (draft == null) return InsertionResult.REJECTED;
        if (draft.revision != capture.revision) {
            draft.pending.add(new PendingInsertion(UUID.randomUUID(), capture.session, transcript));
            changed();
            return InsertionResult.PENDING;
        }
        setText(capture.session, append(draft.text, transcript));
        return InsertionResult.INSERTED;
    }

    /** Keep a refused voice send for explicit review without changing the composer or its intent. */
    public boolean retainPendingTranscript(Insertion capture, String transcript) {
        if (closed || capture == null || !ownerId.equals(capture.ownerId())
                || generation != capture.generation() || transcript == null || transcript.isBlank()) return false;
        Draft draft = drafts.get(capture.session());
        if (draft == null) return false;
        draft.pending.add(new PendingInsertion(UUID.randomUUID(), capture.session(), transcript));
        changed();
        return true;
    }

    public List<PendingInsertion> pendingInsertions(String session) {
        return closed ? List.of() : List.copyOf(draft(session).pending);
    }

    public boolean applyPendingInsertion(UUID id) {
        if (closed) return false;
        for (var entry : drafts.entrySet()) {
            PendingInsertion pending = entry.getValue().pending.stream()
                    .filter(value -> value.id.equals(id)).findFirst().orElse(null);
            if (pending == null) continue;
            entry.getValue().pending.remove(pending);
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
        return existing.isBlank() ? transcript : existing + (existing.endsWith("\n") ? "" : "\n") + transcript;
    }
    private void imagesChanged(ComposerImageDraft.Notice notice) {
        if (closed) return;
        imageNotice = notice;
        String session = images.changedSession();
        if (session != null) draft(session).revision++;
        List<ImageReference> refs = images.retainedReferences();
        // Serialize retain updates so a slower old lease update cannot drop a newly added image.
        leaseWork = leaseWork.handle((ignored, failure) -> null).thenCompose(ignored -> retain.apply(refs));
        changed();
    }
    private void changed() { List.copyOf(listeners).forEach(Runnable::run); }

    @Override public void close() {
        if (closed) return;
        closed = true;
        generation++;
        images.detach();
        views.clear();
        drafts.clear();
        listeners.clear();
        leaseWork = leaseWork.handle((ignored, failure) -> null).thenRun(release);
    }

    public enum SubmissionRoute { ASK, FOLLOW_UP, STEER, EDIT_PENDING, EDIT_INVALID }
    public static SubmissionRoute submissionRoute(DraftIntent intent, boolean requestActive) {
        Objects.requireNonNull(intent, "intent");
        if (intent.editInvalid()) return SubmissionRoute.EDIT_INVALID;
        if (intent.editing()) return SubmissionRoute.EDIT_PENDING;
        return requestActive ? intent.steer() ? SubmissionRoute.STEER : SubmissionRoute.FOLLOW_UP : SubmissionRoute.ASK;
    }
    public enum DraftMode { FOLLOW_UP, STEER }
    public record DraftIntent(DraftMode mode, UUID pendingId, boolean editInvalid) {
        public DraftIntent {
            Objects.requireNonNull(mode, "mode");
            if (editInvalid && pendingId == null) throw new IllegalArgumentException("An invalid edit needs its captured target");
        }
        public static DraftIntent defaults() { return new DraftIntent(DraftMode.FOLLOW_UP, null, false); }
        public boolean editing() { return pendingId != null; }
        public boolean steer() { return mode == DraftMode.STEER; }
    }
    public record IntentCapture(String ownerId, long generation, String session, long intentRevision, DraftIntent intent) {}
    public enum Surface { FULLSCREEN, HUD_INPUT }
    public enum InsertionResult { INSERTED, PENDING, REJECTED }
    public record Insertion(String ownerId, long generation, String session, long revision) {}
    public record PendingInsertion(UUID id, String session, String text) {}
    private static final class Draft {
        String text = "";
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
